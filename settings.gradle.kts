enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://repo.viaversion.com/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
        maven("https://repo.purpurmc.org/snapshots")
        maven("https://repo.spongepowered.org/repository/maven-public/")
        maven("https://jitpack.io")
        maven("https://nexus.codecrafter47.dyndns.eu/content/repositories/public/")
    }
}

pluginManagement {
    includeBuild("build-logic")
    repositories {
        maven("https://repo.spongepowered.org/repository/maven-public/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "TAB-OG"

val process = ProcessBuilder("sh", "bootstrap.sh")
    .directory(rootDir)
    .start()

val exitValue = process.waitFor()
if (exitValue != 0) {
    throw GradleException("bootstrap.sh failed with exit code $exitValue")
}

include("libs:Utilities-OG")
include(":api")
include(":shared")
include(":bukkit")
include(":jar")

