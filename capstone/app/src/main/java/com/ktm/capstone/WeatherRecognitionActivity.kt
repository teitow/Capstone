package com.ktm.capstone

import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class WeatherRecognitionActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val client = OkHttpClient()
    private lateinit var temperatureTextView: TextView
    private lateinit var feelsLikeTextView: TextView
    private lateinit var rainProbabilityTextView: TextView
    private lateinit var windSpeedTextView: TextView
    private lateinit var uvIndexTextView: TextView
    private lateinit var tomorrowWeatherTextView: TextView
    private lateinit var locationTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tts: TextToSpeech
    private lateinit var gestureDetector: GestureDetector
    private var isDetailedMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_recognition)
        supportActionBar?.title = "날씨 확인"

        temperatureTextView = findViewById(R.id.temperatureTextView)
        feelsLikeTextView = findViewById(R.id.feelsLikeTextView)
        rainProbabilityTextView = findViewById(R.id.rainProbabilityTextView)
        windSpeedTextView = findViewById(R.id.windSpeedTextView)
        uvIndexTextView = findViewById(R.id.uvIndexTextView)
        tomorrowWeatherTextView = findViewById(R.id.tomorrowWeatherTextView)
        locationTextView = findViewById(R.id.locationTextView)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        tts = TextToSpeech(this, this)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 != null && e2 != null) {
                    val deltaY = e2.y - e1.y
                    if (Math.abs(deltaY) > 100 && Math.abs(velocityY) > 100) {
                        val intent = Intent(this@WeatherRecognitionActivity, MainActivity::class.java)
                        startActivity(intent)
                        return true
                    }
                }
                return false
            }
        })

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val locationName = getLocationName(it.latitude, it.longitude)
                locationTextView.text = "현재 지역: $locationName"
                locationTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                fetchWeatherData(it.latitude, it.longitude)
            } ?: run {
                Toast.makeText(this, "위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // Load mode from SharedPreferences
        val sharedPref = getSharedPreferences("WeatherModePref", Context.MODE_PRIVATE)
        val mode = sharedPref.getString("MODE", "BASIC")
        isDetailedMode = mode == "DETAILED"
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val prefs = getSharedPreferences("TTSConfig", MODE_PRIVATE)
            val savedPitch = prefs.getFloat("pitch", 1.0f)
            val savedSpeed = prefs.getFloat("speed", 1.0f)

            tts?.let {
                it.language = Locale.KOREAN
                it.setPitch(savedPitch)
                it.setSpeechRate(savedSpeed)
            }

            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Korean language is not supported.")
            } else {
                speakText("날씨 정보를 불러오는 중입니다.") {
                    // Call next speakText in the queue if needed
                }
            }
        } else {
            Toast.makeText(this, "TTS 초기화 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLocationName(lat: Double, lon: Double): String {
        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        return if (addresses?.isNotEmpty() == true) {
            val address = addresses[0]
            "${address.adminArea} ${address.locality} ${address.subLocality}"
        } else {
            "알 수 없는 위치"
        }
    }

    private fun fetchWeatherData(lat: Double, lon: Double) {
        val apiKey = BuildConfig.WEATHER_API_KEY
        val url = "https://api.openweathermap.org/data/3.0/onecall?lat=$lat&lon=$lon&exclude=minutely&units=metric&appid=$apiKey"

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@WeatherRecognitionActivity, "API 호출 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@WeatherRecognitionActivity, "API 오류: ${responseBody}", Toast.LENGTH_LONG).show()
                    }
                    return
                }
                responseBody?.let {
                    Log.d("WeatherData", it)
                    try {
                        parseWeatherData(it)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            Toast.makeText(this@WeatherRecognitionActivity, "데이터 파싱 오류: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }

    private fun parseWeatherData(response: String) {
        try {
            val jsonObject = JSONObject(response)
            if (jsonObject.has("current")) {
                val current = jsonObject.getJSONObject("current")
                val temp = current.getDouble("temp")
                val weatherArray = current.getJSONArray("weather")
                val weather = weatherArray.getJSONObject(0)
                val description = translateWeatherDescription(weather.getString("description"))
                val uvi = current.getDouble("uvi")

                val hourly = jsonObject.getJSONArray("hourly")
                val daily = jsonObject.getJSONArray("daily")

                val tempChanges = getTemperatureChanges(hourly, temp)
                val rainProbabilityMessage = getRainProbability(hourly, daily, current)
                val windSpeedMessage = getWindSpeed(hourly)
                val (maxUVI, uviCategory) = getMaxUVI(hourly)

                // 오늘 날씨 데이터 추출
                val todayTemp = daily.getJSONObject(0).getJSONObject("temp")
                val todayDayTemp = todayTemp.getDouble("day")
                val todayNightTemp = todayTemp.getDouble("night")
                val todayWeatherDescription = translateWeatherDescription(daily.getJSONObject(0).getJSONArray("weather").getJSONObject(0).getString("description"))
                val todayWeatherText = "오늘 낮 기온은 ${todayDayTemp.toInt()}°C이고 밤 기온은 ${todayNightTemp.toInt()}°C입니다. 오늘 날씨는 $todayWeatherDescription 입니다."

                // 내일 날씨 데이터 추출
                val tomorrowWeather = daily.getJSONObject(1)
                val tomorrowTemp = tomorrowWeather.getJSONObject("temp")
                val tomorrowDayTemp = tomorrowTemp.getDouble("day")
                val tomorrowNightTemp = tomorrowTemp.getDouble("night")
                val tomorrowWeatherDescription = translateWeatherDescription(tomorrowWeather.getJSONArray("weather").getJSONObject(0).getString("description"))
                val tomorrowWeatherText = "내일 낮 기온은 ${tomorrowDayTemp.toInt()}°C이고 밤 기온은 ${tomorrowNightTemp.toInt()}°C입니다. 내일 날씨는 $tomorrowWeatherDescription 입니다."

                runOnUiThread {
                    temperatureTextView.text = "현재 기온: ${temp.toInt()}°C"
                    temperatureTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                    feelsLikeTextView.text = todayWeatherText
                    feelsLikeTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                    rainProbabilityTextView.text = rainProbabilityMessage
                    rainProbabilityTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                    windSpeedTextView.text = windSpeedMessage
                    windSpeedTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                    uvIndexTextView.text = "최대 자외선 지수는 ${String.format("%.1f", maxUVI)}이고 자외선 단계는 $uviCategory 입니다."
                    uvIndexTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                    tomorrowWeatherTextView.text = tomorrowWeatherText
                    tomorrowWeatherTextView.setTypeface(null, android.graphics.Typeface.BOLD)

                    // Adjust visibility based on mode
                    if (isDetailedMode) {
                        feelsLikeTextView.visibility = TextView.VISIBLE
                        windSpeedTextView.visibility = TextView.VISIBLE
                        uvIndexTextView.visibility = TextView.VISIBLE
                        tomorrowWeatherTextView.visibility = TextView.VISIBLE
                    } else {
                        feelsLikeTextView.visibility = TextView.GONE
                        windSpeedTextView.visibility = TextView.GONE
                        uvIndexTextView.visibility = TextView.GONE
                        tomorrowWeatherTextView.visibility = TextView.GONE
                    }

                    val fullText = if (isDetailedMode) {
                        "${locationTextView.text}, ${temperatureTextView.text}, $todayWeatherText, ${rainProbabilityTextView.text}, ${windSpeedTextView.text}, 최대 자외선 지수는 ${String.format("%.1f", maxUVI)}이고 자외선 단계는 $uviCategory 입니다. $tomorrowWeatherText"
                    } else {
                        "${locationTextView.text}, ${temperatureTextView.text}, ${rainProbabilityTextView.text}"
                    }
                    speakText(fullText) {
                        speakText("메인 화면으로 돌아가고 싶다면 화면을 슬라이드 해주세요.")
                    }
                }
            } else {
                Log.e("WeatherRecognition", "No value for 'current' in API response")
            }
        } catch (e: Exception) {
            Log.e("WeatherRecognition", "Error parsing weather data", e)
        }
    }



    private fun translateWeatherDescription(description: String): String {
        return when (description) {
            "clear sky" -> "맑음"
            "few clouds" -> "구름 조금"
            "scattered clouds" -> "드문드문 구름"
            "broken clouds" -> "구름 많음"
            "shower rain" -> "소나기"
            "rain" -> "비"
            "thunderstorm with light rain" -> "약한 비를 동반한 천둥번개"
            "thunderstorm with rain" -> "비를 동반한 천둥번개"
            "thunderstorm with heavy rain" -> "강한 비를 동반한 천둥번개"
            "light thunderstorm" -> "약한 천둥번개"
            "thunderstorm" -> "천둥번개"
            "heavy thunderstorm" -> "강한 천둥번개"
            "ragged thunderstorm" -> "심한 천둥번개"
            "thunderstorm with light drizzle" -> "약한 이슬비를 동반한 천둥번개"
            "thunderstorm with drizzle" -> "이슬비를 동반한 천둥번개"
            "thunderstorm with heavy drizzle" -> "강한 이슬비를 동반한 천둥번개"
            "light intensity drizzle" -> "약한 이슬비"
            "drizzle" -> "이슬비"
            "heavy intensity drizzle" -> "강한 이슬비"
            "light intensity drizzle rain" -> "약한 이슬비와 비"
            "drizzle rain" -> "이슬비와 비"
            "heavy intensity drizzle rain" -> "강한 이슬비와 비"
            "shower rain and drizzle" -> "소나기와 이슬비"
            "heavy shower rain and drizzle" -> "강한 소나기와 이슬비"
            "shower drizzle" -> "소나기와 이슬비"
            "light rain" -> "가벼운 비"
            "moderate rain" -> "보통 비"
            "heavy intensity rain" -> "강한 비"
            "very heavy rain" -> "매우 강한 비"
            "extreme rain" -> "극심한 비"
            "freezing rain" -> "얼어붙는 비"
            "light intensity shower rain" -> "가벼운 소나기"
            "shower rain" -> "소나기"
            "heavy intensity shower rain" -> "강한 소나기"
            "ragged shower rain" -> "거센 소나기"
            "light snow" -> "가벼운 눈"
            "snow" -> "눈"
            "heavy snow" -> "많은 눈"
            "sleet" -> "진눈깨비"
            "light shower sleet" -> "가벼운 소나기 진눈깨비"
            "shower sleet" -> "소나기 진눈깨비"
            "light rain and snow" -> "가벼운 비와 눈"
            "rain and snow" -> "비와 눈"
            "light shower snow" -> "가벼운 소나기 눈"
            "shower snow" -> "소나기 눈"
            "heavy shower snow" -> "강한 소나기 눈"
            "mist" -> "엷은 안개"
            "smoke" -> "연기"
            "haze" -> "실안개"
            "sand/dust whirls" -> "모래/먼지 소용돌이"
            "fog" -> "짙은 안개"
            "sand" -> "모래"
            "dust" -> "먼지"
            "volcanic ash" -> "화산재"
            "squalls" -> "돌풍"
            "tornado" -> "토네이도"
            "clear sky" -> "맑은 하늘"
            "few clouds: 11-25%" -> "구름 조금: 11-25%"
            "scattered clouds: 25-50%" -> "드문드문 구름: 25-50%"
            "broken clouds: 51-84%" -> "구름 많음: 51-84%"
            "overcast clouds: 85-100%" -> "흐린 하늘: 85-100%"
            else -> description
        }
    }

    private fun getTemperatureChanges(hourly: JSONArray, currentTemp: Double): String {
        val tempChanges = StringBuilder()
        for (i in 1 until 5) {
            val hourData = hourly.getJSONObject(i)
            val hourTemp = hourData.getDouble("temp")
            val hour = SimpleDateFormat("HH시", Locale.getDefault()).format(Date(hourData.getLong("dt") * 1000))
            if (Math.abs(hourTemp - currentTemp) >= 4) {
                tempChanges.append("오늘 ${hour}에 ${hourTemp}°C 의 급격한 기온차가 있을 예정입니다.\n")
            }
        }
        return if (tempChanges.isEmpty()) "4시간 내 급격한 기온차 없음" else tempChanges.toString()
    }

    private fun getRainProbability(hourly: JSONArray, daily: JSONArray, current: JSONObject): String {
        val currentWeather = current.getJSONArray("weather").getJSONObject(0)
        val isRainingOrSnowing = currentWeather.getString("main") in listOf("Rain", "Snow")

        if (isRainingOrSnowing) {
            val endTime = getEndTimeOfRainOrSnow(hourly)
            return if (endTime == "24:00") {
                "현재 눈/비가 오는 중이고 오늘 하루 내내 올 예정입니다.\n"
            } else {
                "현재 눈/비가 오는 중이고 ${endTime}까지 올 예정입니다.\n"
            }
        }

        val nearestHighProbabilityTime = getNearestHighProbabilityTime(hourly)
        val highestProbabilityTime = getHighestProbabilityTime(hourly)
        val dailyRainVolume = daily.getJSONObject(0).optDouble("rain", 0.0)

        return if (dailyRainVolume < 1) {
            "오늘 자정까지 눈/비가 올 확률이 없습니다.\n"
        } else if (nearestHighProbabilityTime.isNotEmpty() || highestProbabilityTime.isNotEmpty()) {
            "강수 확률이 50%가 넘는 가장 가까운 시간대는 ${nearestHighProbabilityTime["time"]}이고 ${nearestHighProbabilityTime["probability"]}% 확률로 올 예정입니다.\n" +
                    "오늘 하루 강수 확률이 가장 높은 시간대는 ${highestProbabilityTime["time"]}이고 ${highestProbabilityTime["probability"]}% 확률로 올 예정입니다.\n" +
                    "오늘 예상 강우량은 ${dailyRainVolume}mm입니다.\n"
        } else {
            "오늘 밤까지 눈/비가 올 확률이 없습니다.\n"
        }
    }

    private fun getNearestHighProbabilityTime(hourly: JSONArray): Map<String, String> {
        for (i in 0 until hourly.length()) {
            val hourData = hourly.getJSONObject(i)
            val pop = hourData.getDouble("pop")
            if (pop >= 0.5) {
                val time = SimpleDateFormat("HH시", Locale.getDefault()).format(Date(hourData.getLong("dt") * 1000))
                return mapOf("time" to time, "probability" to (pop * 100).toInt().toString())
            }
        }
        return emptyMap()
    }

    private fun getHighestProbabilityTime(hourly: JSONArray): Map<String, String> {
        var highestPop = 0.0
        var highestTime = ""
        for (i in 0 until hourly.length()) {
            val hourData = hourly.getJSONObject(i)
            val pop = hourData.getDouble("pop")
            if (pop > highestPop) {
                highestPop = pop
                highestTime = SimpleDateFormat("HH시", Locale.getDefault()).format(Date(hourData.getLong("dt") * 1000))
            }
        }
        return if (highestTime.isNotEmpty()) {
            mapOf("time" to highestTime, "probability" to (highestPop * 100).toInt().toString())
        } else {
            emptyMap()
        }
    }

    private fun getEndTimeOfRainOrSnow(hourly: JSONArray): String {
        for (i in 0 until hourly.length()) {
            val hourData = hourly.getJSONObject(i)
            val weather = hourData.getJSONArray("weather").getJSONObject(0)
            val main = weather.getString("main")
            if (main !in listOf("Rain", "Snow")) {
                return SimpleDateFormat("HH시", Locale.getDefault()).format(Date(hourData.getLong("dt") * 1000))
            }
        }
        return "24:00"
    }

    private fun getWindSpeed(hourly: JSONArray): String {
        var maxSpeed = 0.0
        var maxSpeedTime = ""

        for (i in 0 until hourly.length()) {
            val hourData = hourly.getJSONObject(i)
            val speed = hourData.getDouble("wind_speed")
            if (speed > maxSpeed) {
                maxSpeed = speed
                maxSpeedTime = SimpleDateFormat("HH시", Locale.getDefault()).format(Date(hourData.getLong("dt") * 1000))
            }
        }
        val maxSpeedMessage = "오늘 최대 풍속은 ${String.format("%.1f", maxSpeed)}m/s입니다."
        val highWindWarning = if (maxSpeed > 10) "오늘 강풍이 불 예정이니 주의해주세요." else ""
        return "$maxSpeedMessage $highWindWarning"
    }

    private fun getMaxUVI(hourly: JSONArray): Pair<Double, String> {
        var maxUVI = 0.0
        for (i in 0 until hourly.length()) {
            val hourData = hourly.getJSONObject(i)
            val uvi = hourData.getDouble("uvi")
            if (uvi > maxUVI) {
                maxUVI = uvi
            }
        }
        val uviCategory = when {
            maxUVI >= 11 -> "위험"
            maxUVI >= 8 -> "매우 높음"
            maxUVI >= 6 -> "높음"
            maxUVI >= 3 -> "보통"
            else -> "낮음"
        }
        return Pair(maxUVI, uviCategory)
    }

    private fun getTomorrowWeather(daily: JSONArray): String {
        if (daily.length() > 1) {
            val tomorrow = daily.getJSONObject(1)
            val temp = tomorrow.getJSONObject("temp")
            val dayTemp = temp.getDouble("day")
            val nightTemp = temp.getDouble("night")
            val weather = tomorrow.getJSONArray("weather").getJSONObject(0)
            val description = translateWeatherDescription(weather.getString("description"))

            return "내일 낮 기온은 ${dayTemp.toInt()}°C이고 밤 기온은 ${nightTemp.toInt()}°C입니다. 내일 날씨는 ${description}입니다."
        }
        return "정보를 불러올 수 없습니다."
    }

    private fun speakText(text: String, onDone: (() -> Unit)? = null) {
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) {
                onDone?.invoke()
            }

            override fun onError(utteranceId: String) {}
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UniqueID")
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    override fun onPause() {
        super.onPause()
        tts.stop() // 다른 Activity로 전환 시 TTS 멈추기
    }

    override fun onStop() {
        super.onStop()
        tts.stop() // 다른 Activity로 전환 시 TTS 멈추기
    }

    override fun onDestroy() {
        if (tts != null) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
