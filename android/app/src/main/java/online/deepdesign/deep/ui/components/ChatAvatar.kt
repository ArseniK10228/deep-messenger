package online.deepdesign.deep.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepAccentDim

@Composable
fun ChatAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    online: Boolean = false
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val gradient = Brush.linearGradient(listOf(DeepAccent, DeepAccentDim))
    val dotSize = (size * 0.28f).coerceAtLeast(10.dp)

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(dotSize)
        ) {
            AnimatedContent(
                targetState = online,
                transitionSpec = {
                    (scaleIn(
                        initialScale = 0.4f,
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(220))) togetherWith
                        (scaleOut(targetScale = 0.5f, animationSpec = tween(180)) + fadeOut(tween(160)))
                },
                label = "onlineDot"
            ) { isOnline ->
                if (isOnline) {
                    OnlineDot(size = dotSize)
                }
            }
        }
    }
}

@Composable
private fun OnlineDot(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF34C759))
    )
}
