plugins {
    eclipse
    id("tab.parent")
}

allprojects {
    group = "me.neznamy"
    version = "6.0.0-SNAPSHOT"
    description = "An all-in-one solution that works"

    ext.set("id", "tab")
    ext.set("website", "https://github.com/true-og/TAB-OG")
    ext.set("author", "NEZNAMY")
    ext.set("credits", "Joseph T. McQuigg (JT122406)")
}

val platformPaths = setOf(
    ":bukkit",
    ":bukkit:v1_19_R3",
    ":bungeecord",
    ":velocity"
)

val specialPaths = setOf(
    ":api",
    ":shared"
)

subprojects {
    when (path) {
        in platformPaths -> plugins.apply("tab.platform-conventions")
        in specialPaths -> plugins.apply("tab.standard-conventions")
        else -> plugins.apply("tab.base-conventions")
    }
}

val copyShadowJar by tasks.registering(Copy::class) {
    dependsOn(":jar:shadowJar")
    from(project(":jar").tasks.named("shadowJar"))
    into(layout.buildDirectory.dir("libs"))
}

tasks.register("build") {
    dependsOn(copyShadowJar)
}
