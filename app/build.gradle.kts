import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        }
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.persistence)
            implementation(projects.core.ui)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.navigation)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)

            implementation(projects.feature.preference)

            implementation(projects.feature.account.domain)
            implementation(projects.feature.account.data)
            implementation(projects.feature.account.remote)

            implementation(projects.feature.attendance)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
dependencies { debugImplementation(compose.uiTooling) }
