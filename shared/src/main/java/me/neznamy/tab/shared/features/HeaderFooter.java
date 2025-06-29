package me.neznamy.tab.shared.features;

import java.util.ArrayList;
import java.util.List;
import me.neznamy.tab.api.tablist.HeaderFooterManager;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;
import me.neznamy.tab.shared.chat.SimpleComponent;
import me.neznamy.tab.shared.chat.TabComponent;
import me.neznamy.tab.shared.features.types.*;
import me.neznamy.tab.shared.placeholders.conditions.Condition;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Feature handler for header and footer.
 */
public class HeaderFooter extends TabFeature
        implements HeaderFooterManager,
                JoinListener,
                Loadable,
                UnLoadable,
                WorldSwitchListener,
                ServerSwitchListener,
                Refreshable {

    private final List<Object> worldGroups = new ArrayList<>(
            config().getConfigurationSection("header-footer.per-world").keySet());
    private final List<Object> serverGroups = new ArrayList<>(
            config().getConfigurationSection("header-footer.per-server").keySet());
    private final DisableChecker disableChecker;

    /**
     * Constructs new instance and registers disable condition checker to feature manager.
     */
    public HeaderFooter() {
        Condition disableCondition = Condition.getCondition(config().getString("header-footer.disable-condition"));
        disableChecker = new DisableChecker(
                getFeatureName(), disableCondition, this::onDisableConditionChange, p -> p.disabledHeaderFooter);
        TAB.getInstance()
                .getFeatureManager()
                .registerFeature(TabConstants.Feature.HEADER_FOOTER + "-Condition", disableChecker);
        TAB.getInstance()
                .getConfigHelper()
                .hint()
                .checkHeaderFooterForRedundancy(config().getConfigurationSection("header-footer"));
    }

    @Override
    public void load() {
        for (TabPlayer p : TAB.getInstance().getOnlinePlayers()) {
            onJoin(p);
        }
    }

    @Override
    public void unload() {
        for (TabPlayer p : TAB.getInstance().getOnlinePlayers()) {
            if (p.disabledHeaderFooter.get()) continue;
            sendHeaderFooter(p, "", "");
        }
    }

    @Override
    public void onJoin(@NotNull TabPlayer connectedPlayer) {
        if (disableChecker.isDisableConditionMet(connectedPlayer)) {
            connectedPlayer.disabledHeaderFooter.set(true);
        }
        refresh(connectedPlayer, true);
    }

    @Override
    public void onServerChange(@NotNull TabPlayer p, @NotNull String from, @NotNull String to) {
        // Velocity clears header/footer on server switch, resend regardless of whether values changed or not
        refresh(p, true);
    }

    @Override
    public void onWorldChange(@NotNull TabPlayer p, @NotNull String from, @NotNull String to) {
        updateProperties(p);
    }

    private void updateProperties(TabPlayer p) {
        boolean refresh =
                p.setProperty(this, TabConstants.Property.HEADER, getProperty(p, TabConstants.Property.HEADER));
        if (p.setProperty(this, TabConstants.Property.FOOTER, getProperty(p, TabConstants.Property.FOOTER))) {
            refresh = true;
        }
        if (refresh) {
            sendHeaderFooter(
                    p,
                    p.getProperty(TabConstants.Property.HEADER).get(),
                    p.getProperty(TabConstants.Property.FOOTER).get());
        }
    }

    @Override
    public void refresh(@NotNull TabPlayer p, boolean force) {
        if (force) {
            p.setProperty(this, TabConstants.Property.HEADER, getProperty(p, TabConstants.Property.HEADER));
            p.setProperty(this, TabConstants.Property.FOOTER, getProperty(p, TabConstants.Property.FOOTER));
        }
        sendHeaderFooter(
                p,
                p.getProperty(TabConstants.Property.HEADER).updateAndGet(),
                p.getProperty(TabConstants.Property.FOOTER).updateAndGet());
    }

    @Override
    @NotNull
    public String getRefreshDisplayName() {
        return "Updating header/footer";
    }

    /**
     * Processes disable condition change.
     *
     * @param   p
     *          Player who the condition has changed for
     * @param   disabledNow
     *          Whether the feature is disabled now or not
     */
    public void onDisableConditionChange(TabPlayer p, boolean disabledNow) {
        if (disabledNow) {
            p.getTabList().setPlayerListHeaderFooter(new SimpleComponent(""), new SimpleComponent(""));
        } else {
            sendHeaderFooter(
                    p,
                    p.getProperty(TabConstants.Property.HEADER).get(),
                    p.getProperty(TabConstants.Property.FOOTER).get());
        }
    }

    private String getProperty(TabPlayer p, String property) {
        String append = getFromConfig(p, property + "append");
        if (!append.isEmpty()) append = "\n" + append;
        return getFromConfig(p, property) + append;
    }

    private String getFromConfig(TabPlayer p, String property) {
        String[] value = TAB.getInstance()
                .getConfiguration()
                .getUsers()
                .getProperty(p.getName(), property, p.getServer(), p.getWorld());
        if (value.length > 0) {
            return value[0];
        }
        value = TAB.getInstance()
                .getConfiguration()
                .getUsers()
                .getProperty(p.getUniqueId().toString(), property, p.getServer(), p.getWorld());
        if (value.length > 0) {
            return value[0];
        }
        value = TAB.getInstance()
                .getConfiguration()
                .getGroups()
                .getProperty(p.getGroup(), property, p.getServer(), p.getWorld());
        if (value.length > 0) {
            return value[0];
        }
        List<String> lines = config().getStringList("header-footer.per-world."
                + TAB.getInstance().getConfiguration().getGroup(worldGroups, p.getWorld()) + "." + property);
        if (lines == null) {
            lines = config().getStringList("header-footer.per-server."
                    + TAB.getInstance().getConfiguration().getServerGroup(serverGroups, p.getServer()) + "."
                    + property);
        }
        if (lines == null) {
            lines = config().getStringList("header-footer." + property);
        }
        if (lines == null) lines = new ArrayList<>();
        return String.join("\n", lines);
    }

    private void sendHeaderFooter(TabPlayer player, String header, String footer) {
        if (player.disabledHeaderFooter.get()) return;
        player.getTabList().setPlayerListHeaderFooter(TabComponent.optimized(header), TabComponent.optimized(footer));
    }

    @Override
    @NotNull
    public String getFeatureName() {
        return "Header/Footer";
    }

    // ------------------
    // API Implementation
    // ------------------

    @Override
    public void setHeader(@NotNull me.neznamy.tab.api.TabPlayer p, @Nullable String header) {
        ensureActive();
        TabPlayer player = (TabPlayer) p;
        player.ensureLoaded();
        player.getProperty(TabConstants.Property.HEADER).setTemporaryValue(header);
        sendHeaderFooter(
                player,
                player.getProperty(TabConstants.Property.HEADER).updateAndGet(),
                player.getProperty(TabConstants.Property.FOOTER).updateAndGet());
    }

    @Override
    public void setFooter(@NotNull me.neznamy.tab.api.TabPlayer p, @Nullable String footer) {
        ensureActive();
        TabPlayer player = (TabPlayer) p;
        player.ensureLoaded();
        player.getProperty(TabConstants.Property.FOOTER).setTemporaryValue(footer);
        sendHeaderFooter(
                player,
                player.getProperty(TabConstants.Property.HEADER).updateAndGet(),
                player.getProperty(TabConstants.Property.FOOTER).updateAndGet());
    }

    @Override
    public void setHeaderAndFooter(
            @NotNull me.neznamy.tab.api.TabPlayer p, @Nullable String header, @Nullable String footer) {
        ensureActive();
        TabPlayer player = (TabPlayer) p;
        player.ensureLoaded();
        player.getProperty(TabConstants.Property.HEADER).setTemporaryValue(header);
        player.getProperty(TabConstants.Property.FOOTER).setTemporaryValue(footer);
        sendHeaderFooter(
                player,
                player.getProperty(TabConstants.Property.HEADER).updateAndGet(),
                player.getProperty(TabConstants.Property.FOOTER).updateAndGet());
    }
}
