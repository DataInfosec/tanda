plugins {
    kotlin("plugin.serialization") version libs.versions.serialization.get()
}

kotlin {
    androidTarget()
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Preference"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.settings)
            api(libs.settings.coroutine)

            implementation(projects.core.common)
            implementation(projects.core.persistence)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.test.coroutine)
            implementation(libs.settings.test)
        }
    }
}
