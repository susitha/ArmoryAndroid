package com.cenango.fetchcicg.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cenango.fetchcicg.R

/**
 * Shared row model + adapter for [CategoryPickerActivity] (grouped: headers +
 * selectable subcategories) and [UserPickerActivity] (flat: no headers).
 */
sealed class PickerRow {
    data class Header(val title: String) : PickerRow()
    data class Item<T>(val title: String, val value: T) : PickerRow()
}

class PickerAdapter(
    private val rows: List<PickerRow>,
    private val onItemClick: (Any) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is PickerRow.Header) TYPE_HEADER else TYPE_ITEM

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutRes = if (viewType == TYPE_HEADER) R.layout.item_picker_header else R.layout.item_picker_row
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return object : RecyclerView.ViewHolder(view) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val textView = holder.itemView as TextView
        when (val row = rows[position]) {
            is PickerRow.Header -> {
                textView.text = row.title
                textView.setOnClickListener(null)
            }
            is PickerRow.Item<*> -> {
                textView.text = row.title
                textView.setOnClickListener { onItemClick(row.value!!) }
            }
        }
    }
}
