package online.deepdesign.deep.ui.call

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.deepdesign.deep.call.CallAudioUiState
import online.deepdesign.deep.call.CallInputRoute
import online.deepdesign.deep.call.CallOutputRoute
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallAudioSettingsSheet(
    audio: CallAudioUiState,
    onDismiss: () -> Unit,
    onOutputSelected: (CallOutputRoute) -> Unit,
    onInputSelected: (CallInputRoute) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                "Аудио",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DeepText
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Динамик",
                style = MaterialTheme.typography.titleSmall,
                color = DeepMuted,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            AudioRouteRow(
                title = CallOutputRoute.Earpiece.label,
                subtitle = "Разговорный динамик",
                icon = Icons.Default.PhoneAndroid,
                selected = audio.outputRoute == CallOutputRoute.Earpiece,
                onClick = { onOutputSelected(CallOutputRoute.Earpiece) }
            )
            AudioRouteRow(
                title = CallOutputRoute.Speaker.label,
                subtitle = "Внешний динамик",
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                selected = audio.outputRoute == CallOutputRoute.Speaker,
                onClick = { onOutputSelected(CallOutputRoute.Speaker) }
            )
            AudioRouteRow(
                title = audio.bluetoothLabel,
                subtitle = if (audio.bluetoothAvailable) "Подключено" else "Нет устройства",
                icon = Icons.Default.Bluetooth,
                selected = audio.outputRoute == CallOutputRoute.Bluetooth,
                enabled = audio.bluetoothAvailable,
                onClick = { onOutputSelected(CallOutputRoute.Bluetooth) }
            )
            AudioRouteRow(
                title = CallOutputRoute.Wired.label,
                subtitle = if (audio.wiredAvailable) "Проводные" else "Не подключены",
                icon = Icons.Default.Headphones,
                selected = audio.outputRoute == CallOutputRoute.Wired,
                enabled = audio.wiredAvailable,
                onClick = { onOutputSelected(CallOutputRoute.Wired) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(
                "Микрофон",
                style = MaterialTheme.typography.titleSmall,
                color = DeepMuted,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            AudioRouteRow(
                title = CallInputRoute.Phone.label,
                subtitle = "Встроенный",
                icon = Icons.Default.Mic,
                selected = audio.inputRoute == CallInputRoute.Phone,
                onClick = { onInputSelected(CallInputRoute.Phone) }
            )
            AudioRouteRow(
                title = CallInputRoute.Headset.label,
                subtitle = if (audio.headsetInputAvailable) "Bluetooth / проводные" else "Нет наушников",
                icon = Icons.Default.Headphones,
                selected = audio.inputRoute == CallInputRoute.Headset,
                enabled = audio.headsetInputAvailable,
                onClick = { onInputSelected(CallInputRoute.Headset) }
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun AudioRouteRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentColor = when {
        !enabled -> DeepMuted.copy(alpha = 0.45f)
        selected -> DeepAccent
        else -> DeepText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = contentColor, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = DeepMuted, style = MaterialTheme.typography.bodySmall)
        }
        RadioButton(selected = selected, onClick = null, enabled = enabled)
    }
}
