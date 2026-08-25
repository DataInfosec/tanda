plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.cargo) apply false
    alias(libs.plugins.uniffi) apply false
}

subprojects {
    extra["android"] = listOf(":app")
    extra["composable"] = listOf(
        ":core:ui",
        ":feature:account:ui",
        ":feature:biometrics:ui",
        ":feature:attendance",
        ":feature:campus:ui",
    )
    extra["library"] = listOf(
        ":feature:scanner:liblfd",
        ":feature:scanner:libusb",
        ":feature:scanner:libibscancommon",
        ":feature:scanner:libibscanuitimate",
    )
    beforeEvaluate {
        project(path) {
            apply("$rootDir/gradle/common.gradle")
        }
    }
}
