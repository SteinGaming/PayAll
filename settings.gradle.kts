pluginManagement {
    plugins {
        id("com.github.johnrengelman.shadow") version ("7.+") apply false
        kotlin("jvm") version "1.8.21" apply false
        java
    }
    repositories {
        maven("https://maven.fabricmc.net") { name = "Fabric" }
        maven("https://maven.minecraftforge.net/")
        gradlePluginPortal()
    }
}
rootProject.name = "PayAll"

include("test-server", "fabric", "forge")