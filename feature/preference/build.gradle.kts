plugins {
    kotlin("plugin.serialization") version libs.versions.serialization.get()
}

kotlin {
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
            implementation(projects.core.common)
            implementation(projects.core.persistence)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)

            implementation(libs.settings)
            implementation(libs.settings.coroutine)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.test.coroutine)
            implementation(libs.settings.test)
        }
    }
}
