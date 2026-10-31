package com.kimifreepainter.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 旧版 MD3 静态色板。刻意不启用动态取色，避免系统取色把界面染成不可控的配色。
private val Light = lightColorScheme(
    primary = Color(0xFF3D6B5A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFE6D6),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4C635A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9DC),
    onSecondaryContainer = Color(0xFF092018),
    tertiary = Color(0xFF8A6E4B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4DFC0),
    onTertiaryContainer = Color(0xFF2B1B00),
    background = Color(0xFFF7FAF7),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF7FAF7),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDBE5DF),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA4D0BF),
    onPrimary = Color(0xFF083728),
    primaryContainer = Color(0xFF24513F),
    onPrimaryContainer = Color(0xFFBFE6D6),
    secondary = Color(0xFFB3CCC1),
    onSecondary = Color(0xFF1E352C),
    secondaryContainer = Color(0xFF354B42),
    onSecondaryContainer = Color(0xFFCFE9DC),
    tertiary = Color(0xFFD8C2A0),
    onTertiary = Color(0xFF3E2E13),
    tertiaryContainer = Color(0xFF57442B),
    onTertiaryContainer = Color(0xFFF4DFC0),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C3),
    outline = Color(0xFF89938D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun KimiFreePainterTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (dark) Dark else Light
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (view.context as Activity).window
            w.statusBarColor = scheme.background.toArgb()
            w.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
