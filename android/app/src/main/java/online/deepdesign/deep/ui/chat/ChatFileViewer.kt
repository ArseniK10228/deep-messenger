package online.deepdesign.deep.ui.chat

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import online.deepdesign.deep.data.AttachmentDownloader
import online.deepdesign.deep.ui.theme.DeepBg
import online.deepdesign.deep.ui.theme.DeepSurface
import online.deepdesign.deep.ui.theme.DeepText
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatFileViewerSheet(
    visible: Boolean,
    title: String,
    localFile: File,
    mimeHint: String?,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val mime = mimeHint?.lowercase() ?: ""
    val isImage = mime.startsWith("image/") || title.endsWith(".jpg", true) ||
        title.endsWith(".jpeg", true) || title.endsWith(".png", true) || title.endsWith(".webp", true)
    val isPdf = mime == "application/pdf" || title.endsWith(".pdf", true) || localFile.extension.equals("pdf", true)
    val fileUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        localFile
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = DeepSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = title,
                    color = DeepText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp, end = 48.dp)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = DeepText)
                }
            }
            when {
                isImage -> {
                    AsyncImage(
                        model = fileUri,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepBg)
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                isPdf -> {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 360.dp, max = 520.dp)
                            .padding(horizontal = 8.dp)
                            .background(DeepBg),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = false
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                webViewClient = WebViewClient()
                                loadUrl(fileUri.toString())
                            }
                        },
                        update = { it.loadUrl(fileUri.toString()) }
                    )
                }
                else -> {
                    Text(
                        text = "Просмотр в приложении недоступен",
                        color = DeepText,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                    TextButton(
                        onClick = {
                            AttachmentDownloader.openLocalFile(context, localFile, mimeHint)
                            onDismiss()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Text("Открыть в другом приложении")
                    }
                }
            }
        }
    }
}
