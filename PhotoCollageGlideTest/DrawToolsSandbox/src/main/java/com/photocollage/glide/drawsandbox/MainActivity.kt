package com.photocollage.glide.drawsandbox

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.photocollage.glide.drawsandbox.databinding.ActivityMainBinding
// Shapes-focused sandbox; Curve/Line/Brush tools removed

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var lockOn: Boolean = false
    companion object { private const val TAG = "DrawSmooth" }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force dark mode for this sandbox
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Bottom bar: Shapes and Eraser
        binding.bottomBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.action_shapes -> {
                    // Activate Shapes tool (rectangle with handles)
                    binding.drawingSurface.setToolMode(DrawingSurfaceView.ToolMode.SHAPE)
                    binding.drawingSurface.setShapeLockEnabled(lockOn)
                    binding.shapeLockScroll.visibility = android.view.View.VISIBLE
                    true
                }
                R.id.action_eraser -> {
                    binding.drawingSurface.clearAll()
                    // Do not change selection; keep current tool highlighted
                    false
                }
                else -> true // no-op for now
            }
        }

        // Initial state: focus on shapes
        binding.bottomBar.selectedItemId = R.id.action_shapes
        binding.drawingSurface.setToolMode(DrawingSurfaceView.ToolMode.SHAPE)
        binding.drawingSurface.setShapeLockEnabled(lockOn)
        binding.shapeLockScroll.visibility = android.view.View.VISIBLE

        // Shape lock toggle group
        binding.shapeLockGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (checkedId != R.id.shape_lock_toggle) return@addOnButtonCheckedListener
            lockOn = isChecked
            binding.drawingSurface.setShapeLockEnabled(lockOn)
        }
    }
}
