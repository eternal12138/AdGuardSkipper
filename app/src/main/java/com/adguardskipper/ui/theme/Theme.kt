package com.adguardskipper.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Brand palette sampled from the Ciallo "跳广" poster: powder-blue sky,
 * cobalt ink, coral sticker red, arcade yellow and the navy used for every
 * heavy outline.
 */
object CialloPalette {
    val SkyTop = Color(0xFFBFEEFF)
    val Sky = Color(0xFFDDF5FF)
    val Cloud = Color(0xFFF5FCFF)
    val Paper = Color(0xFFFFFFFF)
    val Navy = Color(0xFF16265C)
    val Ink = Color(0xFF1E2E66)
    val Muted = Color(0xFF58769A)
    val Blue = Color(0xFF2F6BFF)
    val BlueDeep = Color(0xFF1747C8)
    val Cyan = Color(0xFF35C7F4)
    val Ice = Color(0xFFDDEFFF)
    val Powder = Color(0xFFD8EFFF)
    val Coral = Color(0xFFFF6B6B)
    val CoralDeep = Color(0xFFE94B62)
    val Lemon = Color(0xFFFFD84D)
    val LemonDeep = Color(0xFFF5A400)
    val Blush = Color(0xFFFFF0F5)
    val Lavender = Color(0xFFE9E6FF)
    val Mint = Color(0xFFDDF7F0)

    // Names kept from the first Ciallo pass so notifications and older UI
    // snippets stay source-compatible.
    val DeepBlue = Navy
    val Yellow = Lemon
}

private val CialloLightColors = lightColorScheme(
    primary = CialloPalette.Blue,
    onPrimary = Color.White,
    primaryContainer = CialloPalette.Ice,
    onPrimaryContainer = CialloPalette.Navy,
    secondary = CialloPalette.Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2E5),
    onSecondaryContainer = Color(0xFF5B1620),
    tertiary = CialloPalette.LemonDeep,
    onTertiary = CialloPalette.Navy,
    tertiaryContainer = Color(0xFFFFF2BC),
    onTertiaryContainer = Color(0xFF4A3400),
    error = CialloPalette.CoralDeep,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E7),
    onErrorContainer = Color(0xFF6B111B),
    background = CialloPalette.Cloud,
    onBackground = CialloPalette.Navy,
    surface = Color.White,
    onSurface = CialloPalette.Navy,
    surfaceVariant = CialloPalette.Ice,
    onSurfaceVariant = CialloPalette.Muted,
    outline = Color(0xFF7FA9D6),
    outlineVariant = Color(0xFFC9E6F7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FCFF),
    surfaceContainer = Color(0xFFEFF9FF),
    surfaceContainerHigh = Color(0xFFE4F4FF),
    surfaceContainerHighest = Color(0xFFD6EEFF),
    inverseSurface = CialloPalette.Navy,
    inverseOnSurface = Color(0xFFEAF4FF),
    inversePrimary = Color(0xFF9CC7FF),
    scrim = Color(0xFF07152F),
)

private val CialloTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.5.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.5.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
    ),
)

private val CialloShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AdGuardSkipperTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CialloLightColors,
        typography = CialloTypography,
        shapes = CialloShapes,
        content = content,
    )
}
