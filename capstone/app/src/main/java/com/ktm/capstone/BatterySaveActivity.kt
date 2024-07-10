package com.ktm.capstone

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import kotlin.math.abs

class BatterySaveActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var gestureDetector: GestureDetector
    private lateinit var optionsRecyclerView: RecyclerView
    private lateinit var adapter: OptionsAdapter
    private var options = listOf(
        "모드 켜기",
        "모드 끄기"
    )
    private var selectedPosition = 0
    private var originalBrightness: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery_save)

        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        supportActionBar?.title = "배터리 세이브 모드"

        tts = TextToSpeech(this, this)

        optionsRecyclerView = findViewById(R.id.optionsRecyclerView)
        optionsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = OptionsAdapter(options)
        optionsRecyclerView.adapter = adapter

        // 현재 시스템 밝기 값 저장
        val sharedPref = getSharedPreferences("BatterySavePref", Context.MODE_PRIVATE)
        originalBrightness = sharedPref.getInt("originalBrightness", -1)
        if (originalBrightness == -1) {
            try {
                originalBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                with(sharedPref.edit()) {
                    putInt("originalBrightness", originalBrightness)
                    apply()
                }
            } catch (e: Settings.SettingNotFoundException) {
                e.printStackTrace()
            }
        }

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
    }

    private fun updateSelection() {
        adapter.setSelectedPosition(selectedPosition)
        speakOption(options[selectedPosition])
    }

    private fun executeOption(position: Int) {
        stopTTS()
        val selectedOption = options[position]
        if (selectedOption == "모드 켜기") {
            setSystemBrightness(10) // 밝기 최소화
        } else {
            setSystemBrightness(originalBrightness) // 원래 밝기로 복원
        }
        saveMode(selectedOption)
        finish()
    }

    private fun setSystemBrightness(brightness: Int) {
        val resolver: ContentResolver = contentResolver
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
    }

    private fun saveMode(mode: String) {
        val sharedPref = getSharedPreferences("BatterySavePref", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("MODE", mode)
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
            tts.language = Locale.KOREAN
            tts.speak(
                "배터리 세이브 모드입니다, 모드를 켜시면 화면 밝기가 최소화됩니다, 모드를 끄시면 이전 밝기 상태로 돌아갑니다. 좌우 슬라이드로 모드를 선택해주시고 더블탭으로 실행해주세요, 원래 화면으로 돌아가고 싶으시다면 화면을 상하로 슬라이드해주세요.",
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
