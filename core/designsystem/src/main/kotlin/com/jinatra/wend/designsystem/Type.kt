package com.jinatra.wend.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Source Serif 4 display cut: numerals, the wordmark and section headings. */
val SerifDisplay = FontFamily(
    Font(R.font.source_serif_display_semibold, FontWeight.SemiBold),
    Font(R.font.source_serif_display_bold, FontWeight.Bold),
)

/** Source Serif 4 text cut: titles that sit near body text. */
val SerifText = FontFamily(
    Font(R.font.source_serif_text_regular, FontWeight.Normal),
    Font(R.font.source_serif_text_semibold, FontWeight.SemiBold),
)

private val Sans = FontFamily.Default

/** Tabular lining figures, so clock times line up in a column. */
const val TABULAR = "tnum, lnum"

internal val WendTypography = Typography(
    // The hero numeral.
    displayLarge = TextStyle(fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 112.sp, lineHeight = 104.sp, letterSpacing = (-0.03).em, fontFeatureSettings = "lnum"),
    // Stat numerals.
    displayMedium = TextStyle(fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.02).em, fontFeatureSettings = "lnum"),
    displaySmall = TextStyle(fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.015).em),
    // Section headings and sheet titles.
    headlineLarge = TextStyle(fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.01).em),
    headlineMedium = TextStyle(fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em),
    headlineSmall = TextStyle(fontFamily = SerifText, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = SerifText, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)
