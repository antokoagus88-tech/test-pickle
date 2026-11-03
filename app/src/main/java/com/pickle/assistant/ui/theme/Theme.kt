package com.pickle.assistant.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
fun PickleAssistantTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    
    val isSystemDark = if (context is Activity) {
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    } else {
        darkTheme
    }

    val colorScheme = if (isSystemDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

@Composable
fun SurfaceContainer(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = color,
        shape = MaterialTheme.shapes.medium,
        content = content
    )
}