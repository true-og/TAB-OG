plugins {
    id("tab.parent")
    id("java") // Import Java plugin.
    id("java-library") // Import Java Library plugin.
    eclipse
    id("com.diffplug.spotless") version "7.0.4" apply false
    id("com.gradleup.shadow") version "8.3.6" apply false
}

allprojects {
    group = "net.trueog.tab-og"
    version = "4.1.8"
    description = "An all-in-one solution that works"
    ext.set("id", "tab")
    ext.set("website", "https://github.com/true-og/TAB-OG")
    ext.set("author", "NEZNAMY")
    ext.set("credits", "Joseph T. McQuigg (JT122406)")

}

subprojects {
    plugins.apply("java")
    plugins.apply("com.gradleup.shadow")
    if (name != "Utilities-OG") plugins.apply("com.diffplug.spotless")

    repositories {
        mavenCentral()
        maven("https://repo.purpurmc.org/snapshots")
        maven("https://repo.enginehub.org/repository/release/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://repo.viaversion.com/")
    }

    java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation"))
        options.isFork = true
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.named<ProcessResources>("processResources").configure {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        from(rootProject.file("LICENSE")) { into("/") }
    }

    if (name != "Utilities-OG") {
        the<com.diffplug.gradle.spotless.SpotlessExtension>().apply {
            java {
                removeUnusedImports()
                palantirJavaFormat()
            }
            kotlinGradle {
                ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
                target("*.gradle.kts")
            }
        }
        tasks.named("build").configure { dependsOn("spotlessApply", "shadowJar") }
    } else {
        tasks.named("build").configure { dependsOn("shadowJar") }
    }
}

