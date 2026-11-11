plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from the environment (CI secrets); without it the release build is unsigned.
val releaseSigning = listOf("RAILWATCH_KEYSTORE", "RAILWATCH_STORE_PASSWORD", "RAILWATCH_KEY_ALIAS", "RAILWATCH_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank) }
val hasReleaseSigning = releaseSigning.values.all { it != null }

android {
    namespace = "dev.railwatch.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.railwatch.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    if (hasReleaseSigning) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(releaseSigning.getValue("RAILWATCH_KEYSTORE")!!)
            storePassword = releaseSigning.getValue("RAILWATCH_STORE_PASSWORD")
            keyAlias = releaseSigning.getValue("RAILWATCH_KEY_ALIAS")
            keyPassword = releaseSigning.getValue("RAILWATCH_KEY_PASSWORD")
        }
    }
    buildTypes.named("release") {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // On-device ticket reading through Google Play services (models download once; adds almost nothing to the APK).
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
