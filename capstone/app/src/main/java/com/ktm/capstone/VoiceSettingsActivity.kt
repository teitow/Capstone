package com.ktm.capstone

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.Locale
import kotlin.math.abs
import android.util.Log

class VoiceSettingsActivity : Activity(), GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {
    private var tts: TextToSpeech? = null
    private var gestureDetector: GestureDetector? = null
    private var pitch = 1.0f // 기본 톤
    private var speed = 1.0f // 기본 속도
    private var ttsInitialized = false
    private lateinit var prefs: SharedPreferences

    private lateinit var tvSpeed: TextView
    private lateinit var sbSpeed: SeekBar
    private lateinit var tvPitch: TextView
    private lateinit var sbPitch: SeekBar
    private lateinit var layout: ConstraintLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_settings)

        layout = findViewById(R.id.voice_settings_layout) // ConstraintLayout으로 변경
        tvSpeed = findViewById(R.id.tvSpeed)
        sbSpeed = findViewById(R.id.sbSpeed)
        tvPitch = findViewById(R.id.tvPitch)
        sbPitch = findViewById(R.id.sbPitch)

        prefs = getSharedPreferences("TTSConfig", MODE_PRIVATE)
        pitch = prefs.getFloat("pitch", 1.0f)
        speed = prefs.getFloat("speed", 1.0f)

        val isDarkMode = isDarkModeEnabled()

        if (isDarkMode) {
            enableDarkMode()
        } else {
            enableLightMode()
        }

        tts = TextToSpeech(this, OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let {
                    it.language = Locale.KOREAN
                    it.setPitch(pitch)
                    it.setSpeechRate(speed)
                    ttsInitialized = true
                    it.speak(
                        "화면을 좌우로 슬라이드 해 속도를 조절하고 상하로 슬라이드해서 톤을 조절해주세요. 기본 값은 1입니다. 조절이 완료되면 화면을 두번 누르면 이전 화면으로 돌아갑니다.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                }
            }
        })
        gestureDetector = GestureDetector(this, this)

        updateUI()
    }

    private fun isDarkModeEnabled(): Boolean {
        val themePref = getSharedPreferences("ThemePref", MODE_PRIVATE)
        return themePref.getBoolean("DARK_MODE", false)
    }

    private fun enableDarkMode() {
        layout.setBackgroundColor(Color.BLACK)
        tvSpeed.setTextColor(Color.WHITE)
        tvPitch.setTextColor(Color.WHITE)
        findViewById<TextView>(R.id.tvTitle).setTextColor(Color.WHITE)
    }

    private fun enableLightMode() {
        layout.setBackgroundColor(Color.WHITE)
        tvSpeed.setTextColor(Color.BLACK)
        tvPitch.setTextColor(Color.BLACK)
        findViewById<TextView>(R.id.tvTitle).setTextColor(Color.BLACK)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector?.onTouchEvent(event) ?: super.onTouchEvent(event)
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
        if (!ttsInitialized) return false
        stopTTS()
        val editor = prefs.edit()
        if (abs(velocityX.toDouble()) > abs(velocityY.toDouble())) {
            if (velocityX > 0) {
                speed = (speed + 0.3f).coerceAtMost(4.0f)
            } else {
                speed = (speed - 0.3f).coerceAtLeast(0.1f)
            }
            tts?.setSpeechRate(speed)
            editor.putFloat("speed", speed)
        } else {
            if (velocityY > 0) {
                pitch = (pitch - 0.3f).coerceAtLeast(0.1f)
            } else {
                pitch = (pitch + 0.3f).coerceAtMost(4.0f)
            }
            tts?.setPitch(pitch)
            editor.putFloat("pitch", pitch)
        }
        editor.apply()
        updateUI()
        tts?.speak(
            String.format(Locale.KOREA, "현재 속도는 %.1f, 톤은 %.1f입니다.", speed, pitch),
            TextToSpeech.QUEUE_FLUSH,
            null,
            null
        )
        return true
    }

    private fun updateUI() {
        tvSpeed.text = String.format("속도: %.1f", speed)
        sbSpeed.progress = ((speed - 0.1f) * 10).toInt()
        tvPitch.text = String.format("톤: %.1f", pitch)
        sbPitch.progress = ((pitch - 0.1f) * 10).toInt()

        val speedColor = getColorFromValue(speed)
        val pitchColor = getColorFromValue(pitch)

        Log.d("VoiceSettingsActivity", "Speed: $speed, SpeedColor: $speedColor")
        Log.d("VoiceSettingsActivity", "Pitch: $pitch, PitchColor: $pitchColor")

        sbSpeed.progressDrawable.setColorFilter(speedColor, android.graphics.PorterDuff.Mode.SRC_IN)
        sbPitch.progressDrawable.setColorFilter(pitchColor, android.graphics.PorterDuff.Mode.SRC_IN)

        sbSpeed.thumb.setColorFilter(speedColor, android.graphics.PorterDuff.Mode.SRC_IN)
        sbPitch.thumb.setColorFilter(pitchColor, android.graphics.PorterDuff.Mode.SRC_IN)
    }

    private fun getColorFromValue(value: Float): Int {
        val epsilon = 0.001f // 작은 값 설정

        return when {
            value < 0.5f -> Color.BLUE
            value in 0.6f..0.9f -> Color.parseColor("#4169E1") // 연한 파란색
            value in 0.92f..1.02f -> Color.GREEN
            value in 1.1f..1.8f -> Color.parseColor("#FFB6C1") // 더 연한 붉은색
            value in 1.0f..2.2f -> Color.parseColor("#FF7F7F") // 연한 붉은색
            value > 2.2f -> Color.RED
            else -> Color.MAGENTA
        }
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

    override fun onDoubleTap(e: MotionEvent): Boolean {
        stopTTS()
        val intent = Intent(this, ConfigActivity::class.java)
        startActivity(intent)
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        return false
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        return false
    }

    private fun stopTTS() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
    }

    override fun onDestroy() {
        tts?.let {
            it.stop()
            it.shutdown()
        }
        super.onDestroy()
    }
}
