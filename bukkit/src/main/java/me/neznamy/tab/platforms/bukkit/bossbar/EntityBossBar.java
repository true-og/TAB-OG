package me.neznamy.tab.platforms.bukkit.bossbar;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import me.neznamy.tab.api.bossbar.BarColor;
import me.neznamy.tab.api.bossbar.BarStyle;
import me.neznamy.tab.platforms.bukkit.BukkitTabPlayer;
import me.neznamy.tab.platforms.bukkit.entity.DataWatcher;
import me.neznamy.tab.shared.backend.Location;
import me.neznamy.tab.shared.platform.BossBar;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

/**
 * BossBar using wither entity for <1.9 players on <1.9 servers.
 * Additional logic, such as teleporting the entity must be done
 * separately, as this class does not handle it.
 */
@RequiredArgsConstructor
public class EntityBossBar implements BossBar {

    /** Max health of wither */
    private static final int WITHER_MAX_HEALTH = 300;

    /** Ideal value for invulnerable time for minimal wither size */
    private static final int IDEAL_INVULNERABLE_TIME = 880;

    /** Flag making entity invisible */
    private static final byte INVISIBLE_FLAG = 1 << 5;

    /** Player this handler belongs to */
    @NotNull
    private final BukkitTabPlayer player;

    @Override
    public void create(
            @NotNull UUID id, @NotNull String title, float progress, @NotNull BarColor color, @NotNull BarStyle style) {
        DataWatcher w = new DataWatcher();
        float health = WITHER_MAX_HEALTH * progress;
        if (health == 0) health = 1;
        w.setHealth(health);
        w.setCustomName(title, player.getVersion());
        w.setEntityFlags(INVISIBLE_FLAG);
        w.setWitherInvulnerableTime(IDEAL_INVULNERABLE_TIME);
        player.getEntityView().spawnEntity(id.hashCode(), new UUID(0, 0), EntityType.WITHER, new Location(0, 0, 0), w);
    }

    @Override
    public void update(@NotNull UUID id, @NotNull String title) {
        DataWatcher w = new DataWatcher();
        w.setCustomName(title, player.getVersion());
        player.getEntityView().updateEntityMetadata(id.hashCode(), w);
    }

    @Override
    public void update(@NotNull UUID id, float progress) {
        DataWatcher w = new DataWatcher();
        float health = WITHER_MAX_HEALTH * progress;
        if (health == 0) health = 1;
        w.setHealth(health);
        player.getEntityView().updateEntityMetadata(id.hashCode(), w);
    }

    @Override
    public void update(@NotNull UUID id, @NotNull BarStyle style) {
        /*Added in 1.9*/
    }

    @Override
    public void update(@NotNull UUID id, @NotNull BarColor color) {
        /*Added in 1.9*/
    }

    @Override
    public void remove(@NotNull UUID id) {
        player.getEntityView().destroyEntities(id.hashCode());
    }
}
