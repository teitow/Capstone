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

class DarkModeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var gestureDetector: GestureDetector
    private lateinit var optionsRecyclerView: RecyclerView
    private lateinit var adapter: OptionsAdapter
    private lateinit var currentModeTextView: TextView
    private var options = listOf(
        "라이트 모드",
        "다크 모드"
    )
    private var selectedPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dark_mode)

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
        selectedPosition = if (isDarkMode) 1 else 0
        updateSelection()
    }

    private fun updateCurrentModeText() {
        val sharedPref = getSharedPreferences("ThemePref", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("DARK_MODE", false)
        val mode = if (isDarkMode) "다크 모드" else "라이트 모드"
        currentModeTextView.text = "현재 모드 : $mode"
    }

    private fun updateSelection() {
        adapter.setSelectedPosition(selectedPosition)
        speakOption(options[selectedPosition])
    }

    private fun executeOption(position: Int) {
        stopTTS()
        val isDarkMode = position == 1
        saveMode(isDarkMode)
        updateCurrentModeText()
        speakOption(".")

        // 3초 후에 앱 종료
        optionsRecyclerView.postDelayed({
            System.exit(0)
        }, 1000)
    }

    private fun saveMode(isDarkMode: Boolean) {
        val sharedPref = getSharedPreferences("ThemePref", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("DARK_MODE", isDarkMode)
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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val sharedPrefTheme = getSharedPreferences("ThemePref", Context.MODE_PRIVATE)
            val isDarkMode = sharedPrefTheme.getBoolean("DARK_MODE", false)
            val mode = if (isDarkMode) "다크 모드" else "라이트 모드"
            val sharedPrefTTS = getSharedPreferences("TTSConfig", Context.MODE_PRIVATE)
            val pitch = sharedPrefTTS.getFloat("pitch", 1.0f)
            val speed = sharedPrefTTS.getFloat("speed", 1.0f)
            tts.language = Locale.KOREAN
            tts.setPitch(pitch)
            tts.setSpeechRate(speed)
            tts.speak(
                "현재 모드는 $mode 입니다. 좌우 슬라이드로 모드를 선택해주시고 더블탭으로 실행해주세요, 원래 화면으로 돌아가고 싶으시다면 화면을 상하로 슬라이드해주세요.",
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
