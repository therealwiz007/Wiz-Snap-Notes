package com.wizlabs.snapnotes

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** A fix for the stamp. [place] is null when the phone is offline or the geocoder has no answer. */
data class Spot(val lat: Double, val lng: Double, val place: String?) {
    val coords get() = coords(lat, lng)

    companion object {
        fun coords(lat: Double, lng: Double) = String.format(Locale.US, "%.5f, %.5f", lat, lng)
        fun mapsLink(lat: Double, lng: Double) = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lng)
    }
}

/**
 * One-shot location for "Stamp location". Uses the plain Android LocationManager (no Play
 * services, no SDK). If the phone has no GPS fix in time, the photo is saved without location.
 */
object Locator {
    /** A last known fix this recent is good enough to use straight away. */
    private const val FRESH_MS = 2 * 60 * 1000L
    private const val TIMEOUT_MS = 12_000L

    fun granted(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Location turned off in quick settings, or no GPS/network provider at all. */
    fun enabled(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return runCatching { lm.isLocationEnabled }.getOrDefault(false)
    }

    /** Returns null when permission is missing, location is off, or no fix arrives in time. */
    suspend fun locate(context: Context): Spot? {
        val app = context.applicationContext
        if (!granted(app) || !enabled(app)) return null
        val loc = fix(app) ?: return null
        return Spot(loc.latitude, loc.longitude, placeName(app, loc))
    }

    @SuppressLint("MissingPermission")
    private suspend fun fix(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null

        val now = System.currentTimeMillis()
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time < FRESH_MS }
            .minByOrNull { it.accuracy }
            ?.let { return it }

        return withTimeoutOrNull(TIMEOUT_MS) { firstFix(lm, providers) }
    }

    /** Asks every enabled provider at once; the first real fix wins, the rest are cancelled. */
    private suspend fun firstFix(lm: LocationManager, providers: List<String>): Location? = coroutineScope {
        val asks = providers.map { p -> async { current(lm, p) } }
        try {
            var pending = asks
            while (pending.isNotEmpty()) {
                val (done, loc) = select { pending.forEach { d -> d.onAwait { d to it } } }
                if (loc != null) return@coroutineScope loc
                pending = pending - done
            }
            null
        } finally {
            asks.forEach { it.cancel() }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun current(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= 30) {
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                lm.getCurrentLocation(provider, cancel, { it.run() }) { loc -> if (cont.isActive) cont.resume(loc) }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) { if (cont.isActive) cont.resume(null) }
                }
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }

    /** "Naini Tal, Uttarakhand" style: area + town, falling back to the first address line. */
    private suspend fun placeName(context: Context, loc: Location): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        withTimeoutOrNull(5_000L) {
            runCatching {
                @Suppress("DEPRECATION")
                val a = Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                    ?: return@runCatching null
                val parts = listOfNotNull(
                    a.subLocality ?: a.thoroughfare,
                    a.locality ?: a.subAdminArea,
                    a.adminArea.takeIf { a.locality == null }
                ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                (if (parts.isNotEmpty()) parts.joinToString(", ") else a.getAddressLine(0))?.take(60)
            }.getOrNull()
        }
    }
}
