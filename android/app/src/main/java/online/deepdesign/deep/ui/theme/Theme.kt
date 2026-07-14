package online.deepdesign.deep.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DeepColorScheme = darkColorScheme(
    primary = DeepAccent,
    onPrimary = DeepText,
    secondary = DeepAccentDim,
    background = DeepBg,
    onBackground = DeepText,
    surface = DeepSurface,
    onSurface = DeepText,
    surfaceVariant = DeepSurfaceHigh,
    onSurfaceVariant = DeepMuted,
    error = DeepError,
    onError = DeepText
)

@Composable
fun DeepTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DeepBg.toArgb()
            window.navigationBarColor = DeepBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DeepColorScheme,
        typography = DeepTypography,
        content = content
    )
}
