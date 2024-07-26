package com.ktm.capstone

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import kotlin.math.abs
import android.widget.TextView

class BatterySaveActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var gestureDetector: GestureDetector
    private lateinit var optionsRecyclerView: RecyclerView
    private lateinit var adapter: OptionsAdapter
    private lateinit var currentModeTextView: TextView
    private var options = listOf(
        "기본 모드",
        "절전 모드"
    )
    private var selectedPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery_save)

        supportActionBar?.title = "배터리 세이브 모드"

        tts = TextToSpeech(this, this)

        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        val themePref = getSharedPreferences("ThemePref", Context.MODE_PRIVATE)
        val isDarkMode = themePref.getBoolean("DARK_MODE", false)
        val titleTextView = findViewById<TextView>(R.id.titleTextView)
        titleTextView.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)

        currentModeTextView = findViewById(R.id.currentModeTextView)
        currentModeTextView.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
        currentModeTextView.setTypeface(null, android.graphics.Typeface.BOLD)
        updateCurrentModeText()

        optionsRecyclerView = findViewById(R.id.optionsRecyclerView)
        optionsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = OptionsAdapter(options, isDarkMode)
        optionsRecyclerView.adapter = adapter

        val layout = findViewById<LinearLayout>(R.id.root_layout)
        layout.setBackgroundColor(if (isDarkMode) Color.BLACK else Color.WHITE)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                executeOption(selectedPosition)
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (abs(velocityX) > abs(velocityY)) {
                    if (velocityX > 0) {
                        selectedPosition = (selectedPosition + 1) % options.size
                    } else {
                        selectedPosition = (selectedPosition - 1 + options.size) % options.size
                    }
                    updateSelection()
                } else {
                    finish()
                }
                return true
            }
        })

        optionsRecyclerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        optionsRecyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                return true // 터치 이벤트를 차단하여 기본 클릭 동작을 막음
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                // 처리하지 않음
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                // 처리하지 않음
            }
        })

        // 원래 밝기 값 저장
        if (!isOriginalBrightnessSaved()) {
            saveOriginalBrightness(getScreenBrightness())
        }
    }

    private fun updateCurrentModeText() {
        val sharedPref = getSharedPreferences("BatterySaveModePref", Context.MODE_PRIVATE)
        val mode = sharedPref.getString("MODE", "기본 모드")
        currentModeTextView.text = "현재 모드 : $mode"
    }

    private fun updateSelection() {
        adapter.setSelectedPosition(selectedPosition)
        speakOption(options[selectedPosition])
    }

    private fun executeOption(position: Int) {
        stopTTS()
        val selectedOption = options[position]
        val mode = if (selectedOption == "기본 모드") "BASIC" else "POWER_SAVE"
        saveMode(mode)
        updateCurrentModeText()

        if (mode == "POWER_SAVE") {
            setScreenBrightness(0.1f) // 밝기를 최소화
        } else {
            setScreenBrightness(getSavedOriginalBrightness()) // 원래 밝기로 복원
        }

        finish()
    }

    private fun saveMode(mode: String) {
        val sharedPref = getSharedPreferences("BatterySaveModePref", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("MODE", mode)
            apply()
        }
    }

    private fun isOriginalBrightnessSaved(): Boolean {
        val sharedPref = getSharedPreferences("BrightnessPref", Context.MODE_PRIVATE)
        return sharedPref.contains("ORIGINAL_BRIGHTNESS")
    }

    private fun saveOriginalBrightness(brightness: Float) {
        val sharedPref = getSharedPreferences("BrightnessPref", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putFloat("ORIGINAL_BRIGHTNESS", brightness)
            apply()
        }
    }

    private fun getSavedOriginalBrightness(): Float {
        val sharedPref = getSharedPreferences("BrightnessPref", Context.MODE_PRIVATE)
        return sharedPref.getFloat("ORIGINAL_BRIGHTNESS", 1.0f)
    }

    private fun speakOption(option: String) {
        stopTTS()
        if (::tts.isInitialized) {
            tts.speak(option, TextToSpeech.QUEUE_FLUSH, null, "OptionSelected")
        }
    }

    private fun stopTTS() {
        if (::tts.isInitialized && tts.isSpeaking) {
            tts.stop()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val sharedPrefMode = getSharedPreferences("BatterySaveModePref", Context.MODE_PRIVATE)
            val mode = sharedPrefMode.getString("MODE", "기본 모드")
            val sharedPrefTTS = getSharedPreferences("TTSConfig", Context.MODE_PRIVATE)
            val pitch = sharedPrefTTS.getFloat("pitch", 1.0f)
            val speed = sharedPrefTTS.getFloat("speed", 1.0f)
            tts.language = Locale.KOREAN
            tts.setPitch(pitch)
            tts.setSpeechRate(speed)
            tts.speak(
                "현재 배터리 모드는 $mode 입니다. 좌우 슬라이드로 모드를 선택해주시고 더블탭으로 실행해주세요, 원래 화면으로 돌아가고 싶으시다면 화면을 상하로 슬라이드해주세요.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "InitialInstructions"
            )
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    private fun getScreenBrightness(): Float {
        return try {
            val brightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            brightness / 255.0f
        } catch (e: Settings.SettingNotFoundException) {
            1.0f // 기본 밝기
        }
    }

    private fun setScreenBrightness(brightness: Float) {
        if (Settings.System.canWrite(this)) {
            val cResolver = contentResolver
            val brightnessInt = (brightness * 255).toInt()
            Settings.System.putInt(
                cResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightnessInt
            )
            val layoutParams = window.attributes
            layoutParams.screenBrightness = brightness
            window.attributes = layoutParams
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }
}
