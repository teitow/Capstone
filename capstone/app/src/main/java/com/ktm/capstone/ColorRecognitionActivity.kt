package com.ktm.capstone

import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCapture.OutputFileResults
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutionException
import kotlin.math.abs

class ColorRecognitionActivity : AppCompatActivity(), OnInitListener {
    private var tts: TextToSpeech? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var isTTSInitialized = false
    private var cameraClickSound: MediaPlayer? = null
    private var imageView: ImageView? = null
    private var gestureDetector: GestureDetector? = null
    private var yStart = 0f
    private var isImageDisplayed = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector? = null
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts!!.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Korean language is not supported.")
            } else {
                isTTSInitialized = true
                speak("색상을 인식하려면 사진을 찍어주세요.", "ID_INITIAL")
            }
        } else {
            Log.e("TTS", "Initialization failed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_color_recognition)
        tts = TextToSpeech(this, this)
        imageView = findViewById(R.id.imageView)
        cameraClickSound = MediaPlayer.create(this, R.raw.camera_click)
        gestureDetector = GestureDetector(this, object : SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!isImageDisplayed) {
                    captureAndAnalyze()
                } else {
                    resetToInitialView()
                }
                return true
            }
        })
        initializeCamera()
        setupTTS()
    }

    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                preview = Preview.Builder().build()
                val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
                preview!!.setSurfaceProvider(viewFinder.getSurfaceProvider())
                imageCapture = ImageCapture.Builder().build()
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    (this as LifecycleOwner),
                    cameraSelector!!,
                    preview,
                    imageCapture
                )
            } catch (e: ExecutionException) {
                Log.e("CameraXApp", "Use case binding failed", e)
            } catch (e: InterruptedException) {
                Log.e("CameraXApp", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupTTS() {
        tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                // Nothing to do here
            }

            override fun onDone(utteranceId: String) {
                if ("COLOR_DETECTED" == utteranceId) {
                    tts!!.playSilentUtterance(800, TextToSpeech.QUEUE_ADD, null)
                    speak(
                        "색상을 다시 인식하려면 화면을 두 번 탭하세요. 메인 화면으로 돌아가려면 화면을 상하로 슬라이드하세요.",
                        "ID_GUIDANCE"
                    )
                }
            }

            override fun onError(utteranceId: String) {
                // Handle errors
            }
        })
    }

    private fun speak(text: String, utteranceId: String) {
        if (isTTSInitialized) {
            tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    private fun captureAndAnalyze() {
        imageView!!.visibility = View.GONE
        val fileName = "pic_" + System.currentTimeMillis() + ".jpg"
        val photoFile = File(getExternalFilesDir(null), fileName)
        val options = OutputFileOptions.Builder(photoFile).build()
        imageCapture!!.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: OutputFileResults) {
                    cameraClickSound!!.start()
                    val savedUri = Uri.fromFile(photoFile)
                    imageView!!.setImageURI(savedUri)
                    imageView!!.visibility = View.VISIBLE
                    isImageDisplayed = true
                    Log.d("CameraXApp", "Photo saved successfully: $savedUri")
                    analyzeColor(savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXApp", "Photo capture failed: " + exception.message, exception)
                    speak("사진 촬영에 실패했습니다.", "ID_ERROR")
                }
            })
    }

    private fun analyzeColor(imageUri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
            if (bitmap != null && !bitmap.isRecycled) {
                val centerX = bitmap.width / 2
                val centerY = bitmap.height / 2

                // 이미지 중앙의 픽셀 색상을 가져옵니다.
                val pixel = bitmap.getPixel(centerX, centerY)
                val hsv = FloatArray(3)
                Color.colorToHSV(pixel, hsv)
                val colorName = getColorNameHSV(hsv[0], hsv[1], hsv[2])
                speak("인식된 색상은 " + colorName + "입니다.", "COLOR_DETECTED")
                bitmap.recycle()
            }
        } catch (e: IOException) {
            Log.e("ColorRecognition", "Error accessing file: " + e.message, e)
        }
    }

    private fun getColorNameHSV(hue: Float, saturation: Float, value: Float): String {
        if (value < 0.1) return "검정" // 명도가 매우 낮으면 검정색
        if (value > 0.8 && saturation < 0.2) return "흰색" // 명도가 높고 채도가 낮으면 흰색 (범위 확장)
        if (saturation < 0.1 && value < 0.8) return "회색" // 채도가 매우 낮고 명도가 매우 높지 않으면 회색
        if (hue < 20) return "빨강"
        if (hue < 40) return "주황"
        if (hue < 75) return "노랑"
        if (hue < 140) return "초록"
        if (hue < 160) return "청록색"
        if (hue < 220) return "파랑"
        if (hue < 240) return "남색"
        if (hue < 280) return "보라"
        return if (hue < 330) "핑크" else "색상을 알 수 없습니다. 다시 한번 촬영해주세요"
        // 일부 색상이 겹치거나 구분이 모호할 경우
    }

    private fun resetToInitialView() {
        imageView!!.visibility = View.GONE
        isImageDisplayed = false
        startCameraPreview()
        speak("초기 화면으로 돌아갑니다.", "ID_RESET")
    }

    private fun startCameraPreview() {
        cameraProvider!!.unbindAll()
        camera = cameraProvider!!.bindToLifecycle(
            (this as LifecycleOwner),
            cameraSelector!!,
            preview,
            imageCapture
        )
        val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
        preview!!.setSurfaceProvider(viewFinder.getSurfaceProvider())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector!!.onTouchEvent(event)
        val action = event.actionMasked // 여기를 수정하였습니다.
        if (action == MotionEvent.ACTION_DOWN) {
            yStart = event.y
        } else if (action == MotionEvent.ACTION_UP) {
            val yEnd = event.y
            if (abs((yEnd - yStart).toDouble()) > 100) {
                navigateToMainActivity()
            }
        }
        return super.onTouchEvent(event)
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
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
}
