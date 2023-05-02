plugins {
    application
    id("org.jetbrains.kotlin.jvm")
}
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}
dependencies {
    implementation("com.github.Minestom.Minestom:Minestom:8ad2c7701f")
}

application {
    mainClass.set("eu.steingaming.payall.minestom.MainKt")
}