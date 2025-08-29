package com.widgetapp

import android.appwidget.AppWidgetProviderInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WidgetAdapter(
    private val onWidgetClick: (AppWidgetProviderInfo) -> Unit
) : RecyclerView.Adapter<WidgetAdapter.WidgetViewHolder>() {

    private var widgets = listOf<AppWidgetProviderInfo>()

    fun updateWidgets(newWidgets: List<AppWidgetProviderInfo>) {
        widgets = newWidgets
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.widget_item, parent, false)
        return WidgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
        holder.bind(widgets[position])
    }

    override fun getItemCount(): Int = widgets.size

    inner class WidgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.widget_icon)
        private val labelView: TextView = itemView.findViewById(R.id.widget_label)
        private val descriptionView: TextView = itemView.findViewById(R.id.widget_description)

        fun bind(widgetInfo: AppWidgetProviderInfo) {
            val packageManager = itemView.context.packageManager
            
            try {
                // Load app icon instead of widget icon for consistency
                val appInfo = packageManager.getApplicationInfo(widgetInfo.provider.packageName, 0)
                iconView.setImageDrawable(packageManager.getApplicationIcon(appInfo))
                
                // Show widget label
                labelView.text = widgetInfo.loadLabel(packageManager)
                
                // Show app name instead of package name
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                descriptionView.text = appName
                
            } catch (e: Exception) {
                // Fallback to widget icon and package name
                iconView.setImageDrawable(widgetInfo.loadIcon(itemView.context, 0))
                labelView.text = widgetInfo.loadLabel(packageManager)
                descriptionView.text = widgetInfo.provider.packageName
            }

            itemView.setOnClickListener {
                onWidgetClick(widgetInfo)
            }
        }
    }
}