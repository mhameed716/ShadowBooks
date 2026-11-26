plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val generatedStudyAssets = layout.buildDirectory.dir("generated/study-assets")

android {
    namespace = "com.shadowbooks"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shadowbooks"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0"
        manifestPlaceholders["appLabel"] = "ShadowBooks"
    }

    signingConfigs {
        // One permanent release key for every future build, so updates always install
        // over the previous version. The keystore itself is stored in the repository
        // only in encrypted form (keystore/shadowbooks-release.p12.enc); CI decrypts it
        // with the SHADOWBOOKS_SIGNING_PASSWORD secret and points these env vars at it.
        // Without the env vars the release build stays unsigned rather than silently
        // falling back to a throwaway debug key.
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
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
        }
        // A small, installable sibling of the regular app. Its data/cache never mix with the full edition.
        create("trial") {
            initWith(getByName("release"))
            applicationIdSuffix = ".trial"
            versionNameSuffix = "-trial"
            manifestPlaceholders["appLabel"] = "ShadowBooks تجريبي"
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    sourceSets {
        // The trial stays lightweight. Full variants package downloaded assets from the ignored build directory.
        getByName("main").assets.setSrcDirs(listOf("src/shared/assets"))
        getByName("debug").assets.srcDir("src/main/assets")
        getByName("debug").assets.srcDir(generatedStudyAssets)
        getByName("release").assets.srcDir("src/main/assets")
        getByName("release").assets.srcDir(generatedStudyAssets)
        // src/trial/assets provides the small sample; shared assets remain available to every variant.
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // Pure Java numerical routines. The calculator never loads code or data from the network.
    implementation("org.apache.commons:commons-math3:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
