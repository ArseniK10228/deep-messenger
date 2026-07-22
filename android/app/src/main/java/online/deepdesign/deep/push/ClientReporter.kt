package online.deepdesign.deep.push

import online.deepdesign.deep.BuildConfig
import online.deepdesign.deep.data.ClientReportRequest
import online.deepdesign.deep.data.DeepApi
import online.deepdesign.deep.data.FcmRegisterRequest

object ClientReporter {
    suspend fun report(api: DeepApi) {
        runCatching {
            api.reportClient(
                ClientReportRequest(
                    versionCode = BuildConfig.VERSION_CODE,
                    versionName = BuildConfig.VERSION_NAME
                )
            )
        }
    }

    fun fcmRequest(token: String): FcmRegisterRequest {
        return FcmRegisterRequest(
            token = token,
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME
        )
    }
}
