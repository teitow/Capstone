package com.ktm.capstone

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OptionsAdapter(private val options: List<String>, private val isDarkMode: Boolean) :
    RecyclerView.Adapter<OptionsAdapter.OptionViewHolder>() {

    private var selectedPosition = 0

    inner class OptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val optionTextView: TextView = itemView.findViewById(R.id.optionTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_option, parent, false)
        return OptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        holder.optionTextView.text = options[position]
        holder.optionTextView.setBackgroundColor(
            if (position == selectedPosition) Color.MAGENTA else if (isDarkMode) Color.BLACK else Color.WHITE
        )
        holder.optionTextView.setTextColor(
            if (isDarkMode) Color.WHITE else Color.BLACK
        )
        holder.optionTextView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(previousPosition)
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int {
        return options.size
    }

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }
}
