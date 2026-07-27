import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

apply("$rootDir/gradle/configuration.gradle")

android {
    defaultConfig {
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}
kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "TandaApp"
            isStatic = true
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.splash)

            implementation(projects.feature.scanner.libusb)
            implementation(projects.feature.scanner.liblfd)
            implementation(projects.feature.scanner.libibscancommon)
            implementation(projects.feature.scanner.libibscanuitimate)
        }
        val commonMain by getting { kotlin.srcDir("$buildDir/generated/source/buildConfig") }
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.persistence)
            implementation(projects.core.ui)

            implementation(compose.components.resources)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)

            implementation(projects.feature.preference)

            implementation(projects.feature.biometrics.domain)
            implementation(projects.feature.biometrics.data)
            implementation(projects.feature.biometrics.device)
            implementation(projects.feature.biometrics.verification)
            implementation(projects.feature.biometrics.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
dependencies { debugImplementation(compose.uiTooling) }
tasks.named("preBuild") { dependsOn("generateBuildConstants") }
