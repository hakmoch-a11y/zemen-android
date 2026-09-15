plugins {
    id("com.android.library")
    kotlin("android")
    id("maven-publish")
}

android {
    namespace = "com.zemenai.sdk"
    compileSdk = 34

    defaultConfig {
        // minSdk 24 (Android 7.0) is deliberate — it's why AndroidHttpTransport
        // uses HttpURLConnection instead of java.net.http.HttpClient (API 34+
        // only). Raising minSdk without revisiting that choice would be safe;
        // lowering it further needs re-checking every API used here against
        // the new floor.
        minSdk = 24
        targetSdk = 34
    }

    buildFeatures {
        buildConfig = false
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
    api("androidx.appcompat:appcompat:1.7.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}

apply(from = "publishing.gradle.kts")
