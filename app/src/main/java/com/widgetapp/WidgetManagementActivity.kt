package com.widgetapp

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class WidgetManagementActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WidgetManagementAdapter
    private lateinit var emptyState: View
    
    private val requestWidgetPicker = 100
    
    companion object {
        const val EXTRA_WIDGET_STACK_DATA = "widget_stack_data"
        const val RESULT_STACK_UPDATED = 1001
        const val RESULT_ADD_WIDGET = 1002
        const val EXTRA_NEW_WIDGET_COMPONENT = "new_widget_component"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_management)
        
        // Handle system window insets for cutouts and status bar
        val rootView = findViewById<View>(android.R.id.content)
        rootView.setOnApplyWindowInsetsListener { _, insets ->
            val header = findViewById<View>(R.id.header)
            val originalPadding = 16 // Original padding from layout
            
            header.setPadding(
                originalPadding + insets.systemWindowInsetLeft,
                originalPadding + insets.systemWindowInsetTop,
                originalPadding + insets.systemWindowInsetRight,
                originalPadding
            )
            insets
        }
        
        setupViews()
        setupRecyclerView()
        
        // Load widget stack data passed from main activity
        val serializableStackData = intent.getSerializableExtra(EXTRA_WIDGET_STACK_DATA) as? ArrayList<SerializableWidgetStackItem>
        serializableStackData?.let { serializableWidgets ->
            adapter.updateSerializableWidgets(serializableWidgets)
        }
        
        updateEmptyState()
    }
    
    private fun setupViews() {
        val fabBack: FloatingActionButton = findViewById(R.id.fab_back)
        val fabAddWidget: FloatingActionButton = findViewById(R.id.fab_add_widget)
        emptyState = findViewById(R.id.empty_state)
        
        fabBack.setOnClickListener {
            finishWithResult()
        }
        
        fabAddWidget.setOnClickListener {
            openWidgetPicker()
        }
    }
    
    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.widget_list)
        adapter = WidgetManagementAdapter(
            onItemMoved = { fromPosition, toPosition ->
                // Handle reordering
                updateEmptyState()
            },
            onItemDeleted = { position ->
                // Handle deletion
                updateEmptyState()
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Set up drag and drop
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                adapter.moveItem(fromPosition, toPosition)
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used
            }
            
            override fun isLongPressDragEnabled(): Boolean = false
            
            override fun isItemViewSwipeEnabled(): Boolean = false
        })
        
        itemTouchHelper.attachToRecyclerView(recyclerView)
        adapter.setItemTouchHelper(itemTouchHelper)
    }
    
    private fun openWidgetPicker() {
        val intent = Intent(this, WidgetPickerActivity::class.java)
        startActivityForResult(intent, requestWidgetPicker)
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == requestWidgetPicker && resultCode == Activity.RESULT_OK && data != null) {
            val componentName = data.getParcelableExtra<ComponentName>("widget_provider")
            componentName?.let { 
                // Return to MainActivity with the selected widget component for proper handling
                val serializableWidgets = adapter.getSerializableWidgets()
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_NEW_WIDGET_COMPONENT, componentName)
                    putExtra(EXTRA_WIDGET_STACK_DATA, ArrayList(serializableWidgets))
                }
                setResult(RESULT_ADD_WIDGET, resultIntent)
                finish()
            }
        }
    }
    
    private fun updateEmptyState() {
        val isEmpty = adapter.itemCount == 0
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun finishWithResult() {
        val serializableWidgets = adapter.getSerializableWidgets()
        val resultIntent = Intent().apply {
            putExtra(EXTRA_WIDGET_STACK_DATA, ArrayList(serializableWidgets))
        }
        setResult(RESULT_STACK_UPDATED, resultIntent)
        finish()
    }
    
    override fun onBackPressed() {
        finishWithResult()
    }
}