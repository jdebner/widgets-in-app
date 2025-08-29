package com.widgetapp

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import kotlin.math.max

class ResizableAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    
    init {
        // Ensure we don't clip child content
        clipToPadding = false
        clipChildren = false
    }
    
    private var widgetInfo: AppWidgetProviderInfo? = null
    private var currentWidth = 0
    private var currentHeight = 0
    private var minWidth = 140
    private var minHeight = 140
    private var minResizeWidth = 140
    private var minResizeHeight = 140
    
    private val cellSize = 70 * context.resources.displayMetrics.density
    private var availableSizes = listOf<Pair<Int, Int>>()
    private var currentSizeIndex = 0
    
    
    fun initializeWidget(
        info: AppWidgetProviderInfo?,
        minW: Int,
        minH: Int,
        minResizeW: Int,
        minResizeH: Int
    ) {
        widgetInfo = info
        minWidth = minW
        minHeight = minH
        minResizeWidth = minResizeW
        minResizeHeight = minResizeH
        
        // Calculate available widget sizes based on constraints
        availableSizes = calculateAvailableSizes()
        
        // Set initial size to minimum
        currentWidth = minWidth
        currentHeight = minHeight
        currentSizeIndex = 0
        
        layoutParams = layoutParams?.apply {
            width = currentWidth
            height = currentHeight
        } ?: android.widget.FrameLayout.LayoutParams(currentWidth, currentHeight)
    }
    
    fun refreshWidget() {
        // Convert pixels to dp for widget size reporting
        val density = context.resources.displayMetrics.density
        val widthDp = (currentWidth / density).toInt()
        val heightDp = (currentHeight / density).toInt()
        
        // Update widget size for proper rendering
        updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
        requestLayout()
        invalidate()
    }
    
    
    fun resizeWidget(width: Int, height: Int) {
        currentWidth = max(minResizeWidth, width)
        currentHeight = max(minResizeHeight, height)
        
        layoutParams = layoutParams.apply {
            this.width = currentWidth
            this.height = currentHeight
        }
        
        // Convert pixels to dp for widget size reporting
        val density = context.resources.displayMetrics.density
        val widthDp = (currentWidth / density).toInt()
        val heightDp = (currentHeight / density).toInt()
        
        // Update widget with both pixel and dp sizes
        android.util.Log.d("WidgetApp", "Updating widget size to ${widthDp}dp x ${heightDp}dp (${currentWidth}px x ${currentHeight}px)")
        android.util.Log.d("WidgetApp", "View measured size: ${measuredWidth}x${measuredHeight}, actual size: ${width}x${height}")
        updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
        
        // Force widget to re-evaluate its layout
        if (childCount > 0) {
            val child = getChildAt(0)
            child.requestLayout()
        }
        
        requestLayout()
        invalidate()
    }
    
    
    private fun calculateAvailableSizes(): List<Pair<Int, Int>> {
        val sizes = mutableListOf<Pair<Int, Int>>()
        
        // Use the widget's actual declared minimum size as the base
        val baseWidth = minWidth
        val baseHeight = minHeight
        val resizeWidth = minResizeWidth
        val resizeHeight = minResizeHeight
        
        // Start with the widget's minimum size
        sizes.add(Pair(baseWidth, baseHeight))
        
        // Add incremental sizes based on the minimum resize dimensions
        // This ensures we use sizes the widget is more likely to support
        val widthIncrement = if (resizeWidth > 0) resizeWidth else baseWidth
        val heightIncrement = if (resizeHeight > 0) resizeHeight else baseHeight
        
        // Generate larger sizes by adding increments
        for (widthMultiplier in 1..4) {
            for (heightMultiplier in 1..4) {
                val width = resizeWidth + (widthIncrement * (widthMultiplier - 1))
                val height = resizeHeight + (heightIncrement * (heightMultiplier - 1))
                
                // Only add if it's different from what we already have
                val newSize = Pair(width, height)
                if (!sizes.contains(newSize) && width >= resizeWidth && height >= resizeHeight) {
                    sizes.add(newSize)
                }
            }
        }
        
        // Also add exact Android widget sizes that match system widget picker behavior
        val commonAndroidSizes = listOf(
            // Standard Android launcher widget sizes (based on 70dp cell size)
            Pair(140, 70),   // 1x1 cell
            Pair(280, 70),   // 2x1 cells  
            Pair(280, 140),  // 2x2 cells (common for detailed widgets)
            Pair(420, 70),   // 3x1 cells
            Pair(420, 140),  // 3x2 cells
            Pair(560, 140),  // 4x2 cells (wide detailed view)
            Pair(280, 210),  // 2x3 cells (tall detailed view)  
            Pair(420, 210),  // 3x3 cells
            Pair(560, 210),  // 4x3 cells
            // Pixel-specific sizes that might trigger visual layouts
            Pair(294, 110),  
            Pair(294, 220),  
            Pair(470, 110),
            Pair(470, 220),
            Pair(470, 330),
        )
        
        for (size in commonAndroidSizes) {
            if (size.first >= resizeWidth && size.second >= resizeHeight && !sizes.contains(size)) {
                sizes.add(size)
            }
        }
        
        android.util.Log.d("WidgetApp", "Widget constraints - minWidth: $minWidth, minHeight: $minHeight, resizeWidth: $resizeWidth, resizeHeight: $resizeHeight")
        android.util.Log.d("WidgetApp", "Available widget sizes: ${sizes.map { "${it.first}x${it.second}" }}")
        return sizes.distinct().sortedWith(compareBy({ it.first }, { it.second }))
    }
    
    fun makeBigger(): Boolean {
        if (currentSizeIndex < availableSizes.size - 1) {
            currentSizeIndex++
            val newSize = availableSizes[currentSizeIndex]
            resizeWidget(newSize.first, newSize.second)
            return true
        }
        return false
    }
    
    fun makeSmaller(): Boolean {
        if (currentSizeIndex > 0) {
            currentSizeIndex--
            val newSize = availableSizes[currentSizeIndex]
            resizeWidget(newSize.first, newSize.second)
            return true
        }
        return false
    }
    
    fun getCurrentSize(): Pair<Int, Int> {
        return Pair(currentWidth, currentHeight)
    }
    
    fun getSizeInfo(): String {
        if (availableSizes.isEmpty()) return ""
        val density = context.resources.displayMetrics.density
        val widthDp = (currentWidth / density).toInt()
        val heightDp = (currentHeight / density).toInt()
        return "${widthDp}×${heightDp}dp (${currentSizeIndex + 1}/${availableSizes.size})"
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(currentWidth, currentHeight)
        
        // Measure child with exact dimensions, ensuring no padding interference
        if (childCount > 0) {
            val child = getChildAt(0)
            val childWidthSpec = MeasureSpec.makeMeasureSpec(currentWidth, MeasureSpec.EXACTLY)
            val childHeightSpec = MeasureSpec.makeMeasureSpec(currentHeight, MeasureSpec.EXACTLY)
            child.measure(childWidthSpec, childHeightSpec)
        }
    }
    
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // Layout child to fill the entire view with no clipping
        if (childCount > 0) {
            val child = getChildAt(0)
            child.layout(0, 0, currentWidth, currentHeight)
        }
    }
}