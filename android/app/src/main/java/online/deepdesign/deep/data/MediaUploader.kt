package online.deepdesign.deep.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object MediaUploader {
    suspend fun upload(
        conversationId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        durationMs: Long? = null
    ): MessageDto = withContext(Dispatchers.IO) {
        val token = DeepAppToken.current()
            ?: throw IllegalStateException("Not authenticated")

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
        durationMs?.let { body.addFormDataPart("durationMs", it.toString()) }

        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL.trimEnd('/')}/api/v1/conversations/$conversationId/upload")
            .header("Authorization", "Bearer $token")
            .post(body.build())
            .build()

        val client = ApiClient.okHttp { token }
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string()
                ?: throw IllegalStateException("Empty upload response")
            if (!response.isSuccessful) {
                throw IllegalStateException(raw.ifBlank { "Upload failed (${response.code})" })
            }
            val adapter = ApiClient.moshi.adapter(SendMessageResponse::class.java)
            adapter.fromJson(raw)?.message
                ?: throw IllegalStateException("Invalid upload response")
        }
    }
}

/** Thin bridge so uploader doesn't depend on Application singleton at class-load time. */
object DeepAppToken {
    var current: () -> String? = { null }
}
