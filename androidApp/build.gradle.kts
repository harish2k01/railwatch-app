plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from the environment (CI secrets); without it the release build is unsigned.
val releaseSigning = listOf("RAILWATCH_KEYSTORE", "RAILWATCH_STORE_PASSWORD", "RAILWATCH_KEY_ALIAS", "RAILWATCH_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank) }
val hasReleaseSigning = releaseSigning.values.all { it != null }

// Version: the release workflow passes -PappVersion=1.4.2 (from the git tag); local builds are 0.0.0-dev.
// versionCode is derived so every release is higher than the last: major*10000 + minor*100 + patch.
val appVersion = providers.gradleProperty("appVersion").orNull?.removePrefix("v") ?: "0.0.0-dev"
val appVersionCode = appVersion.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }.plus(listOf(0, 0, 0)).let { (ma, mi, pa) -> ma * 10000 + mi * 100 + pa }.coerceAtLeast(1)

android {
    namespace = "dev.railwatch.app"
    compileSdk = 37
    defaultConfig {
        // The installed identity (permanent once released); the Kotlin code keeps its dev.railwatch.app namespace.
        applicationId = "io.github.harish2k01.railwatch"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
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
    // Optimised like release but signed with the debug key, so it installs over a debug build on a test phone
    // (same signature, data kept) and shows real scrolling and start-up speed.
    buildTypes.create("preview") {
        initWith(buildTypes.getByName("release"))
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += "release"
    }
    buildFeatures { compose = true }
    // The About screen shows the licence and privacy text from these files (one source with the repo).
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/legal").get().asFile)
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

// Copies LICENSE and PRIVACY.md into the app's assets so About shows exactly what the repo publishes.
val copyLegal by tasks.registering(Copy::class) {
    from(rootProject.file("LICENSE"), rootProject.file("PRIVACY.md"), rootProject.file("MANROPE-OFL.txt"))
    into(layout.buildDirectory.dir("generated/legal/legal"))
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach { dependsOn(copyLegal) }
tasks.matching { it.name != "copyLegal" && it.name.contains("lint", ignoreCase = true) }.configureEach { dependsOn(copyLegal) }