plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.plugin"
    compileSdk {
        version = release(36)
    }

    // Fix resource ID package to avoid collision with host app (0x7f)
    // Plugin uses 0x71, host app uses 0x7f
    androidResources {
        additionalParameters += listOf("--package-id", "0x71", "--allow-reserved-package-id")
    }

    defaultConfig {
        applicationId = "com.example.plugin"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // Disable some features that cause issues for plugin APKs
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Minimal dependencies for plugin
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Reference to shared module for PluginInterface (compileOnly to avoid bundling)
    compileOnly(project(":shared"))
}
