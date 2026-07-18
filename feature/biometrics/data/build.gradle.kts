kotlin {
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
            implementation(projects.feature.biometrics.domain)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)
        }
    }
}
