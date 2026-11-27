/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.musicmap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.scope.Scope
import kotlin.coroutines.resume

actual fun createMapLocationSource(scope: Scope): MapLocationSource =
    AndroidMapLocationSource(scope.get<Context>().applicationContext)

private class AndroidMapLocationSource(private val context: Context) : MapLocationSource {

    override val permissions: List<String> =
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

    override fun hasPermission(): Boolean = permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun current(): Pair<Double, Double>? {
        val fix = freshFix() ?: lastKnown() ?: return null
        return fix.latitude to fix.longitude
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(): Location? = withContext(Dispatchers.Main) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return@withContext null
        withTimeoutOrNull(FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val signal = android.os.CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                            if (cont.isActive) cont.resume(location)
                        }
                    } else {
                        val listener = object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                if (cont.isActive) cont.resume(location)
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                            override fun onProviderEnabled(provider: String) = Unit
                            override fun onProviderDisabled(provider: String) = Unit
                        }
                        cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
                        @Suppress("DEPRECATION")
                        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider ->
                runCatching { if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 10_000L
    }
}
