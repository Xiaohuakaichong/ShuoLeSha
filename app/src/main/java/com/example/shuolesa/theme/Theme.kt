package com.example.shuolesa.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary=Blue, onPrimary=Color.White, primaryContainer=Color(0xFFEAF0FF), onPrimaryContainer=Color(0xFF1749AD),
    secondary=Color(0xFF60636C), onSecondary=Color.White, secondaryContainer=Color(0xFFF0F1F3),
    background=Color(0xFFF7F8FA), onBackground=Color(0xFF17181B),
    surface=Color.White, onSurface=Color(0xFF17181B), surfaceVariant=Color(0xFFF0F1F3),
    onSurfaceVariant=Color(0xFF626670), outline=Color(0xFFCCD0D8), outlineVariant=Color(0xFFE5E7EC),
    surfaceContainer=Color.White, surfaceContainerLow=Color(0xFFFAFAFC),
    surfaceContainerHighest=Color(0xFFF0F1F3), surfaceTint=Color.Transparent,
    error=Color(0xFFB43D3D), onError=Color.White,
)
private val Dark = darkColorScheme(
    primary=NightBlue, onPrimary=Color(0xFF102B63), primaryContainer=Color(0xFF202D49), onPrimaryContainer=NightBlue,
    secondary=Color(0xFFB1B4BD), onSecondary=Color(0xFF25272D), secondaryContainer=Color(0xFF26282F),
    background=Color(0xFF111216), onBackground=Color(0xFFF1F2F5),
    surface=Color(0xFF1B1D23), onSurface=Color(0xFFF1F2F5), surfaceVariant=Color(0xFF26282F),
    onSurfaceVariant=Color(0xFFAAADB8), outline=Color(0xFF505460), outlineVariant=Color(0xFF30333C),
    surfaceContainer=Color(0xFF1B1D23), surfaceContainerLow=Color(0xFF17191F),
    surfaceContainerHighest=Color(0xFF26282F), surfaceTint=Color.Transparent,
    error=Color(0xFFFFA0A0), onError=Color(0xFF5F1515),
)
@Composable fun ShuoLeSaTheme(darkTheme: Boolean=false,content: @Composable ()->Unit) {
    MaterialTheme(colorScheme=if(darkTheme) Dark else Light,typography=Typography,content=content)
}
