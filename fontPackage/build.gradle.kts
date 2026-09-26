plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.monotype.android.font.robotobold"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.monotype.android.font.robotobold"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
