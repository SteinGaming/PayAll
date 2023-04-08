plugins {
    kotlin("jvm") version "1.8.0"
    application
}
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}
dependencies {
    implementation("com.github.Minestom.Minestom:Minestom:1.19.3-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}

application {
    mainClass.set("eu.steingaming.payall.minestom.MainKt")
}