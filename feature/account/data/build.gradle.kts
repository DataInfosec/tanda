kotlin {
    androidTarget()
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "AccountData"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.persistence)

            implementation(projects.feature.account.domain)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)
        }
    }
}
