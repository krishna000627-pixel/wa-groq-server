plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aria.reply"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aria.reply"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "11.0"
    }

    signingConfigs {
        create("release") {
            val ks = rootProject.file("keystore/aria-release.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = providers.gradleProperty("ARIA_KEYSTORE_PASSWORD").orElse("M6sbMt7Y32asaB7lcH2zEhyV-6G1waw9").get()
                keyAlias = providers.gradleProperty("ARIA_KEY_ALIAS").orElse("aria-release").get()
                keyPassword = providers.gradleProperty("ARIA_KEY_PASSWORD").orElse("M6sbMt7Y32asaB7lcH2zEhyV-6G1waw9").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseKeystore = rootProject.file("keystore/aria-release.jks")
            if (releaseKeystore.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
