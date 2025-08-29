package com.widgetapp

import android.appwidget.AppWidgetProviderInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class WidgetGroup(
    val appName: String,
    val packageName: String,
    val appIcon: android.graphics.drawable.Drawable?,
    val widgets: List<AppWidgetProviderInfo>,
    var isExpanded: Boolean = false
)

sealed class WidgetItem {
    data class GroupHeader(val group: WidgetGroup) : WidgetItem()
    data class WidgetChild(val widget: AppWidgetProviderInfo) : WidgetItem()
}

class GroupedWidgetAdapter(
    private val onWidgetClick: (AppWidgetProviderInfo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_GROUP_HEADER = 0
        private const val TYPE_WIDGET_ITEM = 1
    }

    private var groups = listOf<WidgetGroup>()
    private var displayItems = listOf<WidgetItem>()

    fun updateWidgets(widgets: List<AppWidgetProviderInfo>, packageManager: android.content.pm.PackageManager) {
        // Group widgets by package name (not app name to avoid duplicates)
        val groupedWidgets = widgets.groupBy { widget ->
            widget.provider.packageName
        }.mapValues { (packageName, widgetList) ->
            // Get app info once per package
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val appIcon = packageManager.getApplicationIcon(appInfo)
                Triple(appName, packageName, appIcon)
            } catch (e: Exception) {
                Triple(packageName, packageName, null)
            } to widgetList
        }

        groups = groupedWidgets.map { (_, pair) ->
            val (appInfo, widgetList) = pair
            WidgetGroup(
                appName = appInfo.first,
                packageName = appInfo.second,
                appIcon = appInfo.third,
                widgets = widgetList
            )
        }.sortedBy { it.appName }

        // Build display items
        rebuildDisplayItems()
        notifyDataSetChanged()
    }

    private fun rebuildDisplayItems() {
        displayItems = groups.flatMap { group ->
            val items = mutableListOf<WidgetItem>()
            items.add(WidgetItem.GroupHeader(group))
            
            if (group.isExpanded) {
                group.widgets.forEach { widget ->
                    items.add(WidgetItem.WidgetChild(widget))
                }
            }
            items
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is WidgetItem.GroupHeader -> TYPE_GROUP_HEADER
            is WidgetItem.WidgetChild -> TYPE_WIDGET_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        
        return when (viewType) {
            TYPE_GROUP_HEADER -> {
                val view = inflater.inflate(R.layout.widget_group_header, parent, false)
                GroupHeaderViewHolder(view)
            }
            TYPE_WIDGET_ITEM -> {
                val view = inflater.inflate(R.layout.widget_item, parent, false)
                WidgetViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is WidgetItem.GroupHeader -> {
                (holder as GroupHeaderViewHolder).bind(item.group)
            }
            is WidgetItem.WidgetChild -> {
                (holder as WidgetViewHolder).bind(item.widget)
            }
        }
    }

    override fun getItemCount(): Int = displayItems.size

    inner class GroupHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val widgetCount: TextView = itemView.findViewById(R.id.widget_count)
        private val expandArrow: TextView = itemView.findViewById(R.id.expand_arrow)

        fun bind(group: WidgetGroup) {
            group.appIcon?.let { appIcon.setImageDrawable(it) }
            appName.text = group.appName
            
            val countText = if (group.widgets.size == 1) {
                "1 widget"
            } else {
                "${group.widgets.size} widgets"
            }
            widgetCount.text = countText

            // Update arrow direction
            expandArrow.text = if (group.isExpanded) "▲" else "▼"

            itemView.setOnClickListener {
                // Toggle expanded state
                group.isExpanded = !group.isExpanded
                rebuildDisplayItems()
                notifyDataSetChanged()
            }
        }
    }

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
                
                // Show widget size info
                descriptionView.text = "${widgetInfo.minWidth}×${widgetInfo.minHeight}px"
                
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