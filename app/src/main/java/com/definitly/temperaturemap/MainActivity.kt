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
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** tier: 1 — видно сразу, 2/3/4/5 — появляются при приближении карты. */
data class City(val name: String, val lat: Double, val lon: Double, val tier: Int)

data class CityWeather(
    val city: City,
    val temp: Double,
    val feels: Double,
    val humidity: Int,
    val wind: Double,
    val code: Int
)

private fun c(name: String, lat: Double, lon: Double, tier: Int) = City(name, lat, lon, tier)

private val CITIES = listOf(
    // tier 1
    c("Москва", 55.7558, 37.6173, 1), c("Санкт-Петербург", 59.9343, 30.3351, 1),
    c("Новосибирск", 55.0084, 82.9357, 1), c("Екатеринбург", 56.8389, 60.6057, 1),
    c("Владивосток", 43.1155, 131.8855, 1), c("Якутск", 62.0355, 129.6755, 1),
    c("Лондон", 51.5074, -0.1278, 1), c("Париж", 48.8566, 2.3522, 1),
    c("Берлин", 52.5200, 13.4050, 1), c("Рим", 41.9028, 12.4964, 1),
    c("Мадрид", 40.4168, -3.7038, 1), c("Стамбул", 41.0082, 28.9784, 1),
    c("Киев", 50.4501, 30.5234, 1), c("Минск", 53.9006, 27.5590, 1),
    c("Астана", 51.1694, 71.4491, 1), c("Пекин", 39.9042, 116.4074, 1),
    c("Дели", 28.6139, 77.2090, 1), c("Каир", 30.0444, 31.2357, 1),
    c("Токио", 35.6762, 139.6503, 1), c("Нью-Йорк", 40.7128, -74.0060, 1),
    // tier 2
    c("Казань", 55.7887, 49.1221, 2), c("Нижний Новгород", 56.2965, 43.9361, 2),
    c("Самара", 53.1959, 50.1002, 2), c("Ростов-на-Дону", 47.2357, 39.7015, 2),
    c("Краснодар", 45.0355, 38.9753, 2), c("Уфа", 54.7388, 55.9721, 2),
    c("Красноярск", 56.0153, 92.8932, 2), c("Омск", 54.9885, 73.3242, 2),
    c("Челябинск", 55.1644, 61.4368, 2), c("Волгоград", 48.7080, 44.5133, 2),
    c("Пермь", 58.0105, 56.2502, 2), c("Воронеж", 51.6720, 39.1843, 2),
    c("Хабаровск", 48.4802, 135.0719, 2), c("Мурманск", 68.9585, 33.0827, 2),
    c("Архангельск", 64.5401, 40.5433, 2), c("Иркутск", 52.2870, 104.3050, 2),
    c("Сочи", 43.5855, 39.7231, 2), c("Варшава", 52.2297, 21.0122, 2),
    c("Стокгольм", 59.3293, 18.0686, 2), c("Хельсинки", 60.1699, 24.9384, 2),
    c("Афины", 37.9838, 23.7275, 2), c("Тбилиси", 41.7151, 44.8271, 2),
    c("Баку", 40.4093, 49.8671, 2), c("Ташкент", 41.2995, 69.2401, 2),
    c("Алматы", 43.2220, 76.8512, 2), c("Тегеран", 35.6892, 51.3890, 2),
    c("Дубай", 25.2048, 55.2708, 2),
    // tier 3
    c("Тюмень", 57.1522, 65.5272, 3), c("Саратов", 51.5336, 46.0343, 3),
    c("Тольятти", 53.5078, 49.4204, 3), c("Ижевск", 56.8498, 53.2045, 3),
    c("Барнаул", 53.3548, 83.7698, 3), c("Ульяновск", 54.3282, 48.3866, 3),
    c("Томск", 56.4846, 84.9476, 3), c("Кемерово", 55.3547, 86.0873, 3),
    c("Оренбург", 51.7682, 55.0970, 3), c("Калининград", 54.7104, 20.4522, 3),
    c("Петрозаводск", 61.7849, 34.3469, 3), c("Сыктывкар", 61.6688, 50.8354, 3),
    c("Салехард", 66.5299, 66.6019, 3), c("Норильск", 69.3558, 88.1893, 3),
    c("Магадан", 59.5638, 150.8035, 3), c("Петропавловск-Камчатский", 53.0452, 158.6483, 3),
    c("Южно-Сахалинск", 46.9591, 142.7380, 3), c("Чита", 52.0340, 113.4994, 3),
    c("Улан-Удэ", 51.8335, 107.5841, 3), c("Симферополь", 44.9521, 34.1024, 3),
    c("Махачкала", 42.9849, 47.5047, 3), c("Тула", 54.1930, 37.6177, 3),
    c("Ярославль", 57.6261, 39.8845, 3), c("Смоленск", 54.7826, 32.0453, 3),
    // tier 4
    c("Вологда", 59.2205, 39.8915, 4), c("Белгород", 50.5997, 36.5983, 4),
    c("Липецк", 52.6031, 39.5708, 4), c("Курск", 51.7304, 36.1926, 4),
    c("Брянск", 53.2434, 34.3640, 4), c("Псков", 57.8136, 28.3496, 4),
    c("Великий Новгород", 58.5215, 31.2755, 4), c("Тверь", 56.8587, 35.9176, 4),
    c("Рязань", 54.6269, 39.6916, 4), c("Пенза", 53.2007, 45.0046, 4),
    c("Киров", 58.6035, 49.6680, 4), c("Курган", 55.4410, 65.3411, 4),
    c("Абакан", 53.7156, 91.4292, 4), c("Благовещенск", 50.2796, 127.5405, 4),
    c("Анадырь", 64.7337, 177.5101, 4), c("Мирный", 62.5353, 113.9611, 4),
    c("Нарьян-Мар", 67.6381, 53.0069, 4), c("Ханты-Мансийск", 61.0042, 69.0019, 4),
    c("Владикавказ", 43.0241, 44.6812, 4), c("Грозный", 43.3180, 45.6981, 4),
    c("Ставрополь", 45.0428, 41.9734, 4), c("Астрахань", 46.3497, 48.0408, 4),
    c("Элиста", 46.3078, 44.2558, 4),
    // tier 2 (ещё города)
    c("Лиссабон", 38.7223, -9.1393, 2), c("Амстердам", 52.3676, 4.9041, 2),
    c("Вена", 48.2082, 16.3738, 2), c("Прага", 50.0755, 14.4378, 2),
    c("Будапешт", 47.4979, 19.0402, 2), c("Бухарест", 44.4268, 26.1025, 2),
    c("Осло", 59.9139, 10.7522, 2), c("Сеул", 37.5665, 126.978, 2),
    c("Шанхай", 31.2304, 121.4737, 2), c("Мумбаи", 19.076, 72.8777, 2),
    c("Мехико", 19.4326, -99.1332, 2), c("Лос-Анджелес", 34.0522, -118.2437, 2),
    c("Сан-Паулу", -23.5505, -46.6333, 2), c("Сидней", -33.8688, 151.2093, 2),
    // tier 3 (ещё города)
    c("Чебоксары", 56.1322, 47.2519, 3), c("Сургут", 61.254, 73.3962, 3),
    c("София", 42.6977, 23.3219, 3), c("Белград", 44.7866, 20.4489, 3),
    c("Загреб", 45.815, 15.9819, 3), c("Копенгаген", 55.6761, 12.5683, 3),
    c("Рига", 56.9496, 24.1052, 3), c("Вильнюс", 54.6872, 25.2797, 3),
    c("Таллин", 59.437, 24.7536, 3), c("Дублин", 53.3498, -6.2603, 3),
    c("Рейкьявик", 64.1466, -21.9426, 3), c("Милан", 45.4642, 9.19, 3),
    c("Барселона", 41.3851, 2.1734, 3), c("Мюнхен", 48.1351, 11.582, 3),
    c("Одесса", 46.4825, 30.7233, 3), c("Кишинёв", 47.0105, 28.8638, 3),
    c("Ереван", 40.1792, 44.4991, 3), c("Бишкек", 42.8746, 74.5698, 3),
    c("Душанбе", 38.5598, 68.787, 3), c("Ашхабад", 37.9601, 58.3261, 3),
    c("Улан-Батор", 47.8864, 106.9057, 3), c("Гонконг", 22.3193, 114.1694, 3),
    c("Бангкок", 13.7563, 100.5018, 3), c("Сингапур", 1.3521, 103.8198, 3),
    c("Карачи", 24.8607, 67.0011, 3), c("Эр-Рияд", 24.7136, 46.6753, 3),
    c("Багдад", 33.3152, 44.3661, 3), c("Лагос", 6.5244, 3.3792, 3),
    c("Найроби", -1.2921, 36.8219, 3), c("Йоханнесбург", -26.2041, 28.0473, 3),
    c("Кейптаун", -33.9249, 18.4241, 3), c("Чикаго", 41.8781, -87.6298, 3),
    c("Торонто", 43.6532, -79.3832, 3), c("Буэнос-Айрес", -34.6037, -58.3816, 3),
    // tier 4 (ещё города)
    c("Калуга", 54.5138, 36.2612, 4), c("Владимир", 56.129, 40.4066, 4),
    c("Иваново", 57.0004, 40.9739, 4), c("Кострома", 57.7679, 40.9269, 4),
    c("Орёл", 52.9651, 36.0785, 4), c("Тамбов", 52.7212, 41.4523, 4),
    c("Йошкар-Ола", 56.6316, 47.8862, 4), c("Саранск", 54.1871, 45.1749, 4),
    c("Череповец", 59.1374, 37.909, 4), c("Ухта", 63.567, 53.683, 4),
    c("Воркута", 67.4975, 64.0409, 4), c("Новороссийск", 44.7235, 37.7687, 4),
    c("Майкоп", 44.6098, 40.1004, 4), c("Нальчик", 43.4981, 43.6189, 4),
    c("Таганрог", 47.2362, 38.8969, 4), c("Набережные Челны", 55.743, 52.3958, 4),
    c("Магнитогорск", 53.4072, 58.9791, 4), c("Нижневартовск", 60.9344, 76.5531, 4),
    c("Новый Уренгой", 66.083, 76.633, 4), c("Нижний Тагил", 57.9101, 59.9813, 4),
    c("Орск", 51.2049, 58.5668, 4), c("Новокузнецк", 53.7596, 87.1216, 4),
    c("Братск", 56.1325, 101.614, 4), c("Кызыл", 51.7191, 94.4378, 4),
    c("Горно-Алтайск", 51.9581, 85.9603, 4), c("Комсомольск-на-Амуре", 50.5499, 137.0079, 4),
    // Дополнительные населённые пункты Чувашии и соседних регионов
    c("Вурнары", 55.4911, 46.9622, 4),
    c("Канаш", 55.5069, 47.4914, 4),
    c("Шумерля", 55.4962, 46.4181, 4),
    c("Ибреси", 55.3029, 47.0599, 4),
    c("Красный Октябрь", 55.4141, 46.6481, 4),
    c("Арзамас", 55.3948, 43.8399, 4),
    c("Муром", 55.5750, 42.0426, 4),
    c("Вековка", 55.2290, 41.0850, 4),
    // tier 5 (ещё города)
    c("Подольск", 55.4242, 37.5547, 5), c("Химки", 55.897, 37.4297, 5),
    c("Сергиев Посад", 56.3153, 38.1363, 5), c("Коломна", 55.0794, 38.7783, 5),
    c("Серпухов", 54.9158, 37.4113, 5), c("Дмитров", 56.3439, 37.52, 5),
    c("Обнинск", 55.0944, 36.6104, 5), c("Орехово-Зуево", 55.8067, 38.9618, 5),
    c("Великие Луки", 56.34, 30.5361, 5), c("Кандалакша", 67.155, 32.414, 5),
    c("Выборг", 60.7094, 28.7518, 5), c("Анапа", 44.894, 37.316, 5),
    c("Черкесск", 44.2269, 42.0587, 5), c("Пятигорск", 44.0486, 43.0594, 5),
    c("Кисловодск", 43.905, 42.716, 5), c("Шахты", 47.7085, 40.216, 5),
    c("Волгодонск", 47.5136, 42.1508, 5), c("Дербент", 42.0678, 48.2899, 5),
    c("Балаково", 52.0278, 47.7994, 5), c("Энгельс", 51.4854, 46.1267, 5),
    c("Ангарск", 52.5448, 103.8884, 5), c("Ноябрьск", 63.193, 75.45, 5),
    c("Ачинск", 56.2695, 90.4979, 5), c("Находка", 42.824, 132.893, 5),
    c("Уссурийск", 43.7972, 131.9518, 5), c("Тикси", 71.636, 128.87, 5)
)

private fun weatherText(code: Int): String = when (code) {
    0 -> "Ясно"
    1 -> "Преимущественно ясно"
    2 -> "Переменная облачность"
    3 -> "Пасмурно"
    45, 48 -> "Туман"
    51, 53, 55, 56, 57 -> "Морось"
    61, 63, 65, 66, 67 -> "Дождь"
    71, 73, 75, 77 -> "Снег"
    80, 81, 82 -> "Ливень"
    85, 86 -> "Снегопад"
    95, 96, 99 -> "Гроза"
    else -> "—"
}

class MainActivity : Activity() {
    private lateinit var map: MapView
    private lateinit var overlay: CityOverlay
    private lateinit var status: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            // отступы под строку состояния и панель навигации (edge-to-edge на Android 15)
            setOnApplyWindowInsetsListener { v, insets ->
                @Suppress("DEPRECATION")
                v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                insets
            }
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
            minZoomLevel = 3.0
            controller.setZoom(4.5)
            controller.setCenter(GeoPoint(55.751244, 37.618423))
        }
        overlay = CityOverlay()
        map.overlays.add(overlay)
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
                val lats = CITIES.joinToString(",") { it.lat.toString() }
                val lons = CITIES.joinToString(",") { it.lon.toString() }
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lats&longitude=$lons" +
                        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                        "&wind_speed_unit=ms"
                )
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    requestMethod = "GET"
                }
                val result = try {
                    val code = connection.responseCode
                    if (code != 200) {
                        val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        throw RuntimeException("HTTP $code ${err.orEmpty().take(120)}")
                    }
                    val arr = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                    // порядок элементов ответа совпадает с порядком запрошенных координат
                    (0 until min(arr.length(), CITIES.size)).map { i ->
                        val cur = arr.getJSONObject(i).getJSONObject("current")
                        CityWeather(
                            CITIES[i],
                            cur.getDouble("temperature_2m"),
                            cur.getDouble("apparent_temperature"),
                            cur.getInt("relative_humidity_2m"),
                            cur.getDouble("wind_speed_10m"),
                            cur.getInt("weather_code")
                        )
                    }
                } finally {
                    connection.disconnect()
                }
                handler.post {
                    overlay.items = result
                    overlay.selected = null
                    map.invalidate()
                    status.text = "Open-Meteo · городов: ${result.size} · нажмите на город, чтобы увидеть подробности. " +
                        "Приблизьте карту — появятся новые города."
                }
            } catch (e: Exception) {
                android.util.Log.e("TemperatureMap", "load failed", e)
                handler.post { status.text = "Ошибка: ${e.javaClass.simpleName}: ${e.localizedMessage}" }
            }
        }
    }

    private fun showDetails(w: CityWeather) {
        status.text = "${w.city.name}: ${w.temp.roundToInt()}° (ощущается как ${w.feels.roundToInt()}°)\n" +
            "${weatherText(w.code)} · ветер ${w.wind.roundToInt()} м/с · влажность ${w.humidity}%"
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

    inner class CityOverlay : Overlay() {
        var items: List<CityWeather> = emptyList()
        var selected: CityWeather? = null

        private val d = resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = 3f * d
        }
        private val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * d
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 30)
            textSize = 11f * d
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val halo = Paint(namePaint).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * d
        }
        private val hits = ArrayList<Pair<CityWeather, RectF>>()

        private fun minZoom(tier: Int) = when (tier) {
            1 -> 3.0
            2 -> 4.5
            3 -> 5.5
            4 -> 6.5
            else -> 7.5
        }

        private fun colorFor(t: Double) = when {
            t < -10 -> Color.rgb(35, 100, 220)
            t < 0 -> Color.rgb(58, 160, 220)
            t < 10 -> Color.rgb(60, 175, 120)
            t < 20 -> Color.rgb(245, 175, 45)
            else -> Color.rgb(225, 70, 55)
        }

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return
            hits.clear()
            val zoom = mapView.zoomLevelDouble
            val projection = mapView.projection
            val occupied = ArrayList<RectF>()
            val margin = 40f * d

            val visible = items
                .filter { zoom >= minZoom(it.city.tier) || it === selected }
                .sortedBy { if (it === selected) 0 else it.city.tier }

            for (w in visible) {
                val p = projection.toPixels(GeoPoint(w.city.lat, w.city.lon), null)
                val x = p.x.toFloat()
                val y = p.y.toFloat()
                if (x < -margin || y < -margin || x > mapView.width + margin || y > mapView.height + margin) continue

                val label = "${w.temp.roundToInt()}°"
                val pillW = tempPaint.measureText(label) + 14f * d
                val pillH = 26f * d
                val pill = RectF(x - pillW / 2, y - pillH / 2, x + pillW / 2, y + pillH / 2)
                val nameW = namePaint.measureText(w.city.name)
                val nameBaseline = pill.bottom + 12f * d
                val full = RectF(
                    min(pill.left, x - nameW / 2), pill.top,
                    max(pill.right, x + nameW / 2), nameBaseline + 4f * d
                )
                // метки не должны перекрываться: более важные города рисуются первыми
                if (w !== selected && occupied.any { RectF.intersects(it, full) }) continue
                occupied.add(full)
                hits.add(w to full)

                fill.color = colorFor(w.temp)
                canvas.drawRoundRect(pill, 8f * d, 8f * d, fill)
                if (w === selected) canvas.drawRoundRect(pill, 8f * d, 8f * d, border)
                canvas.drawText(label, x, pill.centerY() + tempPaint.textSize * 0.35f, tempPaint)
                canvas.drawText(w.city.name, x, nameBaseline, halo)
                canvas.drawText(w.city.name, x, nameBaseline, namePaint)
            }
        }

        override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
            val hit = hits.firstOrNull { it.second.contains(e.x, e.y) }?.first
            return if (hit != null) {
                selected = hit
                showDetails(hit)
                mapView.invalidate()
                true
            } else {
                false
            }
        }
    }
}
