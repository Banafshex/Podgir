package com.podgir.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val NeonCyan = Color(0xFF7CF4E8)
val NeonViolet = Color(0xFFAE8AFF)
private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

/** Keep Material's sizing and accessibility scaling; change only the Persian font family. */
fun persianTypography(): Typography {
    val t = Typography()
    return t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = Vazirmatn), displayMedium = t.displayMedium.copy(fontFamily = Vazirmatn), displaySmall = t.displaySmall.copy(fontFamily = Vazirmatn),
        headlineLarge = t.headlineLarge.copy(fontFamily = Vazirmatn), headlineMedium = t.headlineMedium.copy(fontFamily = Vazirmatn), headlineSmall = t.headlineSmall.copy(fontFamily = Vazirmatn),
        titleLarge = t.titleLarge.copy(fontFamily = Vazirmatn), titleMedium = t.titleMedium.copy(fontFamily = Vazirmatn), titleSmall = t.titleSmall.copy(fontFamily = Vazirmatn),
        bodyLarge = t.bodyLarge.copy(fontFamily = Vazirmatn), bodyMedium = t.bodyMedium.copy(fontFamily = Vazirmatn), bodySmall = t.bodySmall.copy(fontFamily = Vazirmatn),
        labelLarge = t.labelLarge.copy(fontFamily = Vazirmatn), labelMedium = t.labelMedium.copy(fontFamily = Vazirmatn), labelSmall = t.labelSmall.copy(fontFamily = Vazirmatn)
    )
}

/** Low contrast ambient light, drawn behind text and without additional image assets. */
@Composable fun PlayerAmbientBackground(modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "ambient")
    val phase by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(9500, easing = LinearEasing), RepeatMode.Reverse), label = "ambient drift")
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF161B35), Color(0xFF101222), Color(0xFF0B0D17))))
        drawRect(Brush.radialGradient(listOf(Color(0xFF6051A5).copy(alpha = .34f), Color.Transparent), center = Offset(w * (.15f + .12f * phase), h * .20f), radius = w * .9f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF2D918D).copy(alpha = .20f), Color.Transparent), center = Offset(w * (.92f - .12f * phase), h * (.38f + .04f * phase)), radius = w * .8f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF8F4C86).copy(alpha = .13f), Color.Transparent), center = Offset(w * (.35f + .10f * phase), h * .76f), radius = w * .85f))
    }
}
