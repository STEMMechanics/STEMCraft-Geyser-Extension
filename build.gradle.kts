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
    implementation("org.yaml:snakeyaml:2.2")
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


    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }
}
