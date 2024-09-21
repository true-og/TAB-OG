plugins {
    id("tab.parent")
	id("eclipse")
	java
}

allprojects {
    group = "me.neznamy"
    version = "4.1.7-SNAPSHOT"
    description = "An all-in-one solution that works"

    ext.set("id", "tab")
    ext.set("website", "https://github.com/NEZNAMY/TAB")
    ext.set("author", "NEZNAMY")
}

val platforms = setOf(
    projects.bukkit,
).map { it.dependencyProject }

val special = setOf(
    projects.api,
    projects.shared
).map { it.dependencyProject }

subprojects {
    when (this) {
        in platforms -> plugins.apply("tab.platform-conventions")
        in special -> plugins.apply("tab.standard-conventions")
        else -> plugins.apply("tab.base-conventions")
    }

    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
    }

    repositories {
        mavenCentral()
        maven("https://repo.purpurmc.org/snapshots") // Purpur
        maven("https://repo.enginehub.org/repository/release/") // WorldGuard
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") // MiniPlaceholders
        maven("https://repo.viaversion.com/") // ViaVersion
    }

}
