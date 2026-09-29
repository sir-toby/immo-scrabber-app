// PROTOTYPE – Swipe-Bewertung (Wayfinder-Ticket "Swipe-Bewertung: Interaktionsmodell"). Wegwerf-Code.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.immoscrabber.prototype"
    compileSdk = 36
    defaultConfig {
        applicationId = "de.immoscrabber.prototype"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-prototype"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
}
