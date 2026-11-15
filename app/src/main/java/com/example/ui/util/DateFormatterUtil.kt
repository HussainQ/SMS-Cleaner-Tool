package com.example.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatterUtil {
    private val shortFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    }

    private val secondsFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    }

    private val fullFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' hh:mm:ss a", Locale.getDefault())
    }

    private val backupFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    }

    fun formatShort(epochMillis: Long): String =
        shortFormatter.get()?.format(Date(epochMillis)) ?: ""

    fun formatWithSeconds(epochMillis: Long): String =
        secondsFormatter.get()?.format(Date(epochMillis)) ?: ""

    fun formatFull(epochMillis: Long): String =
        fullFormatter.get()?.format(Date(epochMillis)) ?: ""

    fun formatBackupDate(epochMillis: Long): String =
        backupFormatter.get()?.format(Date(epochMillis)) ?: ""
}
