package com.widgetapp

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.ContextThemeWrapper

class WidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {

    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView {
        android.util.Log.d("WidgetApp", "Creating view for widget: ${appWidget?.loadLabel(context.packageManager)}")
        
        // Try multiple context approaches for maximum compatibility
        val contexts = listOf(
            // 1. Non-AppCompat context (most likely to work)
            NonAppCompatContext(context),
            // 2. Plain Android themed context (avoids AppCompat inflation)
            ContextThemeWrapper(context, android.R.style.Theme_Material_Light),
            // 3. Original context (might work for some widgets)
            context,
            // 4. AppCompat themed context (last resort)
            ContextThemeWrapper(context, R.style.Theme_WidgetApp_AppCompat)
        )
        
        for ((index, testContext) in contexts.withIndex()) {
            try {
                android.util.Log.d("WidgetApp", "Trying context approach ${index + 1}")
                val hostView = ResizableAppWidgetHostView(testContext)
                android.util.Log.d("WidgetApp", "Successfully created widget host view with context ${index + 1}")
                return hostView
            } catch (e: Exception) {
                android.util.Log.w("WidgetApp", "Context approach ${index + 1} failed: ${e.message}")
            }
        }
        
        // Fallback to basic context if all themed approaches fail
        android.util.Log.w("WidgetApp", "All context approaches failed, using basic context")
        return ResizableAppWidgetHostView(context)
    }

    override fun onProviderChanged(appWidgetId: Int, appWidget: AppWidgetProviderInfo?) {
        super.onProviderChanged(appWidgetId, appWidget)
    }
}