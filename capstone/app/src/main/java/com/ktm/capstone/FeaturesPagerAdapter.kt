package com.ktm.capstone

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.viewpager.widget.PagerAdapter

class FeaturesPagerAdapter(private val mContext: Context) : PagerAdapter() {

    // 기존 라이트 모드 이미지 배열
    private val imageIdsLight = intArrayOf(
        R.drawable.object_recognition,
        R.drawable.text_to_speech,
        R.drawable.weather_recognition,
        R.drawable.barcode_recognition,
        R.drawable.color_recognition,
        R.drawable.config
    )

    // 기존 다크 모드 이미지 배열
    private val imageIdsDark = intArrayOf(
        R.drawable.object_recognition_dark,
        R.drawable.text_to_speech_dark,
        R.drawable.weather_recognition_dark,
        R.drawable.barcode_recognition_dark,
        R.drawable.color_recognition_dark,
        R.drawable.config_dark
    )

    // 저시각자 모드 이미지 배열 추가
    private val imageIdsLowVision = intArrayOf(
        R.drawable.object_recognition_low_vision,
        R.drawable.text_to_speech_low_vision,
        R.drawable.weather_recognition_low_vision,
        R.drawable.barcode_recognition_low_vision,
        R.drawable.color_recognition_low_vision,
        R.drawable.config_low_vision
    )

    override fun getCount(): Int {
        return imageIdsLight.size // 모든 모드에서 동일한 수의 페이지가 있음
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val layout = LinearLayout(mContext)
        layout.orientation = LinearLayout.VERTICAL
        layout.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // 다크 모드 또는 저시각자 모드 여부에 따라 배경색과 이미지를 설정
        val sharedPref = mContext.getSharedPreferences("ThemePref", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("DARK_MODE", false)
        val isLowVisionMode = sharedPref.getBoolean("LOW_VISION_MODE", false)

        // 모드에 따라 이미지 배열을 선택
        val imageIds = when {
            isLowVisionMode -> imageIdsLowVision
            isDarkMode -> imageIdsDark
            else -> imageIdsLight
        }

        // **저시각자 모드일 때 라이트 모드 기반으로 배경색을 흰색으로 설정**
        layout.setBackgroundColor(if (isLowVisionMode) Color.WHITE else if (isDarkMode) Color.BLACK else Color.WHITE)

        // 이미지 설정
        val imageView = ImageView(mContext)
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageView.setImageResource(imageIds[position])
        layout.addView(imageView)

        container.addView(layout, 0)
        return layout
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    // 각 페이지에 대한 설명 제공
    fun getDescription(position: Int): String {
        val descriptions = arrayOf(
            "객체 인식",
            "텍스트 음성 변환",
            "날씨 확인",
            "바코드 스캔",
            "색상 인식",
            "앱 환경 설정"
        )
        return descriptions[position]
    }
}
