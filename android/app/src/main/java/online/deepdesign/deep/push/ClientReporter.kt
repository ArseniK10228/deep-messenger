package online.deepdesign.deep.push

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.deepdesign.deep.BuildConfig
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.ClientReportRequest
import online.deepdesign.deep.data.DeepApi
import online.deepdesign.deep.data.FcmRegisterRequest
import online.deepdesign.deep.push.DeviceStateCollector

object ClientReporter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    fun scheduleReport() {
        scope.launch {
            runCatching { report(DeepApp.instance.api) }
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
