package com.ktm.capstone

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import java.util.Locale

class LowVisionMainActivity : Activity(), GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {

    private var tts: TextToSpeech? = null
    private lateinit var gestureDetector: GestureDetector
    private var prefs: SharedPreferences? = null
    private var hasShownInitialInstruction = false
    private var selectedSection = 0

    private val sectionIds = intArrayOf(
        R.id.section1, R.id.section2, R.id.section3,
        R.id.section4, R.id.section5, R.id.section6
    )

    private val sectionImages = intArrayOf(
        R.drawable.object_recognition, R.drawable.text_to_speech,
        R.drawable.weather_recognition, R.drawable.barcode_recognition,
        R.drawable.color_recognition, R.drawable.config
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_low_vision_main)

        if (savedInstanceState != null) {
            hasShownInitialInstruction = savedInstanceState.getBoolean("hasShownInitialInstruction", false)
        }

        prefs = getSharedPreferences("TTSConfig", MODE_PRIVATE)
        val savedPitch = prefs?.getFloat("pitch", 1.0f) ?: 1.0f
        val savedSpeed = prefs?.getFloat("speed", 1.0f) ?: 1.0f

        tts = TextToSpeech(this) { status: Int ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.KOREAN)
                tts?.setPitch(savedPitch)
                tts?.setSpeechRate(savedSpeed)
                if (!hasShownInitialInstruction) {
                    tts?.speak(
                        "화면을 터치하여 항목을 선택하고 더블탭하여 실행하세요. 현재 모드는 저시력 지원 모드입니다.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "InitialInstruction"
                    )
                    hasShownInitialInstruction = true
                }
            }
        }

        gestureDetector = GestureDetector(this, this)
        for (i in sectionIds.indices) {
            findViewById<LinearLayout>(sectionIds[i]).setOnTouchListener { _, event: MotionEvent? ->
                gestureDetector.onTouchEvent(event!!)
                true
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        stopTTS()
        val position = selectedSection
        var intent: Intent? = null
        when (position) {
            0 -> intent = Intent(this, ObjectRecognitionActivity::class.java)
            1 -> intent = Intent(this, TextToSpeechActivity::class.java)
            2 -> intent = Intent(this, WeatherRecognitionActivity::class.java)
            3 -> intent = Intent(this, BarcodeRecognitionActivity::class.java)
            4 -> intent = Intent(this, ColorRecognitionActivity::class.java)
            5 -> intent = Intent(this, LowVisionConfigActivity::class.java)
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
        if (Math.abs(velocityY.toDouble()) > Math.abs(velocityX.toDouble())) {
            finish()
            return true
        }
        return false
    }

    override fun onLongPress(e: MotionEvent) {}
    override fun onScroll(
        e1: MotionEvent?,
        p1: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return false
    }

    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val touchedSection = getTouchedSection(e.x, e.y)
        if (touchedSection != -1) {
            selectedSection = touchedSection
            updateSelection()
        }
        Log.d("TouchEvent", "Touched coordinates: (${e.x}, ${e.y}), Selected section: $touchedSection")
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        return false
    }

    private fun stopTTS() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
    }

    private fun getTouchedSection(x: Float, y: Float): Int {
        val sectionWidth = resources.displayMetrics.widthPixels / 2
        val sectionHeight = resources.displayMetrics.heightPixels / 3

        Log.d("TouchEvent", "Screen Width: ${resources.displayMetrics.widthPixels}, Screen Height: ${resources.displayMetrics.heightPixels}")
        Log.d("TouchEvent", "Section Width: $sectionWidth, Section Height: $sectionHeight")

        val column = (x / sectionWidth).toInt()
        val row = (y / sectionHeight).toInt()

        Log.d("TouchEvent", "Column: $column, Row: $row")

        return row * 2 + column
    }

    private fun updateSelection() {
        for (i in sectionIds.indices) {
            val section = findViewById<LinearLayout>(sectionIds[i])
            section.setBackgroundColor(
                if (i == selectedSection) ContextCompat.getColor(this, R.color.selectedSection)
                else ContextCompat.getColor(this, R.color.defaultSection)
            )
        }
        speakSectionDescription(selectedSection)
    }

    private fun speakSectionDescription(section: Int) {
        stopTTS()
        val descriptions = arrayOf(
            "객체 인식",
            "텍스트 음성 변환",
            "날씨 확인",
            "바코드 스캔",
            "색상 인식",
            "앱 환경 설정"
        )
        if (tts != null) {
            tts!!.speak(descriptions[section], TextToSpeech.QUEUE_FLUSH, null, "SectionDescription")
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
