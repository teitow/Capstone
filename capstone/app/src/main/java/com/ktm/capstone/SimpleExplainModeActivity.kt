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

class SimpleExplainModeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var gestureDetector: GestureDetector
    private lateinit var optionsRecyclerView: RecyclerView
    private lateinit var adapter: OptionsAdapter
    private lateinit var currentModeTextView: TextView
    private var options = listOf(
        "SIMPLE 모드", // 심플 모드
        "BASIC 모드"   // 베이직 모드
    )
    private var selectedPosition = 0
    private var isSimpleMode = false // 심플 모드 여부를 저장

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_explain_mode)

        // 액션바 설정
        supportActionBar?.title = "설명 생략 모드"

        // TTS 초기화
        tts = TextToSpeech(this, this)

        // 다크 모드 설정 가져오기
        val themePref = getSharedPreferences("ThemePref", Context.MODE_PRIVATE)
        val isDarkMode = themePref.getBoolean("DARK_MODE", false)

        // 제목 및 현재 모드 텍스트뷰 설정
        val titleTextView = findViewById<TextView>(R.id.titleTextView)
        titleTextView.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)

        currentModeTextView = findViewById(R.id.currentModeTextView)
        currentModeTextView.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
        currentModeTextView.setTypeface(null, android.graphics.Typeface.BOLD)
        updateCurrentModeText()

        // RecyclerView 설정
        optionsRecyclerView = findViewById(R.id.optionsRecyclerView)
        optionsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = OptionsAdapter(options, isDarkMode)
        optionsRecyclerView.adapter = adapter

        // 레이아웃 배경 색상 설정
        val layout = findViewById<LinearLayout>(R.id.root_layout)
        layout.setBackgroundColor(if (isDarkMode) Color.BLACK else Color.WHITE)

        // 제스처 감지기 설정
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
                        // 오른쪽으로 스와이프하면 다음 옵션 선택
                        selectedPosition = (selectedPosition + 1) % options.size
                    } else {
                        // 왼쪽으로 스와이프하면 이전 옵션 선택
                        selectedPosition = (selectedPosition - 1 + options.size) % options.size
                    }
                    updateSelection()
                } else {
                    // 상하로 스와이프하면 액티비티 종료
                    finish()
                }
                return true
            }
        })

        // RecyclerView에서 터치 이벤트를 슬라이드 및 더블탭으로만 처리하도록 설정
        optionsRecyclerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        // 기본 클릭을 차단하고 제스처만 허용
        optionsRecyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                return true // 터치 이벤트 차단
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                // 처리하지 않음
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                // 처리하지 않음
            }
        })
    }

    // 현재 모드를 업데이트하는 함수
    private fun updateCurrentModeText() {
        val sharedPref = getSharedPreferences("ExplainModePref", Context.MODE_PRIVATE)
        val mode = sharedPref.getString("CURRENT_MODE", "BASIC 모드")
        currentModeTextView.text = "현재 모드 : $mode"
        isSimpleMode = mode == "SIMPLE" // 모드가 심플 모드인지 확인
    }

    // 선택된 항목을 업데이트하고 TTS로 말해주는 함수
    private fun updateSelection() {
        adapter.setSelectedPosition(selectedPosition)
        speakOption(options[selectedPosition])
    }

    // 선택된 모드를 실행하는 함수
    private fun executeOption(position: Int) {
        stopTTS()
        val selectedOption = options[position]
        val mode = if (selectedOption == "SIMPLE 모드") "SIMPLE" else "BASIC"
        saveMode(mode)
        updateCurrentModeText()
        finish()
    }

    // 선택된 모드를 SharedPreferences에 저장하는 함수
    private fun saveMode(mode: String) {
        val sharedPref = getSharedPreferences("ExplainModePref", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("CURRENT_MODE", mode)
            apply()
        }
    }

    // 선택된 옵션을 TTS로 읽어주는 함수
    private fun speakOption(option: String) {
        stopTTS()
        if (::tts.isInitialized) {
            tts.speak(option, TextToSpeech.QUEUE_FLUSH, null, "OptionSelected")
        }
    }

    // TTS 중지 함수
    private fun stopTTS() {
        if (::tts.isInitialized && tts.isSpeaking) {
            tts.stop()
        }
    }

    // TTS 초기화
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val sharedPref = getSharedPreferences("ExplainModePref", Context.MODE_PRIVATE)
            val mode = sharedPref.getString("CURRENT_MODE", "BASIC 모드")
            val sharedPrefTTS = getSharedPreferences("TTSConfig", Context.MODE_PRIVATE)
            val pitch = sharedPrefTTS.getFloat("pitch", 1.0f)
            val speed = sharedPrefTTS.getFloat("speed", 1.0f)
            tts.language = Locale.KOREAN
            tts.setPitch(pitch)
            tts.setSpeechRate(speed)

            // 심플 모드일 때는 간단한 메시지, 베이직 모드일 때는 상세 메시지 출력
            if (isSimpleMode) {
                tts.speak(
                    "설명 생략 모드입니다. 현재 모드는 $mode 입니다.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "InitialInstructions"
                )
            } else {
                tts.speak(
                    "현재 설명 생략 모드는 $mode 입니다. 좌우 슬라이드로 모드를 선택해주시고 더블탭으로 실행해주세요. 원래 화면으로 돌아가고 싶으시다면 화면을 상하로 슬라이드해주세요.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "InitialInstructions"
                )
            }
        }
    }

    // TTS 종료 처리
    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
