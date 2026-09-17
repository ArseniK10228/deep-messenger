package online.deepdesign.deep.data

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException

/** Brief retries on VPN / network flaps (connection reset, timeout). */
class ConnectRetryInterceptor(
    private val maxAttempts: Int = 3
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var last: IOException? = null
        repeat(maxAttempts) { attempt ->
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                last = e
                val retryable = e is SocketTimeoutException ||
                    e.message?.contains("ECONNRESET", ignoreCase = true) == true ||
                    e.message?.contains("Failed to connect", ignoreCase = true) == true
                if (!retryable || attempt == maxAttempts - 1) throw e
                Thread.sleep(350L * (attempt + 1))
            }
        }
        throw last ?: IOException("request failed")
    }
}
