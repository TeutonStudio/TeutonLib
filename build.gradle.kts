import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.zip.ZipFile

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
val logisticsTest by sourceSets.creating
val fluidPropagationTest by sourceSets.creating
val trainPortalTest by sourceSets.creating
val mekanismModelTest by sourceSets.creating
val hoseTest by sourceSets.creating
listOf(computerUiExample, dockingTest, energyTest, peripheralTest, logisticsTest, fluidPropagationTest, trainPortalTest, mekanismModelTest, hoseTest).forEach {
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
    maven("https://maven.bawnorton.com/releases") { name = "MixinSquared" }
}

val csoRuntime = providers.gradleProperty("csoRuntime").getOrElse("all")
val logisticsRuntime = providers.gradleProperty("logisticsRuntime")
    .getOrElse(if (providers.gradleProperty("logisticsTests").isPresent) "both"
        else if (providers.gradleProperty("fluidPropagationTests").isPresent) "fluid" else "none")
val tfmgRuntime = providers.gradleProperty("tfmgRuntime")
    .getOrElse(providers.gradleProperty("fluidPropagationTests").isPresent.toString()).toBooleanStrict()
require(logisticsRuntime in listOf("both", "fluid", "factory", "none")) {
    "logisticsRuntime must be both, fluid, factory or none"
}
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
            if (providers.gradleProperty("trainPortalAether").isPresent) {
                systemProperty("teutonlib.trainPortalDestination", "aether:the_aether")
            }
            if (providers.gradleProperty("trainPortalRiderDiagnostics").isPresent) {
                systemProperty("teutonlib.trainPortalRiderDiagnostics", "true")
            }
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
        if (providers.gradleProperty("logisticsTests").isPresent) {
            create("teutonlib_logistics_test") { sourceSet(logisticsTest) }
        }
        if (providers.gradleProperty("fluidPropagationTests").isPresent) {
            create("teutonlib_fluid_test") { sourceSet(fluidPropagationTest) }
        }
        if (providers.gradleProperty("trainPortalTests").isPresent) {
            create("teutonlib_train_portal_test") { sourceSet(trainPortalTest) }
        }
        if (providers.gradleProperty("mekanismModelTests").isPresent) {
            create("teutonlib_mekanism_model_test") { sourceSet(mekanismModelTest) }
        }
        if (providers.gradleProperty("hoseTests").isPresent) {
            create("teutonlib_hose_test") { sourceSet(hoseTest) }
        }
    }
}

// Unmodified TerrArchitecture binaries; never embed foreign mods in the release.
// Simulated is compiled from the extracted JAR but loaded via Aeronautics' bundle.
val localCompileJars = fileTree("dependencies/compile") {
    include("*.jar")
    exclude("sable-neoforge-*.jar")
}
val localRuntimeJars = fileTree("dependencies/runtime") { include("*.jar") }
val sableVersion = providers.gradleProperty("sable_version").get()
val sableJar = files("dependencies/verification/sable-neoforge-1.21.1-$sableVersion.jar")
require(providers.gradleProperty("trainPortalSableVersion").getOrElse(sableVersion) == sableVersion) {
    "Train portal verification requires the supported Sable $sableVersion release"
}
val essentialOuterJars = fileTree("dependencies/compile") { include("Essential_*.jar") }
val essentialApiJar = layout.buildDirectory.file("generated/compatibility/essential-kdiscordipc.jar")
val extractEssentialApi by tasks.registering {
    inputs.files(essentialOuterJars)
    outputs.file(essentialApiJar)
    doLast {
        val outer = essentialOuterJars.files.singleOrNull()
            ?: error("Expected exactly one Essential release JAR in dependencies/compile")
        ZipFile(outer).use { zip ->
            val nested = zip.entries().asSequence()
                .filter { it.name.matches(Regex("essential-[0-9a-f]+\\.jar")) }
                .singleOrNull()
                ?: error("Expected exactly one embedded Essential implementation JAR in ${outer.name}")
            val output = essentialApiJar.get().asFile
            output.parentFile.mkdirs()
            zip.getInputStream(nested).use { input -> output.outputStream().use(input::copyTo) }
        }
    }
}
dependencies {
    compileOnly("com.github.bawnorton.mixinsquared:mixinsquared-common:0.3.7")
    implementation("com.github.bawnorton.mixinsquared:mixinsquared-neoforge:0.3.7")
    jarJar("com.github.bawnorton.mixinsquared:mixinsquared-neoforge:0.3.7")
    compileOnly(localCompileJars)
    compileOnly(sableJar)
    compileOnly(files("dependencies/verification/createbigcannons-5.11.7+mc.1.21.1.jar",
        "dependencies/verification/ritchiesprojectilelib-2.1.2+mc.1.21.1-neoforge.jar"))
    compileOnly(files(essentialApiJar).builtBy(extractEssentialApi))
    runtimeOnly(localRuntimeJars.matching { include("kotlinforforge-*.jar") })
    // The default development environment includes the hybrid train/editing integration.
    if (csoRuntime == "all" || providers.gradleProperty("trainPortalTests").isPresent || providers.gradleProperty("mekanismModelTests").isPresent) {
        runtimeOnly(localCompileJars.matching { include("create_train_functionality-*.jar") })
    }
    if (providers.gradleProperty("trainPortalTests").isPresent || providers.gradleProperty("mekanismModelTests").isPresent) {
        runtimeOnly(localCompileJars.matching {
            include("ip_aeronautics-*.jar", "cloth-config-*.jar", "railways-*.jar")
        })
    }
    if (providers.gradleProperty("hybridTrainTests").isPresent) {
        require(providers.gradleProperty("trainPortalTests").isPresent) { "hybridTrainTests requires trainPortalTests" }
        runtimeOnly(localCompileJars.matching { include("create_radar-*.jar") })
        runtimeOnly(files("dependencies/verification/createbigcannons-5.11.7+mc.1.21.1.jar",
            "dependencies/verification/ritchiesprojectilelib-2.1.2+mc.1.21.1-neoforge.jar"))
    }
    if (providers.gradleProperty("trainPortalAether").isPresent) {
        require(providers.gradleProperty("trainPortalTests").isPresent) {
            "trainPortalAether is an opt-in train portal verification profile"
        }
        val aetherRelease = file("dependencies/verification/aether-1.21.1-1.5.10-neoforge.jar")
        require(aetherRelease.isFile) { "Missing original Aether 1.5.10 verification JAR: $aetherRelease" }
        val owoRelease = file("dependencies/verification/owo-lib-neoforge-0.12.15.5-beta.1+1.21.jar")
        require(owoRelease.isFile) { "Missing original owo verification JAR: $owoRelease" }
        runtimeOnly(files(aetherRelease, owoRelease))
    }
    if (providers.gradleProperty("mekanismModelTests").isPresent) {
        runtimeOnly(localCompileJars.matching {
            include("Mekanism-*.jar", "create_mekanism_compat-*.jar")
        })
    }
    if (providers.gradleProperty("mekanismModelTests").isPresent ||
        providers.gradleProperty("trainPortalSodium").isPresent) {
        // Sodium's outer JAR is an early-loading service library. A Gradle mod dependency
        // hides its nested implementation, while the implementation alone lacks its workarounds.
        // Let the normal mods-directory discovery load the unmodified distribution instead.
        val sodiumOuterJars = localCompileJars.matching { include("sodium-neoforge-*.jar") }
        val sodiumClientDirectory = neoForge.runs.getByName("client").gameDirectory
        tasks.named("runClient") {
            doFirst {
                val outer = sodiumOuterJars.files.singleOrNull()
                    ?: error("Expected exactly one Sodium release JAR in dependencies/compile")
                val output = sodiumClientDirectory.get().asFile.resolve("mods/${outer.name}")
                output.parentFile.mkdirs()
                outer.copyTo(output, overwrite = true)
            }
        }
    }
    if (tfmgRuntime) runtimeOnly(localCompileJars.matching { include("tfmg-*.jar") })
    if (logisticsRuntime == "both" || logisticsRuntime == "fluid") {
        runtimeOnly(localCompileJars.matching { include("fluidlogistics-*.jar") })
    }
    if (logisticsRuntime == "both" || logisticsRuntime == "factory") {
        runtimeOnly(localCompileJars.matching { include("create_factory_logistics-*.jar") })
    }
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
        "all" -> {
            runtimeOnly(localRuntimeJars.matching {
                exclude("kotlinforforge-*.jar", "sable-neoforge-*.jar")
            })
            runtimeOnly(sableJar)
        }
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
    val newAgeJar = localRuntimeJars.matching { include("create-new-age-*.jar") }.singleFile
    inputs.files(localCompileJars, sableJar)
    inputs.file(newAgeJar)
    inputs.file("docs/logistics-compatibility.sha256")
    inputs.file("docs/fluid-propagation-compatibility.sha256")
    systemProperty("teutonlib.compatibilityJars", (localCompileJars + sableJar).asPath)
    systemProperty("teutonlib.newAgeJar", newAgeJar.absolutePath)
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
            file("src/main/java/de/TeutonStudio/TeutonLib/mixin/computer/client/DbwClientMixin.java"),
            file("src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/PowerGridNativeMixin.java"),
            file("src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/ImmersiveMelodiesMidiMixin.java"),
            file("src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/VisualRecipeEditorBackupMixin.java"),
            file("src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/EssentialDiscordIpcMixin.java"),
            file("src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/PonderRenderTargetMixin.java"),
            file("src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/CatnipStencilElementMixin.java"),
            file("src/main/java/de/TeutonStudio/TeutonLib/mixin/resource/ResourcePackLoaderMixin.java")
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
