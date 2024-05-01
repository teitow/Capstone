package com.ktm.capstone;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class ColorRecognitionActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private Camera camera;
    private Preview preview;
    private ImageCapture imageCapture;
    private boolean isTTSInitialized = false;
    private MediaPlayer cameraClickSound;
    private ImageView imageView;
    private GestureDetector gestureDetector;
    private float yStart = 0;
    private boolean isImageDisplayed = false;
    private ProcessCameraProvider cameraProvider;
    private CameraSelector cameraSelector;

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.KOREAN);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Korean language is not supported.");
            } else {
                isTTSInitialized = true;
                speak("색상을 인식하려면 사진을 찍어주세요.", "ID_INITIAL");
            }
        } else {
            Log.e("TTS", "Initialization failed.");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_recognition);

        tts = new TextToSpeech(this, this);
        imageView = findViewById(R.id.imageView);
        cameraClickSound = MediaPlayer.create(this, R.raw.camera_click);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!isImageDisplayed) {
                    captureAndAnalyze();
                } else {
                    resetToInitialView();
                }
                return true;
            }
        });

        initializeCamera();
        setupTTS();
    }

    private void initializeCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                preview = new Preview.Builder().build();
                PreviewView viewFinder = findViewById(R.id.viewFinder);
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraXApp", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void setupTTS() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // Nothing to do here
            }

            @Override
            public void onDone(String utteranceId) {
                if ("COLOR_DETECTED".equals(utteranceId)) {
                    tts.playSilentUtterance(800, TextToSpeech.QUEUE_ADD, null);
                    speak("색상을 다시 인식하려면 화면을 두 번 탭하세요. 메인 화면으로 돌아가려면 화면을 상하로 슬라이드하세요.", "ID_GUIDANCE");
                }
            }

            @Override
            public void onError(String utteranceId) {
                // Handle errors
            }
        });
    }

    private void speak(String text, String utteranceId) {
        if (isTTSInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        }
    }

    private void captureAndAnalyze() {
        imageView.setVisibility(View.GONE);
        String fileName = "pic_" + System.currentTimeMillis() + ".jpg";
        File photoFile = new File(getExternalFilesDir(null), fileName);
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                cameraClickSound.start();
                Uri savedUri = Uri.fromFile(photoFile);
                imageView.setImageURI(savedUri);
                imageView.setVisibility(View.VISIBLE);
                isImageDisplayed = true;
                Log.d("CameraXApp", "Photo saved successfully: " + savedUri);
                analyzeColor(savedUri);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("CameraXApp", "Photo capture failed: " + exception.getMessage(), exception);
                speak("사진 촬영에 실패했습니다.", "ID_ERROR");
            }
        });
    }

    private void analyzeColor(Uri imageUri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
            if (bitmap != null && !bitmap.isRecycled()) {
                int centerX = bitmap.getWidth() / 2;
                int centerY = bitmap.getHeight() / 2;

                // 이미지 중앙의 픽셀 색상을 가져옵니다.
                int pixel = bitmap.getPixel(centerX, centerY);
                float[] hsv = new float[3];
                Color.colorToHSV(pixel, hsv);
                String colorName = getColorNameHSV(hsv[0], hsv[1], hsv[2]);
                speak("인식된 색상은 " + colorName + "입니다.", "COLOR_DETECTED");

                bitmap.recycle();
            }
        } catch (IOException e) {
            Log.e("ColorRecognition", "Error accessing file: " + e.getMessage(), e);
        }
    }

    private String getColorNameHSV(float hue, float saturation, float value) {
        if (value < 0.1) return "검정"; // 명도가 매우 낮으면 검정색
        if (value > 0.8 && saturation < 0.2) return "흰색"; // 명도가 높고 채도가 낮으면 흰색 (범위 확장)
        if (saturation < 0.1 && value < 0.8) return "회색"; // 채도가 매우 낮고 명도가 매우 높지 않으면 회색

        if (hue < 20) return "빨강";
        if (hue < 40) return "주황";
        if (hue < 75) return "노랑";
        if (hue < 140) return "초록";
        if (hue < 160) return "청록색";
        if (hue < 220) return "파랑";
        if (hue < 240) return "남색";
        if (hue < 280) return "보라";
        if (hue < 330) return "핑크";

        return "색상을 알 수 없습니다. 다시 한번 촬영해주세요"; // 일부 색상이 겹치거나 구분이 모호할 경우
    }




    private void resetToInitialView() {
        imageView.setVisibility(View.GONE);
        isImageDisplayed = false;
        startCameraPreview();
        speak("초기 화면으로 돌아갑니다.", "ID_RESET");
    }

    private void startCameraPreview() {
        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
        PreviewView viewFinder = findViewById(R.id.viewFinder);
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        int action = event.getActionMasked();  // 여기를 수정하였습니다.
        if (action == MotionEvent.ACTION_DOWN) {
            yStart = event.getY();
        } else if (action == MotionEvent.ACTION_UP) {
            float yEnd = event.getY();
            if (Math.abs(yEnd - yStart) > 100) {
                navigateToMainActivity();
            }
        }
        return super.onTouchEvent(event);
    }


    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
