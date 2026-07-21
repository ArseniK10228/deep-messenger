package online.deepdesign.deep.ui.call

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import online.deepdesign.deep.call.CallNetworkUiState
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepMuted

@Composable
fun CallNetworkStatus(
    state: CallNetworkUiState,
    modifier: Modifier = Modifier,
    light: Boolean = false
) {
    val labelColor = if (light) Color.White.copy(alpha = 0.85f) else DeepMuted
    val accent = if (state.reconnecting || state.bars <= 1) Color(0xFFFF6B6B) else DeepAccent

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (!state.statusText.isNullOrBlank()) {
            Text(
                state.statusText,
                color = Color(0xFFFFB347),
                style = MaterialTheme.typography.labelMedium
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SignalBars(bars = state.bars, color = accent)
            state.pingMs?.let { ping ->
                Text(
                    "${ping} ms",
                    color = labelColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun SignalBars(bars: Int, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = listOf(6.dp, 9.dp, 12.dp, 15.dp)
        heights.forEachIndexed { index, height ->
            val active = index < bars.coerceIn(0, 4)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .padding(bottom = 0.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .width(4.dp)
                        .height(height),
                    color = if (active) color else color.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(2.dp)
                ) {}
            }
        }
    }
}

@Composable
fun MicLevelIcon(
    icon: ImageVector,
    level: Float,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    light: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    val normalized = if (muted) 0f else (level * 14f).coerceIn(0f, 1f)
    val fillFraction by animateFloatAsState(
        targetValue = normalized,
        animationSpec = tween(70),
        label = "micFill"
    )
    val baseTint = when {
        muted -> if (light) Color.White.copy(alpha = 0.55f) else DeepMuted
        light -> Color.White.copy(alpha = 0.45f)
        else -> DeepMuted.copy(alpha = 0.55f)
    }
    val fillTint = Color(0xFF5CE696)

    Box(modifier = modifier.size(iconSize), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = baseTint, modifier = Modifier.size(iconSize))
        if (!muted) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fillFraction)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(3.dp))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = fillTint,
                        modifier = Modifier
                            .size(iconSize)
                            .align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}
