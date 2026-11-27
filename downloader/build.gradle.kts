plugins {
    id("com.android.application")
}

android {
    namespace = "com.shadowbooks.downloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shadowbooks.downloader"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.0"
    }

    signingConfigs {
        // Same permanent release key as the main app (see app/build.gradle.kts), so
        // downloader updates also install over previous versions forever.
        create("release") {
            val keystorePath = System.getenv("SHADOWBOOKS_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SHADOWBOOKS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SHADOWBOOKS_KEY_ALIAS") ?: "shadowbooks"
                keyPassword = System.getenv("SHADOWBOOKS_KEY_PASSWORD")
                    ?: System.getenv("SHADOWBOOKS_KEYSTORE_PASSWORD")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
}
