plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

group = "com.invision"
version = "1.5.0"
description = "Collective chunk expansion and world fund for InVision Anarchy"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    // This plugin uses only the Bukkit/Paper API; paperweight/userdev is not needed.
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("InvisionWorld-${project.version}.jar")
        relocate("org.sqlite", "com.invision.world.libs.sqlite")
        mergeServiceFiles()
    }

    jar {
        enabled = false
    }
}
