import de.immoscrabber.build.AppVersion
import java.util.Base64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Version aus dem Release-Tag (Gradle-Property oder Umgebungsvariable VERSION_TAG, z. B. "v1.2.0").
val appVersion = AppVersion.fromTag(
    providers.gradleProperty("VERSION_TAG")
        .orElse(providers.environmentVariable("VERSION_TAG"))
        .orNull,
)

val prodBaseUrl: String = providers.gradleProperty("immo.prodBaseUrl").get()

// Release-Signierung aus der Umgebung (CI-Secrets). Der Keystore kommt Base64-kodiert.
val releaseSigningEnv = listOf(
    "RELEASE_KEYSTORE_BASE64",
    "RELEASE_KEYSTORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank) }
val missingReleaseSigningEnv = releaseSigningEnv.filterValues { it == null }.keys

android {
    namespace = "de.immoscrabber.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.immoscrabber.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersion.code
        versionName = appVersion.name

        buildConfigField("String", "PROD_BASE_URL", "\"$prodBaseUrl\"")
    }

    signingConfigs {
        if (missingReleaseSigningEnv.isEmpty()) {
            create("release") {
                val keystoreFile = layout.buildDirectory.file("signing/release.p12").get().asFile
                keystoreFile.parentFile.mkdirs()
                keystoreFile.writeBytes(
                    Base64.getMimeDecoder().decode(releaseSigningEnv.getValue("RELEASE_KEYSTORE_BASE64")),
                )
                storeFile = keystoreFile
                storeType = "PKCS12"
                storePassword = releaseSigningEnv.getValue("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningEnv.getValue("RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningEnv.getValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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

    // Lint bricht nur bei Errors ab, Warnungen landen im Report; keine Baseline (Entscheidung #21).
    lint {
        abortOnError = true
        warningsAsErrors = false
        htmlReport = true
    }

    sourceSets {
        // Build-Logik (buildSrc) ist reines Kotlin und wird hier mitgetestet, damit
        // testDebugUnitTest sie abdeckt.
        getByName("test").java.srcDir("../buildSrc/src/main/kotlin")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Ohne Signier-Secrets bricht der Release-Build mit klarer Meldung ab (Debug bleibt unberührt).
val verifyReleaseSigning by tasks.registering {
    val missing = missingReleaseSigningEnv.toList()
    doLast {
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release-Signierung nicht konfiguriert: Umgebungsvariable(n) ${missing.joinToString()} fehlen. " +
                    "Benötigt werden RELEASE_KEYSTORE_BASE64, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS " +
                    "und RELEASE_KEY_PASSWORD.",
            )
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseSigning) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.browser)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
