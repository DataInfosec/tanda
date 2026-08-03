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
            baseName = "AccountRemote"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.remote)

            implementation(projects.feature.account.domain)
            implementation(projects.feature.account.data)

            implementation(libs.okio)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)

            implementation(libs.ktor.json)
            implementation(libs.ktor.client)
            implementation(libs.ktor.negotiation)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.test.coroutine)
            implementation(libs.ktor.client.mock)
        }
    }
}
