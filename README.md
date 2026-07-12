# TAB-OG

TAB-OG is a soft fork of [TAB](https://github.com/NEZNAMY/TAB) for
[TrueOG Network](https://true-og.net/), based on upstream commit `93cfd74`.

Differences from upstream TAB:
- Versioned `5.9.9` (upstream is on `6.x`), targeting Purpur 1.19.4 serving 1.8–1.21.11
  clients through ViaVersion, ViaBackwards, and ViaRewind.
- Builds only Bukkit `v1_19_R3`, BungeeCord, and Velocity; drops Fabric, Forge, NeoForge,
  and the unused Bukkit/Paper NMS modules.
- Uses LuckPerms `5.5` (Vault removed) with a GraalVM JDK 17 toolchain, producing one
  universal shaded JAR.
- TrueOG compatibility fixes: [Vanish-OG](https://github.com/true-og/Vanish-OG), the
  [AFK-OG](https://github.com/true-og/AFK-OG) indicator, legacy scoreboard team colors,
  and disabled scoreboard number formats.
- Legacy (1.8–1.12.2) nametag rendering: over-head names drop the rank brackets (the
  tablist keeps them), sit one space after the rank in its color, bold the AFK indicator
  for every client, and fit within the 16-character team prefix/suffix limits. A bold
  rank is kept only when the whole tag can render bold — legacy clients width-count
  everything after `§l` as bold and never receive a `§r`, so a plain-rendered name after
  a bold rank pads the nametag end with phantom pixels; when it cannot fit, bold is
  stripped over-head. Keeping bold requires `add-teamcolor-to-prefix: false` in the
  ViaBackwards config, otherwise the appended team color un-bolds the name again.
- Ships live TrueOG Network config values.

Build with `./gradlew build` (JAR in `jar/build/libs/`). Requires LuckPerms; bold legacy
nametags additionally require ViaBackwards with `add-teamcolor-to-prefix: false`.
