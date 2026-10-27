package com.example.shuolesa.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary=Jade, onPrimary=Color.White, primaryContainer=Color(0xFFD3EEE4), onPrimaryContainer=Color(0xFF00382E),
    secondary=Color(0xFF55645E), onSecondary=Color.White, secondaryContainer=Color(0xFFE3EBE7), onSecondaryContainer=Color(0xFF1B2823),
    tertiary=Color(0xFF8A5300), onTertiary=Color.White, tertiaryContainer=Color(0xFFFFE5BD), onTertiaryContainer=Color(0xFF3D2300),
    background=Color(0xFFF5F3EE), onBackground=Color(0xFF1B1C19),
    surface=Color.White, onSurface=Color(0xFF1B1C19), surfaceVariant=Color(0xFFEBE8E0), onSurfaceVariant=Color(0xFF68685F),
    outline=Color(0xFFC8C6BB), outlineVariant=Color(0xFFE7E4DB),
    surfaceContainerLowest=Color.White, surfaceContainerLow=Color(0xFFFAF9F5), surfaceContainer=Color(0xFFF1EFE9),
    surfaceContainerHigh=Color(0xFFECEAE3), surfaceContainerHighest=Color(0xFFE6E3DB), surfaceTint=Color.Transparent,
    inverseSurface=Color(0xFF2F312D), inverseOnSurface=Color(0xFFF2F1EB), inversePrimary=NightJade,
    error=Color(0xFFB93A2E), onError=Color.White, errorContainer=Color(0xFFFADBD5), onErrorContainer=Color(0xFF410C07),
)
private val Dark = darkColorScheme(
    primary=NightJade, onPrimary=Color(0xFF00382E), primaryContainer=Color(0xFF14473D), onPrimaryContainer=Color(0xFFBDF3E3),
    secondary=Color(0xFFB7C6BF), onSecondary=Color(0xFF22302B), secondaryContainer=Color(0xFF2A3733), onSecondaryContainer=Color(0xFFD3E2DB),
    tertiary=Color(0xFFF2BE6A), onTertiary=Color(0xFF442B00), tertiaryContainer=Color(0xFF4A3410), onTertiaryContainer=Color(0xFFFFDDA8),
    background=Color(0xFF0E1311), onBackground=Color(0xFFE6EAE7),
    surface=Color(0xFF161C1A), onSurface=Color(0xFFE6EAE7), surfaceVariant=Color(0xFF232B28), onSurfaceVariant=Color(0xFFA3ACA7),
    outline=Color(0xFF4A5551), outlineVariant=Color(0xFF2A332F),
    surfaceContainerLowest=Color(0xFF0A0F0D), surfaceContainerLow=Color(0xFF131917), surfaceContainer=Color(0xFF1A211E),
    surfaceContainerHigh=Color(0xFF212927), surfaceContainerHighest=Color(0xFF2A3330), surfaceTint=Color.Transparent,
    inverseSurface=Color(0xFFE6EAE7), inverseOnSurface=Color(0xFF1B1C19), inversePrimary=Jade,
    error=Color(0xFFFFB4A9), onError=Color(0xFF5F1515), errorContainer=Color(0xFF7A2A22), onErrorContainer=Color(0xFFFFDAD4),
)
/** v4.1-a.2: extension colours for the service-region tags (境内/局域网/未知·境外) and the warning box. */
@Immutable data class RegionColors(
    val okBg: Color, val okText: Color, val runBg: Color, val runText: Color,
    val warnBg: Color, val warnText: Color, val warningBoxBg: Color, val warningBoxText: Color,
)
val LightRegionColors=RegionColors(
    okBg=Color(0xFFDDF1E6), okText=Color(0xFF17603A), runBg=Color(0xFFDFEAF5), runText=Color(0xFF24527F),
    warnBg=Color(0xFFFFEFC9), warnText=Color(0xFF6F5000), warningBoxBg=Color(0xFFFFEFC9), warningBoxText=Color(0xFF5C4300),
)
val DarkRegionColors=RegionColors(
    okBg=Color(0xFF1C3A2B), okText=Color(0xFF8FD6A8), runBg=Color(0xFF1E3045), runText=Color(0xFF9DBEF5),
    warnBg=Color(0xFF3D3214), warnText=Color(0xFFF2CF6B), warningBoxBg=Color(0xFF3D3214), warningBoxText=Color(0xFFF5DFA0),
)
val LocalRegionColors=staticCompositionLocalOf { LightRegionColors }

/** v5.0: recording state colours. [record] is the live/stop colour; [stage] is the dark recording page. */
@Immutable data class BrandColors(val record: Color, val onRecord: Color, val stage: Color, val stageRaised: Color, val onStage: Color, val onStageMuted: Color)
val LightBrandColors=BrandColors(RecordRed,Color.White,Color(0xFF0B100E),Color(0xFF18201D),Color(0xFFEDF2EF),Color(0xFF9AA6A0))
val DarkBrandColors=BrandColors(NightRecordRed,Color(0xFF3B0A0C),Color(0xFF070B0A),Color(0xFF151C19),Color(0xFFEDF2EF),Color(0xFF9AA6A0))
val LocalBrandColors=staticCompositionLocalOf { LightBrandColors }

private val AppShapes=Shapes(
    extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(16.dp),
    large=RoundedCornerShape(24.dp),extraLarge=RoundedCornerShape(32.dp),
)

@Composable fun ShuoLeSaTheme(darkTheme: Boolean=false,content: @Composable ()->Unit) {
    CompositionLocalProvider(
        LocalRegionColors provides if(darkTheme) DarkRegionColors else LightRegionColors,
        LocalBrandColors provides if(darkTheme) DarkBrandColors else LightBrandColors,
    ) {
        MaterialTheme(colorScheme=if(darkTheme) Dark else Light,typography=Typography,shapes=AppShapes,content=content)
    }
}
