plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

sourceSets {
    main {
        java {
            srcDir("src")
        }
        resources {
            srcDir("res")
        }
    }
}

group = "gecko10000.telefuse"
version = "1.0-SNAPSHOT"

repositories {
    maven("https://jitpack.io")
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(kotlin("stdlib", version = "2.0.0"))
    compileOnly("com.github.serceman:jnr-fuse:0.5.7")
    implementation("io.insert-koin:koin-core:3.5.3")
    implementation("dev.inmo:tgbotapi:15.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
}

kotlin {
    jvmToolchain(17)
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}

application {
    mainClass.set("gecko10000.telefuse.TeleFuseKt")
}
