package com.vibecode.speedometer

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var speedText: TextView
    private lateinit var statusText: TextView
    private lateinit var locationManager: LocationManager
    private var lastLocation: Location? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val speedKmh = if (location.hasSpeed()) location.speed * 3.6 else estimateSpeed(location)
            val accuracy = if (location.hasAccuracy()) location.accuracy else Float.NaN
            speedText.text = String.format(Locale.US, "%.1f", speedKmh.coerceAtLeast(0.0))
            statusText.text = if (accuracy.isNaN()) "GPS • швидкість" else String.format(Locale.US, "GPS • точність %.0f м", accuracy)
            lastLocation = location
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 42)
        } else startGps()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF000000.toInt())
        }
        speedText = TextView(this).apply {
            text = "0.0"
            textSize = 92f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        val unit = TextView(this).apply {
            text = "км/год"
            textSize = 28f
            setTextColor(0xFFBDBDBD.toInt())
            gravity = Gravity.CENTER
        }
        statusText = TextView(this).apply {
            text = "Очікую GPS…"
            textSize = 16f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(speedText, LinearLayout.LayoutParams(-1, -2))
        root.addView(unit, LinearLayout.LayoutParams(-1, -2))
        root.addView(statusText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 32 })
        setContentView(root)
    }

    private fun estimateSpeed(location: Location): Double {
        val previous = lastLocation ?: return 0.0
        val dt = (location.time - previous.time) / 1000.0
        if (dt <= 0) return 0.0
        return previous.distanceTo(location) / dt * 3.6
    }

    private fun startGps() {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            statusText.text = "Увімкни GPS"
            return
        }
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            500L,
            0f,
            listener,
            Looper.getMainLooper()
        )
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { listener.onLocationChanged(it) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) startGps()
        else statusText.text = "Потрібен дозвіл на GPS"
    }

    override fun onDestroy() {
        if (::locationManager.isInitialized) locationManager.removeUpdates(listener)
        super.onDestroy()
    }
}
