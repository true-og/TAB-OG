# TAB-OG

TAB-OG is a soft fork of [TAB](https://github.com/NEZNAMY/TAB) maintained for [TrueOG Network](https://true-og.net/).

# About
TAB-OG keeps the TrueOG-focused packaging and compatibility model while merging upstream TAB changes through commit `93cfd74` (`[Bukkit] Refactor NMS implementation finding`, April 3, 2026).

This fork does not try to mirror upstream TAB's full platform matrix. It intentionally stays on the reduced TrueOG build:
- Bukkit/Purpur 1.19.4
- BungeeCord
- Velocity
- A single universal shaded JAR

The project version is `5.9.9` to make it clear this is a forked release line, not upstream TAB `6.x`.

# Differences from upstream TAB

### Platform support
- **Server**: Purpur 1.19.4 only.
- **Clients**: 1.8 through 1.21.11 via ViaVersion/ViaBackwards/ViaRewind.
- **Removed platforms**: Fabric, Forge, and NeoForge are not included.
- Produces a single universal JAR containing Bukkit, BungeeCord, and Velocity.
- Compatible with [Vanish-OG](https://github.com/true-og/Vanish-OG) in the TrueOG server/client environment (ViaSuite, forge clients, etc).

### Upstream sync level
- Includes upstream changes through commit `93cfd74`.
- Keeps recent upstream fixes that apply to the retained Bukkit/BungeeCord/Velocity modules.
- Does not adopt upstream's Java 21+ modded build toolchain or its broader server-version matrix.

### MiniMessage mixed syntax
Legacy color codes (`&a`, `§x` hex) and TAB's `#RRGGBB` format are automatically converted into MiniMessage tags when the server has MiniMessage available. You can freely mix `&c` legacy codes with `<gradient:#FF0000:#00FF00>` MiniMessage syntax in all text fields (header/footer, nametags, scoreboard, etc.) without manual conversion.

Enabled by default via `components.minimessage-support: true` in config.

### Config defaults
- `proxy-support.enabled` defaults to `true` with type `PLUGIN` (RedisBungee).
- `components.minimessage-support` defaults to `true`.
- `components.disable-shadow-for-heads` defaults to `true`.

# Compiling
Compilation requires GraalVM JDK 17.
To compile the plugin, run `./gradlew build` from the terminal.
Once the plugin compiles, grab the jar from `/jar/build/libs/` folder.

# Documentation
Upstream TAB documentation is available on its [Wiki](https://github.com/NEZNAMY/TAB/wiki). This includes a detailed description
of all features, as well as information regarding compatibility or limitations of each feature.
