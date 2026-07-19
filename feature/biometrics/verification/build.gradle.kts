import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.cargo)
    alias(libs.plugins.uniffi)
    kotlin("plugin.atomicfu") version libs.versions.kotlin.get()
    kotlin("plugin.serialization") version libs.versions.serialization.get()
}

kotlin {
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
        commonMain.dependencies {
//            implementation(projects.feature.biometrics.domain)
//
//            implementation(libs.koin.core)
//            implementation(libs.koin.annotation)
        }
    }
}

//configurations {
//    findByName("uniFfiConfiguration")?.let { remove(it) }
//    create("uniFfiConfiguration") {
//        isCanBeResolved = true
//        isCanBeConsumed = false
//    }
//    matching { it.name.contains("RustRuntime") || it.name.contains("minGWX64") }
//        .all {
//            exclude(group = "Tenda.feature.biometrics", module = "domain")
//        }
//}

//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
//    dependsOn("buildUniffiBindings")
//}
//
//tasks.matching { it.name.startsWith("ksp") }.configureEach {
//    dependsOn("buildUniffiBindings")
//}
