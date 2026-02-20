plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.moneymind"
    compileSdk = 35

    signingConfigs {
        create("installCompat") {
            storeFile = file("C:/temp/moneymind-release.jks")
            storePassword = "moneymind2026"
            keyAlias = "moneymind"
            keyPassword = "moneymind2026"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.moneymind.personal"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"
    }

    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "NOTIFICATION_CAPTURE_ENABLED", "true")
        }
        create("safe") {
            dimension = "edition"
            applicationIdSuffix = ".safe"
            versionNameSuffix = "-safe"
            buildConfigField("boolean", "NOTIFICATION_CAPTURE_ENABLED", "false")
            resValue("string", "app_name", "MoneyMind SAFE")
        }
        create("safeplus") {
            dimension = "edition"
            applicationIdSuffix = ".safe"
            versionNameSuffix = "-safeplus"
            versionCode = 5
            buildConfigField("boolean", "NOTIFICATION_CAPTURE_ENABLED", "true")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".trial"
            versionNameSuffix = "-trial"
        }
        release {
            applicationIdSuffix = ".install"
            versionNameSuffix = "-install"
            signingConfig = signingConfigs.getByName("installCompat")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("play") {
            initWith(getByName("release"))
            applicationIdSuffix = ""
            versionNameSuffix = ""
            signingConfig = signingConfigs.getByName("installCompat")
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            matchingFallbacks += listOf("release")
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
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.01")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.apache.commons:commons-csv:1.11.0")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
}

tasks.matching { it.name == "compileReleaseArtProfile" || it.name == "compilePlayArtProfile" }.configureEach {
    enabled = false
}

tasks.register<Zip>("zipFullPlayNativeDebugSymbols") {
    group = "distribution"
    description = "Packages fullPlay native debug symbols for Play Console upload."
    dependsOn("assembleFullPlay")

    from(layout.buildDirectory.dir("intermediates/merged_native_libs/fullPlay/mergeFullPlayNativeLibs/out/lib"))
    includeEmptyDirs = false
    archiveFileName.set("native-debug-symbols-fullPlay.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/native-debug-symbols/fullPlay"))
}
