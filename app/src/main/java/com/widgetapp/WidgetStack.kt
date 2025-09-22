package com.widgetapp

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import java.io.Serializable

data class WidgetStackItem(
    val widgetId: Int,
    val appWidgetInfo: AppWidgetProviderInfo?,
    val label: String,
    var pendingComponent: ComponentName? = null
)

// Serializable version for passing through Intents
data class SerializableWidgetStackItem(
    val widgetId: Int,
    val label: String,
    val packageName: String?,
    val className: String?,
    val pendingPackageName: String?,
    val pendingClassName: String?
) : Serializable {
    
    companion object {
        fun fromWidgetStackItem(item: WidgetStackItem): SerializableWidgetStackItem {
            return SerializableWidgetStackItem(
                widgetId = item.widgetId,
                label = item.label,
                packageName = item.appWidgetInfo?.provider?.packageName,
                className = item.appWidgetInfo?.provider?.className,
                pendingPackageName = item.pendingComponent?.packageName,
                pendingClassName = item.pendingComponent?.className
            )
        }
        
        fun toWidgetStackItem(serializable: SerializableWidgetStackItem, appWidgetInfo: AppWidgetProviderInfo?): WidgetStackItem {
            val pendingComponent = if (serializable.pendingPackageName != null && serializable.pendingClassName != null) {
                ComponentName(serializable.pendingPackageName, serializable.pendingClassName)
            } else null
            
            return WidgetStackItem(
                widgetId = serializable.widgetId,
                appWidgetInfo = appWidgetInfo,
                label = serializable.label,
                pendingComponent = pendingComponent
            )
        }
    }
}

class WidgetStack {
    private val widgets = mutableListOf<WidgetStackItem>()
    private var currentIndex = 0
    var isFullSizeMode = false
        private set
    
    fun addWidget(item: WidgetStackItem) {
        widgets.add(item)
    }
    
    fun removeWidget(index: Int): WidgetStackItem? {
        if (index < 0 || index >= widgets.size) return null
        
        val removed = widgets.removeAt(index)
        
        // Adjust current index if needed
        if (currentIndex >= widgets.size && widgets.isNotEmpty()) {
            currentIndex = widgets.size - 1
        } else if (widgets.isEmpty()) {
            currentIndex = 0
        }
        
        return removed
    }
    
    fun moveWidget(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex < 0 || fromIndex >= widgets.size || 
            toIndex < 0 || toIndex >= widgets.size) {
            return false
        }
        
        val item = widgets.removeAt(fromIndex)
        widgets.add(toIndex, item)
        
        // Adjust current index if it was affected by the move
        when {
            currentIndex == fromIndex -> currentIndex = toIndex
            fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex--
            fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex++
        }
        
        return true
    }
    
    fun getCurrentWidget(): WidgetStackItem? {
        return if (widgets.isEmpty()) null else widgets.getOrNull(currentIndex)
    }
    
    fun getCurrentIndex(): Int = currentIndex
    
    fun getWidgetCount(): Int = widgets.size
    
    fun getAllWidgets(): List<WidgetStackItem> = widgets.toList()
    
    fun navigateNext(): Boolean {
        if (widgets.size <= 1) return false
        currentIndex = (currentIndex + 1) % widgets.size
        return true
    }
    
    fun navigatePrevious(): Boolean {
        if (widgets.size <= 1) return false
        currentIndex = if (currentIndex == 0) {
            widgets.size - 1
        } else {
            currentIndex - 1
        }
        return true
    }
    
    fun setCurrentIndex(index: Int): Boolean {
        if (index < 0 || index >= widgets.size) return false
        currentIndex = index
        return true
    }
    
    fun toggleFullSizeMode() {
        isFullSizeMode = !isFullSizeMode
    }
    
    fun setFullSizeMode(enabled: Boolean) {
        isFullSizeMode = enabled
    }
    
    fun isEmpty(): Boolean = widgets.isEmpty()
    
    fun clear() {
        widgets.clear()
        currentIndex = 0
        isFullSizeMode = false
    }
}