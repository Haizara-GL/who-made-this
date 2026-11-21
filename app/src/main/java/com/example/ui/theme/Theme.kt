package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class VestnikExtendedColors(
    val emeraldLive: Color,
    val onEmeraldLive: Color,
    val emeraldContainer: Color,
    val onEmeraldContainer: Color,
    val amberAlert: Color,
    val amberContainer: Color,
    val onAmberContainer: Color,
    val heroNavy: Color,
    val outgoingBubble: Color,
    val onOutgoingBubble: Color,
    val incomingBubble: Color,
    val onIncomingBubble: Color
)

private val LightExtendedColors = VestnikExtendedColors(
    emeraldLive = EmeraldLive,
    onEmeraldLive = Color.White,
    emeraldContainer = EmeraldContainerLight,
    onEmeraldContainer = OnEmeraldContainerLight,
    amberAlert = AmberGold,
    amberContainer = AmberContainerLight,
    onAmberContainer = OnAmberContainerLight,
    heroNavy = NavyDeep,
    outgoingBubble = NavyPrimary,
    onOutgoingBubble = Color.White,
    incomingBubble = FrostSurface,
    onIncomingBubble = SlateInk
)

private val DarkExtendedColors = VestnikExtendedColors(
    emeraldLive = EmeraldLight,
    onEmeraldLive = Color(0xFF022C22),
    emeraldContainer = Color(0xFF064E3B),
    onEmeraldContainer = Color(0xFFA7F3D0),
    amberAlert = AmberLight,
    amberContainer = Color(0xFF451A03),
    onAmberContainer = Color(0xFFFDE68A),
    heroNavy = Color(0xFF0B1F3A),
    outgoingBubble = Color(0xFF1D4ED8),
    onOutgoingBubble = Color.White,
    incomingBubble = MidnightSurfaceVariant,
    onIncomingBubble = IceInk
)

val LocalVestnikColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = NavyContainerLight,
    onPrimaryContainer = OnNavyContainerLight,
    secondary = AmberGold,
    onSecondary = Color.White,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = OnAmberContainerLight,
    tertiary = EmeraldLive,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = OnEmeraldContainerLight,
    background = AlabasterBg,
    onBackground = SlateInk,
    surface = FrostSurface,
    onSurface = SlateInk,
    surfaceVariant = FrostSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outline = SlateOutline,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonErrorLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = SkyLight,
    onPrimary = NavyDeep,
    primaryContainer = MidnightContainer,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = AmberLight,
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFF5C2D0C),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = EmeraldLight,
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFD1FAE5),
    background = MidnightBg,
    onBackground = IceInk,
    surface = MidnightSurface,
    onSurface = IceInk,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = IceMuted,
    outline = MidnightOutline,
    error = CrimsonErrorDark,
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val VestnikShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun VestnikTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalVestnikColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = VestnikShapes,
            content = content
        )
    }
}
