plugins {
    java
    `java-library`
    id("com.diffplug.spotless") version "7.0.4"
    id("com.gradleup.shadow") version "8.3.6"
    id("net.kyori.blossom") version "1.3.1"
    id("io.freefair.lombok") version "8.13.1" // Automatic lombok support.
    eclipse
}

java { sourceCompatibility = JavaVersion.VERSION_17 }

repositories { mavenCentral() }

dependencies {
    api(projects.api)
    api("org.yaml:snakeyaml:2.0")
    api("com.github.NEZNAMY:yamlassist:1.0.8")
    api("com.googlecode.json-simple:json-simple:1.1.1") { exclude("junit", "junit") }
    api("net.kyori:event-method:3.0.0") {
        exclude("com.google.guava", "guava")
        exclude("org.checkerframework", "checker-qual")
    }
    compileOnlyApi("com.viaversion:viaversion-api:4.7.0")
    compileOnlyApi("io.netty:netty-all:4.1.90.Final")
    compileOnlyApi("net.luckperms:api:5.4")
    compileOnlyApi("com.google.guava:guava:31.1-jre")
    compileOnlyApi("net.kyori:adventure-api:4.13.0")
    compileOnlyApi("net.kyori:adventure-text-serializer-legacy:4.13.0")
    compileOnlyApi("net.kyori:adventure-text-serializer-gson:4.13.0")
    compileOnlyApi("net.kyori:adventure-text-minimessage:4.13.0")
    implementation(project(":libs:Utilities-OG"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("-Xlint:deprecation")
    options.encoding = "UTF-8"
    options.isFork = true
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

blossom {
    replaceToken("@name@", rootProject.name)
    replaceToken("@id@", rootProject.extra["id"].toString())
    replaceToken("@version@", project.version)
    replaceToken("@description@", project.description)
    replaceToken("@website@", rootProject.extra["website"].toString())
    replaceToken("@author@", rootProject.extra["author"].toString())
    replaceTokenIn("src/main/java/me/neznamy/tab/shared/TabConstants.java")
}

tasks.register<Exec>("runCopyJarScript") {
    workingDir(rootDir)
    commandLine("sh", "copyjar.sh", project.version.toString())
}

tasks.named("build") { finalizedBy("runCopyJarScript") }
