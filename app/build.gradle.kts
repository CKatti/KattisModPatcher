plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.katti.patcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.katti.patcher"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.add("x86_64")
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.ackpine.core)   // Installating APK
    implementation(libs.ackpine.ktx)    // Ackpine Kotlin extensions
    implementation(libs.apksig)         // Signing APK
    implementation(libs.arsclib)        // Modfying ARSC and zip aligning
    implementation(libs.dexlib2)        // MOdify .dex files
    implementation(libs.zip4j)          // Zipping and unzipping
}