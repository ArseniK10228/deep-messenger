package online.deepdesign.deep.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.deepdesign.deep.call.CallUiState
import online.deepdesign.deep.ui.components.deepAppear
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepBg
import online.deepdesign.deep.ui.theme.DeepError
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepSurfaceHigh
import online.deepdesign.deep.ui.theme.DeepText

@Composable
fun CallOverlay(
    state: CallUiState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onHangup: () -> Unit
) {
    when (state) {
        is CallUiState.Incoming -> IncomingCallUi(state.callerName, onAccept, onReject)
        is CallUiState.Outgoing -> ActiveCallUi(
            title = "Вызов…",
            peerName = state.peerName,
            subtitle = "Соединяем",
            onHangup = onHangup
        )
        is CallUiState.Active -> ActiveCallUi(
            title = if (state.connected) "На линии" else "Соединяем…",
            peerName = state.peerName,
            subtitle = if (state.connected) "Голосовой звонок" else "Ждём ответа",
            onHangup = onHangup
        )
        CallUiState.Idle -> Unit
    }
}

@Composable
private fun IncomingCallUi(callerName: String, onAccept: () -> Unit, onReject: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBg)
            .padding(32.dp)
            .deepAppear(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Входящий звонок", color = DeepMuted, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(callerName, color = DeepText, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            FloatingActionButton(
                onClick = onReject,
                containerColor = DeepError,
                contentColor = DeepText,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Close, contentDescription = "Отклонить")
            }
            FloatingActionButton(
                onClick = onAccept,
                containerColor = DeepAccent,
                contentColor = DeepText,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Call, contentDescription = "Принять")
            }
        }
    }
}

@Composable
private fun ActiveCallUi(
    title: String,
    peerName: String,
    subtitle: String,
    onHangup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBg)
            .padding(32.dp)
            .deepAppear(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = DeepMuted, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(peerName, color = DeepText, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = DeepMuted, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(48.dp))
        FloatingActionButton(
            onClick = onHangup,
            containerColor = DeepError,
            contentColor = DeepText,
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
        ) {
            Icon(Icons.Default.CallEnd, contentDescription = "Завершить", modifier = Modifier.size(32.dp))
        }
    }
}
