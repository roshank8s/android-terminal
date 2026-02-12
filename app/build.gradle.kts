plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.roshank8s.androidterminal"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.roshank8s.androidterminal"
        // Target SDK 28 to allow executing files in app data directory
        // (Android 10+ blocks exec from data dirs for apps targeting SDK 29+)
        minSdk = 24
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }

        externalNativeBuild {
            cmake {
                cFlags("-std=c11", "-Wall", "-Wextra")
            }
        }
    }

    // Signing config for release builds (uses env vars from CI or local properties)
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("RELEASE_STORE_FILE")
                ?: project.findProperty("RELEASE_STORE_FILE") as? String
            val storePass = System.getenv("RELEASE_STORE_PASSWORD")
                ?: project.findProperty("RELEASE_STORE_PASSWORD") as? String
            val keyAliasName = System.getenv("RELEASE_KEY_ALIAS")
                ?: project.findProperty("RELEASE_KEY_ALIAS") as? String
            val keyPass = System.getenv("RELEASE_KEY_PASSWORD")
                ?: project.findProperty("RELEASE_KEY_PASSWORD") as? String

            if (storeFilePath != null && storePass != null && keyAliasName != null && keyPass != null) {
                storeFile = file(storeFilePath)
                storePassword = storePass
                keyAlias = keyAliasName
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Use release signing config if keystore is configured
            val releaseConfig = signingConfigs.findByName("release")
            if (releaseConfig?.storeFile != null) {
                signingConfig = releaseConfig
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.tukaani:xz:1.9")
}
