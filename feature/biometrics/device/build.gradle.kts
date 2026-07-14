kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "BiometricsDevice"
            isStatic = true
        }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(projects.feature.scanner.liblfd)
            implementation(projects.feature.scanner.libusb)
            implementation(projects.feature.scanner.libibscancommon)
            implementation(projects.feature.scanner.libibscanuitimate)
        }
        commonMain.dependencies {
            implementation(projects.core.common)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)
        }
    }
}
