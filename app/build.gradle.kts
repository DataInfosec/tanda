kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "App"
            isStatic = true
        }
    }
    
    androidLibrary { namespace = "com.tanda.app" }
    
    sourceSets {
        androidMain.dependencies {
            implementation(projects.feature.scanner.libusb)
            implementation(projects.feature.scanner.liblfd)
            implementation(projects.feature.scanner.libibscancommon)
            implementation(projects.feature.scanner.libibscanuitimate)

            implementation(libs.compose.uiToolingPreview)
        }
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.persistence)
            implementation(projects.core.ui)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)
            implementation(libs.koin.compose)
            implementation(libs.koin.viewmodel)

            implementation(projects.feature.preference)

            implementation(projects.feature.biometrics.domain)
            implementation(projects.feature.biometrics.data)
            implementation(projects.feature.biometrics.device)
            implementation(projects.feature.biometrics.verification)
            implementation(projects.feature.biometrics.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
