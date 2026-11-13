package com.nexomaps.app

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private lateinit var status: TextView
    private lateinit var locationManager: LocationManager
    private val locationPermissionRequest = 4102
    private var isTilted = false
    private var userMarker: Marker? = null
    private var routeLine: Polyline? = null
    private var lastKnownPoint: LatLng? = null
    private var isVehicleMode = false
    private var selectedModel = "🚶 Hombre"
    private var isTracking = false
    private lateinit var tripTime: TextView
    private var tripStartedAt: Long? = null
    private lateinit var preferences: SharedPreferences
    private var currentDestinationName: String? = null
    private var currentDestinationPoint: LatLng? = null
    private var lastRouteInstructions: List<String> = emptyList()
    private val mapStyles = arrayOf("Mapa estándar", "Mapa claro", "Mapa Liberty")
    private val mapStyleUrls = arrayOf("https://demotiles.maplibre.org/style.json", "https://tiles.openfreemap.org/styles/positron", "https://tiles.openfreemap.org/styles/liberty")
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            val started = tripStartedAt ?: return
            val elapsed = ((SystemClock.elapsedRealtime() - started) / 1000L).coerceAtLeast(0L)
            val hours = elapsed / 3600L
            val minutes = (elapsed % 3600L) / 60L
            val seconds = elapsed % 60L
            tripTime.text = if (hours > 0) String.format(Locale.getDefault(), "Recorrido: %02d:%02d:%02d", hours, minutes, seconds)
                else String.format(Locale.getDefault(), "Recorrido: %02d:%02d", minutes, seconds)
            timerHandler.postDelayed(this, 1000L)
        }
    }

    private fun startTripTimer() {
        if (tripStartedAt == null) {
            tripStartedAt = SystemClock.elapsedRealtime()
            timerHandler.removeCallbacks(timerTick)
            timerHandler.post(timerTick)
        }
    }

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            startTripTimer()
            val point = LatLng(location.latitude, location.longitude)
            lastKnownPoint = point
            // Speed-based mode suggestion: walking below 5.5 m/s, vehicle above it.
            val detectedVehicle = selectedModel !in listOf("🚶 Hombre", "🚶‍♀️ Mujer")
            if (detectedVehicle != isVehicleMode) {
                isVehicleMode = detectedVehicle
                refreshUserMarker(point)
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
        preferences = getSharedPreferences("nexo_maps_settings", Context.MODE_PRIVATE)

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
        val modelButton = Button(this).apply {
            text = "🚘  Elegir personaje o vehículo"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(35, 49, 76))
            setOnClickListener { showModelPicker() }
        }
        header.addView(modelButton, LinearLayout.LayoutParams(-1, 46).apply { topMargin = 6 })
        val optionsButton = Button(this).apply {
            text = "★  Favoritos     ·     ⚙  Opciones"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(27, 43, 68))
            setOnClickListener { showOptionsDialog() }
        }
        header.addView(optionsButton, LinearLayout.LayoutParams(-1, 42).apply { topMargin = 5 })
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
        addControl(controls, "Seguir / Pausar") { toggleFollow() }
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
            readyMap.setStyle(Style.Builder().fromUri(mapStyleUrls[preferences.getInt("map_style", 0).coerceIn(0, mapStyleUrls.lastIndex)])) {
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


    private fun showOptionsDialog() {
        val options = arrayOf("Cambiar apariencia del mapa", "Unidades de distancia", "Mis lugares favoritos", "Búsquedas recientes", "Guardar destino actual en favoritos", "Ver instrucciones de la última ruta", "Borrar ruta del mapa", "Compartir destino actual", "Acerca de Nexo Maps")
        AlertDialog.Builder(this).setTitle("Nexo Maps · Opciones").setItems(options) { _, which ->
            when (which) {
                0 -> showMapStylePicker()
                1 -> showDistanceUnits()
                2 -> showSavedPlaces("favorites", "Mis lugares favoritos")
                3 -> showSavedPlaces("history", "Búsquedas recientes")
                4 -> saveCurrentFavorite()
                5 -> showRouteInstructions()
                6 -> {
                    routeLine?.let { map?.removePolyline(it) }
                    routeLine = null
                    lastRouteInstructions = emptyList()
                    status.text = "Ruta borrada"
                }
                7 -> shareCurrentDestination()
                8 -> AlertDialog.Builder(this).setTitle("Nexo Maps").setMessage(
                    "Navegación y exploración con mapa interactivo, GPS, búsqueda, rutas, favoritos e historial.\n\nLas rutas son estimaciones y no incluyen tráfico en vivo. Los semáforos y modelos 3D reales aún requieren fuentes y motores compatibles."
                ).setPositiveButton("Entendido", null).show()
            }
        }.setNegativeButton("Cerrar", null).show()
    }

    private fun showDistanceUnits() {
        val units = arrayOf("Kilómetros (km)", "Millas (mi)")
        val selected = preferences.getInt("distance_unit", 0).coerceIn(0, 1)
        AlertDialog.Builder(this).setTitle("Unidades de distancia")
            .setSingleChoiceItems(units, selected) { dialog, which ->
                preferences.edit().putInt("distance_unit", which).apply()
                dialog.dismiss()
                Toast.makeText(this, "Unidad actualizada para las próximas rutas.", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cerrar", null).show()
    }

    private fun showRouteInstructions() {
        if (lastRouteInstructions.isEmpty()) {
            Toast.makeText(this, "Primero calcula una ruta.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Indicaciones de ruta")
            .setItems(lastRouteInstructions.toTypedArray(), null)
            .setPositiveButton("Cerrar", null).show()
    }

    private fun shareCurrentDestination() {
        val name = currentDestinationName
        val point = currentDestinationPoint
        if (name == null || point == null) {
            Toast.makeText(this, "Primero busca un destino.", Toast.LENGTH_SHORT).show()
            return
        }
        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, name + " https://www.openstreetmap.org/?mlat=" + point.latitude + "&mlon=" + point.longitude)
        }
        startActivity(android.content.Intent.createChooser(share, "Compartir destino"))
    }
    private fun showMapStylePicker() {
        val selected = preferences.getInt("map_style", 0).coerceIn(0, mapStyles.lastIndex)
        AlertDialog.Builder(this).setTitle("Apariencia del mapa")
            .setSingleChoiceItems(mapStyles, selected) { dialog, which ->
                preferences.edit().putInt("map_style", which).apply()
                val camera = map?.cameraPosition
                status.text = "Cambiando a ${mapStyles[which]}..."
                map?.setStyle(Style.Builder().fromUri(mapStyleUrls[which])) {
                    if (camera != null) map?.animateCamera(CameraUpdateFactory.newCameraPosition(camera))
                    lastKnownPoint?.let { point ->
                        userMarker = map?.addMarker(MarkerOptions().position(point).icon(IconFactory.getInstance(this).fromBitmap(createAvatarBitmap(isVehicleMode))))
                    }
                    status.text = "${mapStyles[which]} activado"
                }
                dialog.dismiss()
            }.setNegativeButton("Cerrar", null).show()
    }

    private fun rememberHistory(query: String) {
        val history = preferences.getString("history_ordered", "")?.split("\n")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
        history.remove(query)
        history.add(0, query)
        preferences.edit().putString("history_ordered", history.take(15).joinToString("\n")).apply()
    }

    private fun saveCurrentFavorite() {
        val name = currentDestinationName
        val point = currentDestinationPoint
        if (name == null || point == null) {
            Toast.makeText(this, "Primero busca un lugar para guardarlo.", Toast.LENGTH_SHORT).show()
            return
        }
        val favorites = preferences.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()
        favorites.add("${name}|${point.latitude}|${point.longitude}")
        preferences.edit().putStringSet("favorites", favorites).apply()
        Toast.makeText(this, "Lugar guardado en favoritos", Toast.LENGTH_SHORT).show()
    }

    private fun showSavedPlaces(key: String, title: String) {
        val saved = if (key == "history") {
            preferences.getString("history_ordered", "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        } else {
            preferences.getStringSet(key, emptySet())?.toList()?.sorted() ?: emptyList()
        }
        if (saved.isEmpty()) {
            val message = if (key == "favorites") "Todavía no tienes favoritos. Busca un lugar y usa Opciones → Guardar destino actual en favoritos." else "Todavía no hay búsquedas recientes."
            AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Aceptar", null).show()
            return
        }
        val labels = saved.map { entry -> if (key == "favorites") entry.substringBefore("|") else entry }.toTypedArray()
        AlertDialog.Builder(this).setTitle(title).setItems(labels) { _, which ->
            val entry = saved[which]
            if (key == "favorites") {
                val parts = entry.split("|")
                if (parts.size == 3) {
                    val point = LatLng(parts[1].toDoubleOrNull() ?: 0.0, parts[2].toDoubleOrNull() ?: 0.0)
                    currentDestinationName = parts[0]
                    currentDestinationPoint = point
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15.0))
                    requestRoute(point, parts[0])
                }
            } else searchPlace(entry, false)
        }.setNegativeButton("Cerrar", null).show()
    }

    private fun showModelPicker() {
        val models = arrayOf(
            "🚶 Hombre", "🚶‍♀️ Mujer", "🏎️ Deportivo", "🚗 Sedán",
            "🚙 SUV", "🛻 Pickup", "🚛 Pipa de gas", "🚌 Autobús",
            "🚐 Combi", "🛺 Bicitaxi", "🏍️ Motocicleta", "🚲 Bicicleta"
        )
        val current = models.indexOf(selectedModel).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Selecciona tu personaje o vehículo")
            .setSingleChoiceItems(models, current) { dialog, which ->
                selectedModel = models[which]
                isVehicleMode = which >= 2
                val position = userMarker?.position
                if (position != null) refreshUserMarker(position)
                status.text = "Modelo seleccionado: $selectedModel"
                dialog.dismiss()
            }
            .setNegativeButton("Cerrar", null)
            .show()
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
            .setPositiveButton("Trazar ruta") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) searchPlace(query, true)
                else Toast.makeText(this, "Escribe un destino", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Ver lugar") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) searchPlace(query, false)
                else Toast.makeText(this, "Escribe un lugar", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun searchPlace(query: String, makeRoute: Boolean = false) {
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
                        currentDestinationName = query
                        currentDestinationPoint = point
                        rememberHistory(query)
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 14.0))
                        if (makeRoute) requestRoute(point, query)
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

    private fun requestRoute(destination: LatLng, destinationName: String) {
        val origin = lastKnownPoint
        if (origin == null) { Toast.makeText(this, "Primero activa Seguir y espera a que el GPS encuentre tu ubicación.", Toast.LENGTH_LONG).show(); return }
        status.text = "Calculando ruta hacia $destinationName..."
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://router.project-osrm.org/route/v1/driving/" + origin.longitude + "," + origin.latitude + ";" + destination.longitude + "," + destination.latitude + "?overview=full&geometries=geojson&steps=true")
                connection = (url.openConnection() as HttpURLConnection).apply { requestMethod = "GET"; connectTimeout = 15000; readTimeout = 20000; setRequestProperty("User-Agent", "NexoMaps/0.1 Android") }
                if (connection.responseCode !in 200..299) throw Exception("El servicio de rutas no respondió")
                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                if (root.optString("code") != "Ok") throw Exception("No se encontró una ruta")
                val route = root.getJSONArray("routes").getJSONObject(0)
                val minutes = (route.getDouble("duration") / 60.0).toInt().coerceAtLeast(1)
                val km = route.getDouble("distance") / 1000.0
                val instructions = mutableListOf<String>()
                val legs = route.optJSONArray("legs")
                if (legs != null && legs.length() > 0) {
                    val steps = legs.getJSONObject(0).optJSONArray("steps")
                    if (steps != null) for (i in 0 until steps.length()) {
                        val step = steps.getJSONObject(i)
                        val maneuver = step.optJSONObject("maneuver")
                        val type = maneuver?.optString("type", "continue") ?: "continue"
                        val modifier = maneuver?.optString("modifier", "") ?: ""
                        val road = step.optString("name").ifBlank { "la vía" }
                        val meters = step.optDouble("distance", 0.0)
                        val distanceLabel = if (meters >= 1000) String.format(Locale.getDefault(), "%.1f km", meters / 1000.0) else meters.toInt().toString() + " m"
                        val action = when (type) {
                            "depart" -> "Inicia el recorrido"
                            "arrive" -> "Llegada a tu destino"
                            "turn" -> "Gira " + modifier
                            "roundabout", "rotary" -> "Entra a la glorieta"
                            "merge" -> "Incorpórate"
                            "fork" -> "Toma la bifurcación " + modifier
                            else -> "Continúa"
                        }
                        instructions.add(action + " por " + road + " · " + distanceLabel)
                    }
                }
                val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
                val points = ArrayList<LatLng>()
                for (i in 0 until coordinates.length()) { val pair = coordinates.getJSONArray(i); points.add(LatLng(pair.getDouble(1), pair.getDouble(0))) }
                runOnUiThread {
                    if (points.size >= 2) {
                        routeLine?.let { map?.removePolyline(it) }
                        routeLine = map?.addPolyline(PolylineOptions().addAll(points).color(Color.rgb(25, 125, 255)).width(6f))
                        lastRouteInstructions = instructions
                        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(org.maplibre.android.geometry.LatLngBounds.fromLatLngs(points), 72))
                        val miles = preferences.getInt("distance_unit", 0) == 1
                        val distance = if (miles) km * 0.621371 else km
                        val unit = if (miles) "mi" else "km"
                        status.text = String.format(Locale.getDefault(), "Ruta: %.1f %s · aprox. %d min · instrucciones disponibles", distance, unit, minutes)
                    }
                }
            } catch (e: Exception) { runOnUiThread { status.text = "No se pudo calcular la ruta"; Toast.makeText(this, e.message ?: "Comprueba tu conexión.", Toast.LENGTH_LONG).show() } }
            finally { connection?.disconnect() }
        }.start()
    }
    private fun toggleFollow() {
        if (isTracking) {
            isTracking = false
            locationManager.removeUpdates(gpsListener)
            status.text = "Seguimiento pausado · pulsa Seguir para reanudar"
        } else {
            goToMyLocation()
        }
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

    // Marcador visual provisional; los modelos 3D animados requieren integrar modelos GLB y renderizador 3D.
    private fun createAvatarBitmap(vehicle: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(20, 115, 210)
        canvas.drawCircle(64f, 64f, 58f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 58f
        paint.typeface = Typeface.DEFAULT
        val symbol = selectedModel.substringBefore(" ").ifBlank { if (vehicle) "🚗" else "🚶" }
        canvas.drawText(symbol, 64f, 84f, paint)
        paint.color = Color.rgb(255, 210, 40)
        canvas.drawCircle(64f, 121f, 5f, paint)
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
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (isTracking && (hasFine || hasCoarse)) startGpsUpdates()
    }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onDestroy() { timerHandler.removeCallbacks(timerTick); locationManager.removeUpdates(gpsListener); mapView.onDestroy(); super.onDestroy() }
}
