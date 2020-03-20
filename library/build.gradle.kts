import com.otaliastudios.tools.publisher.PublisherExtension.License
import com.otaliastudios.tools.publisher.PublisherExtension.Release

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("maven-publisher-bintray")
}

android {
    setCompileSdkVersion(property("compileSdkVersion") as Int)

    defaultConfig {
        setMinSdkVersion(property("minSdkVersion") as Int)
        setTargetSdkVersion(property("targetSdkVersion") as Int)
        versionName = "1.7.1"
    }

    buildTypes {
        get("release").consumerProguardFile("proguard-rules.pro")
    }
}

dependencies {
    val kotlinVersion = property("kotlinVersion") as String
    api("androidx.annotation:annotation:1.1.0")
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
    api("com.otaliastudios.opengl:egloo:0.4.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.1")
}

publisher {
    auth.user = "BINTRAY_USER"
    auth.key = "BINTRAY_KEY"
    auth.repo = "BINTRAY_REPO"
    project.group = "com.otaliastudios"
    project.artifact = "zoomlayout"
    project.description = "A collection of Android components that support zooming and panning of View hierarchies, images, video streams, and much more."
    project.url = "https://github.com/natario1/ZoomLayout"
    project.vcsUrl = "https://github.com/natario1/ZoomLayout.git"
    project.addLicense(License.APACHE_2_0)
    release.setSources(Release.SOURCES_AUTO)
    release.setDocs(Release.DOCS_AUTO)
}
