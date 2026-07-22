package online.deepdesign.deep.diag

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.WsEnvelope
import online.deepdesign.deep.push.ClientReporter

object DiagnosticsRelay {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onRequest(env: WsEnvelope) {
        val action = env.action ?: "snapshot"
        scope.launch {
            when (action) {
                "snapshot" -> ClientReporter.report(DeepApp.instance.api)
                else -> ClientReporter.report(DeepApp.instance.api)
            }
        }
    }
}
