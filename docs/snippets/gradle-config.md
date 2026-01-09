# Dynamic Feature Module - Gradle Configuration Snippets

Production-ready Gradle configuration for Dynamic Feature Modules using GloballyDynamic.

## 1. Project-level build.gradle.kts

```kotlin
// Top-level build file
buildscript {
    dependencies {
        classpath("com.jeppeman.globallydynamic.gradle:plugin:1.3.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
}
```

## 2. libs.versions.toml

```toml
[versions]
globallyDynamic = "1.3.0"

[libraries]
globallydynamic-selfhosted = { group = "com.jeppeman.globallydynamic.android", name = "selfhosted", version.ref = "globallyDynamic" }

[plugins]
android-dynamic-feature = { id = "com.android.dynamic-feature", version.ref = "agp" }
globallydynamic = { id = "com.jeppeman.globallydynamic", version = "1.3.0" }
```

## 3. App Module build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.jeppeman.globallydynamic")
}

android {
    // ... existing config ...

    // Declare dynamic feature modules
    dynamicFeatures += setOf(":featureModuleName")
}

dependencies {
    // GloballyDynamic for on-demand delivery
    implementation(libs.globallydynamic.selfhosted)

    // Shared contract module (optional)
    implementation(project(":shared"))
}
```

## 4. Dynamic Feature Module build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.feature"
    compileSdk = 36

    defaultConfig {
        minSdk = 31  // Match app module
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // REQUIRED: Reference to app module
    implementation(project(":app"))

    // Shared contract for interfaces
    implementation(project(":shared"))
}
```

## 5. Shared Contract Module build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
```

## 6. settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "YourProjectName"
include(":app")
include(":shared")
include(":featureModuleName")
```

## 7. Dynamic Feature AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:dist="http://schemas.android.com/apk/distribution">

    <!-- On-demand delivery (user-triggered download) -->
    <dist:module
        dist:instant="false"
        dist:title="@string/title_feature_module">
        <dist:delivery>
            <dist:on-demand />
        </dist:delivery>
        <dist:fusing dist:include="true" />
    </dist:module>

    <application />
</manifest>
```

For install-time delivery (bundled with app):

```xml
<dist:delivery>
    <dist:install-time />
</dist:delivery>
```

## Key Points

1. **GloballyDynamic vs Play Core**: GloballyDynamic provides Play Store-independent delivery
2. **selfhosted artifact**: Use for apps distributed outside Play Store
3. **Shared module**: Contains interfaces/contracts between app and feature modules
4. **No SplitCompat needed**: GloballyDynamic handles resource merging automatically
