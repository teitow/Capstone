package com.ktm.capstone

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.view.GestureDetector
import android.view.MotionEvent
import java.util.Locale
import kotlin.math.abs

class VoiceSettingsActivity : Activity(), GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {
    private var tts: TextToSpeech? = null
    private var gestureDetector: GestureDetector? = null
    private var pitch = 1.0f // Default pitch
    private var speed = 1.0f // Default speed
    private var ttsInitialized = false
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_settings)
        prefs = getSharedPreferences("TTSConfig", MODE_PRIVATE)
        pitch = prefs.getFloat("pitch", 1.0f)
        speed = prefs.getFloat("speed", 1.0f)

        tts = TextToSpeech(this, OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let {
                    it.language = Locale.KOREAN
                    it.setPitch(pitch)
                    it.setSpeechRate(speed)
                    ttsInitialized = true
                    it.speak(
                        "화면을 좌우로 슬라이드 해 속도를 조절하고 상하로 슬라이드해서 톤을 조절해주세요. 기본 값은 1입니다. 조절이 완료되면 화면을 두번 누르면 메인화면으로 돌아갑니다.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                }
            }
        })
        gestureDetector = GestureDetector(this, this)
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
        val editor = prefs.edit()
        if (abs(velocityX.toDouble()) > abs(velocityY.toDouble())) {
            if (velocityX > 0) {
                speed += 0.3f
            } else {
                speed -= 0.3f
            }
            tts?.setSpeechRate(speed)
            editor.putFloat("speed", speed)
        } else {
            if (velocityY > 0) {
                pitch -= 0.3f
            } else {
                pitch += 0.3f
            }
            tts?.setPitch(pitch)
            editor.putFloat("pitch", pitch)
        }
        editor.apply()
        tts?.speak(
            String.format(Locale.KOREA, "현재 속도는 %.1f, 톤은 %.1f입니다.", speed, pitch),
            TextToSpeech.QUEUE_FLUSH,
            null,
            null
        )
        return true
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
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        return false
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        return false
    }

    override fun onDestroy() {
        tts?.let {
            it.stop()
            it.shutdown()
        }
        super.onDestroy()
    }
}
