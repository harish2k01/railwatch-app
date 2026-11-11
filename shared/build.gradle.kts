// Shared RailWatch core (Kotlin Multiplatform): rules, parsing, storage and use cases. Android today; iOS later.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    androidLibrary {
        namespace = "dev.railwatch.shared"
        compileSdk = 37
        minSdk = 26
    }
    // Plain JVM target so the rules and parity fixtures run as fast host tests.
    jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
