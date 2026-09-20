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
        versionCode = 12
        versionName = "12.0"
    }

    signingConfigs {
        create("release") {
            val keystorePath = file("${rootProject.projectDir}/keystore/aria-release.jks")

            storeFile = keystorePath
            storePassword = System.getenv("ARIA_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ARIA_KEY_ALIAS")
            keyPassword = System.getenv("ARIA_KEY_PASSWORD")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
