package com.material3.showcase

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.material3.showcase.databinding.ActivityExpandableComponentsBinding

class ExpandableComponentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExpandableComponentsBinding
    private var isFabExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityExpandableComponentsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupWindowInsets()
        setupToolbar()
        setupDropdowns()
        setupExpandableCards()
        setupExpandableFab()
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    
    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupDropdowns() {
        // Filled dropdown options
        val filledOptions = arrayOf("Option 1", "Option 2", "Option 3", "Option 4")
        val filledAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, filledOptions)
        binding.filledDropdown.editText?.let { editText ->
            (editText as? android.widget.AutoCompleteTextView)?.setAdapter(filledAdapter)
        }
        
        // Outlined dropdown options
        val themeOptions = arrayOf("Default Purple", "Ocean Blue", "Forest Green", "Sunset Orange", "Cherry Red")
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, themeOptions)
        binding.outlinedDropdown.editText?.let { editText ->
            (editText as? android.widget.AutoCompleteTextView)?.setAdapter(themeAdapter)
        }
    }
    
    private fun setupExpandableCards() {
        setupExpandableCard(
            binding.expandableCard1,
            binding.expandableContent1,
            binding.expandIcon1
        )
        
        setupExpandableCard(
            binding.expandableCard2,
            binding.expandableContent2,
            binding.expandIcon2
        )
        
        setupExpandableCard(
            binding.expandableCard3,
            binding.expandableContent3,
            binding.expandIcon3
        )
    }
    
    private fun setupExpandableCard(card: View, content: View, icon: View) {
        card.setOnClickListener {
            val isExpanded = content.visibility == View.VISIBLE
            
            if (isExpanded) {
                // Collapse
                content.visibility = View.GONE
                ObjectAnimator.ofFloat(icon, "rotation", 180f, 0f).apply {
                    duration = 300
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            } else {
                // Expand
                content.visibility = View.VISIBLE
                ObjectAnimator.ofFloat(icon, "rotation", 0f, 180f).apply {
                    duration = 300
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }
        }
    }
    
    private fun setupExpandableFab() {
        binding.expandableFab.setOnClickListener {
            if (isFabExpanded) {
                collapseFab()
            } else {
                expandFab()
            }
        }
    }
    
    private fun expandFab() {
        isFabExpanded = true
        
        // Show speed dial options with animation
        binding.fabOption1.visibility = View.VISIBLE
        binding.fabOption2.visibility = View.VISIBLE
        binding.fabOption3.visibility = View.VISIBLE
        
        // Animate FABs appearing
        animateFab(binding.fabOption1, 0, 100f)
        animateFab(binding.fabOption2, 50, 150f)
        animateFab(binding.fabOption3, 100, 200f)
        
        // Rotate main FAB icon
        ObjectAnimator.ofFloat(binding.expandableFab, "rotation", 0f, 45f).apply {
            duration = 300
            start()
        }
    }
    
    private fun collapseFab() {
        isFabExpanded = false
        
        // Hide speed dial options with animation
        animateFabOut(binding.fabOption3, 0)
        animateFabOut(binding.fabOption2, 50)
        animateFabOut(binding.fabOption1, 100)
        
        // Rotate main FAB icon back
        ObjectAnimator.ofFloat(binding.expandableFab, "rotation", 45f, 0f).apply {
            duration = 300
            start()
        }
    }
    
    private fun animateFab(fab: View, delay: Long, translationY: Float) {
        fab.alpha = 0f
        fab.translationY = translationY
        
        fab.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }
    
    private fun animateFabOut(fab: View, delay: Long) {
        fab.animate()
            .alpha(0f)
            .translationY(100f)
            .setStartDelay(delay)
            .setDuration(150)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                fab.visibility = View.GONE
            }
            .start()
    }
}