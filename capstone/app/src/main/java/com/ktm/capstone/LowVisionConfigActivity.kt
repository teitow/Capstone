package com.ktm.capstone

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
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

class LowVisionConfigActivity : AppCompatActivity(), TextToSpeech.OnInitListener, GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    private lateinit var tts: TextToSpeech
    private lateinit var gestureDetector: GestureDetector
    private lateinit var optionsRecyclerView: RecyclerView
    private lateinit var adapter: OptionsAdapter
    private lateinit var prefs: SharedPreferences
    private var options = listOf(
        "TTS 속도",
        "다크 모드",
        "배터리 세이브",
        "객체 모드",
        "색상 모드",
        "날씨 모드",
        "바코드 모드",
        "저시력 지원 모드"
    )
    private var selectedPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_low_vision_config)

        supportActionBar?.title = "앱 환경 설정"

        prefs = getSharedPreferences("TTSConfig", MODE_PRIVATE)
        val savedPitch = prefs.getFloat("pitch", 1.0f)
        val savedSpeed = prefs.getFloat("speed", 1.0f)

        tts = TextToSpeech(this, this).apply {
            setPitch(savedPitch)
            setSpeechRate(savedSpeed)
        }

        val themePref = getSharedPreferences("ThemePref", Context.MODE_PRIVATE)
        val isDarkMode = themePref.getBoolean("DARK_MODE", false)
        val titleTextView = findViewById<TextView>(R.id.titleTextView)
        titleTextView.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)

        optionsRecyclerView = findViewById(R.id.optionsRecyclerView)
        optionsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = OptionsAdapter(options, isDarkMode)
        optionsRecyclerView.adapter = adapter

        val layout = findViewById<LinearLayout>(R.id.activity_low_vision_config_layout)
        layout.setBackgroundColor(if (isDarkMode) Color.BLACK else Color.WHITE)

        gestureDetector = GestureDetector(this, this)

        optionsRecyclerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        // RecyclerView의 기본 터치 이벤트를 막기 위해 onInterceptTouchEvent 오버라이드
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
    }

    private fun updateSelection() {
        adapter.setSelectedPosition(selectedPosition)
        speakOption(options[selectedPosition])
    }

    private fun executeOption(position: Int) {
        stopTTS()
        val selectedOption = options[position]
        when (selectedOption) {
            "TTS 속도" -> startActivity(Intent(this, VoiceSettingsActivity::class.java))
            "다크 모드" -> startActivity(Intent(this, DarkModeActivity::class.java))
            "배터리 세이브" -> startActivity(Intent(this, BatterySaveActivity::class.java))
            "객체 모드" -> startActivity(Intent(this, ObjectModeActivity::class.java))
            "색상 모드" -> startActivity(Intent(this, ColorModeActivity::class.java))
            "날씨 모드" -> startActivity(Intent(this, WeatherModeActivity::class.java))
            "바코드 모드" -> startActivity(Intent(this, BarcodeModeActivity::class.java))
            "저시력 지원 모드" -> startActivity(Intent(this, LowVisionModeActivity::class.java))
        }
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

    private fun navigateToMainActivity() {
        stopTTS()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREAN
            tts.speak(
                "설정을 선택하려면 탭, 항목을 변경하려면 더블탭하세요.",
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

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        executeOption(selectedPosition)
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        selectedPosition = (selectedPosition + 1) % options.size
        updateSelection()
        return true
    }

    override fun onFling(
        p0: MotionEvent?,
        e1: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (abs(velocityX) > abs(velocityY)) {
            if (velocityX > 0) {
                selectedPosition = (selectedPosition - 1 + options.size) % options.size
            } else {
                selectedPosition = (selectedPosition + 1) % options.size
            }
            updateSelection()
        } else {
            navigateToMainActivity()
        }
        return true
    }

    override fun onDown(e: MotionEvent): Boolean = true
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean = false
    override fun onScroll(
        p0: MotionEvent?,
        e1: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean = false
    override fun onLongPress(e: MotionEvent) {}
    override fun onDoubleTapEvent(e: MotionEvent): Boolean = false
}
