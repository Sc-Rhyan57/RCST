package com.rhyan57.rcst.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5865F2),
    onPrimary = Color.White,
    background = Color(0xFF1E1F22),
    surface = Color(0xFF2B2D31),
    surfaceVariant = Color(0xFF313338),
    onBackground = Color(0xFFF2F3F5),
    onSurface = Color(0xFFF2F3F5),
    onSurfaceVariant = Color(0xFFB0B3B8),
    error = Color(0xFFED4245),
    errorContainer = Color(0xFF3B1A1B),
    onError = Color(0xFFFFDFDE)
)

val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF5865F2),
    onPrimary = Color.White,
    background = Color(0xFF000000),
    surface = Color(0xFF0D0D0D),
    surfaceVariant = Color(0xFF1A1A1A),
    onBackground = Color(0xFFF2F3F5),
    onSurface = Color(0xFFF2F3F5),
    onSurfaceVariant = Color(0xFFB0B3B8),
    error = Color(0xFFED4245),
    errorContainer = Color(0xFF2D0000),
    onError = Color(0xFFFFDFDE)
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5865F2),
    onPrimary = Color.White,
    background = Color(0xFFF2F3F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3E5E8),
    onBackground = Color(0xFF1E1F22),
    onSurface = Color(0xFF1E1F22),
    onSurfaceVariant = Color(0xFF4E5058),
    error = Color(0xFFED4245),
    errorContainer = Color(0xFFFFDADA),
    onError = Color.White
)

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    materialYou: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        materialYou && supportsDynamic -> when (themeMode) {
            ThemeMode.LIGHT -> dynamicLightColorScheme(context)
            ThemeMode.DARK -> dynamicDarkColorScheme(context)
            ThemeMode.AMOLED -> dynamicDarkColorScheme(context).copy(
                background = Color(0xFF000000),
                surface = Color(0xFF0D0D0D)
            )
            ThemeMode.SYSTEM -> if (isSystemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> when (themeMode) {
            ThemeMode.LIGHT -> LightColorScheme
            ThemeMode.DARK -> DarkColorScheme
            ThemeMode.AMOLED -> AmoledColorScheme
            ThemeMode.SYSTEM -> if (isSystemDark) DarkColorScheme else LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colorScheme.background,
                                colorScheme.surface.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                content()
            }
        }
    )
}
