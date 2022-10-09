import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktor_version = "2.1.2"

plugins {
    kotlin("jvm") version "1.6.10"
    application
    kotlin("plugin.serialization") version "1.6.10"
}

group = "me.microsft_is_gay"
version = "1.0"

repositories {
    mavenCentral()
}



tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "16"
}

tasks {
    register<Jar>("fatJar") {
        dependsOn.addAll(
            listOf(
                "compileJava",
                "compileKotlin",
                "processResources"
            )
        ) // We need this for Gradle optimization to work
        // archiveClassifier.set("standalone") // Naming the jar
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest { attributes(mapOf("Main-Class" to application.mainClass)) } // Provided we set it up in the application plugin configuration
        val sourcesMain = sourceSets.main.get()
        val contents = configurations.runtimeClasspath.get()
            .map { if (it.isDirectory) it else zipTree(it) } +
                sourcesMain.output
        from(contents)
    }
}

application {
    mainClass.set("MainKt")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    // ktor
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-client-websockets:$ktor_version")
    implementation("io.ktor:ktor-server-websockets:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    // other
    implementation("ch.qos.logback:logback-classic:1.4.1")
    implementation("org.jetbrains.kotlin:kotlin-serialization:1.6.10")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0")
    implementation("redis.clients:jedis:4.2.3")
    // logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.0")
}