package online.deepdesign.deep.ui.call

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import online.deepdesign.deep.call.WebRtcFactoryHolder
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private const val TRACK_TAG_KEY = 0x7dee0001

@Composable
fun WebRtcVideoView(
    track: VideoTrack?,
    mirror: Boolean,
    modifier: Modifier = Modifier
) {
    val eglContext = remember { WebRtcFactoryHolder.eglBase.eglBaseContext }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
            }
        },
        update = { renderer ->
            renderer.setMirror(mirror)
            @Suppress("UNCHECKED_CAST")
            val previous = renderer.getTag(TRACK_TAG_KEY) as? VideoTrack
            if (previous !== track) {
                previous?.removeSink(renderer)
                renderer.setTag(TRACK_TAG_KEY, track)
                track?.addSink(renderer)
            }
        },
        onRelease = { renderer ->
            @Suppress("UNCHECKED_CAST")
            (renderer.getTag(TRACK_TAG_KEY) as? VideoTrack)?.removeSink(renderer)
            renderer.setTag(TRACK_TAG_KEY, null)
            renderer.release()
        }
    )
}
