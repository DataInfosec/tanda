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
        androidMain.dependencies { implementation(compose.preview) }
        commonMain.dependencies {
            implementation(projects.core.common)

            implementation(libs.lifecycle.viewmodelNavigation3)
            implementation(libs.kotlinx.serialization.json)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.navigation)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)
            implementation(libs.koin.compose)
        }
    }
}
dependencies { debugImplementation(compose.uiTooling) }
