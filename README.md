# TAB-OG

TAB-OG is a soft fork of [TAB](https://github.com/NEZNAMY/TAB) maintained for
[TrueOG Network](https://true-og.net/).

Differences from upstream TAB:
- Based on upstream commit `93cfd74` (`[Bukkit] Refactor NMS implementation
  finding`, April 3, 2026).
- Uses the forked version line `5.9.9` instead of upstream TAB `6.x`.
- Targets the TrueOG server environment: Purpur 1.19.4 with 1.8 through 1.21.11
  clients through ViaVersion, ViaBackwards, and ViaRewind.
- Builds only Bukkit, BungeeCord, Velocity, and Bukkit `v1_19_R3`.
- Removes Fabric, Forge, NeoForge, Paper-version modules, and older/newer Bukkit
  NMS modules from the build.
- Requires GraalVM JDK 17 and does not adopt upstream's newer-Java modded build
  toolchain.
- Produces one universal shaded plugin JAR named `TAB-OG-5.9.9.jar`.
- Includes TrueOG compatibility fixes for
  [Vanish-OG](https://github.com/true-og/Vanish-OG), ViaSuite, Forge clients,
  legacy scoreboard team colors, and disabled scoreboard number formats.
- Supports mixed MiniMessage, legacy color codes, and TAB `#RRGGBB` color syntax
  in text fields when MiniMessage support is available.
- Ships TrueOG-focused default config values for header/footer, scoreboard,
  layout, sorting groups, bossbar, below-name health, global playerlist,
  collision, MiniMessage, packet-events compensation, and Bukkit permission
  forwarding.
- Disables proxy support by default while leaving `type: PLUGIN` configured for
  RedisBungee-style setups.
- Clears sample MySQL, Redis, and RabbitMQ credentials instead of shipping
  placeholder passwords.
- Removes upstream GitHub issue templates, generated marketplace description
  assets, wiki snapshots, and GitHub Actions workflows that are not used by this
  fork.

Compile with `./gradlew build`; the universal plugin JAR is written to
`jar/build/libs/`.
