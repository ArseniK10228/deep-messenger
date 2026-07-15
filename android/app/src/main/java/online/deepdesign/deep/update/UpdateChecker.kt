package online.deepdesign.deep.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import online.deepdesign.deep.BuildConfig
import online.deepdesign.deep.data.ApiClient
import online.deepdesign.deep.data.AppReleaseDto
import java.io.File
import java.util.concurrent.TimeUnit

object UpdateChecker {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun fetchRelease(): AppReleaseDto? = withContext(Dispatchers.IO) {
        runCatching {
            val api = ApiClient.create()
            api.appRelease()
        }.getOrNull()
    }

    fun needsUpdate(release: AppReleaseDto): Boolean {
        return release.versionCode > BuildConfig.VERSION_CODE
    }

    suspend fun downloadApk(context: Context, url: String, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dest = File(context.cacheDir, "deep-update.apk")
            if (dest.exists()) dest.delete()
            val request = Request.Builder().url(url).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed: ${response.code}")
                val body = response.body ?: error("Empty body")
                val total = body.contentLength().coerceAtLeast(1L)
                var read = 0L
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            output.write(buffer, 0, n)
                            read += n
                            onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            dest
        }
}
