# TAB-OG

TAB-OG is a soft fork of [TAB](https://github.com/NEZNAMY/TAB) maintained for [TrueOG Network](https://true-og.net/).

# About
This fork is based on archived source code of the latest release with full MC 1.x support on Bukkit and backporting services on modded platforms - 5.5.0.  
Starting with 6.0.0, upstream TAB dropped support for old Bukkit versions and modded platforms only support MC 26+ without offering backport services to 1.x.

# Differences from upstream TAB

### Platform support
- **Server**: Purpur 1.19.4 only.
- **Clients**: 1.8 through 1.21.11 via ViaVersion/ViaBackwards/ViaRewind.
- **Removed platforms**: Fabric, Forge, and NeoForge are not included.
- Produces a single universal JAR containing Bukkit, BungeeCord, and Velocity.
- Compatible with Vanish-OG.

### MiniMessage mixed syntax
Legacy color codes (`&a`, `§x` hex) and TAB's `#RRGGBB` format are automatically converted into MiniMessage tags when the server has MiniMessage available. You can freely mix `&c` legacy codes with `<gradient:#FF0000:#00FF00>` MiniMessage syntax in all text fields (header/footer, nametags, scoreboard, etc.) without manual conversion.

Enabled by default via `components.minimessage-support: true` in config.

### Vanish-OG compatibility
[Vanish-OG](https://github.com/true-og/Vanish-OG) registers a `VanishIntegration` handler with TAB's API (`me.neznamy.tab.api.integration.VanishIntegration`) on plugin enable, so TAB consults Vanish-OG's state manager directly via `canSee()` / `isVanished()` instead of relying on the `"vanished"` player metadata key. This avoids the join-time race where Forge clients crashed because TAB evaluated tab-list visibility before metadata was set. Vanished players are excluded from nametag team packets, tab list formatting, and sorting for viewers who cannot see them. The legacy `"vanished"` metadata path still works as a fallback for other vanish plugins that set it.

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
