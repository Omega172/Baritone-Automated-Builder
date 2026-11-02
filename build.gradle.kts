plugins {
    id("net.fabricmc.fabric-loom") version "1.18-SNAPSHOT"
}

val minecraftVersion = providers.gradleProperty("minecraft_version").get()

base {
    archivesName = providers.gradleProperty("archives_base_name").get()
    version = "${providers.gradleProperty("mod_version").get()}+$minecraftVersion"
    group = providers.gradleProperty("maven_group").get()
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    mavenCentral()
    exclusiveContent {
        forRepository {
            maven {
                name = "modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")

    implementation("maven.modrinth:litematica:${providers.gradleProperty("litematica_version").get()}")
    implementation("maven.modrinth:malilib:${providers.gradleProperty("malilib_version").get()}")

    implementation("meteordevelopment:baritone:${providers.gradleProperty("baritone_version").get()}")

    if (providers.gradleProperty("meteor").isPresent) {
        runtimeOnly("meteordevelopment:meteor-client:$minecraftVersion-SNAPSHOT")
        providers.gradleProperty("meteorPrinterJar").orNull?.let { runtimeOnly(files(it)) }
    }
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "baritone-automated-builder-test"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
    }
}

sourceSets {
    main {
        java.setSrcDirs(listOf("Source/BaritoneAutomatedBuilder/Java"))
        resources.setSrcDirs(listOf("Source/BaritoneAutomatedBuilder/Resources"))
    }
    named("gametest") {
        java.setSrcDirs(listOf("Source/BaritoneAutomatedBuilder/GameTest/Java"))
        resources.setSrcDirs(listOf("Source/BaritoneAutomatedBuilder/GameTest/Resources"))
    }
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to minecraftVersion,
        )

        inputs.properties(propertyMap)
        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        destinationDirectory = layout.projectDirectory.dir("Build/Release")
        from("LICENSE")
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 25
    }
}
