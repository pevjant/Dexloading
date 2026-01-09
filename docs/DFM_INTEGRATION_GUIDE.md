# Dynamic Feature Module Integration Guide

Production-ready guide for integrating Dynamic Feature Modules using GloballyDynamic.

## Overview

This guide shows how to add on-demand downloadable features to your Android app without depending on Google Play Services. GloballyDynamic provides a Play Store-independent implementation of the Play Core API.

## Prerequisites

- Android Gradle Plugin 8.0+
- Kotlin 1.9+
- minSdk 21+ (recommended: 31+)

## Quick Start

### 1. Add Dependencies

**gradle/libs.versions.toml**

```toml
[versions]
globallyDynamic = "1.3.0"

[libraries]
globallydynamic-selfhosted = { group = "com.jeppeman.globallydynamic.android", name = "selfhosted", version.ref = "globallyDynamic" }

[plugins]
android-dynamic-feature = { id = "com.android.dynamic-feature", version.ref = "agp" }
```

### 2. Configure App Module

**app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Declare dynamic feature modules
    dynamicFeatures += setOf(":yourFeature")
}

dependencies {
    implementation(libs.globallydynamic.selfhosted)
}
```

### 3. Create Dynamic Feature Module

**settings.gradle.kts**

```kotlin
include(":yourFeature")
```

**yourFeature/build.gradle.kts**

```kotlin
plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.yourfeature"
    compileSdk = 36

    defaultConfig {
        minSdk = 31  // Match app module
    }
}

dependencies {
    implementation(project(":app"))
}
```

**yourFeature/src/main/AndroidManifest.xml**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:dist="http://schemas.android.com/apk/distribution">

    <dist:module
        dist:instant="false"
        dist:title="@string/title_your_feature">
        <dist:delivery>
            <dist:on-demand />
        </dist:delivery>
        <dist:fusing dist:include="true" />
    </dist:module>

    <application />
</manifest>
```

### 4. Copy Core Files

Copy these files to your app:

| File | Purpose |
|------|---------|
| `DynamicModuleLoader.kt` | Generic module loader with progress tracking |
| `ModuleRegistry.kt` | Optional: centralized module configuration |

### 5. Register Your Module

**ModuleRegistry.kt** (if using)

```kotlin
private val modules = mapOf(
    "yourFeature" to ModuleConfig(
        moduleName = "yourFeature",
        entryPoint = "com.example.yourfeature.YourFeatureImpl",
        displayName = "Your Feature",
        description = "Feature description"
    )
)
```

### 6. Load Module

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var moduleLoader: DynamicModuleLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moduleLoader = DynamicModuleLoader(this)
    }

    fun loadFeature() {
        val moduleName = "yourFeature"

        if (moduleLoader.isInstalled(moduleName)) {
            // Module ready - load class
            useFeature(moduleName)
        } else {
            // Install first
            moduleLoader.install(
                moduleName = moduleName,
                onProgress = { progress ->
                    updateProgressUI(progress)
                },
                onSuccess = {
                    useFeature(moduleName)
                },
                onFailure = { error ->
                    showError(error)
                }
            )
        }
    }

    private fun useFeature(moduleName: String) {
        val entryPoint = ModuleRegistry.getEntryPoint(moduleName) ?: return
        val feature = moduleLoader.loadAndInstantiate<YourInterface>(entryPoint)
        feature?.doSomething()
    }
}
```

## Shared Contract Pattern

For type-safe communication between app and feature modules, create a shared library module.

### 1. Create Shared Module

**shared/build.gradle.kts**

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
```

**shared/src/main/java/com/example/shared/FeatureInterface.kt**

```kotlin
interface FeatureInterface {
    fun getName(): String
    fun getVersion(): String
    fun initialize(context: Context)
    fun createView(context: Context): View
    fun onDestroy()
}
```

### 2. Implement in Feature Module

**yourFeature/build.gradle.kts**

```kotlin
dependencies {
    implementation(project(":app"))
    implementation(project(":shared"))
}
```

**yourFeature/.../YourFeatureImpl.kt**

```kotlin
class YourFeatureImpl : FeatureInterface {
    override fun getName() = "Your Feature"
    override fun getVersion() = "1.0.0"
    // ... implement other methods
}
```

## Delivery Options

### On-Demand (User Triggered)

```xml
<dist:delivery>
    <dist:on-demand />
</dist:delivery>
```

Best for: Large features, rarely used functionality

### Install-Time (Bundled)

```xml
<dist:delivery>
    <dist:install-time />
</dist:delivery>
```

Best for: Core features, frequently used functionality

### Conditional (Device-based)

```xml
<dist:delivery>
    <dist:install-time>
        <dist:conditions>
            <dist:device-feature dist:name="android.hardware.camera" />
            <dist:min-sdk dist:value="24" />
        </dist:conditions>
    </dist:install-time>
</dist:delivery>
```

## Testing

### Local Testing (Without Server)

```bash
# Build debug APKs
./gradlew assembleDebug

# Install base APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Install feature APK
adb install yourFeature/build/outputs/apk/debug/yourFeature-debug.apk
```

### Bundle Testing

```bash
# Build bundle
./gradlew bundleDebug

# Use bundletool to test
bundletool build-apks --bundle=app/build/outputs/bundle/debug/app-debug.aab \
    --output=app.apks --local-testing

bundletool install-apks --apks=app.apks
```

## Troubleshooting

### Module Not Found

- Verify module name in `settings.gradle.kts`
- Check `dynamicFeatures` in app's `build.gradle.kts`
- Ensure feature module has `implementation(project(":app"))`

### Class Not Found After Install

- Module may need app restart for class loading
- Verify entry point class name is correct
- Check ProGuard/R8 rules preserve the entry class

### Resources Not Accessible

- GloballyDynamic handles resource merging automatically
- No SplitCompat.install() needed
- If issues persist, verify AndroidManifest configuration

## File Reference

| File | Location | Purpose |
|------|----------|---------|
| `DynamicModuleLoader.kt` | `app/.../dynamic/` | Module installation and loading |
| `ModuleRegistry.kt` | `app/.../dynamic/` | Module configuration registry |
| `gradle-config.md` | `docs/snippets/` | Gradle configuration snippets |

## GloballyDynamic vs Play Core

| Feature | GloballyDynamic | Play Core |
|---------|-----------------|-----------|
| Play Store Required | No | Yes |
| Self-hosted Server | Yes | No |
| API Compatibility | 100% | - |
| Package Prefix | `com.jeppeman.globallydynamic` | `com.google.android.play.core` |
