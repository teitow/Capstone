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
    private var isSimpleMode = false // 심플 모드 확인 변수

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_settings)

        // 레이아웃 및 뷰 초기화
        layout = findViewById(R.id.voice_settings_layout)
        tvSpeed = findViewById(R.id.tvSpeed)
        sbSpeed = findViewById(R.id.sbSpeed)
        tvPitch = findViewById(R.id.tvPitch)
        sbPitch = findViewById(R.id.sbPitch)

        prefs = getSharedPreferences("TTSConfig", MODE_PRIVATE)
        pitch = prefs.getFloat("pitch", 1.0f)
        speed = prefs.getFloat("speed", 1.0f)

        // 다크 모드 설정 여부 확인
        val isDarkMode = isDarkModeEnabled()
        if (isDarkMode) {
            enableDarkMode()
        } else {
            enableLightMode()
        }

        // 현재 모드 확인 (심플 모드 여부 체크)
        val explainModePrefs = getSharedPreferences("ExplainModePref", MODE_PRIVATE)
        isSimpleMode = explainModePrefs.getString("CURRENT_MODE", "BASIC") == "SIMPLE"

        // TTS 초기화
        tts = TextToSpeech(this, OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let {
                    it.language = Locale.KOREAN
                    it.setPitch(pitch)
                    it.setSpeechRate(speed)
                    ttsInitialized = true

                    // 모드에 따른 TTS 메시지 설정
                    val message = if (isSimpleMode) {
                        "TTS 속도 변환입니다."
                    } else {
                        "화면을 좌우로 슬라이드 해 속도를 조절하고 상하로 슬라이드해서 톤을 조절해주세요. 기본 값은 1입니다. 조절이 완료되면 화면을 두번 누르면 이전 화면으로 돌아갑니다."
                    }

                    it.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        })

        gestureDetector = GestureDetector(this, this)

        // UI 업데이트
        updateUI()
    }

    // 다크 모드 여부 확인 함수
    private fun isDarkModeEnabled(): Boolean {
        val themePref = getSharedPreferences("ThemePref", MODE_PRIVATE)
        return themePref.getBoolean("DARK_MODE", false)
    }

    // 다크 모드 활성화 함수
    private fun enableDarkMode() {
        layout.setBackgroundColor(Color.BLACK)
        tvSpeed.setTextColor(Color.WHITE)
        tvPitch.setTextColor(Color.WHITE)
        findViewById<TextView>(R.id.tvTitle).setTextColor(Color.WHITE)
    }

    // 라이트 모드 활성화 함수
    private fun enableLightMode() {
        layout.setBackgroundColor(Color.WHITE)
        tvSpeed.setTextColor(Color.BLACK)
        tvPitch.setTextColor(Color.BLACK)
        findViewById<TextView>(R.id.tvTitle).setTextColor(Color.BLACK)
    }

    // 제스처 처리
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector?.onTouchEvent(event) ?: super.onTouchEvent(event)
    }

    // 스와이프에 따른 TTS 속도 및 피치 조정
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

    // UI 업데이트
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

    // 값에 따른 색상 결정 함수
    private fun getColorFromValue(value: Float): Int {
        return when {
            value < 0.5f -> Color.BLUE
            value in 0.6f..0.9f -> Color.parseColor("#4169E1") // 연한 파란색
            value in 0.92f..1.02f -> Color.GREEN
            value in 1.1f..1.8f -> Color.parseColor("#FFB6C1") // 연한 붉은색
            value in 1.0f..2.2f -> Color.parseColor("#FF7F7F") // 연한 붉은색
            value > 2.2f -> Color.RED
            else -> Color.MAGENTA
        }
    }

    // 터치 이벤트 처리
    override fun onDown(e: MotionEvent): Boolean {
        return true
    }

    override fun onLongPress(e: MotionEvent) {}
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
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

    // TTS 중지 함수
    private fun stopTTS() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
    }

    // TTS 종료 처리
    override fun onDestroy() {
        tts?.let {
            it.stop()
            it.shutdown()
        }
        super.onDestroy()
    }
}
