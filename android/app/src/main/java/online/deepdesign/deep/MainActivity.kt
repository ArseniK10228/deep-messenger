package online.deepdesign.deep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import online.deepdesign.deep.navigation.DeepNavHost
import online.deepdesign.deep.ui.theme.DeepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeepTheme {
                DeepNavHost()
            }
        }
    }
}
