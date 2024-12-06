dependencies {
    implementation(projects.shared)
    compileOnly("org.purpurmc.purpur:purpur-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    // Add Utilities-OG dependency
    implementation(project(":libs:Utilities-OG"))
    compileOnly("com.mojang:authlib:1.5.25")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude("org.bukkit", "bukkit")
    }
    compileOnly("com.github.LeonMangler:PremiumVanishAPI:2.8.8")
}
