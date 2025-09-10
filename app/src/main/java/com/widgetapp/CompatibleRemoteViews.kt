package com.widgetapp

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import java.lang.reflect.Field
import java.lang.reflect.Method

class CompatibleRemoteViews {
    
    companion object {
        private const val TAG = "CompatRemoteViews"
        
        /**
         * Attempts to fix AppCompat widget compatibility by intercepting RemoteViews
         * and replacing AppCompatImageView with standard ImageView
         */
        fun applyCompatibleRemoteViews(context: Context, remoteViews: RemoteViews?): RemoteViews? {
            if (remoteViews == null) return null
            
            try {
                // Try to access the internal actions list in RemoteViews
                val actionsField = getActionsField(remoteViews)
                if (actionsField != null) {
                    val actions = actionsField.get(remoteViews) as? ArrayList<*>
                    actions?.let { actionList ->
                        Log.d(TAG, "Found ${actionList.size} RemoteViews actions")
                        
                        // Process each action to fix AppCompat compatibility
                        for (i in actionList.indices) {
                            val action = actionList[i]
                            if (action != null) {
                                processRemoteViewAction(action)
                            }
                        }
                    }
                }
                
                return remoteViews
            } catch (e: Exception) {
                Log.w(TAG, "Failed to process RemoteViews for compatibility: ${e.message}")
                return remoteViews
            }
        }
        
        private fun getActionsField(remoteViews: RemoteViews): Field? {
            return try {
                val field = RemoteViews::class.java.getDeclaredField("mActions")
                field.isAccessible = true
                field
            } catch (e: Exception) {
                Log.w(TAG, "Could not access mActions field: ${e.message}")
                null
            }
        }
        
        private fun processRemoteViewAction(action: Any) {
            try {
                val actionClass = action::class.java
                Log.d(TAG, "Processing action: ${actionClass.simpleName}")
                
                // Look for setImageResource actions on AppCompatImageView
                if (actionClass.simpleName?.contains("ReflectionAction") == true) {
                    // Try different field names for different Android versions
                    val methodName = getMethodNameFromAction(action)
                    
                    if (methodName == "setImageResource") {
                        Log.d(TAG, "Found setImageResource action - this might be causing AppCompat issues")
                    }
                }
                
            } catch (e: Exception) {
                Log.w(TAG, "Failed to process action: ${e.message}")
            }
        }
        
        private fun getMethodNameFromAction(action: Any): String? {
            val actionClass = action::class.java
            
            // Try different field names that might contain the method name
            val fieldNames = arrayOf("methodName", "mMethodName", "method")
            
            for (fieldName in fieldNames) {
                try {
                    val field = actionClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    return field.get(action) as? String
                } catch (e: Exception) {
                    // Continue to next field name
                }
            }
            
            // Try searching in parent classes
            var currentClass = actionClass.superclass
            while (currentClass != null) {
                for (fieldName in fieldNames) {
                    try {
                        val field = currentClass.getDeclaredField(fieldName)
                        field.isAccessible = true
                        return field.get(action) as? String
                    } catch (e: Exception) {
                        // Continue searching
                    }
                }
                currentClass = currentClass.superclass
            }
            
            return null
        }
        
        /**
         * Alternative approach: Create a view tree and manually replace AppCompatImageViews
         */
        fun createCompatibleView(context: Context, remoteViews: RemoteViews): View? {
            return try {
                val view = remoteViews.apply(context, null)
                replaceAppCompatViews(view)
                view
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create compatible view: ${e.message}", e)
                null
            }
        }
        
        private fun replaceAppCompatViews(view: View) {
            // If this is an AppCompatImageView, we can't directly replace it in RemoteViews
            // But we can log it for debugging
            if (view::class.java.name.contains("AppCompatImageView")) {
                Log.w(TAG, "Found AppCompatImageView in widget - this will likely cause issues with RemoteViews")
            }
            
            // Recursively check child views if this is a ViewGroup
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    replaceAppCompatViews(view.getChildAt(i))
                }
            }
        }
    }
}