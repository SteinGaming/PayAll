
plugins {
    id("org.jetbrains.kotlin.jvm")
    java
}
println(
    "Java: ${System.getProperty("java.version")}, JVM: ${System.getProperty("java.vm.version")} (${
        System.getProperty(
            "java.vendor"
        )
    }), Arch: ${System.getProperty("os.arch")}"
)
allprojects {
    apply(plugin = "java")
    group = "eu.steingaming"
    version = "1.0"

    repositories {
        mavenCentral()
    }

    val shade: Configuration by configurations.creating
    configurations.compileClasspath.get().extendsFrom(shade)
    dependencies {
        shade("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4") {
            exclude("org.jetbrains", "annotations")
        }
        testImplementation(kotlin("test"))
    }

}