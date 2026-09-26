plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.blake.robotoboldinstaller"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blake.robotoboldinstaller"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/fontAssets"))
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}

val copyFontPackageApk by tasks.registering(Copy::class) {
    dependsOn(":fontPackage:assembleRelease")
    from(project(":fontPackage").layout.buildDirectory.file("outputs/apk/release/fontPackage-release.apk"))
    into(layout.buildDirectory.dir("generated/fontAssets"))
    rename { "roboto-bold-font.apk" }
}

tasks.named("preBuild") {
    dependsOn(copyFontPackageApk)
}
