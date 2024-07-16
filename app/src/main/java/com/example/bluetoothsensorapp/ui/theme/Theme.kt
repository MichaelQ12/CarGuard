package com.example.bluetoothsensorapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val UWYellow = Color(0xFFFFD100)
private val Black = Color(0xFF000000)
private val Accent = Color(0xFFB3A369)

private val LightColorScheme = lightColorScheme(
    primary = UWYellow,
    primaryContainer = UWYellow,
    secondary = Accent,
    secondaryContainer = Accent,
    background = Color.White,
    surface = Color.White,
    onPrimary = Black,
    onSecondary = Black,
    onBackground = Black,
    onSurface = Black,
)

private val DarkColorScheme = darkColorScheme(
    primary = UWYellow,
    primaryContainer = UWYellow,
    secondary = Accent,
    secondaryContainer = Accent,
    background = Black,
    surface = Black,
    onPrimary = Black,
    onSecondary = Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun BluetoothSensorAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
