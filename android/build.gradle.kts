import org.jetbrains.kotlin.gradle.dsl.JvmTarget

android {
    namespace = "com.tanda"
    defaultConfig {
        applicationId = "com.tanda"
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.ui)
    implementation(projects.app)

    implementation(libs.koin.core)
    implementation(libs.koin.annotation)

    implementation(projects.feature.biometrics.domain)
    implementation(projects.feature.biometrics.data)
    implementation(projects.feature.biometrics.device)

    implementation(projects.feature.scanner.libusb)
    implementation(projects.feature.scanner.liblfd)
    implementation(projects.feature.scanner.libibscancommon)
    implementation(projects.feature.scanner.libibscanuitimate)
}
