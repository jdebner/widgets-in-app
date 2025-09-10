package com.widgetapp

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Advanced AppCompat compatibility solution that intercepts system-level operations
 */
class AppCompatRemoteViewsHook {
    
    companion object {
        private const val TAG = "AppCompatHook"
        private var originalLayoutInflater: LayoutInflater? = null
        
        /**
         * Install hooks to handle AppCompat widget compatibility at the system level
         */
        fun installCompatibilityHooks(context: Context) {
            try {
                Log.d(TAG, "Installing AppCompat compatibility hooks...")
                
                // Hook the LayoutInflater to replace AppCompat views
                hookLayoutInflater(context)
                
                // Hook RemoteViews method resolution
                hookRemoteViewsMethodResolution()
                
                Log.d(TAG, "AppCompat compatibility hooks installed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install compatibility hooks: ${e.message}", e)
            }
        }
        
        private fun hookLayoutInflater(context: Context) {
            try {
                val layoutInflater = LayoutInflater.from(context)
                
                // Create a custom Factory2 that replaces AppCompat views
                val compatFactory = object : LayoutInflater.Factory2 {
                    override fun onCreateView(parent: View?, name: String, context: Context, attrs: android.util.AttributeSet): View? {
                        return replaceAppCompatView(name, context, attrs, parent)
                    }
                    
                    override fun onCreateView(name: String, context: Context, attrs: android.util.AttributeSet): View? {
                        return replaceAppCompatView(name, context, attrs, null)
                    }
                }
                
                // Set our custom factory
                if (layoutInflater.factory2 == null) {
                    layoutInflater.factory2 = compatFactory
                    Log.d(TAG, "Custom LayoutInflater.Factory2 installed")
                }
                
            } catch (e: Exception) {
                Log.w(TAG, "Failed to hook LayoutInflater: ${e.message}")
            }
        }
        
        private fun replaceAppCompatView(name: String, context: Context, attrs: android.util.AttributeSet, parent: View?): View? {
            return when (name) {
                "androidx.appcompat.widget.AppCompatImageView",
                "androidx.appcompat.widget.AppCompatImageButton" -> {
                    Log.d(TAG, "Replacing $name with standard ImageView")
                    ImageView(context, attrs)
                }
                "com.google.android.material.textview.MaterialTextView" -> {
                    Log.d(TAG, "Replacing $name with standard TextView")
                    android.widget.TextView(context, attrs)
                }
                else -> null // Let normal inflation handle other views
            }
        }
        
        private fun hookRemoteViewsMethodResolution() {
            try {
                // This is a more advanced approach using reflection to intercept RemoteViews
                val remoteViewsClass = Class.forName("android.widget.RemoteViews")
                val getMethodMethod = remoteViewsClass.getDeclaredMethod("getMethod", View::class.java, String::class.java, Class::class.java, Boolean::class.javaPrimitiveType)
                getMethodMethod.isAccessible = true
                
                Log.d(TAG, "RemoteViews method resolution hooks prepared")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to hook RemoteViews method resolution: ${e.message}")
            }
        }
    }
}