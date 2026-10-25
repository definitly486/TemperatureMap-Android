package com.definitly.temperaturemap

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Экран подробной погоды по городу: текущие условия, прогноз по дням и часам,
 * плитки (осадки, ветер, воздух, влажность, УФ, видимость, давление, облачность, солнце, луна).
 * Данные — Open-Meteo (прогноз + качество воздуха).
 */
class DetailActivity : Activity() {

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"

        private const val CARD_COLOR = 0xEE1C1B22.toInt()
        private const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val TEXT_SECONDARY = 0xFFB4B6C2.toInt()
        private const val ACCENT_BLUE = 0xFF9CB8FF.toInt()
        private val RU: Locale = Locale.forLanguageTag("ru")
        private val WEEKDAYS = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var cityName = ""
    private var lat = 0.0
    private var lon = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cityName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
        lon = intent.getDoubleExtra(EXTRA_LON, 0.0)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setOnApplyWindowInsetsListener { v, insets ->
                @Suppress("DEPRECATION")
                v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                insets
            }
        }
        applyBackground(isDay = false)

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(16), dp(8))
        }
        bar.addView(TextView(this).apply {
            text = "←"
            textSize = 26f
            setTextColor(TEXT_PRIMARY)
            gravity = Gravity.CENTER
            contentDescription = "Назад"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(TextView(this).apply {
            text = cityName
            textSize = 22f
            setTextColor(TEXT_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(bar)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        showMessage("Загружаем подробности…", withRetry = false)
        load()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- данные

    private fun load() {
        executor.execute {
            try {
                val forecast = JSONObject(httpGet(forecastUrl()))
                // качество воздуха — необязательно: без него экран всё равно показывается
                val air = try {
                    JSONObject(httpGet(airUrl())).getJSONObject("current")
                } catch (e: Exception) {
                    android.util.Log.w("TemperatureMap", "air quality failed", e)
                    null
                }
                handler.post { if (!isFinishing) render(forecast, air) }
            } catch (e: Exception) {
                android.util.Log.e("TemperatureMap", "detail load failed", e)
                handler.post {
                    if (!isFinishing) showMessage("Ошибка: ${e.javaClass.simpleName}: ${e.localizedMessage}", withRetry = true)
                }
            }
        }
    }

    private fun forecastUrl() =
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&timezone=auto&forecast_days=10&wind_speed_unit=ms" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,weather_code,is_day," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,precipitation,cloud_cover,pressure_msl,visibility,uv_index" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum," +
            "sunrise,sunset,uv_index_max"

    private fun airUrl() =
        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=european_aqi,pm2_5"

    private fun httpGet(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            if (code != 200) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw RuntimeException("HTTP $code ${err.orEmpty().take(120)}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    // ---------------------------------------------------------------- отрисовка

    private fun render(forecast: JSONObject, air: JSONObject?) {
        val cur = forecast.getJSONObject("current")
        val daily = forecast.getJSONObject("daily")
        val hourly = forecast.getJSONObject("hourly")
        val isDay = cur.optInt("is_day", 1) == 1
        applyBackground(isDay)
        content.removeAllViews()

        val code = cur.getInt("weather_code")
        val dTimes = daily.getJSONArray("time")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")

        // --- шапка
        content.addView(centerText(weatherText(code), 20f, TEXT_PRIMARY, topDp = 8))
        content.addView(TextView(this).apply {
            text = "${cur.getDouble("temperature_2m").roundToInt()}°C"
            textSize = 88f
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = false
        })
        content.addView(centerText("Ощущается как ${cur.getDouble("apparent_temperature").roundToInt()}°", 17f, TEXT_SECONDARY, topDp = 4))
        content.addView(
            centerText(
                "Ночь: ${dMin.getDouble(0).roundToInt()}° • День: ${dMax.getDouble(0).roundToInt()}°",
                14f, TEXT_SECONDARY, topDp = 4, bottomDp = 16
            )
        )

        // --- ежедневный прогноз
        val dCodes = daily.getJSONArray("weather_code")
        val dProb = daily.getJSONArray("precipitation_probability_max")
        val dailyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0 until dTimes.length()) {
            val day = dTimes.getString(i)
            dailyRow.addView(
                forecastColumn(
                    top = if (i == 0) "Сегодня" else WEEKDAYS[dayOfWeek(day) - 1],
                    sub = day.substring(8, 10) + "." + day.substring(5, 7),
                    emoji = weatherEmoji(dCodes.getInt(i), true),
                    value = "${dMax.getDouble(i).roundToInt()}°",
                    value2 = "${dMin.getDouble(i).roundToInt()}°",
                    prob = if (dProb.isNull(i)) 0 else dProb.getInt(i),
                    widthDp = 64,
                    highlight = i == 0
                )
            )
        }
        content.addView(card("📅  Ежедневный прогноз", hScroll(dailyRow)))

        // --- почасовой прогноз (48 часов, начиная с текущего)
        val hTimes = hourly.getJSONArray("time")
        val hTemp = hourly.getJSONArray("temperature_2m")
        val hCodes = hourly.getJSONArray("weather_code")
        val hProb = hourly.getJSONArray("precipitation_probability")
        val hDay = hourly.getJSONArray("is_day")
        val nowTime = cur.getString("time") // «2026-10-25T21:15» — в часовом поясе города
        val nowHour = nowTime.substring(0, 13) + ":00"
        var start = 0
        while (start < hTimes.length() - 1 && hTimes.getString(start) < nowHour) start++
        val hourlyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in start until min(hTimes.length(), start + 48)) {
            hourlyRow.addView(
                forecastColumn(
                    top = if (i == start) "Сейчас" else hTimes.getString(i).substring(11, 16),
                    sub = null,
                    emoji = weatherEmoji(hCodes.getInt(i), hDay.optInt(i, 1) == 1),
                    value = "${hTemp.getDouble(i).roundToInt()}°",
                    value2 = null,
                    prob = if (hProb.isNull(i)) 0 else hProb.getInt(i),
                    widthDp = 56,
                    highlight = i == start
                )
            )
        }
        content.addView(card("🕒  Почасовой прогноз", hScroll(hourlyRow)))

        // --- плитки
        val tiles = ArrayList<View>()

        val rainToday = daily.getJSONArray("precipitation_sum").optDouble(0, 0.0)
        val rainNow = cur.optDouble("precipitation", 0.0)
        tiles.add(
            tile(
                "🌧  Осадки", "${fmtMm(rainToday)} мм",
                if (rainNow > 0) "Сейчас: ${fmtMm(rainNow)} мм" else "Осадки за сегодня"
            )
        )

        val wind = cur.getDouble("wind_speed_10m")
        val gusts = cur.optDouble("wind_gusts_10m", wind)
        val dir = compass(cur.optDouble("wind_direction_10m", 0.0))
        tiles.add(tile("🌬  Ветер", "${wind.roundToInt()} м/с", "Порывы: ${gusts.roundToInt()} м/с · $dir"))

        if (air != null && !air.isNull("european_aqi")) {
            val aqi = air.getDouble("european_aqi").roundToInt()
            tiles.add(tile("🏭  Воздух", "$aqi", aqiLabel(aqi), aqiColor(aqi)))
        } else {
            tiles.add(tile("🏭  Воздух", "—", "Нет данных"))
        }

        val humidity = cur.getInt("relative_humidity_2m")
        val dew = cur.optDouble("dew_point_2m", Double.NaN)
        tiles.add(
            tile(
                "💧  Влажность", "$humidity %",
                if (dew.isNaN()) "" else "Точка росы: ${dew.roundToInt()}°"
            )
        )

        val uv = cur.optDouble("uv_index", 0.0)
        val uvMax = daily.getJSONArray("uv_index_max").optDouble(0, 0.0)
        tiles.add(tile("🔆  УФ-индекс", "${uv.roundToInt()}", "${uvLabel(uv)} · макс. ${uvMax.roundToInt()}"))

        val vis = cur.optDouble("visibility", Double.NaN)
        if (!vis.isNaN()) {
            val km = vis / 1000.0
            tiles.add(tile("👁  Видимость", "${fmt1(km)} км", visLabel(km)))
        }

        val pressure = cur.optDouble("pressure_msl", Double.NaN)
        if (!pressure.isNaN()) {
            tiles.add(tile("🧭  Давление", fmt1(pressure * 0.750062), "мм рт. ст."))
        }

        tiles.add(tile("☁️  Облачность", "${cur.optInt("cloud_cover", 0)} %", cloudLabel(cur.optInt("cloud_cover", 0))))

        // солнце: рассвет/закат и положение солнца на дуге
        val sunrise = daily.getJSONArray("sunrise").optString(0, "")
        val sunset = daily.getJSONArray("sunset").optString(0, "")
        if (sunrise.length >= 16 && sunset.length >= 16) {
            val rise = minutesOfDay(sunrise.substring(11, 16))
            val set = minutesOfDay(sunset.substring(11, 16))
            val now = minutesOfDay(nowTime.substring(11, 16))
            val progress = if (set > rise) (now - rise).toFloat() / (set - rise) else 0f
            tiles.add(sunTile(sunrise.substring(11, 16), sunset.substring(11, 16), progress))
        }

        // луна
        val moon = moonPhase(System.currentTimeMillis())
        tiles.add(tile("🌙  Луна", moon.emoji, "${moon.name} · ${(moon.illumination * 100).roundToInt()} %"))

        var i = 0
        while (i < tiles.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(tiles[i], tileParams(left = 0, right = 6))
            if (i + 1 < tiles.size) {
                row.addView(tiles[i + 1], tileParams(left = 6, right = 0))
            } else {
                row.addView(View(this), tileParams(left = 6, right = 0))
            }
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            i += 2
        }

        content.addView(centerText("Данные: Open-Meteo · обновлено ${nowTime.substring(11, 16)}", 12f, TEXT_SECONDARY, topDp = 16))
    }

    private fun showMessage(text: String, withRetry: Boolean) {
        content.removeAllViews()
        content.addView(centerText(text, 16f, TEXT_PRIMARY, topDp = 48))
        if (withRetry) {
            content.addView(Button(this).apply {
                this.text = "Повторить"
                setOnClickListener {
                    showMessage("Загружаем подробности…", withRetry = false)
                    load()
                }
            }, LinearLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(16)
            })
        }
    }

    private fun applyBackground(isDay: Boolean) {
        val colors = if (isDay) intArrayOf(0xFF2F7FD8.toInt(), 0xFF8EC5F4.toInt())
        else intArrayOf(0xFF1A1F5E.toInt(), 0xFF5360B8.toInt())
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
    }

    // ---------------------------------------------------------------- строительные блоки

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun centerText(text: String, size: Float, color: Int, topDp: Int = 0, bottomDp: Int = 0) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(0, dp(topDp), 0, dp(bottomDp))
        }

    private fun roundedBackground(color: Int, radiusDp: Int = 24) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun card(title: String, body: View): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(CARD_COLOR)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(TEXT_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), 0, 0, dp(10))
        })
        box.addView(body)
        box.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        return box
    }

    private fun hScroll(row: LinearLayout) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(row)
    }

    private fun forecastColumn(
        top: String, sub: String?, emoji: String, value: String, value2: String?,
        prob: Int, widthDp: Int, highlight: Boolean
    ): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(2), dp(8), dp(2), dp(8))
            if (highlight) background = roundedBackground(0x22FFFFFF, 14)
        }
        fun add(text: String, size: Float, color: Int, bold: Boolean = false, topPad: Int = 0) {
            col.addView(TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(topPad), 0, 0)
            })
        }
        add(top, 14f, TEXT_PRIMARY, bold = true)
        if (sub != null) add(sub, 13f, TEXT_SECONDARY)
        add(emoji, 26f, TEXT_PRIMARY, topPad = 8)
        add(value, 15f, TEXT_PRIMARY, bold = true, topPad = 8)
        if (value2 != null) add(value2, 15f, TEXT_SECONDARY, topPad = 2)
        add(if (prob > 0) "$prob %" else " ", 12f, ACCENT_BLUE, bold = true, topPad = 8)
        col.layoutParams = LinearLayout.LayoutParams(dp(widthDp), -2)
        return col
    }

    private fun tileParams(left: Int, right: Int) =
        LinearLayout.LayoutParams(0, -1, 1f).apply {
            leftMargin = dp(left)
            rightMargin = dp(right)
        }

    private fun tile(title: String, value: String, sub: String, valueColor: Int = TEXT_PRIMARY): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(CARD_COLOR)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            minimumHeight = dp(132)
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(TEXT_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
        })
        box.addView(TextView(this).apply {
            text = value
            textSize = 34f
            setTextColor(valueColor)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, dp(10), 0, dp(4))
        })
        box.addView(TextView(this).apply {
            text = sub
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
        })
        return box
    }

    private fun sunTile(sunrise: String, sunset: String, progress: Float): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(CARD_COLOR)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            minimumHeight = dp(132)
        }
        box.addView(TextView(this).apply {
            text = "☀️  Солнце"
            textSize = 14f
            setTextColor(TEXT_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
        })
        box.addView(SunArcView(this, progress), LinearLayout.LayoutParams(-1, dp(64)).apply {
            topMargin = dp(8)
        })
        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        times.addView(TextView(this).apply {
            text = sunrise
            textSize = 14f
            setTextColor(TEXT_PRIMARY)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        times.addView(TextView(this).apply {
            text = sunset
            textSize = 14f
            setTextColor(TEXT_PRIMARY)
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(times)
        return box
    }

    /** Дуга движения солнца: 0 — восход (слева), 1 — закат (справа). Вне диапазона — точка серая. */
    private class SunArcView(context: Context, private val progress: Float) : View(context) {
        private val d = resources.displayMetrics.density
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * d
            strokeCap = Paint.Cap.ROUND
            color = 0xFFC9A24A.toInt()
        }
        private val horizon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * d
            color = 0x66FFFFFF
            pathEffect = DashPathEffect(floatArrayOf(6f * d, 6f * d), 0f)
        }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val pad = 10f * d
            val r = min((w - 2 * pad) / 2f, h - 2 * pad)
            val cx = w / 2f
            val cy = h - pad
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f, false, arc)
            canvas.drawLine(pad, cy, w - pad, cy, horizon)
            val clamped = max(0f, min(1f, progress))
            val angle = PI * (1.0 - clamped)
            val x = cx + r * cos(angle).toFloat()
            val y = cy - r * sin(angle).toFloat()
            dot.color = if (progress in 0f..1f) 0xFFFFC83D.toInt() else 0xFF8A8C99.toInt()
            canvas.drawCircle(x, y, 7f * d, dot)
        }
    }

    // ---------------------------------------------------------------- вспомогательные функции

    private fun fmt1(v: Double) = "%.1f".format(RU, v)

    private fun fmtMm(v: Double) = if (v <= 0.0) "0" else if (v >= 10) v.roundToInt().toString() else fmt1(v)

    private fun minutesOfDay(hhmm: String) = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()

    /** День недели (1 = воскресенье … 7 = суббота) для даты «yyyy-MM-dd» без java.time (minSdk 23). */
    private fun dayOfWeek(date: String): Int {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(date.substring(0, 4).toInt(), date.substring(5, 7).toInt() - 1, date.substring(8, 10).toInt())
        return cal.get(java.util.Calendar.DAY_OF_WEEK)
    }

    private fun compass(deg: Double): String {
        val names = arrayOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
        return names[(((deg % 360 + 360) % 360) / 45.0).roundToInt() % 8]
    }

    private fun weatherEmoji(code: Int, day: Boolean): String = when (code) {
        0 -> if (day) "☀️" else "🌙"
        1 -> if (day) "🌤️" else "🌙"
        2 -> if (day) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "❔"
    }

    private fun uvLabel(uv: Double) = when {
        uv < 3 -> "Низкий"
        uv < 6 -> "Умеренный"
        uv < 8 -> "Высокий"
        uv < 11 -> "Очень высокий"
        else -> "Экстремальный"
    }

    /** Европейский индекс качества воздуха (EAQI). */
    private fun aqiLabel(aqi: Int) = when {
        aqi <= 20 -> "Хорошо"
        aqi <= 40 -> "Приемлемо"
        aqi <= 60 -> "Умеренно"
        aqi <= 80 -> "Плохо"
        aqi <= 100 -> "Очень плохо"
        else -> "Крайне плохо"
    }

    private fun aqiColor(aqi: Int) = when {
        aqi <= 20 -> 0xFF6FD08C.toInt()
        aqi <= 40 -> 0xFFB5D96B.toInt()
        aqi <= 60 -> 0xFFFFD54F.toInt()
        aqi <= 80 -> 0xFFFF8A3D.toInt()
        aqi <= 100 -> 0xFFFF5252.toInt()
        else -> 0xFFB04BC4.toInt()
    }

    private fun visLabel(km: Double) = when {
        km >= 20 -> "Идеально чисто"
        km >= 10 -> "Хорошая"
        km >= 4 -> "Умеренная"
        km >= 1 -> "Плохая"
        else -> "Очень плохая"
    }

    private fun cloudLabel(p: Int) = when {
        p < 10 -> "Ясно"
        p < 40 -> "Малооблачно"
        p < 75 -> "Облачно с прояснениями"
        else -> "Пасмурно"
    }

    private class MoonInfo(val emoji: String, val name: String, val illumination: Double)

    /** Фаза луны и доля освещённости 0..1. Синодический месяц, эпоха — новолуние 2000-01-06 18:14 UTC. */
    private fun moonPhase(nowMillis: Long): MoonInfo {
        val synodic = 29.530588853
        val epochMillis = 947182440000L
        val days = (nowMillis - epochMillis) / 86_400_000.0
        val age = ((days % synodic) + synodic) % synodic
        val (emoji, name) = when {
            age < 1.85 -> "🌑" to "Новолуние"
            age < 5.54 -> "🌒" to "Растущий серп"
            age < 9.23 -> "🌓" to "Первая четверть"
            age < 12.92 -> "🌔" to "Растущая луна"
            age < 16.61 -> "🌕" to "Полнолуние"
            age < 20.30 -> "🌖" to "Убывающая луна"
            age < 23.99 -> "🌗" to "Последняя четверть"
            age < 27.68 -> "🌘" to "Убывающий серп"
            else -> "🌑" to "Новолуние"
        }
        val illumination = (1 - cos(2 * PI * age / synodic)) / 2
        return MoonInfo(emoji, name, illumination)
    }
}
