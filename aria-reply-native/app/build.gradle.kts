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
        versionCode = 27
        versionName = "27.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    signingConfigs {
        create("release") {
            val filePath = providers.environmentVariable("ARIA_KEYSTORE_FILE").orNull
            val password = providers.environmentVariable("ARIA_KEYSTORE_PASSWORD").orNull
            val alias = providers.environmentVariable("ARIA_KEY_ALIAS").orNull
            val keyPassword = providers.environmentVariable("ARIA_KEY_PASSWORD").orNull
            if (!filePath.isNullOrBlank() && !password.isNullOrBlank() && !alias.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
                storeFile = file(filePath)
                storePassword = password
                keyAlias = alias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val signing = signingConfigs.getByName("release")
            if (signing.storeFile?.exists() == true) signingConfig = signing
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
