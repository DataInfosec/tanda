kotlin {
    androidTarget()
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
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(projects.core.common)

            implementation(libs.lifecycle.viewmodelNavigation3)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.compose.material3)
            implementation(libs.compose.uiToolingPreview)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)
            implementation(libs.koin.compose)
        }
    }
}
