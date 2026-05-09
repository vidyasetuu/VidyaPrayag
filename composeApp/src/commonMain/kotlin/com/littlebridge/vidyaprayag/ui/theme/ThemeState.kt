package com.littlebridge.vidyaprayag.ui.theme

import androidx.compose.runtime.*

enum class AppTheme {
    LIGHT, DARK, MIDNIGHT
}

val LocalAppTheme = staticCompositionLocalOf<AppTheme> { AppTheme.LIGHT }
val LocalThemeSwitcher = staticCompositionLocalOf<(AppTheme) -> Unit> { {} }
