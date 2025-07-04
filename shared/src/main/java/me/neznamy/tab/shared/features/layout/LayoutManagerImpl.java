package me.neznamy.tab.shared.features.layout;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import me.neznamy.tab.api.tablist.layout.Layout;
import me.neznamy.tab.api.tablist.layout.LayoutManager;
import me.neznamy.tab.shared.ProtocolVersion;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;
import me.neznamy.tab.shared.chat.EnumChatFormat;
import me.neznamy.tab.shared.config.file.ConfigurationFile;
import me.neznamy.tab.shared.features.PlayerList;
import me.neznamy.tab.shared.features.layout.skin.SkinManager;
import me.neznamy.tab.shared.features.types.*;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public class LayoutManagerImpl extends TabFeature
        implements LayoutManager,
                JoinListener,
                QuitListener,
                VanishListener,
                Loadable,
                UnLoadable,
                Refreshable,
                TabListClearListener {

    /** Config options */
    private final Direction direction = parseDirection(config().getString("layout.direction", "COLUMNS"));

    private final String defaultSkin = config().getString("layout.default-skin", "mineskin:1753261242");
    private final Map<Integer, String> defaultSkinHashMap = loadDefaultSkins();
    private final boolean remainingPlayersTextEnabled =
            config().getBoolean("layout.enable-remaining-players-text", true);
    private final String remainingPlayersText =
            EnumChatFormat.color(config().getString("layout.remaining-players-text", "... and %s more"));
    private final int emptySlotPing = config().getInt("layout.empty-slot-ping-value", 1000);

    private final SkinManager skinManager = new SkinManager(defaultSkin, defaultSkinHashMap);
    private final Map<Integer, UUID> uuids = new HashMap<Integer, UUID>() {
        {
            for (int slot = 1; slot <= 80; slot++) {
                put(slot, new UUID(0, direction.translateSlot(slot)));
            }
        }
    };
    private final Map<String, LayoutPattern> layouts = loadLayouts();
    private final Map<TabPlayer, String> sortedPlayers =
            Collections.synchronizedMap(new TreeMap<>(Comparator.comparing(p -> p.layoutData.sortingString)));
    private PlayerList playerList;

    private static boolean teamsEnabled;

    public String getDefaultSkin(int slot) {
        return defaultSkinHashMap.getOrDefault(slot, defaultSkin);
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, String> loadDefaultSkins() {
        Map<Integer, String> defaultSkins = new HashMap<>();
        ConfigurationFile config = config();
        Map<String, Map<String, Object>> section = config.getConfigurationSection("layout.default-skins");
        for (Entry<String, Map<String, Object>> entry : section.entrySet()) {
            Map<String, Object> skinData = entry.getValue();
            String skin = (String) skinData.getOrDefault("skin", defaultSkin);
            for (String line : (List<String>) skinData.get("slots")) {
                String[] arr = line.split("-");
                int from = Integer.parseInt(arr[0]);
                int to = arr.length == 1 ? from : Integer.parseInt(arr[1]);
                for (int i = from; i <= to; i++) {
                    defaultSkins.put(i, skin);
                }
            }
        }
        return defaultSkins;
    }

    @Override
    public void load() {
        playerList = TAB.getInstance().getFeatureManager().getFeature(TabConstants.Feature.PLAYER_LIST);
        teamsEnabled = TAB.getInstance().getNameTagManager() != null;
        TAB.getInstance()
                .getFeatureManager()
                .registerFeature(TabConstants.Feature.LAYOUT_LATENCY, new LayoutLatencyRefresher(this));
        for (TabPlayer p : TAB.getInstance().getOnlinePlayers()) {
            onJoin(p);
        }
    }

    private @NotNull Direction parseDirection(@NotNull String value) {
        try {
            return Direction.valueOf(value);
        } catch (IllegalArgumentException e) {
            TAB.getInstance().getConfigHelper().startup().invalidLayoutDirection(value);
            return Direction.COLUMNS;
        }
    }

    private @NotNull Map<String, LayoutPattern> loadLayouts() {
        Map<String, LayoutPattern> layoutMap = new LinkedHashMap<>();
        for (Entry<Object, Map<String, Object>> layout : config().<Object, Map<String, Object>>getConfigurationSection(
                        "layout.layouts")
                .entrySet()) {
            LayoutPattern pattern = new LayoutPattern(this, layout.getKey().toString(), layout.getValue());
            layoutMap.put(pattern.getName(), pattern);
            TAB.getInstance()
                    .getFeatureManager()
                    .registerFeature(TabConstants.Feature.layout(layout.getKey().toString()), pattern);
        }
        return layoutMap;
    }

    @Override
    public void onJoin(@NotNull TabPlayer p) {
        p.layoutData.sortingString = p.sortingData.fullTeamName;
        sortedPlayers.put(p, p.sortingData.fullTeamName);
        LayoutPattern highest = getHighestLayout(p);
        if (highest != null) {
            LayoutView view = new LayoutView(this, highest, p);
            view.send();
            p.layoutData.view = view;
        }
        tickAllLayouts();

        // Unformat original entries for players who can see a layout to avoid spaces due to unparsed placeholders and
        // such
        if (highest == null) return;
        for (TabPlayer all : TAB.getInstance().getOnlinePlayers()) {
            p.getTabList().updateDisplayName(all.getTablistId(), null);
        }
    }

    @Override
    public void onQuit(@NotNull TabPlayer p) {
        sortedPlayers.remove(p);
        for (TabPlayer all : TAB.getInstance().getOnlinePlayers()) {
            if (all == p) continue;
            if (all.layoutData.view != null) all.layoutData.view.tick();
        }
    }

    @Override
    public void refresh(@NotNull TabPlayer p, boolean force) {
        LayoutPattern highest = getHighestLayout(p);
        String highestName = highest == null ? null : highest.getName();
        LayoutView current = p.layoutData.view;
        String currentName = current == null ? null : current.getPattern().getName();
        if (!Objects.equals(highestName, currentName)) {
            if (current != null) current.destroy();
            p.layoutData.view = null;
            if (highest != null) {
                LayoutView view = new LayoutView(this, highest, p);
                view.send();
                p.layoutData.view = view;
            }
        }
    }

    @Override
    @NotNull
    public String getRefreshDisplayName() {
        return "Switching layouts";
    }

    @Override
    public void unload() {
        for (TabPlayer p : TAB.getInstance().getOnlinePlayers()) {
            if (p.getVersion().getMinorVersion() < 8) continue;
            p.getTabList().removeEntries(uuids.values());
        }
    }

    @Override
    public void onVanishStatusChange(@NotNull TabPlayer p) {
        tickAllLayouts();
    }

    private @Nullable LayoutPattern getHighestLayout(@NotNull TabPlayer p) {
        if (p.layoutData.forcedLayout != null) return p.layoutData.forcedLayout;
        for (LayoutPattern pattern : layouts.values()) {
            if (pattern.isConditionMet(p)) return pattern;
        }
        return null;
    }

    public @NotNull UUID getUUID(int slot) {
        return uuids.get(slot);
    }

    public void updateTeamName(@NotNull TabPlayer p, @NotNull String teamName) {
        sortedPlayers.remove(p);
        p.layoutData.sortingString = teamName;
        sortedPlayers.put(p, teamName);
        tickAllLayouts();
    }

    @Override
    @NotNull
    public String getFeatureName() {
        return "Layout";
    }

    @Override
    public void onTabListClear(@NotNull TabPlayer player) {
        if (player.layoutData.view != null) player.layoutData.view.send();
    }

    /**
     * Ticks layouts for all players.
     */
    public void tickAllLayouts() {
        for (TabPlayer all : TAB.getInstance().getOnlinePlayers()) {
            if (all.layoutData.view != null) all.layoutData.view.tick();
        }
    }

    // ------------------
    // API Implementation
    // ------------------

    @Override
    public Layout createNewLayout(String name) {
        ensureActive();
        return new LayoutPattern(this, name, Collections.emptyMap());
    }

    @Override
    public void sendLayout(@NonNull me.neznamy.tab.api.TabPlayer player, @Nullable Layout layout) {
        ensureActive();
        TabPlayer p = (TabPlayer) player;
        p.ensureLoaded();
        p.layoutData.forcedLayout = (LayoutPattern) layout;
        refresh(p, false);
    }

    @Override
    public void resetLayout(@NonNull me.neznamy.tab.api.TabPlayer player) {
        ensureActive();
        TabPlayer p = (TabPlayer) player;
        p.ensureLoaded();
        p.layoutData.forcedLayout = null;
        refresh(p, false);
    }

    @RequiredArgsConstructor
    public enum Direction {
        COLUMNS(slot -> slot),
        ROWS(slot -> (slot - 1) % 4 * 20 + (slot - ((slot - 1) % 4)) / 4 + 1);

        @NotNull
        private final Function<Integer, Integer> slotTranslator;

        public int translateSlot(int slot) {
            return slotTranslator.apply(slot);
        }

        public String getEntryName(TabPlayer viewer, int slot) {
            if (viewer.getVersion().getNetworkId() >= ProtocolVersion.V1_19_3.getNetworkId()) {
                if (teamsEnabled) {
                    return "|slot_" + (10 + slotTranslator.apply(slot));
                } else {
                    return " slot_" + (10 + slotTranslator.apply(slot));
                }
            } else {
                return "";
            }
        }
    }

    /**
     * Class storing layout data for players.
     */
    public static class PlayerData {

        /** Merged string to sort players by */
        public String sortingString;

        /** Layout the player can currently see */
        @Nullable
        public LayoutView view;

        /** Layout forced via API */
        @Nullable
        public LayoutPattern forcedLayout;
    }
}
