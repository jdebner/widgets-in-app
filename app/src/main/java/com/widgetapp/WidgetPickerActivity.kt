package com.widgetapp

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class WidgetPickerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GroupedWidgetAdapter
    private lateinit var appWidgetManager: AppWidgetManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_picker)

        appWidgetManager = AppWidgetManager.getInstance(this)
        recyclerView = findViewById(R.id.recycler_widgets)
        
        setupRecyclerView()
        loadAvailableWidgets()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = GroupedWidgetAdapter { widgetInfo ->
            selectWidget(widgetInfo)
        }
        recyclerView.adapter = adapter
    }

    private fun loadAvailableWidgets() {
        // Try multiple methods to discover widgets
        val allWidgets = mutableListOf<android.appwidget.AppWidgetProviderInfo>()
        
        // Method 1: Standard getInstalledProviders
        val standardWidgets = appWidgetManager.installedProviders
        allWidgets.addAll(standardWidgets)
        android.util.Log.d("WidgetApp", "Standard getInstalledProviders found: ${standardWidgets.size}")
        
        // Method 2: Try getInstalledProvidersForProfile for current user
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val userHandle = android.os.Process.myUserHandle()
                val profileWidgets = appWidgetManager.getInstalledProvidersForProfile(userHandle)
                
                // Add any widgets not already in the list
                for (widget in profileWidgets) {
                    if (!allWidgets.any { it.provider == widget.provider }) {
                        allWidgets.add(widget)
                    }
                }
                android.util.Log.d("WidgetApp", "Profile-based discovery added: ${profileWidgets.size - standardWidgets.size} additional widgets")
            }
        } catch (e: Exception) {
            android.util.Log.d("WidgetApp", "Profile-based discovery failed: ${e.message}")
        }
        
        // Method 3: Query package manager for widget providers
        try {
            val intent = android.content.Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            val packageWidgets = packageManager.queryBroadcastReceivers(intent, 0)
            android.util.Log.d("WidgetApp", "PackageManager found ${packageWidgets.size} widget receivers")
            
            for (resolveInfo in packageWidgets) {
                android.util.Log.d("WidgetApp", "PM Widget: ${resolveInfo.activityInfo.packageName}.${resolveInfo.activityInfo.name}")
            }
        } catch (e: Exception) {
            android.util.Log.d("WidgetApp", "PackageManager query failed: ${e.message}")
        }
        
        android.util.Log.d("WidgetApp", "Total unique widgets found: ${allWidgets.size}")
        
        // Debug log all found widgets
        allWidgets.forEachIndexed { index, provider ->
            try {
                val appInfo = packageManager.getApplicationInfo(provider.provider.packageName, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                android.util.Log.d("WidgetApp", "Widget $index: $appName (${provider.provider.packageName}.${provider.provider.className}) - ${provider.minWidth}x${provider.minHeight}")
            } catch (e: Exception) {
                android.util.Log.d("WidgetApp", "Widget $index: ${provider.provider.packageName}.${provider.provider.className} (failed to get app name) - ${provider.minWidth}x${provider.minHeight}")
            }
        }
        
        // Very minimal filtering - only exclude widgets that definitely won't work
        val availableWidgets = allWidgets.distinct().filter { provider ->
            try {
                // Basic validation: package exists
                packageManager.getPackageInfo(provider.provider.packageName, 0)
                // Allow widgets with minWidth > 0, even if minHeight is 0 (some widgets report this)
                val isValid = provider.minWidth > 0
                
                if (!isValid) {
                    android.util.Log.d("WidgetApp", "Filtered out: ${provider.provider.packageName} (invalid size: ${provider.minWidth}x${provider.minHeight})")
                }
                
                isValid
            } catch (e: Exception) {
                android.util.Log.d("WidgetApp", "Filtered out: ${provider.provider.packageName} (exception: ${e.message})")
                // If we can't get package info, exclude it
                false
            }
        }
        
        android.util.Log.d("WidgetApp", "Available widgets after filtering: ${availableWidgets.size}")
        
        // Sort by app name for better organization
        val sortedWidgets = availableWidgets.sortedBy { provider ->
            try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(provider.provider.packageName, 0)
                ).toString()
            } catch (e: Exception) {
                provider.provider.packageName
            }
        }
        
        adapter.updateWidgets(sortedWidgets, packageManager)
    }

    private fun selectWidget(widgetInfo: AppWidgetProviderInfo) {
        val resultIntent = Intent().apply {
            putExtra("widget_provider", widgetInfo.provider)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}