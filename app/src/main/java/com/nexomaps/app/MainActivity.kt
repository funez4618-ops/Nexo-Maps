package com.nexomaps.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private lateinit var status: TextView
    private lateinit var locationManager: LocationManager
    private val locationPermissionRequest = 4102
    private var isTilted = false
    private var userMarker: Marker? = null
    private var isVehicleMode = false
    private var isTracking = false

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val point = LatLng(location.latitude, location.longitude)
            // Speed-based mode suggestion: walking below 5.5 m/s, vehicle above it.
            val detectedVehicle = location.hasSpeed() && location.speed >= 5.5f
            if (detectedVehicle != isVehicleMode) {
                isVehicleMode = detectedVehicle
                refreshUserMarker(point)
                status.text = if (isVehicleMode) "Modo vehículo · movimiento detectado" else "Modo peatón · caminando"
            }
            if (userMarker == null) {
                userMarker = map?.addMarker(MarkerOptions().position(point).icon(IconFactory.getInstance(this@MainActivity).fromBitmap(createAvatarBitmap(isVehicleMode))))
            } else {
                userMarker?.position = point
            }
            if (isTracking) map?.animateCamera(CameraUpdateFactory.newLatLng(point))
            if (location.speed < 0.7f) status.text = "Detenido · ubicación actualizada"
            else if (!isVehicleMode) status.text = "Modo peatón · siguiendo tu caminata"
            else status.text = "Modo vehículo · siguiendo tu recorrido"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(12, 20, 38)) }
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            setBackgroundColor(Color.rgb(16, 27, 53))
            elevation = 12f
        }
        val brand = TextView(this).apply {
            text = "NEXO MAPS"
            textSize = 19f
            setTextColor(Color.WHITE)
            letterSpacing = 0.16f
        }
        header.addView(brand)
        val subtitle = TextView(this).apply {
            text = "Explora el mundo a tu manera"
            textSize = 12f
            setTextColor(Color.rgb(170, 190, 215))
            setPadding(0, 4, 0, 10)
        }
        header.addView(subtitle)
        val search = Button(this).apply {
            text = "⌕   Buscar ciudad, lugar o dirección"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(42, 61, 91))
            setOnClickListener { showSearchDialog() }
        }
        header.addView(search, LinearLayout.LayoutParams(-1, 50))
        root.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(12, 12, 12, 0)
        })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(6, 8, 6, 8)
            setBackgroundColor(Color.rgb(16, 27, 53))
            elevation = 12f
        }
        addControl(controls, "Seguir") { goToMyLocation() }
        addControl(controls, "−") { zoomBy(-1.0) }
        addControl(controls, "+") { zoomBy(1.0) }
        addControl(controls, "Vista 3D") { toggleTilt() }
        addControl(controls, "Norte") { resetNorth() }
        root.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(10, 0, 10, 16)
        })

        status = TextView(this).apply {
            text = "Mapa mundial · OpenStreetMap"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.rgb(16, 27, 53))
            alpha = 0.95f
        }
        root.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = 78
        })

        setContentView(root)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.isCompassEnabled = true
            readyMap.uiSettings.isRotateGesturesEnabled = true
            readyMap.uiSettings.isZoomGesturesEnabled = true
            readyMap.uiSettings.isScrollGesturesEnabled = true
            readyMap.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) {
                readyMap.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(LatLng(20.0, 0.0)).zoom(1.8).build()
                    )
                )
                status.text = "Mapa mundial cargado · Activa Seguir para moverte"
            }
        }
    }

    private fun addControl(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = if (label.length > 4) 10f else 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(35, 49, 76))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(0, 48, 1f).apply {
            setMargins(3, 0, 3, 0)
        })
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Ej. Ciudad de México, Torre Eiffel..."
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Buscar en Nexo Maps")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Buscar") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) searchPlace(query)
                else Toast.makeText(this, "Escribe un lugar o dirección", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun searchPlace(query: String) {
        status.text = "Buscando: $query"
        Thread {
            try {
                if (!Geocoder.isPresent()) throw Exception("El servicio de búsqueda no está disponible en este dispositivo")
                @Suppress("DEPRECATION")
                val results = Geocoder(this, Locale.getDefault()).getFromLocationName(query, 1)
                runOnUiThread {
                    val place = results?.firstOrNull()
                    if (place != null) {
                        val point = LatLng(place.latitude, place.longitude)
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 14.0))
                        val label = place.locality ?: place.subAdminArea ?: place.countryName ?: query
                        status.text = "Resultado: $label"
                    } else {
                        status.text = "No se encontró: $query"
                        Toast.makeText(this, "No se encontró ese lugar. Prueba con otra búsqueda.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "No se pudo completar la búsqueda"
                    Toast.makeText(this, e.message ?: "Comprueba tu conexión a Internet", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun goToMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                locationPermissionRequest
            )
            return
        }
        try {
            val providers = locationManager.getProviders(true)
            var best: Location? = null
            for (provider in providers) {
                val candidate = locationManager.getLastKnownLocation(provider) ?: continue
                if (best == null || candidate.accuracy < best.accuracy) best = candidate
            }
            if (best != null) {
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(best.latitude, best.longitude), 16.0))
                isTracking = true
                startGpsUpdates()
                val point = LatLng(best.latitude, best.longitude)
                if (userMarker == null) userMarker = map?.addMarker(MarkerOptions().position(point).icon(IconFactory.getInstance(this).fromBitmap(createAvatarBitmap(false))))
                else userMarker?.position = point
                status.text = "Ubicación encontrada · siguiendo tu movimiento"
            } else {
                status.text = "Esperando señal GPS..."
                Toast.makeText(this, "Activa la ubicación/GPS y vuelve a intentarlo.", Toast.LENGTH_LONG).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permiso de ubicación no concedido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startGpsUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, gpsListener)
            } else {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 3f, gpsListener)
            }
        } catch (_: Exception) { status.text = "No se pudo iniciar el seguimiento GPS" }
    }

    private fun refreshUserMarker(point: LatLng) {
        val icon = IconFactory.getInstance(this).fromBitmap(createAvatarBitmap(isVehicleMode))
        if (userMarker == null) userMarker = map?.addMarker(MarkerOptions().position(point).icon(icon))
        else { userMarker?.position = point; userMarker?.icon = icon }
    }

    // Temporary map marker artwork. Replace with animated 3D GLB characters/vehicles in the 3D renderer phase.
    private fun createAvatarBitmap(vehicle: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(96, 112, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(20, 150, 255)
        canvas.drawCircle(48f, 52f, 39f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = if (vehicle) 43f else 48f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(if (vehicle) "A" else "P", 48f, 68f, paint)
        paint.color = Color.rgb(255, 210, 40)
        canvas.drawCircle(48f, 103f, 7f, paint)
        return bitmap
    }

    private fun zoomBy(amount: Double) {
        val current = map?.cameraPosition ?: return
        val nextZoom = (current.zoom + amount).coerceIn(1.0, 20.0)
        map?.animateCamera(CameraUpdateFactory.zoomTo(nextZoom))
    }

    private fun toggleTilt() {
        val current = map?.cameraPosition ?: return
        isTilted = !isTilted
        map?.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(current.target ?: LatLng(20.0, 0.0))
                .zoom(current.zoom.coerceAtLeast(14.0))
                .tilt(if (isTilted) 55.0 else 0.0)
                .bearing(current.bearing).build()
        ))
        status.text = if (isTilted) "Perspectiva inclinada activada" else "Vista superior activada"
    }

    private fun resetNorth() {
        val current = map?.cameraPosition ?: return
        isTilted = false
        map?.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(current.target ?: LatLng(20.0, 0.0))
                .zoom(current.zoom).tilt(0.0).bearing(0.0).build()
        ))
        status.text = "Orientación norte · Vista superior"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequest && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            goToMyLocation()
        } else if (requestCode == locationPermissionRequest) {
            Toast.makeText(this, "Necesitas permitir la ubicación para usar este botón.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onPause() { if (::locationManager.isInitialized) locationManager.removeUpdates(gpsListener); super.onPause(); mapView.onPause() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
}
