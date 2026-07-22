package online.deepdesign.deep.call

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import online.deepdesign.deep.data.ApiClient
import online.deepdesign.deep.data.CallRecordingDto
import online.deepdesign.deep.data.CallRecordingUploadResponse
import online.deepdesign.deep.data.DeepAppToken
import online.deepdesign.deep.data.ApiConfig
import java.io.File

object CallRecordingUploader {
    suspend fun upload(
        file: File,
        callId: String,
        conversationId: String?,
        startedAt: String?,
        endedAt: String,
        durationMs: Long,
        video: Boolean
    ): CallRecordingDto? = withContext(Dispatchers.IO) {
        val token = DeepAppToken.current() ?: return@withContext null
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("callId", callId)
            .addFormDataPart("endedAt", endedAt)
            .addFormDataPart("durationMs", durationMs.toString())
            .addFormDataPart("video", video.toString())
            .apply {
                conversationId?.let { addFormDataPart("conversationId", it) }
                startedAt?.let { addFormDataPart("startedAt", it) }
            }
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("audio/wav".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL.trimEnd('/')}/api/v1/calls/recordings")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        runCatching {
            ApiClient.okHttp { token }.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext null
                ApiClient.moshi.adapter(CallRecordingUploadResponse::class.java)
                    .fromJson(raw)?.recording
            }
        }.getOrNull()
    }
}
