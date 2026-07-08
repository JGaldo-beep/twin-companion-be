package com.example.twincompanion.control

import android.content.Context
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lugares con nombre (Casa, Oficina, ...) guardados LOCALMENTE en el telefono.
 * Las coordenadas nunca salen del dispositivo: al backend solo viaja el nombre.
 */
object PlacesStore {

    private const val PREF = "places"
    private const val KEY = "list"
    const val RADIUS_M = 160f  // que tan cerca para considerar "estas ahi"

    data class Place(val name: String, val lat: Double, val lng: Double)

    fun list(context: Context): List<Place> {
        val raw = prefs(context).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Place(o.getString("name"), o.getDouble("lat"), o.getDouble("lng"))
        }
    }

    fun save(context: Context, name: String, lat: Double, lng: Double) {
        val current = list(context).filterNot { it.name.equals(name, ignoreCase = true) }
        val arr = JSONArray()
        (current + Place(name, lat, lng)).forEach { p ->
            arr.put(JSONObject().put("name", p.name).put("lat", p.lat).put("lng", p.lng))
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    /** Devuelve el nombre del lugar si la ubicacion esta dentro del radio; si no, null. */
    fun match(context: Context, loc: Location): String? {
        val res = FloatArray(1)
        return list(context).firstOrNull { p ->
            Location.distanceBetween(loc.latitude, loc.longitude, p.lat, p.lng, res)
            res[0] <= RADIUS_M
        }?.name
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
