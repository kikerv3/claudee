package com.kikerv.dirspace.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Tamaños en unidades binarias (KB = 1024), que es lo que muestran los
 * gestores de archivos de Android y lo que hace WinDirStat.
 */
fun formatBytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    val abs = abs(bytes)
    if (abs < 1024) return "$bytes B"
    var value = bytes.toDouble()
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var unitIndex = -1
    while (abs(value) >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val pattern = when {
        abs(value) >= 100 -> "%.0f %s"
        abs(value) >= 10 -> "%.1f %s"
        else -> "%.2f %s"
    }
    return String.format(locale, pattern, value, units[unitIndex])
}

fun formatCount(count: Int, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%,d", count)

fun formatPercent(fraction: Float, locale: Locale = Locale.getDefault()): String {
    val pct = fraction * 100f
    return when {
        pct > 0f && pct < 0.1f -> "<0.1 %"
        pct >= 10f -> String.format(locale, "%.0f %%", pct)
        else -> String.format(locale, "%.1f %%", pct)
    }
}

fun formatDate(millis: Long): String {
    if (millis <= 0L) return "—"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}
