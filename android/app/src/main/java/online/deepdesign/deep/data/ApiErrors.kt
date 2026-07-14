package online.deepdesign.deep.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.HttpException

@JsonClass(generateAdapter = true)
data class ApiErrorResponse(@Json(name = "error") val error: String?)

fun HttpException.readApiError(moshi: com.squareup.moshi.Moshi = ApiClient.moshi): String {
    val raw = response()?.errorBody()?.string().orEmpty()
    if (raw.isNotBlank()) {
        runCatching {
            moshi.adapter(ApiErrorResponse::class.java).fromJson(raw)?.error
        }.getOrNull()?.let { return it }
    }
    return message()
}
