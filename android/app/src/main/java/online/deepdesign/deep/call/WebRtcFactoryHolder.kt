package online.deepdesign.deep.call

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

/** WebRTC must be initialized once per process. */
object WebRtcFactoryHolder {
    @Volatile
    private var initialized = false

    private var factory: PeerConnectionFactory? = null

    val eglBase: EglBase = EglBase.create()

    fun getOrCreate(context: Context): PeerConnectionFactory {
        val appContext = context.applicationContext
        synchronized(this) {
            if (!initialized) {
                val initOpts = PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(initOpts)
                initialized = true
            }
            factory?.let { return it }
            val audioModule = JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()
            val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioModule)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            return factory!!
        }
    }
}
