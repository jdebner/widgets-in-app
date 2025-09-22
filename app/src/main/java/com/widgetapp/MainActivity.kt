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
import android.hardware.display.DisplayManager
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var widgetStackView: WidgetStackView
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var widgetHost: WidgetHost
    private lateinit var widgetStackPersistence: WidgetStackPersistence
    private val hostId = 1024
    private val requestWidgetManagement = 100
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
        
        // Check if running on virtual display and adjust accordingly
        try {
            val displayManager = getSystemService(DisplayManager::class.java)
            val currentDisplay = windowManager.defaultDisplay
            val displayMetrics = android.util.DisplayMetrics()
            currentDisplay.getRealMetrics(displayMetrics)
            
            android.util.Log.d("WidgetApp", "Display - ID: ${currentDisplay.displayId}, name: ${currentDisplay.name}")
            android.util.Log.d("WidgetApp", "Display - Size: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}, density: ${displayMetrics.density}")
            
            // Check if this is a virtual display (non-default ID or specific characteristics)
            val isVirtualDisplay = currentDisplay.displayId != android.view.Display.DEFAULT_DISPLAY || 
                                 currentDisplay.name?.contains("overlay", ignoreCase = true) == true ||
                                 currentDisplay.name?.contains("virtual", ignoreCase = true) == true
            
            if (isVirtualDisplay) {
                android.util.Log.w("WidgetApp", "Running on virtual display - applying VD optimizations")
                configureForVirtualDisplay()
            }
        } catch (e: Exception) {
            android.util.Log.w("WidgetApp", "Could not determine display type: ${e.message}")
        }
        
        // Install AppCompat compatibility hooks before any widget operations
        AppCompatRemoteViewsHook.installCompatibilityHooks(this)
        
        setContentView(R.layout.activity_main)

        // Replace FrameLayout with WidgetStackView
        val container = findViewById<FrameLayout>(R.id.widget_container)
        widgetStackView = WidgetStackView(this)
        container.addView(widgetStackView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        val fabSettings: FloatingActionButton = findViewById(R.id.fab_settings)
        val fabSmaller: FloatingActionButton = findViewById(R.id.fab_smaller)
        val fabBigger: FloatingActionButton = findViewById(R.id.fab_bigger)
        val fabScaleDown: FloatingActionButton = findViewById(R.id.fab_scale_down)
        val fabScaleUp: FloatingActionButton = findViewById(R.id.fab_scale_up)
        val fabFullSize: FloatingActionButton = findViewById(R.id.fab_full_size)
        val sizeInfo: TextView = findViewById(R.id.size_info)
        val controlPanel = findViewById<ViewGroup>(R.id.control_panel)

        appWidgetManager = AppWidgetManager.getInstance(this)
        widgetHost = WidgetHost(this, hostId)
        widgetStackPersistence = WidgetStackPersistence(this)
        widgetStackView.initialize(widgetHost)

        fabSettings.setOnClickListener {
            openWidgetManagement()
        }

        fabSmaller.setOnClickListener {
            widgetStackView.makeSmaller()
            updateSizeInfo()
        }

        fabBigger.setOnClickListener {
            widgetStackView.makeBigger()
            updateSizeInfo()
        }

        fabScaleDown.setOnClickListener {
            widgetStackView.scaleDown()
            updateSizeInfo()
        }

        fabScaleUp.setOnClickListener {
            widgetStackView.scaleUp()
            updateSizeInfo()
        }
        
        fabFullSize.setOnClickListener {
            widgetStackView.toggleFullSizeMode()
            updateSizeInfo()
        }
        
        widgetStackView.onWidgetStackChangeListener = {
            updateSizeInfo()
            updateControlPanelVisibility()
            saveWidgetStack()
        }
        
        // Load persisted widget stack
        loadPersistedWidgetStack()
        
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
        // Save widget stack when app goes to background
        saveWidgetStack()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Save final widget stack state
        saveWidgetStack()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        android.util.Log.d("WidgetApp", "Configuration changed: ${newConfig.orientation}")
        
        // Widget stack will automatically adapt to new orientation
        widgetStackView.post {
            // Make sure current widget is visible after orientation change
            val currentWidget = widgetStackView.getCurrentWidget()
            android.util.Log.d("WidgetApp", "Current widget after rotation: ${currentWidget != null}")
            
            widgetStackView.refreshCurrentWidget()
            updateSizeInfo()
        }
    }

    private fun openWidgetManagement() {
        val intent = Intent(this, WidgetManagementActivity::class.java)
        val serializableWidgets = widgetStackView.getWidgetStack().getAllWidgets().map { 
            SerializableWidgetStackItem.fromWidgetStackItem(it) 
        }
        intent.putExtra(
            WidgetManagementActivity.EXTRA_WIDGET_STACK_DATA, 
            ArrayList(serializableWidgets)
        )
        startActivityForResult(intent, requestWidgetManagement)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            requestWidgetManagement -> {
                if (resultCode == WidgetManagementActivity.RESULT_STACK_UPDATED && data != null) {
                    val serializableWidgets = data.getSerializableExtra(
                        WidgetManagementActivity.EXTRA_WIDGET_STACK_DATA
                    ) as? ArrayList<SerializableWidgetStackItem>
                    
                    serializableWidgets?.let { widgets ->
                        convertAndUpdateWidgetStack(widgets)
                    }
                } else if (resultCode == WidgetManagementActivity.RESULT_ADD_WIDGET && data != null) {
                    // Handle new widget addition from management screen
                    val componentName = data.getParcelableExtra<ComponentName>(
                        WidgetManagementActivity.EXTRA_NEW_WIDGET_COMPONENT
                    )
                    val serializableWidgets = data.getSerializableExtra(
                        WidgetManagementActivity.EXTRA_WIDGET_STACK_DATA
                    ) as? ArrayList<SerializableWidgetStackItem>
                    
                    // First restore the existing widgets
                    serializableWidgets?.let { widgets ->
                        convertAndUpdateWidgetStack(widgets.filter { it.pendingPackageName == null })
                    }
                    
                    // Then add the new widget through proper flow
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
                        addWidgetToStack(widgetId, appWidgetInfo)
                    }
                }
            }
        }
    }

    private fun convertAndUpdateWidgetStack(serializableWidgets: List<SerializableWidgetStackItem>) {
        val widgets = serializableWidgets.map { serializable ->
            // Try to get the AppWidgetInfo for existing widgets
            val appWidgetInfo = if (serializable.widgetId != -1) {
                try {
                    appWidgetManager.getAppWidgetInfo(serializable.widgetId)
                } catch (e: Exception) {
                    null
                }
            } else null
            
            SerializableWidgetStackItem.toWidgetStackItem(serializable, appWidgetInfo)
        }
        updateWidgetStack(widgets)
    }
    
    private fun updateWidgetStack(widgets: List<WidgetStackItem>) {
        // Temporarily disable change listener to avoid saving during restoration
        val originalListener = widgetStackView.onWidgetStackChangeListener
        widgetStackView.onWidgetStackChangeListener = null
        
        try {
            // Clear current stack
            widgetStackView.clearWidgetStack()
            
            // Separate existing widgets from new widgets
            val existingWidgets = widgets.filter { it.pendingComponent == null && it.widgetId != -1 }
            val newWidgets = widgets.filter { it.pendingComponent != null }
            
            // Restore existing widgets first
            if (existingWidgets.isNotEmpty()) {
                widgetStackView.restoreWidgetStack(existingWidgets)
            }
            
            // Re-enable change listener
            widgetStackView.onWidgetStackChangeListener = originalListener
            
            // Process new widgets (these will trigger the change listener)
            for (widget in newWidgets) {
                widget.pendingComponent?.let { component ->
                    addWidget(component)
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("WidgetApp", "Failed to update widget stack: ${e.message}", e)
            // Re-enable change listener even if there's an error
            widgetStackView.onWidgetStackChangeListener = originalListener
        }
        
        updateControlPanelVisibility()
        updateSizeInfo()
    }
    
    private fun loadPersistedWidgetStack() {
        val persistedWidgets = widgetStackPersistence.loadWidgetStack(widgetStackView.getWidgetStack())
        
        if (persistedWidgets.isNotEmpty()) {
            // Temporarily disable change listener to avoid saving during restoration
            val originalListener = widgetStackView.onWidgetStackChangeListener
            widgetStackView.onWidgetStackChangeListener = null
            
            try {
                widgetStackView.restoreWidgetStack(persistedWidgets)
            } finally {
                // Re-enable change listener
                widgetStackView.onWidgetStackChangeListener = originalListener
            }
        }
        
        updateControlPanelVisibility()
        updateSizeInfo()
    }
    
    private fun saveWidgetStack() {
        widgetStackPersistence.saveWidgetStack(widgetStackView.getWidgetStack())
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
            addWidgetToStack(widgetId, appWidgetInfo)
        }
    }

    private fun addWidgetToStack(widgetId: Int, appWidgetInfo: AppWidgetProviderInfo?) {
        try {
            widgetStackView.addWidget(widgetId, appWidgetInfo)
            
            // Start listening after the widget is added
            widgetHost.startListening()
            
            updateControlPanelVisibility()
            updateSizeInfo()
            
        } catch (e: Exception) {
            android.util.Log.e("WidgetApp", "Failed to add widget to stack: ${e.message}", e)
            
            // Clean up failed widget
            widgetHost.deleteAppWidgetId(widgetId)
            
            // Show error message
            val sizeInfo = findViewById<TextView>(R.id.size_info)
            sizeInfo.text = "Couldn't add widget\nWidget: ${appWidgetInfo?.loadLabel(packageManager) ?: "Unknown"}\nError: ${e.message}"
            sizeInfo.visibility = ViewGroup.VISIBLE
        }
    }
    
    private fun updateSizeInfo() {
        val sizeInfo = findViewById<TextView>(R.id.size_info)
        val stackSizeInfo = widgetStackView.getSizeInfo()
        val constraintsInfo = widgetStackView.getWidgetConstraints()
        
        val fullInfo = if (constraintsInfo.isNotEmpty()) {
            "$stackSizeInfo\n\n$constraintsInfo"
        } else {
            stackSizeInfo
        }
        
        if (stackSizeInfo.isNotEmpty()) {
            sizeInfo.text = fullInfo
            sizeInfo.visibility = ViewGroup.VISIBLE
        } else {
            sizeInfo.visibility = ViewGroup.GONE
        }
    }
    
    private fun updateControlPanelVisibility() {
        val controlPanel = findViewById<ViewGroup>(R.id.control_panel)
        val hasWidgets = !widgetStackView.getWidgetStack().isEmpty()
        controlPanel.visibility = if (hasWidgets) ViewGroup.VISIBLE else ViewGroup.GONE
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
    
    private fun configureForVirtualDisplay() {
        try {
            // Get virtual display metrics
            val displayMetrics = resources.displayMetrics
            android.util.Log.d("WidgetApp", "VD Configuration - density: ${displayMetrics.density}, scaledDensity: ${displayMetrics.scaledDensity}")
            
            // Apply virtual display specific configuration to the widget stack view
            // Use a later callback since widgetStackView might not be initialized yet
            findViewById<android.view.View>(android.R.id.content).post {
                if (::widgetStackView.isInitialized) {
                    widgetStackView.configureForVirtualDisplay(displayMetrics)
                } else {
                    android.util.Log.w("WidgetApp", "WidgetStackView not yet initialized for VD config")
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.w("WidgetApp", "Failed to configure for virtual display: ${e.message}")
        }
    }
}