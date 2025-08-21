package com.material3.showcase

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.material3.showcase.databinding.ActivityExpandingButtonsBinding

class ExpandingButtonsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExpandingButtonsBinding
    private var isAnimating = false
    private var isExpanded = false
    private var currentAnimator: ObjectAnimator? = null
    
    // Animation parameters controlled by sliders
    private var animationDuration = 700L
    private var fadeStartThreshold = 0.3f
    private var selectedFadeCurve = FadeCurve.EASE_IN
    
    enum class FadeCurve {
        LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force enable animations regardless of system settings
        forceEnableAnimations()
        
        binding = ActivityExpandingButtonsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupWindowInsets()
        setupToolbar()
        setupAnimationControls()
        setupMovingDot()
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
    
    private fun setupAnimationControls() {
        val speedSlider = findViewById<com.google.android.material.slider.Slider>(R.id.speedSlider)
        val speedLabel = findViewById<TextView>(R.id.speedLabel)
        val fadeStartSlider = findViewById<com.google.android.material.slider.Slider>(R.id.fadeStartSlider)
        val fadeStartLabel = findViewById<TextView>(R.id.fadeStartLabel)
        val fadeCurveToggleGroup = findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.fadeCurveToggleGroup)
        
        // Setup speed slider
        speedSlider.addOnChangeListener { _, value, _ ->
            animationDuration = value.toLong()
            speedLabel.text = "${value.toInt()}ms"
        }
        
        // Setup fade start slider
        fadeStartSlider.addOnChangeListener { _, value, _ ->
            fadeStartThreshold = value / 100f
            fadeStartLabel.text = "${value.toInt()}%"
        }
        
        // Setup fade curve toggle buttons
        fadeCurveToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedFadeCurve = when (checkedId) {
                    R.id.linearButton -> FadeCurve.LINEAR
                    R.id.easeInButton -> FadeCurve.EASE_IN
                    R.id.easeOutButton -> FadeCurve.EASE_OUT
                    R.id.easeInOutButton -> FadeCurve.EASE_IN_OUT
                    else -> FadeCurve.EASE_IN
                }
            }
        }
    }
    
    private fun applyFadeCurve(linearProgress: Float): Float {
        return when (selectedFadeCurve) {
            FadeCurve.LINEAR -> linearProgress
            FadeCurve.EASE_IN -> linearProgress * linearProgress
            FadeCurve.EASE_OUT -> kotlin.math.sqrt(linearProgress)
            FadeCurve.EASE_IN_OUT -> {
                // S-curve: slow start, fast middle, slow end
                if (linearProgress < 0.5f) {
                    2f * linearProgress * linearProgress
                } else {
                    1f - 2f * (1f - linearProgress) * (1f - linearProgress)
                }
            }
        }
    }
    
    private fun setupMovingDot() {
        val container = binding.buttonContainer1
        
        container.setOnClickListener {
            if (!isAnimating) {
                if (!isExpanded) {
                    Log.d("MovingDot", "Container tapped - starting square slide animation")
                    startExpandingAnimation()
                } else {
                    Log.d("MovingDot", "Container tapped - starting square return animation")
                    startCollapseAnimation()
                }
            } else {
                Log.d("MovingDot", "Animation already in progress")
            }
        }
        
        Log.d("MovingDot", "Container setup complete - squares ready to slide")
    }
    
    private fun startExpandingAnimation() {
        val container = binding.buttonContainer1
        
        // Get all squares
        val square1 = findViewById<View>(R.id.square1)
        val square2 = findViewById<View>(R.id.square2)
        val square3 = findViewById<View>(R.id.square3)
        val square4 = findViewById<View>(R.id.square4)
        val square5 = findViewById<View>(R.id.square5)
        
        // Calculate vertical sliding - from top to near bottom of container
        val containerHeight = container.height.toFloat()
        val squareSize = 60f * resources.displayMetrics.density // Convert dp to px (now 60dp)
        val topMargin = 10f * resources.displayMetrics.density // Convert 10dp to px
        val bottomMargin = 20f * resources.displayMetrics.density // Leave 20dp from bottom
        
        // Calculate slide distance from top position to near bottom
        val slideDistance = containerHeight - squareSize - topMargin - bottomMargin
        
        // All squares slide down this distance
        val finalYPosition = slideDistance
        
        // Calculate covering overlay height (100dp converted to pixels)
        val coveringHeight = 100f * resources.displayMetrics.density
        
        Log.d("MovingDot", "Starting square slide animation")
        Log.d("MovingDot", "Container height: ${containerHeight}px, Square size: ${squareSize}px")
        Log.d("MovingDot", "Slide distance: ${slideDistance}px")
        Log.d("MovingDot", "Covering overlay height: ${coveringHeight}px")
        
        isAnimating = true
        
        // Set all squares to start position (inside container) and make them visible
        square1.translationY = 0f
        square2.translationY = 0f
        square3.translationY = 0f
        square4.translationY = 0f
        square5.translationY = 0f
        
        // Make squares visible and set initial alpha to 0 (fully transparent)
        square1.visibility = View.VISIBLE
        square2.visibility = View.VISIBLE
        square3.visibility = View.VISIBLE
        square4.visibility = View.VISIBLE
        square5.visibility = View.VISIBLE
        
        square1.alpha = 0f
        square2.alpha = 0f
        square3.alpha = 0f
        square4.alpha = 0f
        square5.alpha = 0f
        
        // Enable hardware acceleration for all squares
        listOf(square1, square2, square3, square4, square5).forEach {
            it.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        
        // Use dynamic animation duration from slider
        
        // Create animators for each square - all slide down together with fade in
        val animator1 = ValueAnimator.ofFloat(0f, finalYPosition).apply {
            duration = animationDuration
            interpolator = OvershootInterpolator(2.0f)
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                square1.translationY = value
                
                // Use dynamic fade start threshold from slider
                val threshold = finalYPosition * fadeStartThreshold
                val progress = if (value <= threshold) {
                    0f // Stay invisible until threshold reached
                } else {
                    // Calculate linear progress from threshold to final position
                    val linearProgress = (value - threshold) / (finalYPosition - threshold)
                    // Apply selected fade curve
                    applyFadeCurve(linearProgress)
                }
                square1.alpha = progress.coerceIn(0f, 1f)
            }
        }
        
        val animator2 = ValueAnimator.ofFloat(0f, finalYPosition).apply {
            duration = animationDuration
            interpolator = OvershootInterpolator(2.0f)
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                square2.translationY = value
                
                // Use dynamic fade start threshold from slider
                val threshold = finalYPosition * fadeStartThreshold
                val progress = if (value <= threshold) {
                    0f // Stay invisible until threshold reached
                } else {
                    // Calculate linear progress from threshold to final position
                    val linearProgress = (value - threshold) / (finalYPosition - threshold)
                    // Apply selected fade curve
                    applyFadeCurve(linearProgress)
                }
                square2.alpha = progress.coerceIn(0f, 1f)
            }
        }
        
        val animator3 = ValueAnimator.ofFloat(0f, finalYPosition).apply {
            duration = animationDuration
            interpolator = OvershootInterpolator(2.0f)
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                square3.translationY = value
                
                // Use dynamic fade start threshold from slider
                val threshold = finalYPosition * fadeStartThreshold
                val progress = if (value <= threshold) {
                    0f // Stay invisible until threshold reached
                } else {
                    // Calculate linear progress from threshold to final position
                    val linearProgress = (value - threshold) / (finalYPosition - threshold)
                    // Apply selected fade curve
                    applyFadeCurve(linearProgress)
                }
                square3.alpha = progress.coerceIn(0f, 1f)
            }
        }
        
        val animator4 = ValueAnimator.ofFloat(0f, finalYPosition).apply {
            duration = animationDuration
            interpolator = OvershootInterpolator(2.0f)
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                square4.translationY = value
                
                // Use dynamic fade start threshold from slider
                val threshold = finalYPosition * fadeStartThreshold
                val progress = if (value <= threshold) {
                    0f // Stay invisible until threshold reached
                } else {
                    // Calculate linear progress from threshold to final position
                    val linearProgress = (value - threshold) / (finalYPosition - threshold)
                    // Apply selected fade curve
                    applyFadeCurve(linearProgress)
                }
                square4.alpha = progress.coerceIn(0f, 1f)
            }
        }
        
        val animator5 = ValueAnimator.ofFloat(0f, finalYPosition).apply {
            duration = animationDuration
            interpolator = OvershootInterpolator(2.0f)
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                square5.translationY = value
                
                // Use dynamic fade start threshold from slider
                val threshold = finalYPosition * fadeStartThreshold
                val progress = if (value <= threshold) {
                    0f // Stay invisible until threshold reached
                } else {
                    // Calculate linear progress from threshold to final position
                    val linearProgress = (value - threshold) / (finalYPosition - threshold)
                    // Apply selected fade curve
                    applyFadeCurve(linearProgress)
                }
                square5.alpha = progress.coerceIn(0f, 1f)
                
                // Log progress from one square (they all move the same)
                val timeProgress = animator.currentPlayTime.toFloat() / animator.duration
                Log.d("MovingDot", "Square slide progress: ${(timeProgress * 100).toInt()}% - Squares Y: $value, Alpha: ${square5.alpha}, FadeThreshold: $fadeStartThreshold")
            }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    Log.d("MovingDot", "Square slide animation STARTED")
                }
                
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    Log.d("MovingDot", "Square slide animation ENDED - squares spread out")
                    // Clean up hardware acceleration
                    listOf(square1, square2, square3, square4, square5).forEach {
                        it.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                    isExpanded = true
                    isAnimating = false
                }
                
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    Log.d("MovingDot", "Square slide animation CANCELLED")
                    listOf(square1, square2, square3, square4, square5).forEach {
                        it.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                    isAnimating = false
                }
                
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }
        
        // Start all animations simultaneously
        animator1.start()
        animator2.start()
        animator3.start()
        animator4.start()
        animator5.start()
    }
    
    private fun startCollapseAnimation() {
        val square1 = findViewById<View>(R.id.square1)
        val square2 = findViewById<View>(R.id.square2)
        val square3 = findViewById<View>(R.id.square3)
        val square4 = findViewById<View>(R.id.square4)
        val square5 = findViewById<View>(R.id.square5)
        
        val startPos1 = square1.translationY
        val startPos2 = square2.translationY
        val startPos3 = square3.translationY
        val startPos4 = square4.translationY
        val startPos5 = square5.translationY
        
        Log.d("MovingDot", "Starting square return animation")
        
        // Enable hardware acceleration
        listOf(square1, square2, square3, square4, square5).forEach {
            it.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        
        // Use dynamic animation duration from slider (same as expand)
        
        // Create return animators - all slide back up together
        val returnAnimator1 = ValueAnimator.ofFloat(startPos1, 0f).apply {
            duration = animationDuration
            interpolator = OvershootInterpolator(2.0f)
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                square1.translationY = value
            }
        }
        
        val returnAnimator2 = ValueAnimator.ofFloat(startPos2, 0f).apply {
            duration = animationDuration
            interpolator = OvershootInterpolator(2.0f)
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                square2.translationY = value
            }
        }
        
        val returnAnimator3 = ValueAnimator.ofFloat(startPos3, 0f).apply {
            duration = animationDuration
            interpolator = OvershootInterpolator(2.0f)
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                square3.translationY = value
            }
        }
        
        val returnAnimator4 = ValueAnimator.ofFloat(startPos4, 0f).apply {
            duration = animationDuration
            interpolator = OvershootInterpolator(2.0f)
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                square4.translationY = value
            }
        }
        
        val returnAnimator5 = ValueAnimator.ofFloat(startPos5, 0f).apply {
            duration = animationDuration
            interpolator = OvershootInterpolator(2.0f)
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                square5.translationY = value
                
                // Log progress
                val progress = animator.currentPlayTime.toFloat() / animator.duration
                Log.d("MovingDot", "Square return progress: ${(progress * 100).toInt()}% - Squares Y: $value")
            }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    Log.d("MovingDot", "Square return animation STARTED")
                }
                
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    Log.d("MovingDot", "Square return animation ENDED - squares back to origin")
                    // Clean up hardware acceleration (squares stay visible but hidden by container background)
                    listOf(square1, square2, square3, square4, square5).forEach {
                        it.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                    isExpanded = false
                    isAnimating = false
                }
                
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    Log.d("MovingDot", "Square return animation CANCELLED")
                    listOf(square1, square2, square3, square4, square5).forEach {
                        it.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                    isAnimating = false
                }
                
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }
        
        // Start all return animations simultaneously
        returnAnimator1.start()
        returnAnimator2.start()
        returnAnimator3.start()
        returnAnimator4.start()
        returnAnimator5.start()
    }
    
    /**
     * Force enable animations regardless of system animation scale settings
     * Uses reflection to override the duration scale for this app
     */
    private fun forceEnableAnimations() {
        try {
            // Get current animation scale
            val durationScale = Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 
                1f
            )
            
            Log.d("MovingDot", "Current animation duration scale: $durationScale")
            
            if (durationScale != 1f) {
                // Use reflection to set duration scale to 1.0 for this app
                val setDurationScaleMethod = ValueAnimator::class.java.getMethod(
                    "setDurationScale", 
                    Float::class.javaPrimitiveType
                )
                setDurationScaleMethod.invoke(null, 1f)
                Log.d("MovingDot", "Successfully forced animation scale to 1.0")
            } else {
                Log.d("MovingDot", "Animation scale already at 1.0, no change needed")
            }
        } catch (e: Exception) {
            Log.e("MovingDot", "Failed to override animation scale using reflection", e)
            // Could fall back to duration compensation if needed
        }
    }
}