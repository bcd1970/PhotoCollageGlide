# Android Development Rules - Claude Code Configuration

## 1. Android-Specific CLAUDE.md (Add to project CLAUDE.md)

```markdown
# Android Development Standards

## Android UI Framework Rules
- Use Native Android Views created programmatically (no XML layouts)
- Use RecyclerView + DiffUtil for all lists
- Use Coil for all image loading
- Create views in Kotlin code programmatically
- Use ConstraintLayout programmatically for complex layouts
- Use LinearLayout/FrameLayout for simple layouts

## Android Architecture Rules
- Follow MVVM pattern with Repository pattern
- Use Clean Architecture: data → domain → presentation
- Use Kotlin coroutines and Flow for asynchronous operations
- Use Hilt for dependency injection
- Use sealed classes for UI states
- Follow Android naming conventions

## Android Performance Requirements
- Target 60fps performance (16ms frame time)
- No XML layout inflation overhead
- Efficient RecyclerView with DiffUtil
- Proper Coil caching configuration
- Minimal allocations in ViewHolder bind()
- Use View.generateViewId() for programmatic view IDs
- Cache view instances, don't recreate in bind()

## Android Testing Rules
- Test UI components with real interactions
- Test RecyclerView scrolling and data updates
- Test image loading scenarios
- Use Espresso for UI testing
- Test on different screen sizes and densities
```

## 2. Android .claude/settings.json

```json
{
  "rules": {
    "android": {
      "uiFramework": "nativeViewsProgrammatic",
      "imageLoading": "coil",
      "listComponent": "recyclerView",
      "noXMLLayouts": true,
      "performanceFirst": true,
      "architecturePattern": "mvvm",
      "dependencyInjection": "hilt"
    },
    "allowedShellCommands": [
      "gradlew",
      "adb",
      "git",
      "./gradlew",
      "ktlint",
      "detekt"
    ],
    "blockedShellCommands": [
      "rm -rf",
      "sudo",
      "chmod 777"
    ],
    "fileAccess": {
      "allowWrite": [
        "app/src/**/*.kt",
        "*.gradle.kts",
        "*.md",
        "app/src/test/**/*",
        "app/src/androidTest/**/*",
        "gradle.properties"
      ],
      "blockWrite": [
        ".git/**",
        "gradle/wrapper/**",
        "*.keystore",
        "local.properties",
        "app/src/main/res/layout/**/*.xml"
      ]
    }
  }
}
```

## 3. Android Commands

### .claude/commands/implement-android-feature.md
```markdown
# Implement Android Feature: $ARGUMENTS

## Research Phase
1. Analyze existing Android architecture patterns
2. Review current RecyclerView implementations
3. Check existing Coil usage patterns
4. Review existing ViewHolder and adapter patterns

## Implementation Phase
1. Write tests first (NO MOCK DATA - use real services)
2. Create views programmatically in Kotlin (no XML)
3. Use RecyclerView for any lists with DiffUtil
4. Use Coil for image loading with proper configuration
5. Follow MVVM pattern
6. Use Hilt for dependency injection
7. Implement proper error handling
8. Ensure 60fps performance

## Android-Specific Requirements
- No XML layout inflation
- Efficient RecyclerView scrolling
- Proper Coil caching
- Material Design compliance
- Proper lifecycle handling
```

### .claude/commands/create-recyclerview.md
```markdown
# Create RecyclerView Implementation: $ARGUMENTS

## Implementation Steps
1. Research existing RecyclerView patterns in codebase
2. Create Adapter with DiffUtil.ItemCallback
3. Create ViewHolder with programmatic view creation
4. Use Coil for image loading in ViewHolder
5. Implement proper view recycling
6. Write tests with real data

## ViewHolder Pattern
- Create views programmatically in ViewHolder constructor
- Use View.generateViewId() for view IDs
- Set up ConstraintLayout constraints programmatically
- Cache view references, don't recreate
- Use Coil for image loading with proper sizing

## Performance Rules
- No view recreation in bind()
- Use appropriate image sizes with Coil
- Configure Coil with memory/disk cache
- Use DiffUtil for efficient updates
- Target smooth 60fps scrolling
- Minimize allocations in bind() method
```

### .claude/commands/setup-coil.md
```markdown
# Setup Coil Configuration: $ARGUMENTS

## Setup Process
1. Research existing Coil configuration in codebase
2. Configure global ImageLoader in Application class if needed
3. Set up memory and disk caching
4. Update existing documentation

## Coil Usage Pattern
```kotlin
imageView.load(imageUrl) {
    crossfade(true)
    placeholder(R.drawable.placeholder)
    size(width.dp, height.dp) // Exact size for efficiency
    scale(Scale.FILL)
    memoryCachePolicy(CachePolicy.ENABLED)
    diskCachePolicy(CachePolicy.ENABLED)
}
```

## Performance Configuration
- Use exact image sizes, not larger
- Enable memory and disk caching
- Configure proper cache sizes
- Use crossfade animations
- Set appropriate placeholders
```

### .claude/commands/create-programmatic-view.md
```markdown
# Create Programmatic Android View: $ARGUMENTS

## Implementation Process
1. Research existing programmatic view patterns
2. Create view programmatically in Kotlin
3. Use ConstraintLayout for complex layouts
4. Set up proper view hierarchy and constraints
5. Test on different screen sizes

## Programmatic View Pattern
```kotlin
class CustomView(context: Context) : ConstraintLayout(context) {
    
    private val titleText = TextView(context).apply {
        id = View.generateViewId()
        // Configure text view properties
    }
    
    private val imageView = ImageView(context).apply {
        id = View.generateViewId()
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    
    init {
        addView(titleText)
        addView(imageView)
        
        ConstraintSet().apply {
            clone(this@CustomView)
            // Set up constraints programmatically
            applyTo(this@CustomView)
        }
    }
}
```

## Rules
- Create all views in constructor/init
- Use View.generateViewId() for IDs
- Set up constraints programmatically
- Use Coil for images
- No XML layouts allowed
- Follow Material Design guidelines
```

### .claude/commands/android-test.md
```markdown
# Android Test Implementation: $ARGUMENTS

## Android Testing Rules
- NO MOCK DATA: Use real services and data sources
- Test RecyclerView scrolling and interactions
- Test image loading with Coil
- Test view creation and binding
- Use Espresso for UI testing
- Test on different screen configurations

## Test Areas
- ViewHolder binding with real data
- RecyclerView adapter updates and DiffUtil
- Coil image loading scenarios
- Programmatic view creation
- User interactions and touch events
- Screen rotation and configuration changes

## Performance Testing
- Test scrolling performance (60fps)
- Memory usage during image loading
- View recycling efficiency
- UI responsiveness under load
```

### .claude/commands/android-refactor.md
```markdown
# Refactor Android Code: $ARGUMENTS

## Analysis
1. Research current Android implementation
2. Identify XML layouts that need conversion
3. Check RecyclerView efficiency
4. Verify Coil usage optimization
5. Review MVVM pattern compliance

## Android Refactoring Rules
1. Convert XML layouts to programmatic views
2. Optimize RecyclerView performance
3. Improve Coil image loading
4. Enhance MVVM pattern implementation
5. Update existing tests for Android components
6. Improve performance (target 60fps)

## Performance Focus
- Eliminate XML layout inflation
- Optimize image loading with Coil
- Improve RecyclerView scrolling
- Reduce memory allocations
- Use proper view recycling
- Configure caching appropriately
```

## 4. Android Dependencies (build.gradle.kts reference)

```kotlin
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // ConstraintLayout for programmatic layouts
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Coil for image loading
    implementation("io.coil-kt:coil:2.5.0")
    
    // Architecture Components
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    
    // Dependency Injection
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Testing
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")
}
```

## Usage Instructions

### Android Commands Usage
```bash
# Implement Android features
/implement-android-feature "Product listing screen"

# Create RecyclerView
/create-recyclerview "Product list"

# Setup image loading
/setup-coil "Configure image loading"

# Create UI components
/create-programmatic-view "Product card"

# Test Android components
/android-test "RecyclerView scrolling"

# Refactor Android code
/android-refactor "Convert XML to programmatic"
```

## Key Android Rules Summary

### UI Framework
- ✅ **Native Android Views (programmatic, no XML)**
- ✅ **RecyclerView + DiffUtil for all lists**
- ✅ **Coil for image loading (best performance)**
- ✅ **ConstraintLayout programmatically**

### Architecture
- ✅ **MVVM pattern**
- ✅ **Clean Architecture**
- ✅ **Hilt for dependency injection**
- ✅ **Coroutines and Flow**

### Performance
- ✅ **60fps target**
- ✅ **No XML inflation overhead**
- ✅ **Efficient image loading**
- ✅ **Proper view recycling**
- ✅ **Minimal memory allocations**