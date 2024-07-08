package com.ktm.capstone

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class ConfigActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var gestureDetector: GestureDetector
    private lateinit var optionsRecyclerView: RecyclerView
    private lateinit var adapter: OptionsAdapter
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

        tts = TextToSpeech(this, this)

        optionsRecyclerView = findViewById(R.id.optionsRecyclerView)
        optionsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = OptionsAdapter(options) { position ->
            onOptionSelected(position)
        }
        optionsRecyclerView.adapter = adapter

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                selectedPosition = (selectedPosition + 1) % options.size
                updateSelection()
                return true
            }

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
                if (velocityY > 0) {
                    selectedPosition = (selectedPosition + 1) % options.size
                } else {
                    selectedPosition = (selectedPosition - 1 + options.size) % options.size
                }
                updateSelection()
                return true
            }
        })

        optionsRecyclerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun onOptionSelected(position: Int) {
        selectedPosition = position
        updateSelection()
    }

    private fun updateSelection() {
        adapter.setSelectedPosition(selectedPosition)
        speakOption(options[selectedPosition])
    }

    private fun executeOption(position: Int) {
        val selectedOption = options[position]
        when (selectedOption) {
            "TTS 속도" -> startActivity(Intent(this, TTSSpeedActivity::class.java))
            "다크 모드" -> startActivity(Intent(this, DarkModeActivity::class.java))
            "배터리 세이브" -> startActivity(Intent(this, BatterySaveActivity::class.java))
            "객체 모드" -> startActivity(Intent(this, ObjectModeActivity::class.java))
            "색상 모드" -> startActivity(Intent(this, ColorModeActivity::class.java))
            "날씨 모드" -> startActivity(Intent(this, WeatherModeActivity::class.java))
            "바코드 모드" -> startActivity(Intent(this, BarcodeModeActivity::class.java))
        }
    }

    private fun speakOption(option: String) {
        if (::tts.isInitialized) {
            tts.speak(option, TextToSpeech.QUEUE_FLUSH, null, "OptionSelected")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREAN
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
