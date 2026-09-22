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
            val ksFile = file("../../keystore/aria-release.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("ARIA_KEYSTORE_PASSWORD") ?: "changeit"
                keyAlias = System.getenv("ARIA_KEY_ALIAS") ?: "aria"
                keyPassword = System.getenv("ARIA_KEY_PASSWORD") ?: "changeit"
            }
        }
    }

    buildTypes {
        release {
            minifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val ksFile = file("../../keystore/aria-release.jks")
            if (ksFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
