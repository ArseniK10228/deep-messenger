package online.deepdesign.deep.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

data class PickedFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

data class PickedFileMeta(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long?
)

fun readPickedMeta(context: Context, uri: Uri): PickedFileMeta? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val name = queryDisplayName(context, uri)
        ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { "file.$it" }
        ?: "file"
    val size = querySizeBytes(context, uri)
    return PickedFileMeta(name, mime, size)
}

fun readPickedFile(context: Context, uri: Uri): PickedFile? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val name = queryDisplayName(context, uri)
        ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { "file.$it" }
        ?: "file"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.isEmpty()) return null
    return PickedFile(name, mime, bytes)
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
    }
    return null
}

private fun querySizeBytes(context: Context, uri: Uri): Long? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (idx >= 0 && cursor.moveToFirst()) {
            val v = cursor.getLong(idx)
            if (v > 0L) return v
        }
    }
    return null
}
