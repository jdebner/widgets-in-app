package com.widgetapp

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.min

class WidgetStackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val widgetStack = WidgetStack()
    private val widgetViews = mutableMapOf<Int, ResizableAppWidgetHostView>()
    private lateinit var widgetHost: WidgetHost
    
    private var currentWidgetView: ResizableAppWidgetHostView? = null
    
    // Progress dots - styled like mockup
    private val dotBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80FFFFFF.toInt() // Semi-transparent white background
        style = Paint.Style.FILL
    }
    private val dotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt() // White border
        style = Paint.Style.STROKE
        strokeWidth = 6f // Thick border like mockup
    }
    private val activeDotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2196F3.toInt() // Blue fill for active dot
        style = Paint.Style.FILL
    }
    
    private var showProgressDots = false
    private var progressDotsAlpha = 0f
    private val dotRadius = 16f // Larger dots like mockup
    private val dotSpacing = 48f // More spacing
    private val progressDotsAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 300
        addUpdateListener { 
            progressDotsAlpha = it.animatedValue as Float
            invalidate()
        }
    }
    
    private val hideProgressDotsAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
        duration = 500
        startDelay = 2000 // Stay visible longer
        addUpdateListener { 
            progressDotsAlpha = it.animatedValue as Float
            invalidate()
        }
    }
    
    // Gesture detection
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            
            val deltaY = e2.y - e1.y
            val deltaX = e2.x - e1.x
            
            android.util.Log.d("WidgetApp", "Swipe detected: deltaY=$deltaY, deltaX=$deltaX, velocityY=$velocityY")
            
            // Only handle vertical swipes if they are more significant than horizontal
            if (abs(deltaY) > abs(deltaX) && abs(deltaY) > 100 && abs(velocityY) > 300) {
                android.util.Log.d("WidgetApp", "Valid swipe: ${if (deltaY > 0) "down" else "up"}")
                if (deltaY > 0) {
                    // Swipe down - previous widget
                    navigateToPrevious()
                } else {
                    // Swipe up - next widget  
                    navigateToNext()
                }
                return true
            }
            return false
        }
    })
    
    // Scaling and sizing
    private var isFullSizeMode = false
    private val availableScaleFactors = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)
    private var currentScaleIndex = 2 // Start at 1.0f
    
    var onWidgetStackChangeListener: (() -> Unit)? = null
    
    fun initialize(widgetHost: WidgetHost) {
        this.widgetHost = widgetHost
        clipToPadding = false
        clipChildren = false
        
        // Force ViewGroup to call onDraw() by setting setWillNotDraw(false)
        setWillNotDraw(false)
        // Use a drawable background instead of color to avoid virtual display issues
        background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
    }
    
    fun addWidget(widgetId: Int, appWidgetInfo: android.appwidget.AppWidgetProviderInfo?) {
        val label = appWidgetInfo?.loadLabel(context.packageManager)?.toString() ?: "Unknown Widget"
        val stackItem = WidgetStackItem(widgetId, appWidgetInfo, label)
        widgetStack.addWidget(stackItem)
        
        // Create widget view only if we don't already have one
        if (!widgetViews.containsKey(widgetId)) {
            createWidgetView(widgetId, appWidgetInfo)
        }
        
        // If this is the first widget or no current widget is showing, display it
        if (widgetStack.getWidgetCount() == 1 || currentWidgetView == null) {
            widgetViews[widgetId]?.let { hostView ->
                showWidget(hostView)
                widgetStack.setCurrentIndex(0)
            }
        }
        
        onWidgetStackChangeListener?.invoke()
    }
    
    fun restoreWidget(stackItem: WidgetStackItem) {
        // Add to stack without triggering change listener
        widgetStack.addWidget(stackItem)
        
        // Create widget view if we don't already have one
        if (!widgetViews.containsKey(stackItem.widgetId) && stackItem.appWidgetInfo != null) {
            createWidgetView(stackItem.widgetId, stackItem.appWidgetInfo)
        }
    }
    
    private fun createWidgetView(widgetId: Int, appWidgetInfo: android.appwidget.AppWidgetProviderInfo?) {
        try {
            // Create widget view
            val hostView = widgetHost.createView(context, widgetId, appWidgetInfo) as ResizableAppWidgetHostView
            
            // Initialize the widget view with proper constraints
            val minWidth = appWidgetInfo?.minWidth ?: 280
            val minHeight = appWidgetInfo?.minHeight ?: 140
            val minResizeWidth = appWidgetInfo?.minResizeWidth ?: minWidth  
            val minResizeHeight = appWidgetInfo?.minResizeHeight ?: minHeight
            
            hostView.initializeWidget(appWidgetInfo, minWidth, minHeight, minResizeWidth, minResizeHeight)
            widgetViews[widgetId] = hostView
        } catch (e: Exception) {
            android.util.Log.e("WidgetApp", "Failed to create widget view for $widgetId: ${e.message}", e)
        }
    }
    
    fun removeWidget(index: Int) {
        val removed = widgetStack.removeWidget(index)
        removed?.let { item ->
            widgetViews[item.widgetId]?.let { view ->
                if (currentWidgetView == view) {
                    removeView(view)
                    currentWidgetView = null
                    // Show the new current widget if available
                    widgetStack.getCurrentWidget()?.let { currentItem ->
                        widgetViews[currentItem.widgetId]?.let { newCurrentView ->
                            showWidget(newCurrentView)
                        }
                    }
                }
                widgetViews.remove(item.widgetId)
            }
            widgetHost.deleteAppWidgetId(item.widgetId)
        }
        onWidgetStackChangeListener?.invoke()
    }
    
    fun moveWidget(fromIndex: Int, toIndex: Int) {
        if (widgetStack.moveWidget(fromIndex, toIndex)) {
            onWidgetStackChangeListener?.invoke()
        }
    }
    
    private fun showWidget(hostView: ResizableAppWidgetHostView) {
        // Remove current widget view
        currentWidgetView?.let { removeView(it) }
        
        // Add new widget view
        val layoutParams = FrameLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }
        
        addView(hostView, layoutParams)
        currentWidgetView = hostView
        
        // Apply current scaling mode
        applyCurrentScaling()
        
        // Start listening and refresh
        hostView.post {
            hostView.refreshWidget()
        }
    }
    
    fun navigateToNext(): Boolean {
        if (widgetStack.navigateNext()) {
            widgetStack.getCurrentWidget()?.let { currentItem ->
                widgetViews[currentItem.widgetId]?.let { view ->
                    showWidget(view)
                    showProgressDots()
                    return true
                }
            }
        }
        return false
    }
    
    fun navigateToPrevious(): Boolean {
        if (widgetStack.navigatePrevious()) {
            widgetStack.getCurrentWidget()?.let { currentItem ->
                widgetViews[currentItem.widgetId]?.let { view ->
                    showWidget(view)
                    showProgressDots()
                    return true
                }
            }
        }
        return false
    }
    
    fun makeBigger(): Boolean {
        return currentWidgetView?.makeBigger() ?: false
    }
    
    fun makeSmaller(): Boolean {
        return currentWidgetView?.makeSmaller() ?: false
    }
    
    fun scaleUp(): Boolean {
        if (!isFullSizeMode && currentScaleIndex < availableScaleFactors.size - 1) {
            currentScaleIndex++
            applyCurrentScaling()
            return true
        }
        return false
    }
    
    fun scaleDown(): Boolean {
        if (!isFullSizeMode && currentScaleIndex > 0) {
            currentScaleIndex--
            applyCurrentScaling()
            return true
        }
        return false
    }
    
    fun toggleFullSizeMode(): Boolean {
        isFullSizeMode = !isFullSizeMode
        widgetStack.setFullSizeMode(isFullSizeMode)
        applyCurrentScaling()
        return true
    }
    
    private fun applyCurrentScaling() {
        currentWidgetView?.let { widget ->
            if (isFullSizeMode) {
                // Detect if we're on a virtual display for special handling
                val displayMetrics = context.resources.displayMetrics
                val isVirtualDisplay = try {
                    val display = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
                    display.displayId != android.view.Display.DEFAULT_DISPLAY
                } catch (e: Exception) {
                    false
                }
                
                val realScreenWidth = displayMetrics.widthPixels
                val realScreenHeight = displayMetrics.heightPixels
                val containerWidth = width
                val containerHeight = height
                
                // For virtual displays, use container dimensions but ensure minimum usable size
                val effectiveWidth = if (isVirtualDisplay) {
                    // On VD, use container dimensions but ensure it's reasonable
                    val containerW = if (containerWidth > 0) containerWidth else realScreenWidth
                    // Ensure we have a minimum reasonable width for VD
                    maxOf(containerW, 300)
                } else {
                    if (containerWidth > 0) minOf(realScreenWidth, containerWidth) else realScreenWidth
                }
                
                val effectiveHeight = if (isVirtualDisplay) {
                    val containerH = if (containerHeight > 0) containerHeight else realScreenHeight
                    // Ensure we have a minimum reasonable height for VD
                    maxOf(containerH, 200)
                } else {
                    if (containerHeight > 0) minOf(realScreenHeight, containerHeight) else realScreenHeight
                }
                
                val widgetSize = widget.getCurrentSize()
                
                android.util.Log.d("WidgetApp", "Full-size scaling (VD: $isVirtualDisplay):")
                android.util.Log.d("WidgetApp", "  Real screen: ${realScreenWidth}x${realScreenHeight}")
                android.util.Log.d("WidgetApp", "  Container: ${containerWidth}x${containerHeight}")
                android.util.Log.d("WidgetApp", "  Effective: ${effectiveWidth}x${effectiveHeight}")
                android.util.Log.d("WidgetApp", "  Widget size: ${widgetSize.first}x${widgetSize.second}")
                
                if (effectiveWidth > 0 && effectiveHeight > 0 && widgetSize.first > 0 && widgetSize.second > 0) {
                    val scaleX = effectiveWidth.toFloat() / widgetSize.first
                    val scaleY = effectiveHeight.toFloat() / widgetSize.second
                    // Use larger scaling for virtual displays since they tend to show smaller
                    val margin = if (isVirtualDisplay) 0.9f else 0.8f
                    val scale = min(scaleX, scaleY) * margin
                    
                    android.util.Log.d("WidgetApp", "  Scale factors: X=$scaleX, Y=$scaleY, final=$scale")
                    
                    // For virtual displays, ensure minimum scale to make widgets visible
                    val finalScale = if (isVirtualDisplay && scale < 1.0f) {
                        maxOf(scale, 1.5f) // Minimum 1.5x scale for VD visibility
                    } else {
                        scale
                    }
                    
                    android.util.Log.d("WidgetApp", "  Final scale applied: $finalScale")
                    
                    widget.scaleX = finalScale
                    widget.scaleY = finalScale
                } else {
                    android.util.Log.w("WidgetApp", "Invalid dimensions for full-size scaling")
                }
            } else {
                // Use manual scale factor
                val scale = availableScaleFactors[currentScaleIndex]
                android.util.Log.d("WidgetApp", "Manual scaling: $scale")
                widget.scaleX = scale
                widget.scaleY = scale
            }
        }
    }
    
    private fun showProgressDots() {
        if (widgetStack.getWidgetCount() <= 1) return
        
        android.util.Log.d("WidgetApp", "Showing progress dots: count=${widgetStack.getWidgetCount()}")
        showProgressDots = true
        
        hideProgressDotsAnimator.cancel()
        progressDotsAnimator.start()
        
        // Auto-hide after delay
        progressDotsAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                hideProgressDotsAnimator.start()
            }
        })
    }
    
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Only intercept for gesture detection, don't interfere with widget touch events
        gestureDetector.onTouchEvent(ev)
        return false // Let widgets handle their own touch events
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        android.util.Log.d("WidgetApp", "WidgetStackView size changed: ${w}x${h} (was ${oldw}x${oldh})")
        android.util.Log.d("WidgetApp", "willNotDraw: ${willNotDraw()}, background: ${background != null}")
        
        // Reapply scaling when size changes
        if (isFullSizeMode) {
            applyCurrentScaling()
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Only log when actually drawing dots
        if (showProgressDots && progressDotsAlpha > 0) {
            android.util.Log.d("WidgetApp", "Drawing progress dots - alpha=$progressDotsAlpha, widgetCount=${widgetStack.getWidgetCount()}")
        }
        
        // Draw progress dots if visible
        if (showProgressDots && progressDotsAlpha > 0) {
            drawProgressDots(canvas)
        }
    }
    
    private fun drawProgressDots(canvas: Canvas) {
        val count = widgetStack.getWidgetCount()
        
        if (count <= 1) return
        
        val currentIndex = widgetStack.getCurrentIndex()
        
        // Position dots vertically on the right side of the widget
        val totalHeight = (count - 1) * dotSpacing
        val startY = (height - totalHeight) / 2f
        val centerX = width * 0.9f // Right side instead of bottom
        
        dotBackgroundPaint.alpha = (128 * progressDotsAlpha).toInt() // Semi-transparent background
        dotBorderPaint.alpha = (255 * progressDotsAlpha).toInt()
        activeDotFillPaint.alpha = (255 * progressDotsAlpha).toInt()
        
        for (i in 0 until count) {
            val y = startY + i * dotSpacing
            
            if (i == currentIndex) {
                // Active dot: blue fill + white border
                canvas.drawCircle(centerX, y, dotRadius, activeDotFillPaint)
                canvas.drawCircle(centerX, y, dotRadius, dotBorderPaint)
            } else {
                // Inactive dot: semi-transparent fill + white border
                canvas.drawCircle(centerX, y, dotRadius, dotBackgroundPaint)
                canvas.drawCircle(centerX, y, dotRadius, dotBorderPaint)
            }
        }
    }
    
    fun getCurrentWidget(): ResizableAppWidgetHostView? = currentWidgetView
    
    fun clearWidgetStack() {
        // Remove all views
        removeAllViews()
        currentWidgetView = null
        
        // Clear widget views map (but don't delete widget IDs - let caller handle that)
        widgetViews.clear()
        
        // Clear stack
        widgetStack.clear()
    }
    
    fun restoreWidgetStack(widgets: List<WidgetStackItem>) {
        // Restore all widgets to stack
        for (widget in widgets) {
            restoreWidget(widget)
        }
        
        // Show the current widget if available
        val currentWidget = widgetStack.getCurrentWidget()
        currentWidget?.let { widget ->
            widgetViews[widget.widgetId]?.let { hostView ->
                showWidget(hostView)
            }
        }
        
        onWidgetStackChangeListener?.invoke()
    }
    
    fun getWidgetStack(): WidgetStack = widgetStack
    
    fun getSizeInfo(): String {
        val stackInfo = "Stack: ${widgetStack.getCurrentIndex() + 1}/${widgetStack.getWidgetCount()}"
        val modeInfo = if (isFullSizeMode) "Full Size Mode" else "Manual Scale: ${availableScaleFactors[currentScaleIndex]}x"
        val widgetInfo = currentWidgetView?.getSizeInfo() ?: ""
        
        return "$stackInfo\n$modeInfo\n$widgetInfo"
    }
    
    fun getWidgetConstraints(): String {
        return currentWidgetView?.getWidgetConstraints() ?: ""
    }
    
    fun configureForVirtualDisplay(displayMetrics: DisplayMetrics) {
        android.util.Log.d("WidgetApp", "Configuring WidgetStackView for virtual display")
        android.util.Log.d("WidgetApp", "  WidgetStackView size: ${width}x${height}")
        
        // Apply virtual display configuration to all widget views
        for (widgetView in widgetViews.values) {
            widgetView.configureForVirtualDisplay(displayMetrics)
        }
        
        // Force layout update and ensure current scaling is applied
        post {
            requestLayout()
            invalidate()
            
            // Re-apply current scaling to ensure it works with VD
            applyCurrentScaling()
        }
    }
    
    fun refreshCurrentWidget() {
        android.util.Log.d("WidgetApp", "refreshCurrentWidget called")
        val currentWidget = widgetStack.getCurrentWidget()
        android.util.Log.d("WidgetApp", "Current widget in stack: $currentWidget")
        
        if (currentWidget != null) {
            // Get the widget view from our map
            val widgetView = widgetViews[currentWidget.widgetId]
            android.util.Log.d("WidgetApp", "Widget view for ID ${currentWidget.widgetId}: $widgetView")
            
            if (widgetView != null) {
                // If the widget view exists but isn't currently showing, show it
                if (currentWidgetView != widgetView) {
                    android.util.Log.d("WidgetApp", "Showing widget that wasn't currently displayed")
                    showWidget(widgetView)
                } else {
                    // Widget is already showing, just refresh it
                    android.util.Log.d("WidgetApp", "Refreshing already displayed widget")
                    widgetView.refreshWidget()
                    
                    // Make sure the widget is still visible after refresh
                    if (widgetView.parent == null) {
                        android.util.Log.w("WidgetApp", "Widget lost parent during refresh, re-adding")
                        showWidget(widgetView)
                    }
                }
            } else {
                android.util.Log.w("WidgetApp", "Widget view not found for ID ${currentWidget.widgetId}, attempting to recreate")
                // Try to recreate the widget view
                if (currentWidget.appWidgetInfo != null) {
                    createWidgetView(currentWidget.widgetId, currentWidget.appWidgetInfo)
                    widgetViews[currentWidget.widgetId]?.let { newView ->
                        showWidget(newView)
                    }
                }
            }
        } else {
            android.util.Log.w("WidgetApp", "No current widget in stack to refresh")
        }
    }
}