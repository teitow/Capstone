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

class ConfigActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

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
        "바코드 모드"
    )
    private var selectedPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

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

        optionsRecyclerView = findViewById(R.id.optionsRecyclerView)
        optionsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = OptionsAdapter(options, isDarkMode)
        optionsRecyclerView.adapter = adapter

        val layout = findViewById<LinearLayout>(R.id.activity_config_layout)
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
                    // 상하 슬라이드 및 하상 슬라이드 처리
                    navigateToMainActivity()
                }
                return true
            }
        })

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
                "좌우 슬라이드로 기능 선택을 해주시고 더블탭으로 기능 실행을 해주세요, 원래 기능으로 돌아가고 싶으시다면 화면을 상하로 슬라이드해주세요.",
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
}
