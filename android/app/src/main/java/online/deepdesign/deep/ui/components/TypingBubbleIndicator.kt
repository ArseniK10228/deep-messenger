package online.deepdesign.deep.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import online.deepdesign.deep.ui.theme.DeepAccent

@Composable
fun TypingBubbleIndicator(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    dotSize: Dp = if (compact) 5.dp else 6.dp,
    bubblePaddingH: Dp = if (compact) 9.dp else 11.dp,
    bubblePaddingV: Dp = if (compact) 5.dp else 7.dp
) {
    val transition = rememberInfiniteTransition(label = "typingBubble")
    val bounce = (0..2).map { index ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 420,
                    delayMillis = index * 110,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bounce$index"
        )
    }

    Surface(
        modifier = modifier,
        color = DeepAccent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(if (compact) 11.dp else 14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = bubblePaddingH, vertical = bubblePaddingV),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bounce.forEach { anim ->
                val lift = -anim.value * if (compact) 3.5f else 5f
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .graphicsLayer { translationY = lift }
                        .clip(CircleShape)
                        .background(DeepAccent.copy(alpha = 0.55f + anim.value * 0.45f))
                )
            }
        }
    }
}
