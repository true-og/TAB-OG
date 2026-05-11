enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        mavenCentral() // Netty, SnakeYaml, json-simple, Guava, Kyori event, bStats, AuthLib, LuckPerms
        maven("https://repo.viaversion.com/") // ViaVersion
        maven("https://repo.william278.net/releases/") // VelocityScoreboardAPI
        maven("https://repo.codemc.org/repository/nms/") // CraftBukkit + NMS
        maven("https://repo.papermc.io/repository/maven-public/") // paperweight, Velocity, Adventure
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") // PlaceholderAPI
        maven("https://repo.opencollab.dev/maven-snapshots/") // Floodgate, Bungeecord-proxy
        maven("https://repo.purpurmc.org/snapshots") // Purpur
        maven("https://jitpack.io") // PremiumVanish, YamlAssist, RedisBungee
        maven("https://mvn.lib.co.nz/public") // LibsDisguises
        maven("https://repo.william278.net/velocity/") // Velocity-proxy
    }
}

pluginManagement {
    includeBuild("build-logic")
    repositories {
        maven("https://repo.spongepowered.org/repository/maven-public/")
        maven("https://maven.architectury.dev/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "TAB-OG"

include(":api")
include(":shared")
include(":velocity")
include(":bukkit")
include(":bukkit:v1_19_R3")
include(":bungeecord")
include(":jar")
