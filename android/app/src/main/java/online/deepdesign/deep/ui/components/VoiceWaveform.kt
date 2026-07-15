package online.deepdesign.deep.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun VoiceWaveform(
    seed: String,
    progress: Float,
    playing: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    bars: Int = 32
) {
    val heights = remember(seed) {
        List(bars) { i ->
            val v = sin(seed.hashCode() * 0.31 + i * 0.85).toFloat()
            6 + ((v + 1f) / 2f * 18).toInt()
        }
    }
    val pulse by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEachIndexed { i, h ->
            val barProgress = i.toFloat() / bars
            val filled = barProgress <= progress
            val scale = if (playing && filled) pulse else 1f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((h * scale).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (filled) activeColor else inactiveColor)
            )
        }
    }
}
