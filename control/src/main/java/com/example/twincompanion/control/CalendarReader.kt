package com.example.twincompanion.control

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Lee el calendario del telefono y arma un texto del estado de agenda:
 * "En reunion: X hasta las 16:00", "Libre — proximo: Y a las 18:00", o "Sin eventos hoy".
 */
object CalendarReader {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

    fun read(context: Context): String? {
        if (!hasPermission(context)) return null

        val now = System.currentTimeMillis()
        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(builder, now)
        android.content.ContentUris.appendId(builder, endOfDay)

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        )

        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        context.contentResolver.query(
            builder.build(), projection, null, null,
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0)?.trim().ifNullOrBlank("(sin titulo)")
                val begin = cursor.getLong(1)
                val end = cursor.getLong(2)
                val allDay = cursor.getInt(3) == 1
                if (allDay) continue

                return if (begin <= now && now < end) {
                    "En reunion: $title hasta las ${fmt.format(end)}"
                } else {
                    "Libre — proximo: $title a las ${fmt.format(begin)}"
                }
            }
        }
        return "Sin eventos hoy"
    }

    private fun String?.ifNullOrBlank(default: String) =
        if (this.isNullOrBlank()) default else this
}
