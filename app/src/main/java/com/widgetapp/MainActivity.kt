package com.widgetapp

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var widgetContainer: FrameLayout
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var widgetHost: WidgetHost
    private var currentWidgetView: ResizableAppWidgetHostView? = null
    private val hostId = 1024
    private val requestWidgetPicker = 100
    private val requestBindWidget = 101
    private val requestConfigureWidget = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        widgetContainer = findViewById(R.id.widget_container)
        val fabAddWidget: FloatingActionButton = findViewById(R.id.fab_add_widget)
        val fabSmaller: FloatingActionButton = findViewById(R.id.fab_smaller)
        val fabBigger: FloatingActionButton = findViewById(R.id.fab_bigger)
        val sizeInfo: TextView = findViewById(R.id.size_info)
        val controlPanel = findViewById<ViewGroup>(R.id.control_panel)

        appWidgetManager = AppWidgetManager.getInstance(this)
        widgetHost = WidgetHost(this, hostId)

        fabAddWidget.setOnClickListener {
            openWidgetPicker()
        }

        fabSmaller.setOnClickListener {
            currentWidgetView?.makeSmaller()
            updateSizeInfo()
        }

        fabBigger.setOnClickListener {
            currentWidgetView?.makeBigger()
            updateSizeInfo()
        }
    }

    override fun onResume() {
        super.onResume()
        widgetHost.startListening()
    }

    override fun onPause() {
        super.onPause()
        widgetHost.stopListening()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Widget and layout will automatically adapt to new orientation
        // since the activity is not destroyed/recreated
        currentWidgetView?.let { widget ->
            // Refresh the widget to ensure it adapts to new screen dimensions
            widget.post {
                widget.refreshWidget()
                updateSizeInfo()
            }
        }
    }

    private fun openWidgetPicker() {
        val intent = Intent(this, WidgetPickerActivity::class.java)
        startActivityForResult(intent, requestWidgetPicker)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            requestWidgetPicker -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val componentName = data.getParcelableExtra<ComponentName>("widget_provider")
                    componentName?.let { addWidget(it) }
                }
            }
            requestBindWidget -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    if (widgetId != -1) {
                        configureAndAddWidget(widgetId)
                    }
                }
            }
            requestConfigureWidget -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    if (widgetId != -1) {
                        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)
                        createAndAddWidgetView(widgetId, appWidgetInfo)
                    }
                }
            }
        }
    }

    private fun addWidget(componentName: ComponentName) {
        val widgetId = widgetHost.allocateAppWidgetId()
        
        val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, componentName)
        if (canBind) {
            configureAndAddWidget(widgetId)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, componentName)
            }
            startActivityForResult(intent, requestBindWidget)
        }
    }

    private fun configureAndAddWidget(widgetId: Int) {
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)
        
        if (appWidgetInfo?.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = appWidgetInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            startActivityForResult(intent, requestConfigureWidget)
        } else {
            createAndAddWidgetView(widgetId, appWidgetInfo)
        }
    }

    private fun createAndAddWidgetView(widgetId: Int, appWidgetInfo: AppWidgetProviderInfo?) {
        // Clean up existing widget if any
        currentWidgetView?.let {
            widgetHost.deleteAppWidgetId(it.appWidgetId)
        }
        
        val hostView = widgetHost.createView(this, widgetId, appWidgetInfo) as ResizableAppWidgetHostView
        
        widgetContainer.removeAllViews()
        
        // Get actual widget size requirements (already in pixels)
        val minWidth = appWidgetInfo?.minWidth ?: 280
        val minHeight = appWidgetInfo?.minHeight ?: 140
        val minResizeWidth = appWidgetInfo?.minResizeWidth ?: minWidth  
        val minResizeHeight = appWidgetInfo?.minResizeHeight ?: minHeight
        
        // Debug logging
        android.util.Log.d("WidgetApp", "Widget: ${appWidgetInfo?.loadLabel(packageManager)}")
        android.util.Log.d("WidgetApp", "minWidth: $minWidth, minHeight: $minHeight")
        android.util.Log.d("WidgetApp", "minResizeWidth: $minResizeWidth, minResizeHeight: $minResizeHeight")
        
        // Initialize the widget view with proper constraints
        hostView.initializeWidget(
            appWidgetInfo,
            minWidth,
            minHeight,
            minResizeWidth,
            minResizeHeight
        )
        
        val layoutParams = FrameLayout.LayoutParams(
            minWidth,  // Use actual widget width instead of WRAP_CONTENT
            minHeight  // Use actual widget height instead of WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }
        
        widgetContainer.addView(hostView, layoutParams)
        currentWidgetView = hostView
        
        findViewById<ViewGroup>(R.id.control_panel).visibility = ViewGroup.VISIBLE
        
        // Start listening after the view is added
        widgetHost.startListening()
        
        // Post to ensure the widget is properly laid out
        hostView.post {
            hostView.refreshWidget()
            updateSizeInfo()
        }
    }
    
    private fun updateSizeInfo() {
        val sizeInfo = findViewById<TextView>(R.id.size_info)
        currentWidgetView?.let { widget ->
            val info = widget.getSizeInfo()
            if (info.isNotEmpty()) {
                sizeInfo.text = info
                sizeInfo.visibility = ViewGroup.VISIBLE
            }
        }
    }
}