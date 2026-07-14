package online.deepdesign.deep.data

import online.deepdesign.deep.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

object ApiConfig {
    const val BASE_URL = BuildConfig.API_BASE_URL
}

interface DeepApi {
    @POST("/api/v1/auth/firebase")
    suspend fun authFirebase(@Body body: FirebaseAuthRequest): AuthResponse
}

data class FirebaseAuthRequest(val idToken: String, val displayName: String? = null)
data class AuthResponse(val token: String, val user: UserDto)
data class UserDto(
    val id: String,
    val phone: String,
    val displayName: String,
    val avatarUrl: String?
)

class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain) = chain.proceed(
        chain.request().newBuilder().apply {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }.build()
    )
}

object ApiClient {
    fun create(tokenProvider: () -> String? = { null }): DeepApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(DeepApi::class.java)
    }
}
