package com.definitly.temperaturemap

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var map: MapView
    private lateinit var temperatureOverlay: TemperatureOverlay
    private lateinit var status: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 8, 10, 8)
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(this).apply {
            text = "Температура на карте"
            textSize = 18f
            setTextColor(Color.BLACK)
            setPadding(4, 0, 8, 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            text = "Москва"
            setOnClickListener {
                map.controller.setZoom(5.5)
                map.controller.setCenter(GeoPoint(55.751244, 37.618423))
                loadTemperatures()
            }
        })
        header.addView(Button(this).apply {
            text = "↻"
            contentDescription = "Обновить температуру"
            setOnClickListener { loadTemperatures() }
        })
        root.addView(header)

        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(4.5)
            controller.setCenter(GeoPoint(55.751244, 37.618423))
        }
        temperatureOverlay = TemperatureOverlay()
        map.overlays.add(temperatureOverlay)
        root.addView(map, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply {
            text = "Загрузка температуры…"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.WHITE)
        }
        root.addView(status)
        setContentView(root)
        loadTemperatures()
    }

    private fun loadTemperatures() {
        status.text = "Загружаем данные Open-Meteo…"
        executor.execute {
            try {
                val points = buildList {
                    val latitudes = listOf(70.0, 60.0, 50.0, 40.0, 30.0)
                    val longitudes = listOf(-10.0, 10.0, 30.0, 50.0, 70.0, 90.0)
                    for (lat in latitudes) for (lon in longitudes) add(GeoPoint(lat, lon))
                }
                val temps = points.mapNotNull { point ->
                    try {
                        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${point.latitude}&longitude=${point.longitude}&current=temperature_2m")
                        val connection = (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 8000
                            readTimeout = 8000
                            requestMethod = "GET"
                        }
                        try {
                            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                            val value = json.getJSONObject("current").getDouble("temperature_2m")
                            TemperaturePoint(point.latitude, point.longitude, value)
                        } finally {
                            connection.disconnect()
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
                handler.post {
                    temperatureOverlay.points = temps
                    map.invalidate()
                    status.text = if (temps.isEmpty()) {
                        "Не удалось загрузить данные. Проверьте интернет и нажмите ↻."
                    } else {
                        "Температура сейчас · Open-Meteo · точек: ${temps.size}"
                    }
                }
            } catch (e: Exception) {
                handler.post { status.text = "Ошибка загрузки: ${e.localizedMessage ?: "нет соединения"}" }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::map.isInitialized) map.onResume()
    }

    override fun onPause() {
        if (::map.isInitialized) map.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    data class TemperaturePoint(val latitude: Double, val longitude: Double, val celsius: Double)

    private inner class TemperatureOverlay : Overlay() {
        var points: List<TemperaturePoint> = emptyList()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val radius = 29f

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return
            val projection = mapView.projection
            for (point in points) {
                val pixel = projection.toPixels(GeoPoint(point.latitude, point.longitude), null)
                val color = when {
                    point.celsius < -10 -> Color.rgb(35, 100, 220)
                    point.celsius < 0 -> Color.rgb(58, 160, 220)
                    point.celsius < 10 -> Color.rgb(60, 175, 120)
                    point.celsius < 20 -> Color.rgb(245, 175, 45)
                    else -> Color.rgb(225, 70, 55)
                }
                fill.color = color
                val bounds = RectF(pixel.x - radius, pixel.y - radius, pixel.x + radius, pixel.y + radius)
                canvas.drawRoundRect(bounds, 18f, 18f, fill)
                canvas.drawText("${point.celsius.roundToInt()}°", pixel.x.toFloat(), pixel.y + 10f, text)
            }
        }
    }
}
