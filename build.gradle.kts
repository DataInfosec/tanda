plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.cargo) apply false
    alias(libs.plugins.uniffi) apply false
}

subprojects {
    extra["android"] = listOf(":android")
    extra["ui"] = listOf(":app", ":feature:biometrics:ui")
    extra["extension"] = listOf(":feature:biometrics:verification")
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
