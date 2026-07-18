kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "CoreUi"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodelNavigation3)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)
            implementation(libs.koin.compose)
        }
    }
}
