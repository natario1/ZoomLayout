import io.deepmedia.tools.publisher.common.License
import io.deepmedia.tools.publisher.common.Release
import io.deepmedia.tools.publisher.common.GithubScm
import io.deepmedia.tools.publisher.sonatype.Sonatype

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("io.deepmedia.tools.publisher")
}

android {
    compileSdk = 31
    defaultConfig {
        minSdk = 16
        targetSdk = 31
    }
    buildTypes {
        get("release").consumerProguardFile("proguard-rules.pro")
    }
}

dependencies {
    api("androidx.annotation:annotation:1.3.0")
    api("com.otaliastudios.opengl:egloo:0.6.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

publisher {
    project.description = "A collection of Android components that support zooming and panning of " +
            "View hierarchies, images, video streams, and much more."
    project.artifact = "zoomlayout"
    project.group = "com.otaliastudios"
    project.url = "https://github.com/natario1/ZoomLayout"
    project.scm = GithubScm("natario1", "ZoomLayout")

    project.addLicense(License.APACHE_2_0)
    project.addDeveloper("Mattia Iavarone", "mat.iavarone@gmail.com")
    // project.addDeveloper("Markus Ressel", email = "???")
    release.sources = Release.SOURCES_AUTO
    release.docs = Release.DOCS_AUTO
    release.version = "1.9.0"

    directory()

    sonatype {
        auth.user = "SONATYPE_USER"
        auth.password = "SONATYPE_PASSWORD"
        signing.key = "SIGNING_KEY"
        signing.password = "SIGNING_PASSWORD"
    }

    sonatype("snapshot") {
        repository = Sonatype.OSSRH_SNAPSHOT_1
        release.version = "latest-SNAPSHOT"
        auth.user = "SONATYPE_USER"
        auth.password = "SONATYPE_PASSWORD"
        signing.key = "SIGNING_KEY"
        signing.password = "SIGNING_PASSWORD"
    }
}
