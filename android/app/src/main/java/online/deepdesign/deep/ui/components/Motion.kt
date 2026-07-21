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

/** Telegram-style: исходящие всплывают снизу, входящие — лёгкий slide. Один раз на id. */
fun Modifier.messageSendEnter(outgoing: Boolean): Modifier = composed {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val offsetY by animateFloatAsState(
        targetValue = if (started) 0f else if (outgoing) 28f else 14f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "msgOffset"
    )
    val alpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "msgAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (started) 1f else if (outgoing) 0.92f else 0.96f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "msgScale"
    )
    graphicsLayer {
        translationY = offsetY
        this.alpha = alpha
        scaleX = scale
        scaleY = scale
    }
}
