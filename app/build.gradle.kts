plugins {
    id("com.android.application")
}

android {
    setCompileSdkVersion(rootProject.extra["compileSdkVersion"] as Int)

    defaultConfig {
        applicationId = "com.otaliastudios.zoom.demo"
        setMinSdkVersion(rootProject.extra["minSdkVersion"] as Int)
        setTargetSdkVersion(rootProject.extra["targetSdkVersion"] as Int)
        versionCode = 1
        versionName = "1.0"
        setProperty("archivesBaseName", "ZoomLayout_v${versionName}_($versionCode)")
    }

    // required by ExoPlayer
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("com.google.android.exoplayer:exoplayer-core:2.9.3")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.9.3")
    implementation("com.otaliastudios.opengl:egloo:0.4.0")
    implementation(project(":library"))
}
