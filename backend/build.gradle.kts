val ktorVersion = "2.2.3"
val koinVersion = "3.1.2"
val logbackVersion = "1.2.1"
val kotlinVersion = "1.8.10"  // Define the Kotlin version for dependencies

plugins {
    application
    kotlin("jvm") version "1.8.10"
    id("com.github.node-gradle.node") version "7.0.2"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))  // Ensure Kotlin targets the same Java version
    }
}

application {
    mainClass.set("com.landonpatmore.yahoofantasybot.backend.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server dependencies
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-host-common:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-serialization-gson:$ktorVersion")  // Use ktor-serialization-gson instead of ktor-gson
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-gson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")

    // Koin dependencies for dependency injection
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")
    implementation(project(":shared"))
    testImplementation("io.ktor:ktor-server-tests:$ktorVersion")
    // Other dependencies
    implementation("ch.qos.logback:logback-classic:1.2.1")
}

sourceSets {
    main {
        kotlin.srcDir("src")
        resources.srcDir("resources")
    }
    test {
        kotlin.srcDir("test")
        resources.srcDir("testresources")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"  // Align Kotlin and Java targets to JVM 17
    }
}

tasks {
    register<Delete>("cleanFrontend") {
        delete("resources/frontend")
    }

    register<Copy>("copyFrontend") {
        dependsOn("cleanFrontend")
        from(file("../frontend/build"))
        into(file("resources/frontend"))
    }

    register<com.github.gradle.node.npm.task.NpmTask>("buildFrontend") {
        dependsOn("npmInstall")
        args.set(listOf("run", "build"))
        finalizedBy("copyFrontend")
    }

    named<ProcessResources>("processResources") {
        dependsOn("copyFrontend")
    }

    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        dependsOn("buildFrontend")
    }
}

node {
    nodeProjectDir.set(file("../frontend"))
    download.set(true)
}
