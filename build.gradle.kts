// build.gradle.kts
/* This is free and unencumbered software released into the public domain */

import org.gradle.kotlin.dsl.provideDelegate

/* ------------------------------ Plugins ------------------------------ */
plugins {
    id("java") // Import Java plugin.
    id("java-library") // Import Java Library plugin.
    id("com.diffplug.spotless") version "7.0.4" // Import Spotless plugin.
    id("com.gradleup.shadow") version "8.3.6" // Import Shadow plugin.
    id("checkstyle") // Import Checkstyle plugin.
    eclipse // Import Eclipse plugin.
    kotlin("jvm") version "2.1.21" // Import Kotlin JVM plugin.
    id("io.freefair.lombok") version "8.6"
    id("net.kyori.blossom") version "2.1.0"
}

extra["kotlinAttribute"] = Attribute.of("kotlin-tag", Boolean::class.javaObjectType)

val kotlinAttribute: Attribute<Boolean> by rootProject.extra

/* --------------------------- JDK / Kotlin ---------------------------- */
java {
    sourceCompatibility = JavaVersion.VERSION_17 // Compile with JDK 17 compatibility.
    toolchain { // Select Java toolchain.
        languageVersion.set(JavaLanguageVersion.of(17)) // Use JDK 17.
        vendor.set(JvmVendorSpec.GRAAL_VM) // Use GraalVM CE.
    }
}

kotlin { jvmToolchain(17) }

/* ----------------------------- Metadata ------------------------------ */
group = "net.trueog.tab-og" // Declare bundle identifier.

version = "4.1.8" // Declare plugin version (will be in .jar).

val apiVersion = "1.19" // Declare minecraft server target version.

description = "An all-in-one solution that works"

ext.set("id", "tab")

ext.set("website", "https://github.com/true-og/TAB-OG")

ext.set("author", "NEZNAMY")

ext.set("credits", "Joseph T. McQuigg (JT122406)")

/* ------------------------------- Sources ----------------------------- */
sourceSets {
    named("main") {
        java.setSrcDirs(listOf("api/src/main/java", "shared/src/main/java", "bukkit/src/main/java"))
        resources.setSrcDirs(listOf("api/src/main/resources", "shared/src/main/resources", "bukkit/src/main/resources"))
    }
}

/* ----------------------------- Resources ----------------------------- */
tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to version, "apiVersion" to apiVersion)
    inputs.properties(props) // Indicates to rerun if version changes.
    filesMatching("plugin.yml") { expand(props) }
    from("LICENSE") { into("/") } // Bundle licenses into jarfiles.
}

/* ---------------------------- Repos ---------------------------------- */
repositories {
    mavenCentral() // Import the Maven Central Maven Repository.
    gradlePluginPortal() // Import the Gradle Plugin Portal Maven Repository.
    maven { url = uri("https://repo.purpurmc.org/snapshots") } // Import the PurpurMC Maven Repository.
    maven { url = uri("https://repo.enginehub.org/repository/release/") }
    maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }
    maven { url = uri("https://repo.viaversion.com/") }
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://repo.opencollab.dev/maven-snapshots/") }
    maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://nexus.codecrafter47.dyndns.eu/content/repositories/public/") }
    maven { url = uri("file://${System.getProperty("user.home")}/.m2/repository") }
    System.getProperty("SELF_MAVEN_LOCAL_REPO")?.let {
        val dir = file(it)
        if (dir.isDirectory) {
            println("Using SELF_MAVEN_LOCAL_REPO at: $it")
            maven { url = uri("file://${dir.absolutePath}") }
        } else {
            logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
            mavenLocal()
        }
    } ?: logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
}

/* ---------------------- Java project deps ---------------------------- */
dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT") // Declare Purpur API version to be packaged.
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.mojang:authlib:1.5.25")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { exclude("org.bukkit", "bukkit") }
    compileOnly("com.github.LeonMangler:PremiumVanishAPI:2.8.8")
    compileOnlyApi("org.jetbrains:annotations:24.1.0")
    api("org.yaml:snakeyaml:2.0")
    api("com.github.NEZNAMY:yamlassist:1.0.8")
    api("com.googlecode.json-simple:json-simple:1.1.1") { exclude("junit", "junit") }
    api("net.kyori:event-method:3.0.0") {
        exclude("com.google.guava", "guava")
        exclude("org.checkerframework", "checker-qual")
    }
    compileOnlyApi("com.viaversion:viaversion-api:5.2.1")
    compileOnlyApi("io.netty:netty-all:4.1.90.Final")
    compileOnlyApi("net.luckperms:api:5.4")
    compileOnlyApi("com.google.guava:guava:31.1-jre")
    compileOnlyApi("net.kyori:adventure-api:4.18.0")
    compileOnlyApi("net.kyori:adventure-text-minimessage:4.18.0")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.24.0")
    implementation("net.kyori:adventure-text-serializer-gson:4.24.0")
    implementation("com.saicone.delivery4j:delivery4j:1.1.1")
    implementation("com.saicone.delivery4j:broker-rabbitmq:1.1.1") { exclude("org.slf4j", "slf4j-api") }
    implementation("com.saicone.delivery4j:broker-redis:1.1.1") {
        exclude("com.google.code.gson", "gson")
        exclude("org.slf4j", "slf4j-api")
    }
    implementation("com.saicone.delivery4j:extension-guava:1.1.1")
    compileOnlyApi(project(":libs:Utilities-OG")) // Import TrueOG Network Utilities-OG Java API (from source).
}

apply(from = "eclipse.gradle.kts") // Import eclipse classpath support script.

/* ---------------------- Reproducible jars ---------------------------- */
tasks.withType<AbstractArchiveTask>().configureEach { // Ensure reproducible .jars
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val tabConstantsGenDir = layout.buildDirectory.dir("generated/sources/tabconstants/main")
val materializeTabConstants by
    tasks.registering(Copy::class) {
        from("shared/src/main/java-templates") { include("me/neznamy/tab/shared/TabConstants.java") }
        into(tabConstantsGenDir)
        expand(
            mapOf(
                "name" to rootProject.name,
                "id" to ext["id"].toString(),
                "version" to version.toString(),
                "description" to (project.description ?: ""),
                "website" to ext["website"].toString(),
                "author" to ext["author"].toString(),
                "credits" to ext["credits"].toString(),
            )
        )
        filteringCharset = "UTF-8"
    }

sourceSets.named("main") { java.srcDir(tabConstantsGenDir) }

tasks.withType<JavaCompile>().configureEach { dependsOn(materializeTabConstants) }

/* ----------------------------- Shadow -------------------------------- */
tasks.shadowJar {
    archiveBaseName.set("TAB-OG")
    archiveClassifier.set("") // Use empty string instead of null.
    minimize()
}

tasks.jar { archiveClassifier.set("part") } // Applies to root jarfile only.

tasks.build { dependsOn(tasks.spotlessApply, tasks.shadowJar) } // Build depends on spotless and shadow.

/* --------------------------- Javac opts ------------------------------- */
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters") // Enable reflection for java code.
    options.isFork = true // Run javac in its own process.
    options.compilerArgs.add("-Xlint:deprecation") // Trigger deprecation warning messages.
    options.encoding = "UTF-8" // Use UTF-8 file encoding.
}

/* ----------------------------- Auto Formatting ------------------------ */
spotless {
    java {
        eclipse().configFile("config/formatter/eclipse-java-formatter.xml") // Eclipse java formatting.
        leadingTabsToSpaces() // Convert leftover leading tabs to spaces.
        removeUnusedImports() // Remove imports that aren't being called.
        targetExclude("build/generated/**")
    }
    kotlinGradle {
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) } // JetBrains Kotlin formatting.
        target("build.gradle.kts", "settings.gradle.kts") // Gradle files to format.
    }
}

checkstyle {
    toolVersion = "10.18.1" // Declare checkstyle version to use.
    configFile = file("config/checkstyle/checkstyle.xml") // Point checkstyle to config file.
    isIgnoreFailures = true // Don't fail the build if checkstyle does not pass.
    isShowViolations = true // Show the violations in any IDE with the checkstyle plugin.
}

tasks.named("compileJava") {
    dependsOn("spotlessApply") // Run spotless before compiling with the JDK.
}

tasks.named("spotlessCheck") {
    dependsOn("spotlessApply") // Run spotless before checking if spotless ran.
}

/* ------------------------------ Eclipse SHIM ------------------------- */

// This can't be put in eclipse.gradle.kts because Gradle is weird.
subprojects {
    apply(plugin = "java-library")
    apply(plugin = "eclipse")
    eclipse.project.name = "${project.name}-${rootProject.name}"
    tasks.withType<Jar>().configureEach { archiveBaseName.set("${project.name}-${rootProject.name}") }
}

/* ------------------------------ Blossom ------------------------------ */
sourceSets.main {
    blossom {
        javaSources {
            property("name", rootProject.name)
            property("id", rootProject.ext.get("id")!!.toString())
            property("version", project.version.toString())
            property("description", project.description)
            property("website", rootProject.ext.get("website")!!.toString())
            property("author", rootProject.ext.get("author")!!.toString())
            property("credits", rootProject.ext.get("credits")!!.toString())
        }
    }
}
