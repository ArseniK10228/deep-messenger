package online.deepdesign.deep.data

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

object AttachmentDownloader {
    suspend fun downloadMessage(context: Context, messageId: String): File = withContext(Dispatchers.IO) {
        val token = DeepAppToken.current()
            ?: throw IllegalStateException("Not authenticated")
        val dir = File(context.cacheDir, "chat_attachments").apply { mkdirs() }
        val url =
            "${ApiConfig.BASE_URL.trimEnd('/')}/api/v1/messages/$messageId/attachment"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val client = ApiClient.okHttp { token }
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed (${response.code})")
            }
            val body = response.body ?: throw IllegalStateException("Empty body")
            val disposition = response.header("Content-Disposition")
            val name = parseFileName(disposition) ?: "$messageId.bin"
            val dest = File(dir, name)
            dest.outputStream().use { out -> body.byteStream().copyTo(out) }
            dest
        }
    }

    fun openLocalFile(context: Context, file: File, mimeHint: String?) {
        val mime = mimeHint?.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                file.extension.lowercase()
            )
            ?: "application/octet-stream"
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, file.name))
    }

    private fun parseFileName(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null
        val utf = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
            .find(contentDisposition)?.groupValues?.getOrNull(1)
        if (!utf.isNullOrBlank()) {
            return runCatching { java.net.URLDecoder.decode(utf, "UTF-8") }.getOrNull()
        }
        val plain = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
            .find(contentDisposition)?.groupValues?.getOrNull(1)
        return plain?.trim()
    }
}
