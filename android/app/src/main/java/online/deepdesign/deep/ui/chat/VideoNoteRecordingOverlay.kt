package online.deepdesign.deep.ui.chat

import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepError
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepSurface
import online.deepdesign.deep.ui.theme.DeepText
import kotlin.math.roundToInt

@Composable
fun VideoNoteRecordingOverlay(
    durationMs: Long,
    locked: Boolean,
    maxDurationMs: Long = 60_000L,
    onPreviewView: (PreviewView) -> Unit,
    onFlipCamera: () -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    onLock: () -> Unit
) {
    val pulse by rememberInfiniteTransition(label = "vnPulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "vnPulseScale"
    )
    val ringAlpha by rememberInfiniteTransition(label = "vnRing").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "vnRingAlpha"
    )
    val progress = (durationMs.toFloat() / maxDurationMs).coerceIn(0f, 1f)
    val timerSec = (durationMs / 1000f).roundToInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    if (!locked && drag.y < -80f) onLock()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (locked) "Запись (свайп вверх — блокировка)" else "Свайп вверх — отпустить руки",
                color = DeepMuted,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(12.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
                Canvas(
                    modifier = Modifier
                        .size(280.dp)
                        .scale(pulse)
                ) {
                    drawCircle(
                        color = DeepAccent.copy(alpha = ringAlpha),
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = DeepError,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(248.dp)
                        .clip(CircleShape)
                        .background(DeepSurface)
                ) {
                    AndroidView(
                        factory = {
                            PreviewView(it).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                onPreviewView(this)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = formatVideoNoteTimer(timerSec),
                color = DeepText,
                style = MaterialTheme.typography.titleLarge
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = onFlipCamera,
                containerColor = DeepSurface.copy(alpha = 0.9f),
                contentColor = DeepText,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Cameraswitch, contentDescription = "Сменить камеру")
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionButton(
                onClick = onCancel,
                containerColor = DeepSurface,
                contentColor = DeepText,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Отмена")
            }
            if (locked) {
                FloatingActionButton(
                    onClick = onLock,
                    containerColor = DeepSurface,
                    contentColor = DeepAccent,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Заблокировано")
                }
            }
            FloatingActionButton(
                onClick = onSend,
                containerColor = DeepAccent,
                contentColor = DeepText,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Отправить")
            }
        }
    }
}

private fun formatVideoNoteTimer(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}
