package com.ktm.capstone

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.content.Context
import android.graphics.Color
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity(), GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {
    private lateinit var viewPager: ViewPager
    private lateinit var adapter: FeaturesPagerAdapter
    private var tts: TextToSpeech? = null
    private lateinit var gestureDetector: GestureDetector
    private var prefs: SharedPreferences? = null
    private var hasShownInitialInstruction = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ThemePref에서 다크 모드와 저시각자 모드 설정을 가져옴
        val themePref = getSharedPreferences("ThemePref", Context.MODE_PRIVATE)
        val isDarkMode = themePref.getBoolean("DARK_MODE", false)
        val isLowVisionMode = themePref.getBoolean("LOW_VISION_MODE", false)

        // activity_main.xml의 루트 레이아웃에서 main_layout ID를 설정한 경우 이를 참조
        val layout = findViewById<View>(R.id.viewPager)

        // 저시각자 모드는 라이트 모드를 기반으로 하여 배경색을 흰색으로 설정
        layout.setBackgroundColor(
            if (isLowVisionMode) Color.WHITE else if (isDarkMode) Color.BLACK else Color.WHITE
        )

        if (savedInstanceState != null) {
            hasShownInitialInstruction = savedInstanceState.getBoolean("hasShownInitialInstruction", false)
        }

        viewPager = findViewById(R.id.viewPager)
        adapter = FeaturesPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.currentItem = 0 // 초기 항목을 첫 번째로 설정
        viewPager.addOnPageChangeListener(object : SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                readFeatureDescription(position % adapter.count) // 설명을 순환하도록 모듈로 사용
            }
        })

        // SharedPreferences에서 TTS 설정 불러오기
        prefs = getSharedPreferences("TTSConfig", MODE_PRIVATE)
        val savedPitch = prefs?.getFloat("pitch", 1.0f) ?: 1.0f
        val savedSpeed = prefs?.getFloat("speed", 1.0f) ?: 1.0f

        tts = TextToSpeech(this) { status: Int ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.KOREAN)
                tts?.setPitch(savedPitch) // 저장된 피치 적용
                tts?.setSpeechRate(savedSpeed) // 저장된 속도 적용
                if (!hasShownInitialInstruction) {
                    tts?.speak(
                        "좌우 슬라이드로 기능 선택을 해주시고 더블탭으로 기능 실행을 해주세요, 원래 기능으로 돌아가고 싶으시다면 화면을 상하로 슬라이드해주세요.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "InitialInstruction"
                    )
                    hasShownInitialInstruction = true
                }
            }
        }

        gestureDetector = GestureDetector(this, this)
        viewPager.setOnTouchListener(View.OnTouchListener { _, event: MotionEvent? ->
            gestureDetector.onTouchEvent(event!!)
            false // ViewPager가 스와이프를 처리할 수 있도록 함
        })
    }

    // 현재 선택된 기능 설명을 읽어주는 메서드
    private fun readFeatureDescription(position: Int) {
        if (tts != null) {
            val description = adapter.getDescription(position)
            tts!!.speak(description, TextToSpeech.QUEUE_FLUSH, null, "FeatureDescription")
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        stopTTS()
        val position = viewPager.currentItem
        var intent: Intent? = null
        when (position) {
            0 -> {
                intent = Intent(this, ObjectRecognitionActivity::class.java)
                val modePrefs = getSharedPreferences("ObjectModePref", MODE_PRIVATE)
                val mode = modePrefs.getString("MODE", "BASIC")
                intent.putExtra("MODE", mode)
            }
            1 -> intent = Intent(this, TextToSpeechActivity::class.java)
            2 -> intent = Intent(this, WeatherRecognitionActivity::class.java)
            3 -> intent = Intent(this, BarcodeRecognitionActivity::class.java)
            4 -> intent = Intent(this, ColorRecognitionActivity::class.java)
            5 -> intent = Intent(this, ConfigActivity::class.java)
        }
        intent?.let { startActivity(it) }
        return true
    }

    override fun onDown(e: MotionEvent): Boolean {
        return true
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (abs(velocityY.toDouble()) > abs(velocityX.toDouble())) {
            finish() // 현재 액티비티 종료, 뒤로가기 버튼과 유사한 동작
            return true
        }
        return false
    }

    override fun onLongPress(e: MotionEvent) {}
    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return false
    }

    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        return false
    }

    private fun stopTTS() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
    }

    override fun onDestroy() {
        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("hasShownInitialInstruction", hasShownInitialInstruction)
    }
}
