plugins {
    kotlin("plugin.serialization") version libs.versions.serialization.get()
}

apply("$rootDir/gradle/configuration.gradle")

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
        val commonMain by getting { kotlin.srcDir("$buildDir/generated/source/buildConfig") }
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.persistence)
            implementation(projects.core.remote)
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

            implementation(libs.ktor.client)
            implementation(libs.ktor.json)
            implementation(libs.ktor.negotiation)

            implementation(libs.koin.core)
            implementation(libs.koin.annotation)
            implementation(libs.koin.compose)
            implementation(libs.koin.viewmodel)

            implementation(projects.feature.account.domain)

            api(projects.feature.biometrics.domain)
            api(projects.feature.biometrics.data)
            api(projects.feature.biometrics.device)

            implementation(projects.feature.biometrics.verification)
            implementation(projects.feature.biometrics.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.test.coroutine)
        }
    }
}
dependencies { debugImplementation(compose.uiTooling) }
tasks.named("preBuild") { dependsOn("generateBuildConstants") }

tasks.configureEach {
    if ((name.startsWith("compile") && name.contains("Kotlin")) || name.startsWith("ksp")) {
        dependsOn("generateBuildConstants")
    }
}
