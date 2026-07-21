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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import online.deepdesign.deep.ui.theme.DeepBubbleIn
import online.deepdesign.deep.ui.theme.DeepMuted

private val incomingBubbleShape = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = 4.dp,
    bottomEnd = 18.dp
)

@Composable
fun TypingBubbleIndicator(
    modifier: Modifier = Modifier,
    asMessageBubble: Boolean = false,
    compact: Boolean = false,
    dotSize: Dp = if (compact) 5.dp else 7.dp,
    bubblePaddingH: Dp = if (compact) 12.dp else 14.dp,
    bubblePaddingV: Dp = if (compact) 9.dp else 11.dp
) {
    val content = @Composable {
        TypingDots(
            dotSize = dotSize,
            spacing = if (compact) 4.dp else 5.dp
        )
    }

    if (asMessageBubble) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .clip(incomingBubbleShape)
                    .background(DeepBubbleIn)
                    .padding(horizontal = bubblePaddingH, vertical = bubblePaddingV)
            ) {
                content()
            }
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}

@Composable
private fun TypingDots(dotSize: Dp, spacing: Dp) {
    val transition = rememberInfiniteTransition(label = "typingDots")
    val alphas = (0..2).map { index ->
        transition.animateFloat(
            initialValue = 0.28f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 520,
                    delayMillis = index * 160,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotAlpha$index"
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        alphas.forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(DeepMuted.copy(alpha = alpha.value))
            )
        }
    }
}
