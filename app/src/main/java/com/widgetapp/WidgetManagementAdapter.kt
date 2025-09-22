package com.widgetapp

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class WidgetManagementAdapter(
    private val onItemMoved: (fromPosition: Int, toPosition: Int) -> Unit,
    private val onItemDeleted: (position: Int) -> Unit
) : RecyclerView.Adapter<WidgetManagementAdapter.ViewHolder>() {

    private val widgets = mutableListOf<SerializableWidgetStackItem>()
    private var itemTouchHelper: ItemTouchHelper? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
        val widgetName: TextView = view.findViewById(R.id.widget_name)
        val widgetPackage: TextView = view.findViewById(R.id.widget_package)
        val deleteWidget: ImageView = view.findViewById(R.id.delete_widget)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.widget_management_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val widget = widgets[position]
        
        holder.widgetName.text = widget.label
        holder.widgetPackage.text = widget.packageName 
            ?: widget.pendingPackageName 
            ?: "Unknown package"
        
        
        holder.deleteWidget.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                widgets.removeAt(currentPosition)
                notifyItemRemoved(currentPosition)
                onItemDeleted(currentPosition)
            }
        }
        
        // Set up drag handle
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                itemTouchHelper?.startDrag(holder)
                true
            } else {
                false
            }
        }
        
    }

    override fun getItemCount(): Int = widgets.size

    fun updateSerializableWidgets(newWidgets: List<SerializableWidgetStackItem>) {
        widgets.clear()
        widgets.addAll(newWidgets)
        notifyDataSetChanged()
    }
    
    fun addWidget(widget: SerializableWidgetStackItem) {
        widgets.add(widget)
        notifyItemInserted(widgets.size - 1)
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        Collections.swap(widgets, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        onItemMoved(fromPosition, toPosition)
    }

    fun getSerializableWidgets(): List<SerializableWidgetStackItem> = widgets.toList()
    
    fun setItemTouchHelper(itemTouchHelper: ItemTouchHelper) {
        this.itemTouchHelper = itemTouchHelper
    }
}