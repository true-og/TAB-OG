enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        mavenCentral() // Netty, SnakeYaml, json-simple, Guava, Kyori event, bStats, AuthLib, LuckPerms
        maven("https://repo.papermc.io/repository/maven-public/") // Velocity
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") // PlaceholderAPI
        maven("https://repo.viaversion.com/") // ViaVersion
        maven("https://repo.opencollab.dev/maven-snapshots/") // Floodgate
        maven("https://repo.purpurmc.org/snapshots") // Purpur
        maven("https://repo.spongepowered.org/repository/maven-public/") // Sponge
        maven("https://jitpack.io") // PremiumVanish, Vault, YamlAssist, RedisBungee
        maven("https://nexus.codecrafter47.dyndns.eu/content/repositories/public/") // BungeeCord-proxy // I feel bad for doing this
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

// Run bootstrap.sh during the settings configuration
exec {
    workingDir(rootDir)
    commandLine("sh", "bootstrap.sh")
}

include(":libs:Utilities-OG")
include(":api")
include(":shared")
include(":bukkit")
include(":jar")
