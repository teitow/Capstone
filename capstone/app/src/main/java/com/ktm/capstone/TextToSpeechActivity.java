package com.ktm.capstone;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.InputStream;
import java.util.Locale;

public class TextToSpeechActivity extends Activity {
    private TextToSpeech tts;
    private TessBaseAPI mTess;
    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_to_speech);

        imageUri = getIntent().getData();
        initializeOCR();
        initializeTTS();

        if (imageUri != null) {
            processImage(imageUri);
        } else {
            speak("처리할 이미지가 없습니다.", TextToSpeech.QUEUE_FLUSH);
        }
    }

    private void initializeOCR() {
        String datapath = getFilesDir() + "/tesseract/";
        File tessdata = new File(datapath + "tessdata/");
        if (!tessdata.exists() && !tessdata.mkdirs()) {
            speak("테스데이터 디렉토리 생성 오류.", TextToSpeech.QUEUE_ADD);
            return;
        }

        copyTessDataFiles(tessdata);

        mTess = new TessBaseAPI();
        if (!mTess.init(datapath, "kor+eng")) {
            speak("OCR 엔진 초기화 실패.", TextToSpeech.QUEUE_ADD);
        }
    }

    private void copyTessDataFiles(File tessdata) {
        try {
            String[] files = getAssets().list("tessdata");
            for (String fileName : files) {
                File tessFile = new File(tessdata, fileName);
                if (!tessFile.exists()) {
                    InputStream in = getAssets().open("tessdata/" + fileName);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(tessFile);

                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) > 0) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.close();
                    in.close();
                }
            }
        } catch (Exception e) {
            speak("테스데이터 파일 복사 실패.", TextToSpeech.QUEUE_ADD);
        }
    }

    private void initializeTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.KOREAN);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    speak("한국어 TTS가 지원되지 않습니다. 영어로 전환합니다.", TextToSpeech.QUEUE_FLUSH);
                    tts.setLanguage(Locale.US);
                }
                updateTTS();
            } else {
                speak("텍스트 음성 변환 엔진 초기화 실패.", TextToSpeech.QUEUE_FLUSH);
            }
        });
    }

    private void updateTTS() {
        float pitch = getSharedPreferences("TTSConfig", MODE_PRIVATE).getFloat("pitch", 1.0f);
        float speed = getSharedPreferences("TTSConfig", MODE_PRIVATE).getFloat("speed", 1.0f);
        tts.setPitch(pitch);
        tts.setSpeechRate(speed);
    }

    private void processImage(Uri imageUri) {
        try {
            InputStream imageStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
            if (bitmap != null) {
                mTess.setImage(bitmap);
                final String extractedText = mTess.getUTF8Text();
                speak(extractedText.isEmpty() ? "이미지 내 텍스트를 찾을 수 없습니다." : extractedText, TextToSpeech.QUEUE_FLUSH);
            } else {
                speak("이미지 로드 실패.", TextToSpeech.QUEUE_FLUSH);
            }
        } catch (Exception e) {
            speak("이미지 처리 오류.", TextToSpeech.QUEUE_FLUSH);
        }
    }

    private void speak(String text, int queueMode) {
        if (tts != null) {
            tts.speak(text, queueMode, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (mTess != null) {
            mTess.end();
        }
        super.onDestroy();
    }
}
