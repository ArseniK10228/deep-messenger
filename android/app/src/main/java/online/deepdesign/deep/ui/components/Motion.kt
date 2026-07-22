package online.deepdesign.deep.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/** Появление сообщения в списке: fade + slide (исходящие и входящие). */
fun Modifier.messageBubbleEnter(mine: Boolean = false): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "messageBubbleEnter"
    )
    val slide = if (mine) 18f else 14f
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * slide
        scaleX = 0.94f + 0.06f * progress
        scaleY = 0.94f + 0.06f * progress
    }
}

/** @deprecated use messageBubbleEnter */
fun Modifier.messageSendEnter(mine: Boolean): Modifier = messageBubbleEnter(mine)

/** Лёгкая fade+scale анимация, friendly для 120Hz (GPU layer, без layout). */
fun Modifier.deepAppear(
    delayMillis: Int = 0,
    durationMillis: Int = 380
): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        visible = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        label = "deepAppear"
    )
    graphicsLayer {
        alpha = progress
        scaleX = 0.96f + 0.04f * progress
        scaleY = 0.96f + 0.04f * progress
    }
}
