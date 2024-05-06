package com.ktm.capstone

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity(), GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {
    private var viewPager: ViewPager? = null
    private var adapter: FeaturesPagerAdapter? = null
    private var tts: TextToSpeech? = null
    private var gestureDetector: GestureDetector? = null
    private var prefs: SharedPreferences? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewPager = findViewById(R.id.viewPager)
        adapter = FeaturesPagerAdapter(this)
        viewPager.setAdapter(adapter)
        viewPager.setCurrentItem(0) // Set initial item to the first one
        viewPager.addOnPageChangeListener(object : SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                readFeatureDescription(position % adapter!!.count) // Use modulo to cycle descriptions
            }
        })

        // Load TTS settings from SharedPreferences
        prefs = getSharedPreferences("TTSConfig", MODE_PRIVATE)
        val savedPitch = prefs.getFloat("pitch", 1.0f)
        val savedSpeed = prefs.getFloat("speed", 1.0f)
        tts = TextToSpeech(this) { status: Int ->
            if (status == TextToSpeech.SUCCESS) {
                tts!!.setLanguage(Locale.KOREAN)
                tts!!.setPitch(savedPitch) // Apply saved pitch
                tts!!.setSpeechRate(savedSpeed) // Apply saved speed
                tts!!.speak(
                    "화면을 슬라이드 하거나 탭을 하세요.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "InitialInstruction"
                )
            }
        }
        gestureDetector = GestureDetector(this, this)
        viewPager.setOnTouchListener(OnTouchListener { v: View?, event: MotionEvent? ->
            gestureDetector!!.onTouchEvent(event!!)
            false // Allow the ViewPager to handle the swipe
        })
    }

    private fun readFeatureDescription(position: Int) {
        if (tts != null && adapter != null) {
            val description = adapter!!.getDescription(position)
            tts!!.speak(description, TextToSpeech.QUEUE_FLUSH, null, "FeatureDescription")
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val currentItem = viewPager!!.currentItem
        viewPager!!.setCurrentItem(
            (currentItem + 1) % adapter!!.count,
            true
        ) // Move to the next item, wrap around
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        val position = viewPager!!.currentItem
        var intent: Intent? = null
        when (position) {
            0 -> intent = Intent(this, ObjectRecognitionActivity::class.java)
            1 -> intent = Intent(this, TextToSpeechActivity::class.java)
            2 -> intent = Intent(this, MoneyRecognitionActivity::class.java)
            3 -> intent = Intent(this, BarcodeRecognitionActivity::class.java)
            4 -> intent = Intent(this, ColorRecognitionActivity::class.java)
            5 -> intent = Intent(this, VoiceSettingsActivity::class.java)
            else -> {}
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
            if (velocityY > 0) {
                viewPager!!.setCurrentItem((viewPager!!.currentItem + 1) % adapter!!.count, true)
            } else {
                var targetIndex = (viewPager!!.currentItem - 1) % adapter!!.count
                if (targetIndex < 0) {
                    targetIndex += adapter!!.count
                }
                viewPager!!.setCurrentItem(targetIndex, true)
            }
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
}
