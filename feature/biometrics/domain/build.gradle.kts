kotlin {
    androidTarget()
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "BiometricsDomain"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
