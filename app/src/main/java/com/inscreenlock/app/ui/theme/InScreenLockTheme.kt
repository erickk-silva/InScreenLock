package com.inscreenlock.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun InScreenLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InScreenLockDarkColorScheme,
        typography = InScreenLockTypography,
        shapes = InScreenLockShapes,
        content = content,
    )
}
