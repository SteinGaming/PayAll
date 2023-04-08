@file:Suppress("UNUSED_VARIABLE")

import net.minecraftforge.gradle.common.util.RunConfig
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "1.8.0"
    application
    id("net.minecraftforge.gradle") version ("5.+")
    id("com.github.johnrengelman.shadow") version ("7.+")
}

group = "eu.steingaming"
version = "1.0"

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))

println(
    "Java: ${System.getProperty("java.version")}, JVM: ${System.getProperty("java.vm.version")} (${
        System.getProperty(
            "java.vendor"
        )
    }), Arch: ${System.getProperty("os.arch")}"
)


repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net/")
}

val minecraftVersions = listOf("1.19.3-44.1.0", "1.19-41.1.0")
val selectedMinecraftVersion = 0

minecraft {
    // The mappings can be changed at any time and must be in the following format.
    // Channel:   Version:
    // official   MCVersion             Official field/method names from Mojang mapping files
    // parchment  YYYY.MM.DD-MCVersion  Open community-sourced parameter names and javadocs layered on top of official
    //
    // You must be aware of the Mojang license when using the "official" or "parchment" mappings.
    // See more information here: https://github.com/MinecraftForge/MCPConfig/blob/master/Mojang.md
    //
    // Parchment is an unofficial project maintained by ParchmentMC, separate from MinecraftForge
    // Additional setup is needed to use their mappings: https://github.com/ParchmentMC/Parchment/wiki/Getting-Started
    //
    // Use non-default mappings at your own risk. They may not always work.
    // Simply re-run your setup task after changing the mappings to update your workspace.
    mappings(mapOf("channel" to "official", "version" to minecraftVersions[selectedMinecraftVersion].split("-")[0]))

    // accessTransformer = file("src/main/resources/META-INF/accesstransformer.cfg") // Currently, this location cannot be changed from the default.

    // Default run configurations.
    // These can be tweaked, removed, or duplicated as needed.
    fun RunConfig.apply() {
        workingDirectory(project.file("run"))

        // Recommended logging data for a userdev environment
        // The markers can be added/remove as needed separated by commas.
        // "SCAN": For mods scan.
        // "REGISTRIES": For firing of registry events.
        // "REGISTRYDUMP": For getting the contents of all registries.
        property("forge.logging.markers", "REGISTRIES")

        // Recommended logging level for the console
        // You can set various levels here.
        // Please read: https://stackoverflow.com/questions/2031163/when-to-use-the-different-log-levels
        property("forge.logging.console.level", "debug")

        // Comma-separated list of namespaces to load gametests from. Empty = all namespaces.
        property("forge.enabledGameTestNamespaces", "payall")

        mods {
            val payall by creating {
                source(sourceSets.main.get())
            }
        }
    }
    runs {
        val client by creating {
            apply()
        }
        val server by creating {
            apply()
        }

        // This run config launches GameTestServer and runs all registered gametests, then exits.
        // By default, the server will crash when no gametests are provided.
        // The gametest system is also enabled by default for other run configs under the /test command.
        val gameTestServer by creating {
            apply()
        }

        val data by creating {
            apply()
        }
    }
}

// Include resources generated by data generators.
sourceSets.main.get().resources { srcDir("src/generated/resources") }

val minecraft: Configuration by configurations.getting
val shade: Configuration by configurations.creating
configurations.compileClasspath.get().extendsFrom(shade)
dependencies {
    minecraft("net.minecraftforge:forge:${minecraftVersions[selectedMinecraftVersion]}")
    //minecraftLibrary("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4") {
    //    exclude("org.jetbrains", "annotations")
    //}
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4") {
    //    exclude("org.jetbrains", "annotations")
    //}
    shade("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4") {
        exclude("org.jetbrains", "annotations")
    }
    testImplementation(kotlin("test"))
}
fun Jar.apply() {
    manifest {
        attributes(
            "Specification-Title" to "PayAll",
            "Specification-Vendor" to "SteinGaming",
            "Specification-Version" to "1", // We are version 1 of ourselves
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.tasks.jar.get().archiveVersion.get(),
            "Implementation-Vendor" to "SteinGaming",
            //"Implementation-Timestamp" to Date().format("yyyy-MM-dd HH:mm:ssZ")
        )
    }
    finalizedBy("reobfJar")
}
reobf.create("shadowJar") {
    doLast {
        copy {
            from(this@create.input.asFile.get())
            into("output")
        }
    }
}
tasks {
    shadowJar {
        configurations = listOf(shade)
        finalizedBy("reobfShadowJar")
    }

    jar {
        apply()
    }
    test {
        useJUnitPlatform()
    }
}
kotlin {
    jvmToolchain(17)
}

tasks.withType(JavaCompile::class).configureEach {
    options.encoding = "UTF-8" // Use the UTF-8 charset for Java compilation
}

application {
    mainClass.set("MainKt")
}