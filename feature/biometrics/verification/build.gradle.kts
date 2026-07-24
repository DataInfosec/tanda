import gobley.gradle.rust.targets.RustAndroidTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.cargo)
    alias(libs.plugins.uniffi)
    kotlin("plugin.atomicfu") version libs.versions.kotlin.get()
    kotlin("plugin.serialization") version libs.versions.serialization.get()
}

kotlin {
    androidTarget()
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "BiometricsVerification"
            isStatic = true
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                kotlin.srcDir("build/generated/uniffi/commonMain/kotlin")

                implementation(projects.feature.biometrics.domain)

                implementation(libs.koin.core)
                implementation(libs.koin.annotation)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.tanda.biometrics.verification"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

uniffi {
    generateFromLibrary {
        namespace = "biometric_sdk"
        packageName = "com.datainfosec.biometric"
        build = RustAndroidTarget.Arm64
    }
}

// Gobley 0.3.4 copies every common project dependency into this bucket, even
// when that project does not publish UniFFI metadata. This module generates
// bindings only for its own Rust crate, so keep the bucket empty.
configurations.named("uniFfiImplementation") {
    dependencies.clear()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("buildUniffiBindings")
}

tasks.matching { it.name.startsWith("ksp") }.configureEach {
    dependsOn("buildUniffiBindings")
}
