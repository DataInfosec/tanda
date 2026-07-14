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
    }
}

include(":core:common")
include(":core:persistence")
include(":core:ui")

include(":feature:account:domain")
include(":feature:account:data")
include(":feature:account:ui")

include(":feature:biometrics:domain")
include(":feature:biometrics:data")
include(":feature:biometrics:device")
include(":feature:biometrics:ui")

include(":feature:scanner:libibscancommon")
include(":feature:scanner:libibscanuitimate")
include(":feature:scanner:liblfd")
include(":feature:scanner:libusb")

include(":android")
include(":app")
