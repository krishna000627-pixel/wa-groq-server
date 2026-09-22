plugins {
    id("com.android.application")
}

android {
    namespace = "com.aria.reply"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aria.reply"
        minSdk = 26
        targetSdk = 35
        versionCode = 30
        versionName = "30.0"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ARIA_KEYSTORE_FILE")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ARIA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ARIA_KEY_ALIAS")
                keyPassword = System.getenv("ARIA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
