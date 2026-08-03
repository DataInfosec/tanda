kotlin {
    androidTarget()
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "AccountDomain"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)
        }
    }
}
