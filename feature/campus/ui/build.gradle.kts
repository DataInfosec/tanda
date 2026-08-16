kotlin {
    androidTarget()
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "CampusUi"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.ui)
            implementation(projects.feature.campus.domain)
            implementation(projects.feature.biometrics.ui)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.navigation)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)
            implementation(libs.koin.compose)
            implementation(libs.koin.viewmodel)

            implementation(libs.kotlin.coil)
        }
    }
}
