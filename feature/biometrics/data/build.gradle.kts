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
            baseName = "BiometricsData"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.remote)
            implementation(projects.feature.biometrics.domain)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)

            implementation(libs.ktor.json)
            implementation(libs.ktor.client)
            implementation(libs.ktor.negotiation)
        }
    }
}
