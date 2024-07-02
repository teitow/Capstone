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
            tts.language = Locale.KOREAN
            speakText("날씨 정보를 불러오는 중입니다.") {
                // Call next speakText in the queue if needed
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
                val rainProbabilityMessage = getRainProbability(hourly, daily)

                runOnUiThread {
                    temperatureTextView.text = "현재 기온: ${temp}°C"
                    temperatureTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                    feelsLikeTextView.text = "체감 온도: ${feelsLike}°C"
                    feelsLikeTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                    temperatureChangeTextView.text = tempChanges
                    temperatureChangeTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                    rainProbabilityTextView.text = rainProbabilityMessage
                    rainProbabilityTextView.setTypeface(null, android.graphics.Typeface.BOLD)

                    val fullText =
                        "${locationTextView.text}, ${temperatureTextView.text}, ${feelsLikeTextView.text}, ${temperatureChangeTextView.text}, ${rainProbabilityTextView.text}"
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
        for (i in 0 until 4) {
            val hourData = hourly.getJSONObject(i)
            val hourTemp = hourData.getDouble("temp")
            val hour = SimpleDateFormat(
                "HH시",
                Locale.getDefault()
            ).format(Date(hourData.getLong("dt") * 1000))
            if (Math.abs(hourTemp - currentTemp) >= 4) {
                tempChanges.append("급격한 기온차: $hourTemp°C at $hour\n")
            }
        }
        return if (tempChanges.isEmpty()) "4시간 내 급격한 기온차 없음" else tempChanges.toString()
    }

    private fun getRainProbability(hourly: JSONArray, daily: JSONArray): String {
        val now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val endOfDay = 24 - now

        val rainProbabilityMessage = StringBuilder()
        val addedTimes = mutableSetOf<String>()
        var rainStartHour: String? = null
        var isRaining = false

        for (i in 0 until endOfDay) {
            val hourData = hourly.getJSONObject(i)
            val pop = hourData.getDouble("pop")
            if (pop > 0) {
                val hour = SimpleDateFormat(
                    "HH시",
                    Locale.getDefault()
                ).format(Date(hourData.getLong("dt") * 1000))
                if (!addedTimes.contains(hour)) {
                    addedTimes.add(hour)
                    if (rainStartHour == null) {
                        rainStartHour = hour
                    }
                    rainProbabilityMessage.append("강수 확률: ${(pop * 100).toInt()}% at $hour\n")
                    if (hourData.getJSONArray("weather").getJSONObject(0).getString("main") == "Rain") {
                        isRaining = true
                    }
                }
            }
        }

        val todayRain = daily.getJSONObject(0).getDouble("pop")
        if (todayRain > 0) {
            val rainStartHourDaily = SimpleDateFormat("HH시", Locale.getDefault()).format(
                Date(
                    daily.getJSONObject(0).getLong("dt") * 1000
                )
            )
            if (rainStartHour == null) {
                rainStartHour = rainStartHourDaily
            }
            rainProbabilityMessage.append("오늘 비올 확률: ${(todayRain * 100).toInt()}% from $rainStartHour\n")
        }

        if (isRaining) {
            rainProbabilityMessage.append("현재 비가 오고 있습니다.")
        }

        return if (rainProbabilityMessage.isEmpty()) "오늘 밤까지 비올 확률 없음" else rainProbabilityMessage.toString()
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
        tts.stop() // MainActivity로 전환 시 TTS 멈추기
    }

    override fun onDestroy() {
        if (tts != null) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
