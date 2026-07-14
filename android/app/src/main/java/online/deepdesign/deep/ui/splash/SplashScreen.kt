package online.deepdesign.deep.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import online.deepdesign.deep.ui.components.deepAppear
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepMuted

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(900)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            modifier = Modifier.deepAppear(),
            text = "Deep",
            style = androidx.compose.material3.MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = DeepAccent
        )
        Text(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .deepAppear(delayMillis = 200),
            text = "messenger",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = DeepMuted
        )
    }
}
