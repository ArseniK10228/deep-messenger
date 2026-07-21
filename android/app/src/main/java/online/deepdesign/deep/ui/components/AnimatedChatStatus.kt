package online.deepdesign.deep.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.util.PresenceFormatter

private enum class ChatStatusKey { Typing, Online, Offline }

@Composable
fun AnimatedChatStatus(
    online: Boolean,
    lastSeenAt: String?,
    typing: Boolean,
    modifier: Modifier = Modifier
) {
    val key = when {
        typing -> ChatStatusKey.Typing
        online -> ChatStatusKey.Online
        else -> ChatStatusKey.Offline
    }
    val offlineText = PresenceFormatter.chatStatus(
        online = false,
        lastSeenAt = lastSeenAt,
        typing = false
    )

    AnimatedContent(
        targetState = key,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ) { it / 3 }) togetherWith
                (fadeOut(tween(180)) +
                    slideOutVertically { -it / 4 })
        },
        label = "chatStatus"
    ) { state ->
        when (state) {
            ChatStatusKey.Typing -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "печатает",
                        color = DeepAccent,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.width(6.dp))
                    TypingBubbleIndicator(compact = true)
                }
            }
            ChatStatusKey.Online -> {
                Text(
                    text = "в сети",
                    color = DeepAccent,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            ChatStatusKey.Offline -> {
                Text(
                    text = offlineText,
                    color = DeepMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
