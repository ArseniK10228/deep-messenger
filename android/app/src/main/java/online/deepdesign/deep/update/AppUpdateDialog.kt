package online.deepdesign.deep.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import online.deepdesign.deep.data.AppReleaseDto
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepText

@Composable
fun AppUpdateDialog(
    release: AppReleaseDto,
    downloading: Boolean,
    progress: Float,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    val force = release.forceUpdate == true
    AlertDialog(
        onDismissRequest = { if (!force && !downloading) onDismiss() },
        title = { Text("Обновление приложения") },
        text = {
            Column {
                Text(
                    "Версия ${release.versionName}",
                    color = DeepText,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    release.changelog
                        ?: "Выпущена новая версия Deep Messenger. Рекомендуется установить обновление для обеспечения стабильной работы сервиса.",
                    color = DeepMuted
                )
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        color = DeepAccent
                    )
                    Text(
                        "Скачивание… ${(progress * 100).toInt()}%",
                        color = DeepText,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate, enabled = !downloading) {
                Text(if (downloading) "Скачиваем…" else "Обновить")
            }
        },
        dismissButton = if (!force) {
            {
                TextButton(onClick = onDismiss, enabled = !downloading) {
                    Text("Позже")
                }
            }
        } else null,
        shape = RoundedCornerShape(20.dp)
    )
}
