package com.widgetapp

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
    private val requestPermissions = 200
    
    // Common widget permissions that need runtime approval
    private val widgetPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_SMS
    ).let { permissions ->
        // Add media permissions for API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions + arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            permissions
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Install AppCompat compatibility hooks before any widget operations
        AppCompatRemoteViewsHook.installCompatibilityHooks(this)
        
        setContentView(R.layout.activity_main)

        widgetContainer = findViewById(R.id.widget_container)
        val fabAddWidget: FloatingActionButton = findViewById(R.id.fab_add_widget)
        val fabSmaller: FloatingActionButton = findViewById(R.id.fab_smaller)
        val fabBigger: FloatingActionButton = findViewById(R.id.fab_bigger)
        val fabScaleDown: FloatingActionButton = findViewById(R.id.fab_scale_down)
        val fabScaleUp: FloatingActionButton = findViewById(R.id.fab_scale_up)
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

        fabScaleDown.setOnClickListener {
            currentWidgetView?.scaleDown()
            updateSizeInfo()
        }

        fabScaleUp.setOnClickListener {
            currentWidgetView?.scaleUp()
            updateSizeInfo()
        }
        
        // Request permissions needed for widgets
        requestWidgetPermissions()
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
        
        android.util.Log.d("WidgetApp", "Creating widget view for: ${appWidgetInfo?.loadLabel(packageManager)}")
        android.util.Log.d("WidgetApp", "Widget provider: ${appWidgetInfo?.provider}")
        android.util.Log.d("WidgetApp", "Widget ID: $widgetId")
        
        try {
            val hostView = widgetHost.createView(this, widgetId, appWidgetInfo) as ResizableAppWidgetHostView
            android.util.Log.d("WidgetApp", "Widget host view created successfully")
        
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
            android.util.Log.d("WidgetApp", "Refreshing widget...")
            hostView.refreshWidget()
            updateSizeInfo()
            android.util.Log.d("WidgetApp", "Widget setup complete")
        }
        
        } catch (e: Exception) {
            android.util.Log.e("WidgetApp", "Failed to create widget: ${e.message}", e)
            
            // Clean up failed widget
            widgetHost.deleteAppWidgetId(widgetId)
            
            // Show detailed error
            val sizeInfo = findViewById<TextView>(R.id.size_info)
            sizeInfo.text = "Couldn't add widget\nWidget: ${appWidgetInfo?.loadLabel(packageManager) ?: "Unknown"}\nError: ${e.message}\n\nThis widget may use AppCompat views that aren't compatible with RemoteViews."
            sizeInfo.visibility = ViewGroup.VISIBLE
            findViewById<ViewGroup>(R.id.control_panel).visibility = ViewGroup.GONE
        }
    }
    
    private fun updateSizeInfo() {
        val sizeInfo = findViewById<TextView>(R.id.size_info)
        currentWidgetView?.let { widget ->
            val currentSizeInfo = widget.getSizeInfo()
            val constraintsInfo = widget.getWidgetConstraints()
            val fullInfo = "$currentSizeInfo\n\n$constraintsInfo"
            
            if (currentSizeInfo.isNotEmpty()) {
                sizeInfo.text = fullInfo
                sizeInfo.visibility = ViewGroup.VISIBLE
            }
        }
    }
    
    private fun requestWidgetPermissions() {
        val missingPermissions = widgetPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                requestPermissions
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            requestPermissions -> {
                val deniedPermissions = permissions.filterIndexed { index, _ ->
                    grantResults[index] != PackageManager.PERMISSION_GRANTED
                }
                
                if (deniedPermissions.isNotEmpty()) {
                    android.util.Log.w("WidgetApp", "Some permissions denied: ${deniedPermissions.joinToString()}")
                    android.util.Log.w("WidgetApp", "Some widgets may not work correctly without these permissions")
                } else {
                    android.util.Log.d("WidgetApp", "All widget permissions granted")
                }
            }
        }
    }
}