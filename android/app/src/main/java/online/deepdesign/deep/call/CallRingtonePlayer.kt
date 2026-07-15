package online.deepdesign.deep.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class CallRingtonePlayer(context: Context) {
    private val appContext = context.applicationContext
    private var ringtone: Ringtone? = null
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var ringbackHandler: Handler? = null
    private var ringbackActive = false

    fun playIncoming() {
        stop()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone = RingtoneManager.getRingtone(appContext, uri)?.apply {
                    isLooping = true
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
            } else {
                playLooped(uri, AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "playIncoming failed", e)
        }
    }

    fun playOutgoingRingback() {
        stop()
        ringbackActive = true
        ensureCallAudioMode()
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 85)
            ringbackHandler = Handler(Looper.getMainLooper())
            ringbackHandler?.post(ringbackRunnable)
        } catch (e: Exception) {
            Log.w(TAG, "playOutgoingRingback failed", e)
        }
    }

    fun onAudioRouteChanged() {
        if (!ringbackActive) return
        refreshRingbackGenerator()
    }

    private fun ensureCallAudioMode() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private val ringbackRunnable = object : Runnable {
        override fun run() {
            if (!ringbackActive) return
            val tg = toneGenerator ?: return
            tg.startTone(ToneGenerator.TONE_CDMA_NETWORK_USA_RINGBACK, 2000)
            ringbackHandler?.postDelayed(this, 5000)
        }
    }

    private fun playLooped(uri: android.net.Uri, usage: Int) {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(appContext, uri)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun refreshRingbackGenerator() {
        runCatching { toneGenerator?.stopTone() }
        runCatching { toneGenerator?.release() }
        toneGenerator = null
        try {
            ensureCallAudioMode()
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 85)
        } catch (e: Exception) {
            Log.w(TAG, "refreshRingbackGenerator failed", e)
        }
    }

    fun stop() {
        ringbackActive = false
        ringbackHandler?.removeCallbacksAndMessages(null)
        ringbackHandler = null
        runCatching { toneGenerator?.release() }
        toneGenerator = null
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    companion object {
        private const val TAG = "CallRingtonePlayer"
    }
}
