package com.nexomaps.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(12, 20, 38))
        }

        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        root.addView(
            mapView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18)
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
            setPadding(0, 5, 0, 10)
        }
        header.addView(subtitle)

        val search = TextView(this).apply {
            text = "⌕   Buscar lugares o direcciones"
            textSize = 15f
            setTextColor(Color.rgb(225, 235, 248))
            setPadding(16)
            setBackgroundColor(Color.rgb(35, 49, 76))
        }
        header.addView(search)

        root.addView(
            header,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply {
                setMargins(14, 14, 14, 0)
            }
        )

        val status = TextView(this).apply {
            text = "Mapa interactivo · OpenStreetMap"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(14)
            setBackgroundColor(Color.rgb(16, 27, 53))
            alpha = 0.94f
        }
        root.addView(
            status,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = 22
            }
        )

        setContentView(root)

        mapView.getMapAsync { map ->
            map.setStyle(
                Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")
            )
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }
}
