package com.ktm.capstone

import android.content.Context
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.util.Log
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

class SplashActivity : Activity(), OnInitListener {
    private var tts: TextToSpeech? = null
    private var isPermissionsRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 테마 적용
        val themePref = getSharedPreferences("ThemePref", Context.MODE_PRIVATE)
        val isDarkMode = themePref.getBoolean("DARK_MODE", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        setContentView(R.layout.splash_screen)

        // 다크 모드 여부 확인 및 이미지 설정
        val splashLayout = findViewById<RelativeLayout>(R.id.splash_layout)
        val splashImage = findViewById<ImageView>(R.id.splash_image)
        if (isDarkMode) {
            splashLayout.setBackgroundColor(Color.BLACK)
            splashImage.setImageResource(R.drawable.splash_screen_dark)
        } else {
            splashLayout.setBackgroundColor(Color.WHITE)
            splashImage.setImageResource(R.drawable.splash_screen)
        }

        if (savedInstanceState != null) {
            isPermissionsRequested = savedInstanceState.getBoolean("isPermissionsRequested", false)
        }

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
            if (!isPermissionsRequested) {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
                isPermissionsRequested = true
            }
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                checkWriteSettingsPermissionAndProceed()
            }, 800) // 800 밀리초 = 0.8초
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
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
                checkWriteSettingsPermissionAndProceed()
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

    private fun checkWriteSettingsPermissionAndProceed() {
        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivityForResult(intent, WRITE_SETTINGS_REQUEST_CODE)
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                proceedToMain()
            }, 800) // 800 밀리초 = 0.8초
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WRITE_SETTINGS_REQUEST_CODE) {
            if (Settings.System.canWrite(this)) {
                proceedToMain()
            } else {
                tts!!.speak(
                    "필요한 설정 권한을 얻지 못하여 애플리케이션을 사용할 수 없습니다. 애플리케이션을 종료합니다.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    null
                )
                finish() // 권한 거부 시 앱 종료
            }
        }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isPermissionsRequested", isPermissionsRequested)
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1240
        private const val WRITE_SETTINGS_REQUEST_CODE = 1241
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
    }
}
