package com.ktm.capstone

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LowVisionOptionAdapter(private val options: List<String>, private val isDarkMode: Boolean) : RecyclerView.Adapter<LowVisionOptionAdapter.OptionViewHolder>() {

    private var selectedPosition = -1

    fun setSelectedPosition(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(previousPosition)
        notifyItemChanged(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_option_low_vision, parent, false)
        return OptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        holder.bind(options[position], position == selectedPosition, isDarkMode)
    }

    override fun getItemCount(): Int = options.size

    class OptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.optionTextView)

        fun bind(option: String, isSelected: Boolean, isDarkMode: Boolean) {
            textView.text = option
            textView.setBackgroundColor(if (isSelected) Color.MAGENTA else if (isDarkMode) Color.BLACK else Color.WHITE)
            textView.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
            textView.setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }
}
