package com.ktm.capstone;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.view.GestureDetector;
import android.view.MotionEvent;
import java.io.IOException;
import java.util.Locale;
import com.googlecode.tesseract.android.TessBaseAPI;

public class TextToSpeechActivity extends Activity implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {
    private TextToSpeech tts;
    private GestureDetector gestureDetector;
    private TessBaseAPI tessBaseAPI;
    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_to_speech);

        gestureDetector = new GestureDetector(this, this);
        tessBaseAPI = new TessBaseAPI();
        tessBaseAPI.init(getFilesDir() + "/tesseract/", "kor+eng");

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.KOREAN);
            }
        });

        imageUri = getIntent().getParcelableExtra("data");
        if (imageUri != null) {
            try {
                processImage(imageUri);
            } catch (IOException e) {
                tts.speak("이미지를 처리하는 중 오류가 발생했습니다.", TextToSpeech.QUEUE_FLUSH, null, null);
            }
        } else {
            tts.speak("이미지 데이터를 받지 못했습니다.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void processImage(Uri uri) throws IOException {
        Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
        tessBaseAPI.setImage(bitmap);
        String extractedText = tessBaseAPI.getUTF8Text();

        if (extractedText.isEmpty()) {
            tts.speak("문자가 없습니다. 다시 카메라를 찍고 싶으면 화면을 두 번 눌러주세요.", TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            tts.speak(extractedText, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (Math.abs(velocityY) > Math.abs(velocityX)) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            return true;
        }
        return false;
    }

    @Override
    public boolean onDown(MotionEvent e) { return true; }
    @Override
    public void onLongPress(MotionEvent e) {}
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) { return false; }
    @Override
    public void onShowPress(MotionEvent e) {}
    @Override
    public boolean onSingleTapUp(MotionEvent e) { return false; }
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) { return false; }
    @Override
    public boolean onDoubleTapEvent(MotionEvent e) { return false; }
}
