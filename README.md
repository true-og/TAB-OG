# TAB-OG

TAB-OG is a soft fork of [TAB](https://github.com/NEZNAMY/TAB) maintained for [TrueOG Network](https://true-og.net/).

# Fork-specific targets

- Based on latest TAB v4.
- Purpur 1.19.4 server target.
- GraalVM 17 toolchain.
- Clients from 1.8 through 1.21.11 supported via merged upstream compatibility layers.

# About TAB
TAB aims to be a superior all-in-one minecraft plugin for displaying information that outperforms all
similar plugins in terms of features, performance and compatibility.
More information can be found at [Why TAB?](https://github.com/NEZNAMY/TAB/wiki/Why-TAB%3F) wiki page.

# Compiling
Compilation requires GraalVM JDK 17.
To compile the plugin, run `./gradlew build` from the terminal.
Once the plugin compiles, grab the jar from `/jar/build/libs/` folder.
The universal jar contains the merged Bukkit, BungeeCord and Velocity modules used by this fork.

# Documentation
You can find everything about TAB on its [Wiki](https://github.com/NEZNAMY/TAB/wiki). This includes a detailed description
of all features, as well as information regarding compatibility or limitations of each feature.
