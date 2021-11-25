plugins {
    id("com.android.application")
}

android {
    compileSdk = 31

    defaultConfig {
        applicationId = "com.otaliastudios.zoom.demo"
        minSdk = 21
        targetSdk = 31
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
    implementation("androidx.appcompat:appcompat:1.4.0")
    implementation("com.google.android.exoplayer:exoplayer-core:2.16.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.16.1")
    implementation("com.otaliastudios.opengl:egloo:0.6.1")
    implementation(project(":library"))
}
