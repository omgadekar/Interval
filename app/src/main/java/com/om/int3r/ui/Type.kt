package com.om.int3r.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.wear.compose.material3.Typography
import com.om.int3r.R

/** Inter: the closest open typeface to SF Pro; tabular figures keep timers from jittering. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Screen classes: small (Pixel Watch 41 mm, Galaxy Watch 40 mm), regular, large (45-47 mm). */
enum class ScreenSize { SMALL, REGULAR, LARGE }

@Composable
fun screenSize(): ScreenSize {
    val dp = LocalConfiguration.current.screenWidthDp
    return when {
        dp < 205 -> ScreenSize.SMALL
        dp < 225 -> ScreenSize.REGULAR
        else -> ScreenSize.LARGE
    }
}

private operator fun TextUnit.times(f: Float): TextUnit = if (isSpecified) this * f.toDouble() else this

private fun TextStyle.scaled(f: Float) = if (f == 1f) this else copy(fontSize = fontSize * f, lineHeight = lineHeight * f)

/** Apple-style hierarchy: tight, heavier numbers and titles; quiet, slightly tracked labels. */
@Composable
fun int3rTypography(): Typography {
    val base = Typography(defaultFontFamily = Inter)
    val f = when (screenSize()) {
        ScreenSize.SMALL -> 0.92f
        ScreenSize.REGULAR -> 1f
        ScreenSize.LARGE -> 1.04f
    }
    fun TextStyle.numeral() = copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em).scaled(f)
    fun TextStyle.title(weight: FontWeight = FontWeight.SemiBold) = copy(fontWeight = weight, letterSpacing = (-0.01).em)
    fun TextStyle.label() = copy(fontWeight = FontWeight.Medium, letterSpacing = 0.01.em, fontFeatureSettings = "tnum")
    return base.copy(
        numeralExtraLarge = base.numeralExtraLarge.numeral(),
        numeralLarge = base.numeralLarge.numeral(),
        numeralMedium = base.numeralMedium.numeral(),
        numeralSmall = base.numeralSmall.numeral(),
        numeralExtraSmall = base.numeralExtraSmall.numeral(),
        displayLarge = base.displayLarge.title(FontWeight.Bold).scaled(f),
        displayMedium = base.displayMedium.title(FontWeight.Bold).scaled(f),
        displaySmall = base.displaySmall.title().scaled(f),
        titleLarge = base.titleLarge.title(),
        titleMedium = base.titleMedium.title(),
        titleSmall = base.titleSmall.title(),
        labelLarge = base.labelLarge.label(),
        labelMedium = base.labelMedium.label(),
        labelSmall = base.labelSmall.label(),
        bodyLarge = base.bodyLarge,
        bodyMedium = base.bodyMedium,
        bodySmall = base.bodySmall,
        bodyExtraSmall = base.bodyExtraSmall,
    )
}