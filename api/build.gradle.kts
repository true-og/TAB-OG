plugins {
    id("java") // Tell gradle this is a java project.
    id("java-library") // Import helper for source-based libraries.
    eclipse // Import eclipse plugin for IDE integration.
    id("io.freefair.lombok") version "8.13.1" // Automatic lombok support.
}

dependencies { compileOnlyApi("org.jetbrains:annotations:24.1.0") }

tasks.javadoc { enabled = project.hasProperty("enable-javadoc") }
