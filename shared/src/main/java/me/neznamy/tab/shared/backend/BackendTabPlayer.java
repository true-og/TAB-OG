package me.neznamy.tab.shared.backend;

import java.util.UUID;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.backend.entityview.EntityView;
import me.neznamy.tab.shared.hook.ViaVersionHook;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * TabPlayer extension for backend platforms, which have access to
 * more data and can display it.
 */
public abstract class BackendTabPlayer extends TabPlayer {

    /**
     * Constructs new instance with given parameters
     *
     * @param   platform
     *          Server platform reference
     * @param   player
     *          platform-specific player object
     * @param   uniqueId
     *          Player's unique ID
     * @param   name
     *          Player's name
     * @param   world
     *          Player's world
     * @param   serverVersion
     *          Server version
     */
    protected BackendTabPlayer(
            @NotNull BackendPlatform platform,
            @NotNull Object player,
            @NotNull UUID uniqueId,
            @NotNull String name,
            @NotNull String world,
            int serverVersion) {
        super(
                platform,
                player,
                uniqueId,
                name,
                TAB.getInstance().getConfiguration().getServerName(),
                world,
                ViaVersionHook.getInstance().getPlayerVersion(uniqueId, name, serverVersion),
                true);
    }

    /**
     * Returns player's health for {@link me.neznamy.tab.shared.TabConstants.Placeholder#HEALTH} placeholder.
     *
     * @return  player's health
     */
    public abstract double getHealth();

    /**
     * Returns player's display name for {@link me.neznamy.tab.shared.TabConstants.Placeholder#DISPLAY_NAME} placeholder.
     *
     * @return  player's display name
     */
    public abstract String getDisplayName();

    /**
     * Returns player's entity view
     *
     * @return  player's entity view
     */
    public abstract EntityView getEntityView();
}
