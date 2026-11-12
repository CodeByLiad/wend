package com.jinatra.wend.today

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats engine minutes (which may run past midnight) as a clock time. */
class TimeFormat(private val formatter: DateTimeFormatter) {
    fun format(minute: Int): String {
        val m = Math.floorMod(minute, 24 * 60)
        return LocalTime.of(m / 60, m % 60).format(formatter)
    }

    companion object {
        fun of(is24Hour: Boolean, locale: Locale): TimeFormat = TimeFormat(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale))
    }
}

@Composable
fun rememberTimeFormat(): TimeFormat {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24 = DateFormat.is24HourFormat(context)
    return remember(locale, is24) { TimeFormat.of(is24, locale) }
}

@Composable
fun formatDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) { date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)) }
}

@Composable
fun durationText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> stringResource(R.string.duration_min, m)
        m == 0 -> stringResource(R.string.duration_h, h)
        else -> stringResource(R.string.duration_h_min, h, m)
    }
}
