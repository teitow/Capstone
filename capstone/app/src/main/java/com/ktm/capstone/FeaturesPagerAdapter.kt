package com.ktm.capstone

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.viewpager.widget.PagerAdapter

class FeaturesPagerAdapter(private val mContext: Context) : PagerAdapter() {
    private val imageIds = intArrayOf(
        R.drawable.object_recognition,
        R.drawable.text_to_speech,
        R.drawable.money_recognition,
        R.drawable.barcode_recognition,
        R.drawable.color_recognition,
        R.drawable.voice_config
    )
    private val descriptions = arrayOf(
        "객체 인식",
        "텍스트 음성 변환",
        "지폐 인식",
        "바코드 인식",
        "색상 인식",
        "음성 응답의 속도나 톤 조절 기능"
    )

    override fun getCount(): Int {
        return imageIds.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val imageView = ImageView(mContext)
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageView.setImageResource(imageIds[position])
        container.addView(imageView, 0)
        return imageView
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as ImageView)
    }

    fun getDescription(position: Int): String {
        return descriptions[position]
    }
}
