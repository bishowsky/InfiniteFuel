plugins {
    `java-library`
}

group = "pl.puffmc"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API 1.21.4 (compatible with 1.21-1.21.11)
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

tasks {
    jar {
        // CRITICAL: JAR output name without version suffix (PuffMC standard)
        archiveFileName.set("InfiniteFuel.jar")
    }
    
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
        options.compilerArgs.add("-Xlint:deprecation")
    }
    
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        
        val props = mapOf(
            "name" to rootProject.name,
            "version" to project.version,
            "group" to project.group
        )
        
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
