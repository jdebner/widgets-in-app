package com.widgetapp

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class WidgetStackPersistence(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "widget_stack_prefs", Context.MODE_PRIVATE
    )
    private val appWidgetManager = AppWidgetManager.getInstance(context)
    
    companion object {
        private const val KEY_WIDGET_STACK = "widget_stack"
        private const val KEY_CURRENT_INDEX = "current_index"
        private const val KEY_FULL_SIZE_MODE = "full_size_mode"
    }
    
    fun saveWidgetStack(widgetStack: WidgetStack) {
        val widgets = widgetStack.getAllWidgets()
        val jsonArray = JSONArray()
        
        for (widget in widgets) {
            val jsonObject = JSONObject().apply {
                put("widgetId", widget.widgetId)
                put("label", widget.label)
                    
                // Save provider info if available
                widget.appWidgetInfo?.provider?.let { provider ->
                    put("packageName", provider.packageName)
                    put("className", provider.className)
                }
                
                // Save pending component info if available
                widget.pendingComponent?.let { component ->
                    put("pendingPackageName", component.packageName)
                    put("pendingClassName", component.className)
                }
            }
            jsonArray.put(jsonObject)
        }
        
        prefs.edit()
            .putString(KEY_WIDGET_STACK, jsonArray.toString())
            .putInt(KEY_CURRENT_INDEX, widgetStack.getCurrentIndex())
            .putBoolean(KEY_FULL_SIZE_MODE, widgetStack.isFullSizeMode)
            .apply()
    }
    
    fun loadWidgetStack(widgetStack: WidgetStack): List<WidgetStackItem> {
        val jsonString = prefs.getString(KEY_WIDGET_STACK, null) ?: return emptyList()
        val loadedWidgets = mutableListOf<WidgetStackItem>()
        
        try {
            val jsonArray = JSONArray(jsonString)
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val widgetId = jsonObject.getInt("widgetId")
                val label = jsonObject.getString("label")
                
                // Try to restore widget info
                var appWidgetInfo: android.appwidget.AppWidgetProviderInfo? = null
                var pendingComponent: ComponentName? = null
                
                // Check if widget still exists and is valid
                if (widgetId != -1 && isWidgetValid(widgetId)) {
                    appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)
                }
                
                // Check for pending component
                if (jsonObject.has("pendingPackageName") && jsonObject.has("pendingClassName")) {
                    pendingComponent = ComponentName(
                        jsonObject.getString("pendingPackageName"),
                        jsonObject.getString("pendingClassName")
                    )
                }
                
                // Only add if widget is valid or has pending component
                if (appWidgetInfo != null || pendingComponent != null) {
                    val stackItem = WidgetStackItem(
                        widgetId = widgetId,
                        appWidgetInfo = appWidgetInfo,
                        label = label,
                        pendingComponent = pendingComponent
                    )
                    loadedWidgets.add(stackItem)
                }
            }
            
            // Restore state
            val currentIndex = prefs.getInt(KEY_CURRENT_INDEX, 0)
            val fullSizeMode = prefs.getBoolean(KEY_FULL_SIZE_MODE, false)
            
            widgetStack.setCurrentIndex(if (currentIndex < loadedWidgets.size) currentIndex else 0)
            widgetStack.setFullSizeMode(fullSizeMode)
            
        } catch (e: Exception) {
            android.util.Log.e("WidgetApp", "Failed to load widget stack: ${e.message}", e)
        }
        
        return loadedWidgets
    }
    
    private fun isWidgetValid(widgetId: Int): Boolean {
        return try {
            val info = appWidgetManager.getAppWidgetInfo(widgetId)
            info != null
        } catch (e: Exception) {
            false
        }
    }
    
    fun clearWidgetStack() {
        prefs.edit()
            .remove(KEY_WIDGET_STACK)
            .remove(KEY_CURRENT_INDEX)
            .remove(KEY_FULL_SIZE_MODE)
            .apply()
    }
    
    fun hasPersistedStack(): Boolean {
        return prefs.contains(KEY_WIDGET_STACK)
    }
}