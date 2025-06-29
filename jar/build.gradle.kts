import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins { id("com.gradleup.shadow") }

val platforms = listOf(project(":bukkit"))

tasks {
    shadowJar {
        archiveFileName.set("TAB-${project.version}.jar")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        platforms.forEach { p ->
            val shadeTask = p.tasks.named<ShadowJar>("shadowJar")
            dependsOn(shadeTask)
            from(zipTree(shadeTask.get().archiveFile))
        }
    }
    build { dependsOn(shadowJar) }
}
