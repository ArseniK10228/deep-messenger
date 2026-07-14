package online.deepdesign.deep.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import online.deepdesign.deep.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

object ApiConfig {
    const val BASE_URL = BuildConfig.API_BASE_URL
    val WS_URL: String
        get() = BASE_URL.trimEnd('/')
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/ws"
}

interface DeepApi {
    @POST("api/v1/auth/telegram/send")
    suspend fun telegramSend(@Body body: TelegramSendRequest): TelegramSendResponse

    @POST("api/v1/auth/telegram/verify")
    suspend fun telegramVerify(@Body body: TelegramVerifyRequest): AuthResponse

    @POST("api/v1/auth/firebase")
    suspend fun authFirebase(@Body body: FirebaseAuthRequest): AuthResponse

    @GET("api/v1/me")
    suspend fun me(): MeResponse

    @GET("api/v1/conversations")
    suspend fun conversations(): ConversationsResponse

    @POST("api/v1/conversations/direct")
    suspend fun createDirect(@Body body: DirectChatRequest): DirectChatResponse

    @GET("api/v1/conversations/{id}/messages")
    suspend fun messages(
        @Path("id") conversationId: String,
        @Query("limit") limit: Int = 50
    ): MessagesResponse

    @POST("api/v1/conversations/{id}/messages")
    suspend fun sendMessage(
        @Path("id") conversationId: String,
        @Body body: SendMessageRequest
    ): SendMessageResponse

    @POST("api/v1/messages/{id}/read")
    suspend fun markRead(@Path("id") messageId: String): Map<String, Boolean>

    @GET("api/v1/users/search")
    suspend fun searchUsers(@Query("q") query: String): UsersSearchResponse

    @GET("api/v1/calls/ice")
    suspend fun callIce(): IceServersResponse

    @POST("api/v1/calls")
    suspend fun startCall(@Body body: StartCallRequest): StartCallResponse

    @POST("api/v1/calls/{id}/accept")
    suspend fun acceptCall(@Path("id") callId: String): AcceptCallResponse

    @POST("api/v1/calls/{id}/reject")
    suspend fun rejectCall(@Path("id") callId: String): Map<String, Boolean>

    @POST("api/v1/calls/{id}/end")
    suspend fun endCall(@Path("id") callId: String): Map<String, Boolean>

    @POST("api/v1/auth/fcm")
    suspend fun registerFcm(@Body body: FcmRegisterRequest): Map<String, Boolean>
}

class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain) = chain.proceed(
        chain.request().newBuilder().apply {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }.build()
    )
}

object ApiClient {
    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

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
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DeepApi::class.java)
    }

    fun okHttp(tokenProvider: () -> String? = { null }): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenProvider))
            .build()
    }
}
