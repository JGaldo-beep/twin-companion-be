package com.example.twincompanion.control

import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Lee la ubicacion del telefono y la convierte en:
 *  - el nombre de un lugar guardado (Casa, Oficina...) si estas cerca, o
 *  - la zona/ciudad ("En Bogota") si no.
 * Usa permiso fino para precision, pero NUNCA expone la direccion exacta al backend.
 */
object LocationReader {

    /** Ultima ubicacion cruda (para guardar lugares). Se queda en el telefono. */
    @Volatile
    var lastRaw: Location? = null
        private set

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun read(context: Context): String? {
        if (!hasPermission(context)) return null
        val location = freshLocation(context) ?: lastKnown(context) ?: return "Ubicacion no disponible"
        lastRaw = location
        // Primero: lugar guardado. Si no, zona/ciudad.
        return PlacesStore.match(context, location) ?: reverseGeocode(context, location)
    }

    private fun freshLocation(context: Context): Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!lm.isProviderEnabled(p)) continue
            val latch = CountDownLatch(1)
            var result: Location? = null
            try {
                lm.getCurrentLocation(p, null, Executors.newSingleThreadExecutor()) { loc ->
                    result = loc
                    latch.countDown()
                }
            } catch (e: SecurityException) {
                continue
            }
            latch.await(6, TimeUnit.SECONDS)
            if (result != null) return result
        }
        return null
    }

    private fun lastKnown(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (p in providers) {
            val loc = try {
                if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null
            } catch (e: SecurityException) {
                null
            }
            if (loc != null && (best == null || loc.time > best!!.time)) best = loc
        }
        return best
    }

    private fun reverseGeocode(context: Context, loc: Location): String {
        if (!Geocoder.isPresent()) return "Ubicacion desconocida"
        return try {
            @Suppress("DEPRECATION")
            val results = Geocoder(context, Locale.getDefault())
                .getFromLocation(loc.latitude, loc.longitude, 1)
            val a = results?.firstOrNull() ?: return "Ubicacion desconocida"
            val city = a.locality ?: a.subAdminArea ?: a.adminArea
            val neighborhood = a.subLocality
            when {
                !neighborhood.isNullOrBlank() && !city.isNullOrBlank() -> "Por $neighborhood, $city"
                !city.isNullOrBlank() -> "En $city"
                else -> "Ubicacion desconocida"
            }
        } catch (e: Exception) {
            "Ubicacion desconocida"
        }
    }
}
