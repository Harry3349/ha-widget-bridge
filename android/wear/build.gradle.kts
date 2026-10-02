plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Wear-OS-Modul: liefert eine Tile mit denselben Widget-Definitionen wie die
// Handy-App. Bewusst ohne Compose, damit der Bauaufwand klein bleibt.
android {
    namespace = "de.reimann.hawidget.wear"
    compileSdk = 35

    defaultConfig {
        // WICHTIG: identisch mit der Handy-App. Der Wearable Data Layer ordnet
        // Nachrichten und Daten über den *Paketnamen* zu – bei anderem Namen
        // scheitert die Zustellung ("Failed to deliver message to AppKey").
        // Telefon- und Uhren-App werden nie auf demselben Gerät installiert.
        applicationId = "de.reimann.hawidget"
        // Wear OS 3 = API 30; die Pixel Watch 3 läuft mit API 37
        minSdk = 30
        targetSdk = 35
        versionCode = 6
        versionName = "0.1.9"
        resourceConfigurations += listOf("de", "en")
    }

    // Fester Debug-Schlüssel (wie in der Handy-App), damit Updates installierbar bleiben
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Stabile Tile-Bibliothek (Wear OS 3+). Die neuen "Wear Widgets" werden auf
    // der Pixel Watch 3 (keine Teilhöhen) ohnehin als Tile dargestellt.
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("com.google.guava:guava:32.1.3-android")
    // Daten und Befehle laufen über die Handy-App (Wearable Data Layer)
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
