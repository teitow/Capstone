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
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class WeatherRecognitionActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val client = OkHttpClient()
    private lateinit var temperatureTextView: TextView
    private lateinit var temperatureChangeTextView: TextView
    private lateinit var rainProbabilityTextView: TextView
    private lateinit var locationTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationData: Map<String, List<Pair<String, String>>>
    private lateinit var tts: TextToSpeech
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_recognition)
        supportActionBar?.title = "날씨 확인"

        temperatureTextView = findViewById(R.id.temperatureTextView)
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
                        val intent = Intent(this@WeatherRecognitionActivity, MainActivity::class.java)
                        startActivity(intent)
                        return true
                    }
                }
                return false
            }
        })

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val locationName = getLocationName(it.latitude, it.longitude)
                locationTextView.text = "현재 지역: $locationName"
                locationTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                val coordinates = getGridCoordinates(locationName)
                fetchWeatherData(coordinates.first, coordinates.second)
            } ?: run {
                Toast.makeText(this, "위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        loadLocationData()
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

    private fun loadLocationData() {
        // 로컬에서 locationData를 로드 (예: SharedPreferences, 파일 등)
        locationData = mapOf(
            "서울특별시 종로구 청운효자동" to listOf(Pair("60", "127")),
            "서울특별시 종로구 사직동" to listOf(Pair("60", "127")),
            // 다른 데이터를 여기에 추가
        )
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

    private fun getGridCoordinates(locationName: String): Pair<String, String> {
        val coordinates = locationData[locationName]
        return if (coordinates != null && coordinates.isNotEmpty()) {
            coordinates[0]
        } else {
            Pair("60", "127")  // 기본 좌표
        }
    }

    private fun fetchWeatherData(nx: String, ny: String) {
        val serviceKey = BuildConfig.WEATHER_API_KEY
        val sdfDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val baseDate = sdfDate.format(Date())

        val baseTime = getBaseTime()

        val urlBuilder = StringBuilder("http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtFcst")
        urlBuilder.append("?serviceKey=$serviceKey")
        urlBuilder.append("&pageNo=1")
        urlBuilder.append("&numOfRows=1000")
        urlBuilder.append("&dataType=JSON")  // JSON 형식으로 요청
        urlBuilder.append("&base_date=$baseDate")
        urlBuilder.append("&base_time=$baseTime")
        urlBuilder.append("&nx=$nx")
        urlBuilder.append("&ny=$ny")

        val request = Request.Builder().url(urlBuilder.toString()).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let {
                    try {
                        parseWeatherData(it)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    private fun getBaseTime(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val roundedHour = if (minute < 30) hour - 1 else hour
        return String.format("%02d00", roundedHour)
    }

    private fun parseWeatherData(response: String) {
        try {
            val jsonObject = JSONObject(response)
            val itemArray = jsonObject.getJSONObject("response")
                .getJSONObject("body")
                .getJSONObject("items")
                .getJSONArray("item")

            var currentTemp = 0.0
            var rainProbability = 0
            var rainTime = ""
            val tempChanges = mutableListOf<Pair<String, Double>>()
            val rainProbabilities = mutableListOf<Pair<String, Int>>()

            for (i in 0 until itemArray.length()) {
                val item = itemArray.getJSONObject(i)
                val category = item.getString("category")
                val fcstValueString = item.getString("fcstValue")
                val fcstTime = item.getString("fcstTime")
                val fcstValue = fcstValueString.toDoubleOrNull()

                when (category) {
                    "T1H" -> fcstValue?.let { currentTemp = it }
                    "PTY" -> {
                        fcstValue?.let {
                            if (it > 0) {
                                rainProbabilities.add(Pair(fcstTime, (it * 100).toInt()))
                            }
                        }
                    }
                }

                if (category == "T1H" && fcstValue != null) {
                    tempChanges.add(Pair(fcstTime, fcstValue))
                }
            }

            // 6시간 내 비 올 확률 계산
            rainProbabilities.sortBy { it.first }
            if (rainProbabilities.isNotEmpty()) {
                rainProbability = rainProbabilities.maxOf { it.second }
                rainTime = rainProbabilities.find { it.second == rainProbability }?.first ?: ""
            }

            val temperatureChangeMessage = getTemperatureChangeMessage(tempChanges, currentTemp)
            val rainProbabilityMessage = if (rainProbability > 0) {
                "비 올 확률: $rainProbability% at $rainTime"
            } else {
                "6시간 내 비 올 확률 없음"
            }

            runOnUiThread {
                temperatureTextView.text = "현재 기온: ${currentTemp}°C"
                temperatureTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                temperatureChangeTextView.text = temperatureChangeMessage
                temperatureChangeTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                rainProbabilityTextView.text = rainProbabilityMessage
                rainProbabilityTextView.setTypeface(null, android.graphics.Typeface.BOLD)

                val fullText = "${locationTextView.text}, ${temperatureTextView.text}, ${temperatureChangeTextView.text}, ${rainProbabilityTextView.text}"
                speakText(fullText) {
                    speakText("메인 화면으로 돌아가고 싶다면 화면을 슬라이드 해주세요.")
                }
            }
        } catch (e: Exception) {
            Log.e("WeatherRecognition", "Error parsing weather data", e)
        }
    }

    private fun getTemperatureChangeMessage(tempChanges: List<Pair<String, Double>>, currentTemp: Double): String {
        for ((time, temp) in tempChanges) {
            if (Math.abs(temp - currentTemp) >= 4) {
                return "급격한 기온차: $temp°C at $time"
            }
        }
        return "6시간 내 급격한 기온차 없음"
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
