rootProject.name = "Tanda"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

include(":core:common")
include(":core:persistence")
include(":core:remote")
include(":core:ui")

include(":feature:preference")

include(":feature:account:domain")
include(":feature:account:data")
include(":feature:account:remote")
include(":feature:account:ui")

include(":feature:biometrics:domain")
include(":feature:biometrics:data")
include(":feature:biometrics:device")
include(":feature:biometrics:verification")
include(":feature:biometrics:ui")

include(":feature:scanner:libibscancommon")
include(":feature:scanner:libibscanuitimate")
include(":feature:scanner:liblfd")
include(":feature:scanner:libusb")

include(":feature:attendance")
include(":feature:campus")

include(":app")
