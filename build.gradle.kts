import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.moddev") version "2.0.143"
    kotlin("jvm") version "2.2.20"
    idea
}

val modId = providers.gradleProperty("mod_id").get()
version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("mod_group_id").get()
base { archivesName = modId }

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}
kotlin.jvmToolchain(21)

// Verification mods are only loaded in explicitly requested development runs.
val mainSource = sourceSets.main.get()
val computerUiExample by sourceSets.creating
val dockingTest by sourceSets.creating
val energyTest by sourceSets.creating
val peripheralTest by sourceSets.creating
listOf(computerUiExample, dockingTest, energyTest, peripheralTest).forEach {
    it.compileClasspath += mainSource.output + mainSource.compileClasspath
    it.runtimeClasspath += mainSource.runtimeClasspath
}
peripheralTest.compileClasspath += computerUiExample.output
peripheralTest.runtimeClasspath += computerUiExample.output
sourceSets.test {
    compileClasspath += mainSource.compileClasspath
    runtimeClasspath += mainSource.compileClasspath
}
kotlin.sourceSets.named("computerUiExample") {
    kotlin.srcDir("examples/computer-ui/src/main/kotlin")
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases") { name = "NeoForge" }
}

val csoRuntime = providers.gradleProperty("csoRuntime").getOrElse("all")
require(csoRuntime in listOf("all", "none", "create")) {
    "csoRuntime must be all, none or create"
}

neoForge {
    version = providers.gradleProperty("neo_version").get()
    parchment {
        mappingsVersion = providers.gradleProperty("parchment_mappings_version").get()
        minecraftVersion = providers.gradleProperty("parchment_minecraft_version").get()
    }
    runs {
        create("client") {
            listOf("computerUiTests", "computerUiConfigTests", "peripheralLinkUiTests").forEach {
                if (providers.gradleProperty(it).isPresent) systemProperty("teutonlib.$it", "true")
            }
            client()
            gameDirectory = file("run/client")
        }
        create("server") {
            server()
            gameDirectory = file("run/server")
            programArgument("--nogui")
        }
        create("smokeServer") {
            server()
            gameDirectory = file(providers.gradleProperty("peripheralTestDir").getOrElse("run/smoke/$csoRuntime"))
            programArgument("--nogui")
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.INFO
        }
    }
    mods {
        create(modId) { sourceSet(mainSource) }
        if (providers.gradleProperty("analogDockingTests").isPresent) {
            create("teutonlib_docking_test") { sourceSet(dockingTest) }
        }
        if (providers.gradleProperty("motorEnergyTests").isPresent) {
            create("teutonlib_energy_test") { sourceSet(energyTest) }
        }
        if (providers.gradleProperty("peripheralTests").isPresent) {
            create("teutonlib_peripheral_test") {
                sourceSet(peripheralTest)
                sourceSet(computerUiExample)
            }
        }
    }
}

// Unmodified TerrArchitecture binaries; never embed foreign mods in the release.
// Simulated is compiled from the extracted JAR but loaded via Aeronautics' bundle.
val localCompileJars = fileTree("dependencies/compile") { include("*.jar") }
val localRuntimeJars = fileTree("dependencies/runtime") { include("*.jar") }
dependencies {
    compileOnly(localCompileJars)
    runtimeOnly(localRuntimeJars.matching { include("kotlinforforge-*.jar") })
    if (csoRuntime == "all" || providers.gradleProperty("peripheralRuntime").isPresent) {
        runtimeOnly(localCompileJars.matching { include("cc-tweaked-*.jar") })
        if (providers.gradleProperty("peripheralRuntime").getOrElse("cc") == "console") {
            runtimeOnly(localCompileJars.matching { include("cc-aeroworks-*.jar", "aeroworks-*.jar") })
        }
    }
    if (csoRuntime == "all" || providers.gradleProperty("dbwTests").isPresent) {
        runtimeOnly(localCompileJars.matching { include("drivebywire-*.jar") })
    }
    when (csoRuntime) {
        "all" -> runtimeOnly(localRuntimeJars.matching { exclude("kotlinforforge-*.jar") })
        "create" -> runtimeOnly(localRuntimeJars.matching { include("create-1.21.1-*.jar") })
    }
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.mojang:brigadier:1.3.10")
    testImplementation("org.ow2.asm:asm:9.8")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.jar)
    inputs.files(localCompileJars)
    systemProperty("teutonlib.compatibilityJars", localCompileJars.asPath)
    systemProperty("teutonlib.architectureArtifact", tasks.jar.get().archiveFile.get().asFile.absolutePath)
}

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val props = listOf(
        "minecraft_version", "minecraft_version_range", "neo_version", "kotlin_for_forge_version",
        "loader_version_range", "create_mod_version", "sable_mod_version", "simulated_mod_version_range",
        "aeronautics_mod_version_range",
        "mod_id", "mod_name", "mod_version", "mod_group_id", "mod_authors", "mod_license", "mod_description"
    ).associateWith { providers.gradleProperty(it).get() }
    inputs.properties(props)
    expand(props)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}
sourceSets.main { resources.srcDir(generateModMetadata) }
neoForge.ideSyncTask(generateModMetadata)

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
// Optional Aeroworks binaries use newer Kotlin metadata; their JVM APIs remain compatible.
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
    }
}
tasks.jar { manifest { attributes("MixinConfigs" to "teutonlib.mixins.json") } }

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            artifactId = modId
            from(components["java"])
        }
    }
    repositories { maven { url = layout.projectDirectory.dir("repo").asFile.toURI() } }
}

tasks.withType<Jar>().configureEach {
    from(files("LICENSE", "TEMPLATE_LICENSE.txt")) { into("META-INF") }
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val verifyKotlinSources by tasks.registering {
    doLast {
        val allowedJavaSources = setOf(
            file("src/main/java/de/TeutonStudio/TeutonLib/mixin/computer/client/DbwClientMixin.java")
        )
        val javaSources = (fileTree("src") { include("**/*.java") } +
            fileTree("examples") { include("**/*.java") }
        ).files
        check(javaSources == allowedJavaSources) {
            "Unexpected Java sources: ${javaSources - allowedJavaSources}; missing required Java mixins: ${allowedJavaSources - javaSources}"
        }
    }
}
tasks.check { dependsOn("compileComputerUiExampleKotlin", verifyKotlinSources) }

// Self-contained hand-off for addon authors, excluding local binaries and test mods.
tasks.register<Zip>("releaseBundle") {
    dependsOn(tasks.build)
    archiveClassifier = "computer-ui-template"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from(tasks.jar) { into("mods") }
    from(tasks.named("sourcesJar")) { into("sources") }
    from("README.md", "LICENSE", "TEMPLATE_LICENSE.txt")
    from("docs") { into("docs") }
    from("examples/computer-ui") { into("examples/computer-ui") }
}
