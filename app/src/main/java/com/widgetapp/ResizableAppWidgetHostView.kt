package com.widgetapp

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
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
    
    // Android widget grid system: 56dp per grid unit
    private val gridCellSizeDp = 56
    private val gridCellSizePx = gridCellSizeDp * getPrimaryDisplayDensity()
    private var availableGridSizes = listOf<Pair<Int, Int>>() // Grid units (e.g., 1x1, 2x1)
    private var currentSizeIndex = 0
    
    // Widget size constraints in grid units
    private var minGridWidth = 1
    private var minGridHeight = 1
    private var maxGridWidth = 5
    private var maxGridHeight = 5
    private var minResizeGridWidth = 1
    private var minResizeGridHeight = 1
    
    // Scale factor for automotive/display scaling
    private var scaleFactor = 1.0f
    private val availableScaleFactors = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)
    private var currentScaleIndex = 2 // Start at 1.0f
    
    private fun getPrimaryDisplayDensity(): Float {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val primaryDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val displayMetrics = android.util.DisplayMetrics()
        primaryDisplay?.getRealMetrics(displayMetrics)
        return displayMetrics.density
    }
    
    
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
        
        // Convert pixel sizes to grid units
        val density = getPrimaryDisplayDensity()
        minGridWidth = dpToGridUnits((minW / density).toInt())
        minGridHeight = dpToGridUnits((minH / density).toInt())
        minResizeGridWidth = dpToGridUnits((minResizeW / density).toInt())
        minResizeGridHeight = dpToGridUnits((minResizeH / density).toInt())
        
        // Calculate max reasonable grid sizes (5x5 should cover most widgets)
        maxGridWidth = 5
        maxGridHeight = 5
        
        // Calculate available widget sizes in grid units
        availableGridSizes = calculateGridSizes()
        
        // Set initial size to minimum grid size
        val initialGridSize = availableGridSizes.firstOrNull() ?: Pair(minResizeGridWidth, minResizeGridHeight)
        currentWidth = gridUnitsToPixels(initialGridSize.first)
        currentHeight = gridUnitsToPixels(initialGridSize.second)
        currentSizeIndex = 0
        
        layoutParams = layoutParams?.apply {
            width = currentWidth
            height = currentHeight
        } ?: android.widget.FrameLayout.LayoutParams(currentWidth, currentHeight)
        
        // Apply initial scale factor
        scaleFactor = availableScaleFactors[currentScaleIndex]
        applyScaleTransform()
    }
    
    private fun dpToGridUnits(dp: Int): Int {
        return kotlin.math.max(1, kotlin.math.ceil(dp.toDouble() / gridCellSizeDp).toInt())
    }
    
    private fun gridUnitsToPixels(gridUnits: Int): Int {
        return (gridUnits * gridCellSizePx).toInt()
    }
    
    private fun gridUnitsToDp(gridUnits: Int): Int {
        return gridUnits * gridCellSizeDp
    }
    
    private fun applyScaleTransform() {
        scaleX = scaleFactor
        scaleY = scaleFactor
        
        // Ensure widget stays centered after scaling
        pivotX = currentWidth / 2f
        pivotY = currentHeight / 2f
        
        android.util.Log.d("WidgetApp", "Applied scale factor: ${scaleFactor} to widget ${currentWidth}x${currentHeight}")
    }
    
    fun refreshWidget() {
        // Convert pixels to dp for widget size reporting using primary display density
        val density = getPrimaryDisplayDensity()
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
        
        // Convert pixels to dp for widget size reporting using primary display density
        val density = getPrimaryDisplayDensity()
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
        
        // Reapply scale transform for new size
        applyScaleTransform()
        
        requestLayout()
        invalidate()
    }
    
    
    private fun calculateGridSizes(): List<Pair<Int, Int>> {
        val gridSizes = mutableListOf<Pair<Int, Int>>()
        
        // Get widget's actual max constraints from AppWidgetProviderInfo
        val actualMaxGridWidth = widgetInfo?.let { info ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // API 31+ has maxResizeWidth/Height
                val maxResizeWidthDp = if (info.maxResizeWidth > 0) {
                    (info.maxResizeWidth / getPrimaryDisplayDensity()).toInt()
                } else {
                    gridUnitsToDp(maxGridWidth) // Use our default max
                }
                dpToGridUnits(maxResizeWidthDp)
            } else {
                maxGridWidth // Use our default max for older APIs
            }
        } ?: maxGridWidth
        
        val actualMaxGridHeight = widgetInfo?.let { info ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // API 31+ has maxResizeHeight
                val maxResizeHeightDp = if (info.maxResizeHeight > 0) {
                    (info.maxResizeHeight / getPrimaryDisplayDensity()).toInt()
                } else {
                    gridUnitsToDp(maxGridHeight) // Use our default max
                }
                dpToGridUnits(maxResizeHeightDp)
            } else {
                maxGridHeight // Use our default max for older APIs
            }
        } ?: maxGridHeight
        
        // Check if widget is resizable
        val isResizable = widgetInfo?.resizeMode != AppWidgetProviderInfo.RESIZE_NONE
        
        if (isResizable) {
            // Generate grid combinations within widget's actual constraints
            // Start from the larger of minWidth or minResizeWidth to respect the widget's true minimum
            val startWidth = kotlin.math.max(minGridWidth, minResizeGridWidth)
            val startHeight = kotlin.math.max(minGridHeight, minResizeGridHeight)
            
            for (width in startWidth..kotlin.math.min(actualMaxGridWidth, maxGridWidth)) {
                for (height in startHeight..kotlin.math.min(actualMaxGridHeight, maxGridHeight)) {
                    // Ensure sizes meet both the minimum size and minimum resize requirements
                    if (width >= startWidth && height >= startHeight) {
                        gridSizes.add(Pair(width, height))
                    }
                }
            }
        } else {
            // Non-resizable widget: only offer its minimum size
            gridSizes.add(Pair(minGridWidth, minGridHeight))
        }
        
        android.util.Log.d("WidgetApp", "Widget grid constraints:")
        android.util.Log.d("WidgetApp", "  Min size: ${minGridWidth}x${minGridHeight} grid units (${gridUnitsToDp(minGridWidth)}x${gridUnitsToDp(minGridHeight)}dp)")
        android.util.Log.d("WidgetApp", "  Min resize: ${minResizeGridWidth}x${minResizeGridHeight} grid units (${gridUnitsToDp(minResizeGridWidth)}x${gridUnitsToDp(minResizeGridHeight)}dp)")
        android.util.Log.d("WidgetApp", "  Actual max: ${actualMaxGridWidth}x${actualMaxGridHeight} grid units (${gridUnitsToDp(actualMaxGridWidth)}x${gridUnitsToDp(actualMaxGridHeight)}dp)")
        android.util.Log.d("WidgetApp", "  App max: ${maxGridWidth}x${maxGridHeight} grid units (${gridUnitsToDp(maxGridWidth)}x${gridUnitsToDp(maxGridHeight)}dp)")
        android.util.Log.d("WidgetApp", "Available grid sizes: ${gridSizes.map { "${it.first}x${it.second}" }}")
        
        return gridSizes.sortedWith(compareBy({ it.first }, { it.second }))
    }
    
    fun makeBigger(): Boolean {
        if (currentSizeIndex < availableGridSizes.size - 1) {
            currentSizeIndex++
            val newGridSize = availableGridSizes[currentSizeIndex]
            val newPixelWidth = gridUnitsToPixels(newGridSize.first)
            val newPixelHeight = gridUnitsToPixels(newGridSize.second)
            resizeWidget(newPixelWidth, newPixelHeight)
            return true
        }
        return false
    }
    
    fun makeSmaller(): Boolean {
        if (currentSizeIndex > 0) {
            currentSizeIndex--
            val newGridSize = availableGridSizes[currentSizeIndex]
            val newPixelWidth = gridUnitsToPixels(newGridSize.first)
            val newPixelHeight = gridUnitsToPixels(newGridSize.second)
            resizeWidget(newPixelWidth, newPixelHeight)
            return true
        }
        return false
    }
    
    fun scaleUp(): Boolean {
        if (currentScaleIndex < availableScaleFactors.size - 1) {
            currentScaleIndex++
            scaleFactor = availableScaleFactors[currentScaleIndex]
            applyScaleTransform()
            return true
        }
        return false
    }
    
    fun scaleDown(): Boolean {
        if (currentScaleIndex > 0) {
            currentScaleIndex--
            scaleFactor = availableScaleFactors[currentScaleIndex]
            applyScaleTransform()
            return true
        }
        return false
    }
    
    fun getCurrentSize(): Pair<Int, Int> {
        return Pair(currentWidth, currentHeight)
    }
    
    fun getCurrentScale(): Float {
        return scaleFactor
    }
    
    fun getEffectiveSize(): Pair<Int, Int> {
        val effectiveWidth = (currentWidth * scaleFactor).toInt()
        val effectiveHeight = (currentHeight * scaleFactor).toInt()
        return Pair(effectiveWidth, effectiveHeight)
    }
    
    fun getSizeInfo(): String {
        if (availableGridSizes.isEmpty()) return ""
        val currentGridSize = availableGridSizes[currentSizeIndex]
        val widthDp = gridUnitsToDp(currentGridSize.first)
        val heightDp = gridUnitsToDp(currentGridSize.second)
        val effectiveSize = getEffectiveSize()
        val density = getPrimaryDisplayDensity()
        val effectiveWidthDp = (effectiveSize.first / density).toInt()
        val effectiveHeightDp = (effectiveSize.second / density).toInt()
        
        return """
            Grid: ${currentGridSize.first}×${currentGridSize.second} (${widthDp}×${heightDp}dp) [${currentSizeIndex + 1}/${availableGridSizes.size}]
            Scale: ${scaleFactor}x [${currentScaleIndex + 1}/${availableScaleFactors.size}]
            Effective: ${effectiveWidthDp}×${effectiveHeightDp}dp
        """.trimIndent()
    }
    
    fun getWidgetConstraints(): String {
        val minWidthDp = gridUnitsToDp(minGridWidth)
        val minHeightDp = gridUnitsToDp(minGridHeight)
        val minResizeWidthDp = gridUnitsToDp(minResizeGridWidth)
        val minResizeHeightDp = gridUnitsToDp(minResizeGridHeight)
        
        // Get actual max from widget info
        val actualMaxGridWidth = widgetInfo?.let { info ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && info.maxResizeWidth > 0) {
                val maxResizeWidthDp = (info.maxResizeWidth / getPrimaryDisplayDensity()).toInt()
                dpToGridUnits(maxResizeWidthDp)
            } else {
                maxGridWidth
            }
        } ?: maxGridWidth
        
        val actualMaxGridHeight = widgetInfo?.let { info ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && info.maxResizeHeight > 0) {
                val maxResizeHeightDp = (info.maxResizeHeight / getPrimaryDisplayDensity()).toInt()
                dpToGridUnits(maxResizeHeightDp)
            } else {
                maxGridHeight
            }
        } ?: maxGridHeight
        
        val actualMaxWidthDp = gridUnitsToDp(actualMaxGridWidth)
        val actualMaxHeightDp = gridUnitsToDp(actualMaxGridHeight)
        
        val resizableText = widgetInfo?.let { info ->
            if (info.resizeMode != AppWidgetProviderInfo.RESIZE_NONE) {
                val modes = mutableListOf<String>()
                if (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0) modes.add("horizontal")
                if (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0) modes.add("vertical")
                "Resizable: ${modes.joinToString(", ")}"
            } else {
                "Not resizable"
            }
        } ?: "Unknown resize mode"
        
        return """
            Widget Size Constraints:
            Min: ${minGridWidth}×${minGridHeight} grid (${minWidthDp}×${minHeightDp}dp)
            Min Resize: ${minResizeGridWidth}×${minResizeGridHeight} grid (${minResizeWidthDp}×${minResizeHeightDp}dp)  
            Max: ${actualMaxGridWidth}×${actualMaxGridHeight} grid (${actualMaxWidthDp}×${actualMaxHeightDp}dp)
            $resizableText
            Grid Cell: ${gridCellSizeDp}dp
            Available sizes: ${availableGridSizes.size}
        """.trimIndent()
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