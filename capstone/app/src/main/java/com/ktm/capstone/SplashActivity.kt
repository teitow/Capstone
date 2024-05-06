package com.ktm.capstone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class SplashActivity : Activity(), OnInitListener {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_screen)
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts!!.setLanguage(Locale.KOREAN) // 한국어로 설정
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "한국어 지원이 불가능합니다.")
            } else {
                checkPermissionsAndProceed()
            }
        } else {
            Log.e("TTS", "TTS 초기화 실패!")
        }
    }

    private fun checkPermissionsAndProceed() {
        if (!hasPermissions(REQUIRED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
        } else {
            proceedToMain()
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (allPermissionsGranted(grantResults)) {
                proceedToMain()
            } else {
                tts!!.speak(
                    "필요한 권한을 얻지 못하여 애플리케이션을 사용할 수 없습니다. 애플리케이션을 종료합니다.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    null
                )
                finish() // 권한 거부 시 앱 종료
            }
        }
    }

    private fun allPermissionsGranted(grantResults: IntArray): Boolean {
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun proceedToMain() {
        val intent = Intent(this@SplashActivity, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
        super.onDestroy()
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1240
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
}
