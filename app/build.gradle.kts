plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.kitkat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.kitkat"
        minSdk = 19
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    // Using older versions compatible with API 19
    implementation("androidx.appcompat:appcompat:1.0.0") // Last version supporting API 19

    // HTTP client with custom SSL support
    implementation("com.squareup.okhttp3:okhttp:3.12.13") // Last version supporting API 19

    // JSON parsing
    implementation("com.google.code.gson:gson:2.8.9")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}