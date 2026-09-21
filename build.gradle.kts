plugins {
    id("org.jetbrains.intellij") version "1.17.4"
    kotlin("jvm") version "1.9.22"
}

group = "com.example.tieba"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
}

intellij {
    localPath.set("D:/Program Files/java/repository1/AppData/Local/Temp/opencode/idea/idea-IC-201.8743.12")
    updateSinceUntilBuild.set(false)
}

kotlin {
    jvmToolchain(8)
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
    named("instrumentCode") {
        enabled = false
    }
    named("buildSearchableOptions") {
        enabled = false
    }
}
