package com.ktm.capstone;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

public class FeaturesPagerAdapter extends PagerAdapter {
    private Context mContext;
    private int[] imageIds = new int[] {
            R.drawable.object_recognition,
            R.drawable.text_to_speech,
            R.drawable.money_recognition,
            R.drawable.barcode_recognition,
            R.drawable.color_recognition,
            R.drawable.voice_config
    };
    private String[] descriptions = new String[] {
            "객체 인식",
            "텍스트 음성 변환",
            "지폐 인식",
            "바코드 인식",
            "색상 인식",
            "음성 응답의 속도나 톤 조절 기능"
    };

    public FeaturesPagerAdapter(Context context) {
        mContext = context;
    }

    @Override
    public int getCount() {
        return imageIds.length;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        ImageView imageView = new ImageView(mContext);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setImageResource(imageIds[position]);
        container.addView(imageView, 0);
        return imageView;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((ImageView) object);
    }

    public String getDescription(int position) {
        return descriptions[position];
    }
}
