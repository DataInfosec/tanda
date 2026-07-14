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
    implementation(projects.app)
}
