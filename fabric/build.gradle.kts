plugins {
    id("fabric-loom") version "1.1-SNAPSHOT"
    id("org.jetbrains.kotlin.jvm")
    id("com.github.johnrengelman.shadow")
}

loom {
    //splitEnvironmentSourceSets()

}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    //withSourcesJar()
}
val e by configurations.creating
tasks {
    jar {
        //configurations = listOf(shade)
        archiveBaseName.set("PayAll-Fabric")
        archiveClassifier.set("all")
    }
    shadowJar {
        configurations = listOf(e)
        finalizedBy("remapJar")
    }
    remapJar {
        inputFile.set(shadowJar.get().archiveFile.get())
        doLast {
            copy {
                from(archiveFile.get())
                into("$rootDir/output/")
                rename(Transformer { "PayAll-Fabric-${project.version}-all.jar" })
            }
        }
    }
}
dependencies {
    minecraft("com.mojang:minecraft:${project.extra["minecraft_version"] as String}")
    mappings("net.fabricmc:yarn:${project.extra["yarn_mappings"] as String}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.extra["loader_version"] as String}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.extra["fabric_version"] as String}")
    include(implementation(rootProject)!!)
    include(implementation(kotlin("stdlib"))!!)
    //include(implementation(kotlin("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4"))!!)
    //compileOnly(rootProject)
    //include("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    //compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    //include(kotlin("stdlib"))
    //compileOnly(kotlin("stdlib"))
    e(rootProject)
    e("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    e(kotlin("stdlib"))
    modImplementation("net.fabricmc", "fabric-language-kotlin", project.extra["fabric_kotlin_version"] as String)
}
