package com.ktm.capstone;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.GestureDetector;
import android.view.MotionEvent;
import java.util.Locale;

public class VoiceSettingsActivity extends Activity implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {
    private TextToSpeech tts;
    private GestureDetector gestureDetector;
    private float pitch = 1.0f; // Default pitch
    private float speed = 1.0f; // Default speed
    private boolean ttsInitialized = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_settings);

        prefs = getSharedPreferences("TTSConfig", Context.MODE_PRIVATE);
        pitch = prefs.getFloat("pitch", 1.0f);
        speed = prefs.getFloat("speed", 1.0f);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.KOREAN);
                tts.setPitch(pitch);
                tts.setSpeechRate(speed);
                ttsInitialized = true;
                tts.speak("화면을 좌우로 슬라이드 해 속도를 조절하고 상하로 슬라이드해서 톤을 조절해주세요. 기본 값은 1입니다. 조절이 완료되면 화면을 두번 누르면 메인화면으로 돌아갑니다.", TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        gestureDetector = new GestureDetector(this, this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (!ttsInitialized) return false;

        SharedPreferences.Editor editor = prefs.edit();
        if (Math.abs(velocityX) > Math.abs(velocityY)) {
            if (velocityX > 0) {
                speed += 0.3f;
            } else {
                speed -= 0.3f;
            }
            tts.setSpeechRate(speed);
            editor.putFloat("speed", speed);
        } else {
            if (velocityY > 0) {
                pitch -= 0.3f;
            } else {
                pitch += 0.3f;
            }
            tts.setPitch(pitch);
            editor.putFloat("pitch", pitch);
        }
        editor.apply();
        tts.speak(String.format(Locale.KOREA, "현재 속도는 %.1f, 톤은 %.1f입니다.", speed, pitch), TextToSpeech.QUEUE_FLUSH, null, null);
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {}

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {}

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return false;
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
