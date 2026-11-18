plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.pjcinc.csepractice"
    compileSdk = 36

    defaultConfig {
        // Placeholder — change to your real reverse-DNS before any Play Store upload.
        // Whatever this is at the time of first publish becomes permanent for that listing.
        applicationId = "com.pjcinc.csepractice"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.0-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // Online "Ask the tutor" (needs Steve's server + INTERNET permission). Off = fully offline app.
        buildConfigField("boolean", "TUTOR_ENABLED", "false")
    }

    buildTypes {
        debug {
            // Friendlier obfuscated package name in logs.
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Small, optimized APK for testers (Pearl etc.), installable side-by-side with debug.
        // Signed with the local DEBUG key on purpose: no Play Store key is created yet.
        // Never upload this one to the Play Store.
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-tester"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    kotlin {
        jvmToolchain(17)
    }
    testOptions {
        unitTests {
            // Robolectric runs the real app on the JVM; Roborazzi saves screenshots to app/build/outputs/roborazzi
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.forkEvery = 1 // fresh JVM per test class: DataStore + Room are process singletons
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX core / lifecycle / activity
    // Pinned to 1.13.1 — core-ktx 1.15+ requires compileSdk 36 + AGP 8.9.1.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Charts — Vico 2.x (Compose-native, Android-targeted). Vico 3.x requires
    // Compose Multiplatform; staying on 2.x keeps the dep footprint smaller.
    // Bug fixes for 2.x are committed through July 31, 2026 per upstream.
    // For tighter Material 3 integration, also pull in:
    //   implementation("com.patrykandpatrick.vico:compose-m3:2.4.3")
    implementation("com.patrykandpatrick.vico:compose:2.4.3")

    // QR code on share cards — zxing core only (pure Java, offline encoder, no camera/network)
    implementation("com.google.zxing:core:3.5.3")

    // JSON
    implementation("com.google.code.gson:gson:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.26.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.26.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.26.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
