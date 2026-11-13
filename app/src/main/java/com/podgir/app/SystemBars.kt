package com.podgir.app

import android.os.Build
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Keep system icons legible and let app content paint behind transparent system bars. */
fun configureSystemBars(window: Window, dark: Boolean, transparent: Boolean, status: Color, navigation: Color) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
    @Suppress("DEPRECATION")
    window.statusBarColor = if (transparent) android.graphics.Color.TRANSPARENT else status.toArgb()
    @Suppress("DEPRECATION")
    window.navigationBarColor = if (transparent) android.graphics.Color.TRANSPARENT else navigation.toArgb()
    if (Build.VERSION.SDK_INT >= 29) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }
}

@Composable
fun DialogSystemBars(dark: Boolean, status: Color, navigation: Color) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { if (window != null) configureSystemBars(window, dark, false, status, navigation) }
}
