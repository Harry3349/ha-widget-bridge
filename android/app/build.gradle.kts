plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.reimann.hawidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.reimann.hawidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "0.1.14"
        resourceConfigurations += listOf("de", "en")
    }

    // Fester Debug-Schlüssel (identisch in CI und lokal, damit Updates installierbar bleiben)
    signingConfigs {
        getByName("debug") {
            val keystore = File(System.getProperty("user.home"), ".android/debug.keystore")
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        compose = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Wearable Data Layer: Snapshot an die Uhr übertragen und Befehle empfangen
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
