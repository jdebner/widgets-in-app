package com.widgetapp

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.view.LayoutInflater
import android.util.Log

/**
 * A context wrapper that prevents AppCompat view inflation
 */
class NonAppCompatContext(base: Context) : ContextWrapper(base) {
    
    private var customLayoutInflater: LayoutInflater? = null
    
    companion object {
        private const val TAG = "NonAppCompatContext"
    }
    
    override fun getSystemService(name: String): Any? {
        if (Context.LAYOUT_INFLATER_SERVICE == name) {
            if (customLayoutInflater == null) {
                customLayoutInflater = createCustomLayoutInflater()
            }
            return customLayoutInflater
        }
        return super.getSystemService(name)
    }
    
    private fun createCustomLayoutInflater(): LayoutInflater {
        val originalInflater = LayoutInflater.from(baseContext)
        val clonedInflater = originalInflater.cloneInContext(this)
        
        // Create a factory that prevents AppCompat view inflation
        val nonAppCompatFactory = object : LayoutInflater.Factory2 {
            override fun onCreateView(parent: android.view.View?, name: String, context: Context, attrs: android.util.AttributeSet): android.view.View? {
                return createNonAppCompatView(name, context, attrs)
            }
            
            override fun onCreateView(name: String, context: Context, attrs: android.util.AttributeSet): android.view.View? {
                return createNonAppCompatView(name, context, attrs)
            }
        }
        
        // Only set factory if none exists
        if (clonedInflater.factory2 == null) {
            try {
                clonedInflater.factory2 = nonAppCompatFactory
                Log.d(TAG, "Installed non-AppCompat factory")
            } catch (e: Exception) {
                Log.w(TAG, "Could not set factory2: ${e.message}")
            }
        }
        
        return clonedInflater
    }
    
    private fun createNonAppCompatView(name: String, context: Context, attrs: android.util.AttributeSet): android.view.View? {
        return when {
            name.contains("AppCompatImageView") -> {
                Log.d(TAG, "Preventing AppCompatImageView inflation, using ImageView")
                android.widget.ImageView(context, attrs)
            }
            name.contains("AppCompatImageButton") -> {
                Log.d(TAG, "Preventing AppCompatImageButton inflation, using ImageButton")
                android.widget.ImageButton(context, attrs)
            }
            name.contains("MaterialTextView") -> {
                Log.d(TAG, "Preventing MaterialTextView inflation, using TextView")
                android.widget.TextView(context, attrs)
            }
            name.contains("AppCompatTextView") -> {
                Log.d(TAG, "Preventing AppCompatTextView inflation, using TextView")
                android.widget.TextView(context, attrs)
            }
            else -> null // Let normal inflation handle standard views
        }
    }
    
    override fun getTheme(): Resources.Theme {
        // Use a plain Material theme to avoid AppCompat theme issues
        val theme = super.getTheme()
        theme.applyStyle(android.R.style.Theme_Material_Light, true)
        return theme
    }
}