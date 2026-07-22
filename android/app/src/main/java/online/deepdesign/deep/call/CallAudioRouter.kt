package online.deepdesign.deep.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Routes call audio to earpiece, speaker, Bluetooth or wired headset.
 * Supports separate microphone source when the OS allows it.
 */
class CallAudioRouter(private val context: Context) {
    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(CallAudioUiState())
    val state: StateFlow<CallAudioUiState> = _state.asStateFlow()

    private var sessionActive = false
    private var userPinnedOutput = false
    private var legacyScoStarted = false
    private var audioFocusRequest: AudioFocusRequest? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            onDevicesChanged(addedDevices.toList(), added = true)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            onDevicesChanged(removedDevices.toList(), added = false)
        }
    }

    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) return
            val scoState = intent.getIntExtra(
                AudioManager.EXTRA_SCO_AUDIO_STATE,
                AudioManager.SCO_AUDIO_STATE_ERROR
            )
            if (scoState == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                applyRouting()
            }
        }
    }

    fun startSession() {
        if (sessionActive) {
            applyRouting()
            return
        }
        sessionActive = true
        userPinnedOutput = false
        requestAudioFocus()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                scoReceiver,
                IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            appContext.registerReceiver(
                scoReceiver,
                IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            )
        }
        refreshDevices()
        autoSelectHeadsetIfNeeded()
        applyRouting()
    }

    fun stopSession() {
        if (!sessionActive) return
        sessionActive = false
        userPinnedOutput = false
        stopLegacySco()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        }
        runCatching { appContext.unregisterReceiver(scoReceiver) }
        abandonAudioFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        }
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
        _state.value = CallAudioUiState()
    }

    fun setOutputRoute(route: CallOutputRoute) {
        if (!sessionActive) return
        userPinnedOutput = true
        val normalized = normalizeOutput(route)
        val input = normalizeInputForOutput(_state.value.inputRoute, normalized)
        _state.update {
            it.copy(
                outputRoute = normalized,
                inputRoute = input,
                outputLabel = outputLabel(normalized),
                inputLabel = input.label
            )
        }
        applyRouting()
    }

    fun setInputRoute(route: CallInputRoute) {
        if (!sessionActive) return
        val normalized = normalizeInput(route)
        val output = normalizeOutputForInput(_state.value.outputRoute, normalized)
        _state.update {
            it.copy(
                inputRoute = normalized,
                outputRoute = output,
                outputLabel = outputLabel(output),
                inputLabel = normalized.label
            )
        }
        applyRouting()
    }

    fun cycleOutputRoute() {
        if (!sessionActive) return
        val s = _state.value
        val next = when (s.outputRoute) {
            CallOutputRoute.Earpiece -> CallOutputRoute.Speaker
            CallOutputRoute.Speaker -> when {
                s.bluetoothAvailable -> CallOutputRoute.Bluetooth
                s.wiredAvailable -> CallOutputRoute.Wired
                else -> CallOutputRoute.Earpiece
            }
            CallOutputRoute.Bluetooth -> when {
                s.wiredAvailable -> CallOutputRoute.Wired
                else -> CallOutputRoute.Earpiece
            }
            CallOutputRoute.Wired -> CallOutputRoute.Earpiece
        }
        setOutputRoute(next)
    }

    fun refreshDevicesNow() {
        if (!sessionActive) return
        refreshDevices()
        applyRouting()
    }

    fun applyRouting() {
        if (!sessionActive) return
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applyRoutingApi31()
        } else {
            applyRoutingLegacy()
        }
    }

    private fun onDevicesChanged(devices: List<AudioDeviceInfo>, added: Boolean) {
        val hadHeadset = _state.value.bluetoothAvailable || _state.value.wiredAvailable
        refreshDevices()
        val hasHeadset = _state.value.bluetoothAvailable || _state.value.wiredAvailable
        if (!sessionActive) return

        if (added && hasHeadset && !hadHeadset && !userPinnedOutput) {
            autoSelectHeadsetIfNeeded()
        }
        if (!hasHeadset) {
            val current = _state.value
            if (current.outputRoute == CallOutputRoute.Bluetooth ||
                current.outputRoute == CallOutputRoute.Wired
            ) {
                _state.update {
                    it.copy(
                        outputRoute = CallOutputRoute.Earpiece,
                        inputRoute = CallInputRoute.Phone,
                        outputLabel = CallOutputRoute.Earpiece.label,
                        inputLabel = CallInputRoute.Phone.label
                    )
                }
            }
        }
        applyRouting()
    }

    private fun autoSelectHeadsetIfNeeded() {
        val s = _state.value
        if (userPinnedOutput) return
        when {
            s.bluetoothAvailable -> {
                _state.update {
                    it.copy(
                        outputRoute = CallOutputRoute.Bluetooth,
                        inputRoute = CallInputRoute.Headset,
                        outputLabel = outputLabel(CallOutputRoute.Bluetooth),
                        inputLabel = CallInputRoute.Headset.label
                    )
                }
            }
            s.wiredAvailable -> {
                _state.update {
                    it.copy(
                        outputRoute = CallOutputRoute.Wired,
                        inputRoute = CallInputRoute.Headset,
                        outputLabel = CallOutputRoute.Wired.label,
                        inputLabel = CallInputRoute.Headset.label
                    )
                }
            }
        }
    }

    private fun refreshDevices() {
        val bluetooth = findBluetoothDevice()
        val wired = findWiredDevice()
        val bluetoothAvailable = bluetooth != null
        val wiredAvailable = wired != null
        val headsetInputAvailable = bluetoothAvailable || wiredAvailable
        val bluetoothLabel = bluetooth?.productName?.toString()?.takeIf { it.isNotBlank() }
            ?: "Bluetooth"
        _state.update {
            it.copy(
                bluetoothAvailable = bluetoothAvailable,
                wiredAvailable = wiredAvailable,
                headsetInputAvailable = headsetInputAvailable,
                bluetoothLabel = bluetoothLabel,
                outputLabel = outputLabel(it.outputRoute),
                inputLabel = it.inputRoute.label
            )
        }
    }

    private fun normalizeOutput(route: CallOutputRoute): CallOutputRoute {
        val s = _state.value
        return when (route) {
            CallOutputRoute.Bluetooth -> if (s.bluetoothAvailable) route else CallOutputRoute.Earpiece
            CallOutputRoute.Wired -> if (s.wiredAvailable) route else CallOutputRoute.Earpiece
            else -> route
        }
    }

    private fun normalizeInput(route: CallInputRoute): CallInputRoute {
        if (route == CallInputRoute.Headset && !_state.value.headsetInputAvailable) {
            return CallInputRoute.Phone
        }
        return route
    }

    private fun normalizeInputForOutput(
        input: CallInputRoute,
        output: CallOutputRoute
    ): CallInputRoute {
        if (output == CallOutputRoute.Bluetooth || output == CallOutputRoute.Wired) {
            return CallInputRoute.Headset
        }
        return normalizeInput(input)
    }

    private fun normalizeOutputForInput(
        output: CallOutputRoute,
        input: CallInputRoute
    ): CallOutputRoute {
        if (input != CallInputRoute.Headset) return normalizeOutput(output)
        return when {
            _state.value.bluetoothAvailable -> CallOutputRoute.Bluetooth
            _state.value.wiredAvailable -> CallOutputRoute.Wired
            else -> normalizeOutput(output)
        }
    }

    private fun outputLabel(route: CallOutputRoute): String = when (route) {
        CallOutputRoute.Bluetooth -> _state.value.bluetoothLabel
        else -> route.label
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun applyRoutingApi31() {
        stopLegacySco()
        val output = _state.value.outputRoute
        val input = _state.value.inputRoute

        when (output) {
            CallOutputRoute.Speaker -> {
                audioManager.clearCommunicationDevice()
                audioManager.isSpeakerphoneOn = true
            }
            CallOutputRoute.Bluetooth -> {
                audioManager.isSpeakerphoneOn = false
                val device = findBluetoothDevice()
                if (device != null && audioManager.setCommunicationDevice(device)) return
                startLegacySco()
            }
            CallOutputRoute.Wired -> {
                audioManager.isSpeakerphoneOn = false
                findWiredDevice()?.let { audioManager.setCommunicationDevice(it) }
            }
            CallOutputRoute.Earpiece -> {
                audioManager.isSpeakerphoneOn = false
                if (input == CallInputRoute.Headset) {
                    findWiredDevice()?.let {
                        audioManager.setCommunicationDevice(it)
                        return
                    }
                    findBluetoothDevice()?.let {
                        audioManager.setCommunicationDevice(it)
                        return
                    }
                }
                findDevice(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)?.let {
                    if (audioManager.setCommunicationDevice(it)) return
                }
                audioManager.clearCommunicationDevice()
            }
        }
    }

    private fun applyRoutingLegacy() {
        stopLegacySco()
        when (_state.value.outputRoute) {
            CallOutputRoute.Speaker -> {
                audioManager.isSpeakerphoneOn = true
            }
            CallOutputRoute.Bluetooth -> {
                audioManager.isSpeakerphoneOn = false
                startLegacySco()
            }
            CallOutputRoute.Wired -> {
                audioManager.isSpeakerphoneOn = false
            }
            CallOutputRoute.Earpiece -> {
                audioManager.isSpeakerphoneOn = false
                if (_state.value.inputRoute == CallInputRoute.Headset &&
                    _state.value.bluetoothAvailable
                ) {
                    startLegacySco()
                }
            }
        }
    }

    private fun startLegacySco() {
        if (legacyScoStarted) return
        if (!_state.value.bluetoothAvailable) return
        runCatching {
            audioManager.startBluetoothSco()
            legacyScoStarted = true
        }.onFailure { Log.w(TAG, "startBluetoothSco failed", it) }
    }

    private fun stopLegacySco() {
        if (!legacyScoStarted) return
        runCatching {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }
        legacyScoStarted = false
    }

    private fun findBluetoothDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            appContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return audioManager.availableCommunicationDevices.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            } ?: findDevice(
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET
            )
        }
        return findDevice(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET
        )
    }

    private fun findWiredDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return audioManager.availableCommunicationDevices.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
        }
        return findDevice(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        )
    }

    private fun findDevice(vararg types: Int): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return (outputs + inputs).firstOrNull { device -> types.contains(device.type) }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { focus ->
                    when (focus) {
                        AudioManager.AUDIOFOCUS_GAIN,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                            if (sessionActive) {
                                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                                applyRouting()
                            }
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            if (sessionActive) requestAudioFocus()
                        }
                    }
                }
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focus ->
                    if (focus == AudioManager.AUDIOFOCUS_GAIN && sessionActive) {
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        applyRouting()
                    }
                },
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    companion object {
        private const val TAG = "CallAudioRouter"
    }
}
