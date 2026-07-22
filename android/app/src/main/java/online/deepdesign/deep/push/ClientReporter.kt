package online.deepdesign.deep.push

import online.deepdesign.deep.BuildConfig
import online.deepdesign.deep.data.ClientReportRequest
import online.deepdesign.deep.data.DeepApi
import online.deepdesign.deep.data.FcmRegisterRequest
import online.deepdesign.deep.push.DeviceStateCollector

object ClientReporter {
    suspend fun report(api: DeepApi) {
        val state = DeviceStateCollector.snapshot()
        runCatching {
            api.reportClient(
                ClientReportRequest(
                    versionCode = BuildConfig.VERSION_CODE,
                    versionName = BuildConfig.VERSION_NAME,
                    foreground = state.foreground,
                    batteryPct = state.batteryPct,
                    charging = state.charging,
                    network = state.network,
                    inCall = state.inCall
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
