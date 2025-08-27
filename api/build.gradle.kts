plugins {
    id("tab.parent")
    id("java") // Import Java plugin.
    id("java-library") // Import Java Library plugin.
    id("io.freefair.lombok") // Import automatic lombok support.
}

dependencies { compileOnlyApi("org.jetbrains:annotations:24.1.0") }

tasks.javadoc { enabled = project.hasProperty("enable-javadoc") }
