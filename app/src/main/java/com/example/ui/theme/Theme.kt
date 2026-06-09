package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ParrotLightBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = ParrotDarkBlue,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = ParrotYellow,
    onSecondary = androidx.compose.ui.graphics.Color.Black,
    tertiary = ParrotOrange,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    error = ParrotRed,
    onError = androidx.compose.ui.graphics.Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ParrotLightBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = ParrotDarkBlue,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = ParrotYellow,
    onSecondary = androidx.compose.ui.graphics.Color.Black,
    tertiary = ParrotOrange,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    error = ParrotRed,
    onError = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFFFFFBFE),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFBFE),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1C1B1F),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1C1B1F)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Force dynamicColor to false by default so our custom Parrot colors are used
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
