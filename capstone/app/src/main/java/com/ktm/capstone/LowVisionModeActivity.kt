package com.ktm.capstone

import android.content.Context
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
import android.content.Intent

class LowVisionModeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var gestureDetector: GestureDetector
    private lateinit var optionsRecyclerView: RecyclerView
    private lateinit var adapter: OptionsAdapter
    private lateinit var currentModeTextView: TextView
    private var options = listOf(
        "전맹 모드",
        "저시력 모드"
    )
    private var selectedPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_low_vision_mode)

        supportActionBar?.title = "모드 선택"

        tts = TextToSpeech(this, this)

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

        // 초기 선택 상태 업데이트
        selectedPosition = if (isLowVisionModeEnabled()) 1 else 0
        updateSelection()
    }

    private fun updateCurrentModeText() {
        val sharedPref = getSharedPreferences("LowVisionModePref", Context.MODE_PRIVATE)
        val isLowVisionMode = sharedPref.getBoolean("LOW_VISION_MODE", false)
        val mode = if (isLowVisionMode) "저시력 Low Vision" else "전맹 Blind"
        currentModeTextView.text = "현재 모드 : $mode"
    }

    private fun updateSelection() {
        adapter.setSelectedPosition(selectedPosition)
        speakOption(options[selectedPosition])
    }

    private fun executeOption(position: Int) {
        stopTTS()
        val isLowVisionMode = position == 1
        saveMode(isLowVisionMode)
        updateCurrentModeText()
        speakOption(".")

        // LowVisionMainActivity로 이동
        val intent = Intent(this, LowVisionMainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun saveMode(isLowVisionMode: Boolean) {
        val sharedPref = getSharedPreferences("LowVisionModePref", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("LOW_VISION_MODE", isLowVisionMode)
            apply()
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

    private fun isLowVisionModeEnabled(): Boolean {
        val sharedPref = getSharedPreferences("LowVisionModePref", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("LOW_VISION_MODE", false)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val sharedPrefMode = getSharedPreferences("LowVisionModePref", Context.MODE_PRIVATE)
            val isLowVisionMode = sharedPrefMode.getBoolean("LOW_VISION_MODE", false)
            val mode = if (isLowVisionMode) "저시력 모드" else "전맹 모드"
            val sharedPrefTTS = getSharedPreferences("TTSConfig", Context.MODE_PRIVATE)
            val pitch = sharedPrefTTS.getFloat("pitch", 1.0f)
            val speed = sharedPrefTTS.getFloat("speed", 1.0f)
            tts.language = Locale.KOREAN
            tts.setPitch(pitch)
            tts.setSpeechRate(speed)
            tts.speak(
                "현재 모드는 $mode 입니다. 모드를 변경하려면 좌우로 슬라이드하고 더블탭하여 선택하세요. 원래 화면으로 돌아가려면 상하로 슬라이드하세요.",
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