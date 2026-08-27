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
            baseName = "Attendance"
            isStatic = true
        }
    }
    sourceSets {
        androidMain.dependencies {
            api(projects.feature.scanner.libusb)
            api(projects.feature.scanner.liblfd)
            api(projects.feature.scanner.libibscancommon)
            api(projects.feature.scanner.libibscanuitimate)

            implementation(compose.preview)
        }
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.ui)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

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
            implementation(libs.koin.viewmodel)

            implementation(projects.feature.account.domain)

            implementation(projects.feature.biometrics.domain)
            implementation(projects.feature.biometrics.ui)
        }
    }
}
dependencies { debugImplementation(compose.uiTooling) }
