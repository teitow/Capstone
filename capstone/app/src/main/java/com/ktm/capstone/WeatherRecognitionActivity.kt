package com.ktm.capstone

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
    private lateinit var temperatureChangeTextView: TextView
    private lateinit var rainProbabilityTextView: TextView
    private lateinit var locationTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tts: TextToSpeech
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_recognition)
        supportActionBar?.title = "날씨 확인"

        temperatureTextView = findViewById(R.id.temperatureTextView)
        feelsLikeTextView = findViewById(R.id.feelsLikeTextView)
        temperatureChangeTextView = findViewById(R.id.temperatureChangeTextView)
        rainProbabilityTextView = findViewById(R.id.rainProbabilityTextView)
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
                        val intent =
                            Intent(this@WeatherRecognitionActivity, MainActivity::class.java)
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
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // SharedPreferences에서 TTS 설정 불러오기
            val prefs = getSharedPreferences("TTSConfig", MODE_PRIVATE)
            val savedPitch = prefs.getFloat("pitch", 1.0f)
            val savedSpeed = prefs.getFloat("speed", 1.0f)

            tts?.let {
                it.language = Locale.KOREAN
                it.setPitch(savedPitch) // 저장된 피치 적용
                it.setSpeechRate(savedSpeed) // 저장된 속도 적용
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
        val url = "https://api.openweathermap.org/data/3.0/onecall?lat=$lat&lon=$lon&units=metric&appid=$apiKey"

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
                val feelsLike = current.getDouble("feels_like")
                val weatherArray = current.getJSONArray("weather")
                val weather = weatherArray.getJSONObject(0)
                val description = weather.getString("description")

                val hourly = jsonObject.getJSONArray("hourly")
                val daily = jsonObject.getJSONArray("daily")

                val tempChanges = getTemperatureChanges(hourly, temp)
                val rainProbabilityMessage = getRainProbability(hourly, daily, current)

                runOnUiThread {
                    temperatureTextView.text = "현재 기온: ${temp.toInt()}°C"
                    temperatureTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                    feelsLikeTextView.text = "체감 온도: ${feelsLike.toInt()}°C"
                    feelsLikeTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                    temperatureChangeTextView.text = tempChanges
                    temperatureChangeTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                    rainProbabilityTextView.text = rainProbabilityMessage
                    rainProbabilityTextView.setTypeface(null, android.graphics.Typeface.BOLD)

                    val fullText = "${locationTextView.text}, ${temperatureTextView.text}, ${feelsLikeTextView.text}, ${temperatureChangeTextView.text}, ${rainProbabilityTextView.text}"
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

    private fun getTemperatureChanges(hourly: JSONArray, currentTemp: Double): String {
        val tempChanges = StringBuilder()
        for (i in 1 until 5) {
            val hourData = hourly.getJSONObject(i)
            val hourTemp = hourData.getDouble("temp")
            val hour = SimpleDateFormat("HH시", Locale.getDefault()).format(Date(hourData.getLong("dt") * 1000))
            if (Math.abs(hourTemp - currentTemp) >= 4) {
                tempChanges.append("급격한 기온차: $hourTemp°C at $hour\n")
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

        return if (nearestHighProbabilityTime.isNotEmpty() || highestProbabilityTime.isNotEmpty()) {
            "강수 확률이 40%가 넘는 가장 가까운 시간대는 ${nearestHighProbabilityTime["time"]}이고 ${nearestHighProbabilityTime["probability"]}% 확률로 올 예정입니다.\n" +
                    "강수 확률이 가장 높은 시간대는 ${highestProbabilityTime["time"]}이고 ${highestProbabilityTime["probability"]}% 확률로 올 예정입니다.\n" +
                    "오늘 예상 강우량은 ${dailyRainVolume}mm입니다."
        } else {
            "오늘 밤까지 눈/비가 올 확률 없습니다."
        }
    }

    private fun getNearestHighProbabilityTime(hourly: JSONArray): Map<String, String> {
        for (i in 0 until hourly.length()) {
            val hourData = hourly.getJSONObject(i)
            val pop = hourData.getDouble("pop")
            if (pop >= 0.4) {
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
