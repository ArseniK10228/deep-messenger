package online.deepdesign.deep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import online.deepdesign.deep.call.CallUiState
import online.deepdesign.deep.navigation.DeepNavHost
import online.deepdesign.deep.ui.call.CallOverlay
import online.deepdesign.deep.ui.theme.DeepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeepTheme {
                val callState by DeepApp.instance.callManager.state.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    if (callState is CallUiState.Idle) {
                        DeepNavHost()
                    }
                    CallOverlay(
                        state = callState,
                        onAccept = { DeepApp.instance.callManager.acceptIncoming() },
                        onReject = { DeepApp.instance.callManager.rejectIncoming() },
                        onHangup = { DeepApp.instance.callManager.hangup() }
                    )
                }
            }
        }
    }
}
