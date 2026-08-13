plugins {
    java
}

repositories {
    maven("https://repo.opencollab.dev/main/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
    mavenCentral()
}

dependencies {
    compileOnly("org.geysermc.geyser:core:${property("geyser_version")}")
    implementation("org.yaml:snakeyaml:2.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 21
    }

    processResources {
        filesMatching("extension.yml") {
            expand("version" to project.version)
        }
    }

    test {
        useJUnitPlatform()
    }


    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }
}
