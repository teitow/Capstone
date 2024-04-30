package com.ktm.capstone;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
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
import android.content.Intent;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class TextToSpeechActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private Camera camera;
    private Preview preview;
    private ImageCapture imageCapture;
    private TextRecognizer recognizer;
    private boolean isTTSInitialized = false;
    private MediaPlayer cameraClickSound;
    private ImageView imageView;
    private GestureDetector gestureDetector;
    private float yStart = 0;
    private boolean isImageDisplayed = false;
    private ProcessCameraProvider cameraProvider;
    private CameraSelector cameraSelector;

    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.KOREAN);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Korean language is not supported.");
            } else {
                isTTSInitialized = true;
                speak("사진을 찍어주세요.", "ID_INITIAL");
            }
        } else {
            Log.e("TTS", "Initialization failed.");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_to_speech);

        tts = new TextToSpeech(this, this);
        recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        imageView = findViewById(R.id.imageView);
        cameraClickSound = MediaPlayer.create(this, R.raw.camera_click);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!isImageDisplayed) {
                    captureAndAnalyze();
                } else {
                    resetToInitialView(); // 초기 화면으로 리셋
                }
                return true;
            }
        });

        initializeCamera();
        setupTTS();
    }

    private void setupTTS() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // Nothing to do here
            }

            @Override
            public void onDone(String utteranceId) {
                if ("ID_TEXT_READ".equals(utteranceId)) {
                    tts.playSilentUtterance(800, TextToSpeech.QUEUE_ADD, null); // 1.5초 대기 후 메시지 재생
                    speakGuidance("다른 문장을 읽고 싶다면 화면을 두 번 누르세요. 메인 화면으로 돌아가고 싶다면 화면을 상하로 슬라이드 해주세요.", "ID_GUIDANCE");
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

    private void speakGuidance(String text, String utteranceId) {
        if (isTTSInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId);
        }
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

    private void captureAndAnalyze() {
        imageView.setVisibility(View.GONE); // 새 사진을 찍기 전에 ImageView를 숨깁니다.
        String fileName = "pic_" + System.currentTimeMillis() + ".jpg"; // 타임스탬프를 이용해 고유한 파일 이름 생성
        File photoFile = new File(getExternalFilesDir(null), fileName);
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                cameraClickSound.start();
                Uri savedUri = Uri.fromFile(photoFile); // 새로 저장된 사진의 URI 사용
                imageView.setImageURI(savedUri);
                imageView.setVisibility(View.VISIBLE); // 사진을 저장하고 ImageView를 다시 표시합니다.
                isImageDisplayed = true; // 이미지가 표시되었다고 상태 업데이트
                Log.d("CameraXApp", "Photo saved successfully: " + savedUri);
                try {
                    processImage(InputImage.fromFilePath(TextToSpeechActivity.this, savedUri));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("CameraXApp", "Photo capture failed: " + exception.getMessage(), exception);
                speak("사진 촬영에 실패했습니다.", "ID_ERROR");
            }
        });
    }

    private void processImage(InputImage image) {
        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    if (visionText.getText().trim().isEmpty()) {
                        speak("문자가 없습니다. 다시 찍으려면 화면을 두 번 누르세요.", "ID_ERROR");
                    } else {
                        speak("인식된 텍스트: " + visionText.getText(), "ID_TEXT_READ");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("TextRecognition", "Failed to process image: " + e.getMessage(), e);
                    speak("텍스트 인식에 실패했습니다.", "ID_ERROR");
                });
    }

    private void resetToInitialView() {
        // 이미지 뷰를 숨김으로써 이미지가 보이지 않도록 설정
        imageView.setVisibility(View.GONE);
        // 이미지 표시 상태를 false로 설정
        isImageDisplayed = false;
        // 카메라 프리뷰를 재시작하여 사용자가 다시 사진을 찍을 수 있게 준비
        startCameraPreview();
        speak("초기 화면으로 돌아갑니다.", "ID_RESET");
    }

    private void startCameraPreview() {
        // 모든 카메라 사용 사례를 해제
        cameraProvider.unbindAll();
        // 카메라 프리뷰를 다시 설정
        camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
        // 카메라 미리보기가 화면에 보이도록 설정
        PreviewView viewFinder = findViewById(R.id.viewFinder);
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            yStart = event.getY();
        } else if (action == MotionEvent.ACTION_UP) {
            float yEnd = event.getY();
            if (Math.abs(yEnd - yStart) > 100) { // 슬라이드 거리가 100픽셀 이상이면 MainActivity로 이동
                navigateToMainActivity();
            }
        }
        return super.onTouchEvent(event);
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish(); // 현재 액티비티 종료
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
