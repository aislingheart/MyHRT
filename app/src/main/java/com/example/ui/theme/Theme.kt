package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val TransBlueLight = Color(0xFF5BCEFA)
val TransPinkLight = Color(0xFFF5A9B8)

val TransBlueDark = Color(0xFF3B9BBA)
val TransPinkDark = Color(0xFFB57080)

private val DarkColorScheme =
  darkColorScheme(
      primary = TransBlueLight,
      secondary = TransPinkLight,
      tertiary = TransPinkLight,
      background = Color(0xFF121212),
      surface = Color(0xFF1E1E1E),
      onPrimary = Color.Black,
      onSecondary = Color.Black,
      onTertiary = Color.Black,
      onBackground = Color.White,
      onSurface = Color.White,
      primaryContainer = Color(0xFF2A4B5D),
      secondaryContainer = Color(0xFF5C3340),
      onPrimaryContainer = Color(0xFFD6F0FD),
      onSecondaryContainer = Color(0xFFFDE8ED)
  )

private val LightColorScheme =
  lightColorScheme(
      primary = TransBlueDark,
      secondary = TransPinkDark,
      tertiary = TransPinkDark,
      background = Color(0xFFFDFDFD),
      surface = Color(0xFFF5F5F5),
      onPrimary = Color.White,
      onSecondary = Color.White,
      onTertiary = Color.White,
      onBackground = Color(0xFF1A1A1A),
      onSurface = Color(0xFF1C1C1C),
      primaryContainer = Color(0xFFD6F0FD),
      secondaryContainer = Color(0xFFFDE8ED),
      onPrimaryContainer = Color(0xFF113243),
      onSecondaryContainer = Color(0xFF3F1B25)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
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
