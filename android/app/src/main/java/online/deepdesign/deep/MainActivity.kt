package online.deepdesign.deep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
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
                val callError by DeepApp.instance.callManager.error.collectAsState()
                val snackbar = remember { SnackbarHostState() }
                val callManager = DeepApp.instance.callManager

                val micPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) callManager.acceptIncoming()
                }

                fun acceptCall() {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) callManager.acceptIncoming()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }

                LaunchedEffect(callError) {
                    callError?.let {
                        snackbar.showSnackbar(it)
                        callManager.clearError()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) }
                ) { padding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        DeepNavHost()
                        if (callState !is CallUiState.Idle) {
                            CallOverlay(
                                state = callState,
                                onAccept = { acceptCall() },
                                onReject = { callManager.rejectIncoming() },
                                onHangup = { callManager.hangup() }
                            )
                        }
                    }
                }
            }
        }
    }
}
