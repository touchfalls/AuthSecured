plugins {
    java
    id("com.gradleup.shadow") version "9.6.1" apply false
    id("net.fabricmc.fabric-loom") version "1.17.19" apply false
}

allprojects {
    group = "io.github.touchfalls.authsecured"
    version = "1.2.7"

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/") {
            content { includeGroupByRegex("net\\.fabricmc.*") }
        }
        maven("https://repo.papermc.io/repository/maven-public/") {
            content {
                includeGroupByRegex("io\\.papermc.*")
                includeGroup("com.mojang")
                includeGroup("net.md-5")
            }
        }
        maven("https://oss.sonatype.org/content/groups/public/")
    }
}

subprojects {
    apply(plugin = "java")

    dependencies {
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.10.2")
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
