package me.neznamy.tab.shared.features.sorting;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import me.neznamy.tab.api.tablist.SortingManager;
import me.neznamy.tab.shared.Limitations;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;
import me.neznamy.tab.shared.features.layout.LayoutManagerImpl;
import me.neznamy.tab.shared.features.nametags.NameTag;
import me.neznamy.tab.shared.features.redis.RedisPlayer;
import me.neznamy.tab.shared.features.redis.RedisSupport;
import me.neznamy.tab.shared.features.sorting.types.Groups;
import me.neznamy.tab.shared.features.sorting.types.Permissions;
import me.neznamy.tab.shared.features.sorting.types.Placeholder;
import me.neznamy.tab.shared.features.sorting.types.PlaceholderAtoZ;
import me.neznamy.tab.shared.features.sorting.types.PlaceholderHighToLow;
import me.neznamy.tab.shared.features.sorting.types.PlaceholderLowToHigh;
import me.neznamy.tab.shared.features.sorting.types.PlaceholderZtoA;
import me.neznamy.tab.shared.features.sorting.types.SortingType;
import me.neznamy.tab.shared.features.types.JoinListener;
import me.neznamy.tab.shared.features.types.Loadable;
import me.neznamy.tab.shared.features.types.Refreshable;
import me.neznamy.tab.shared.features.types.TabFeature;
import me.neznamy.tab.shared.platform.Scoreboard;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class for handling player sorting rules
 */
public class Sorting extends TabFeature implements SortingManager, JoinListener, Loadable, Refreshable {

    private NameTag nameTags;
    private LayoutManagerImpl layout;
    private RedisSupport redis;

    // map of all registered sorting types
    private final Map<String, BiFunction<Sorting, String, SortingType>> types = new LinkedHashMap<>();

    // if sorting is case-sensitive or not
    @Getter
    private final boolean caseSensitiveSorting = config().getBoolean("scoreboard-teams.case-sensitive-sorting", true);

    // active sorting types
    private final SortingType[] usedSortingTypes;

    /**
     * Constructs new instance and loads config options
     */
    public Sorting() {

        types.put("GROUPS", Groups::new);
        types.put("PERMISSIONS", Permissions::new);
        types.put("PLACEHOLDER", Placeholder::new);
        types.put("PLACEHOLDER_A_TO_Z", PlaceholderAtoZ::new);
        types.put("PLACEHOLDER_Z_TO_A", PlaceholderZtoA::new);
        types.put("PLACEHOLDER_LOW_TO_HIGH", PlaceholderLowToHigh::new);
        types.put("PLACEHOLDER_HIGH_TO_LOW", PlaceholderHighToLow::new);
        usedSortingTypes = compile(config().getStringList("scoreboard-teams.sorting-types", new ArrayList<>()));

    }

    @Override
    public void refresh(@NotNull TabPlayer p, boolean force) {

        String previousShortName = p.sortingData.shortTeamName;
        constructTeamNames(p);
        if (!p.sortingData.shortTeamName.equals(previousShortName)) {

            if (nameTags != null && p.sortingData.forcedTeamName == null && !nameTags.hasTeamHandlingPaused(p)
                    && !p.disabledNametags.get())
            {

                for (TabPlayer viewer : TAB.getInstance().getOnlinePlayers()) {

                    viewer.getScoreboard().unregisterTeam(previousShortName);

                }

                nameTags.registerTeam(p);

            }

            if (layout != null)
                layout.updateTeamName(p, p.sortingData.fullTeamName);

        }

    }

    @Override
    @NotNull
    public String getRefreshDisplayName() {

        return "Updating team name";

    }

    @Override
    public void load() {

        // All of these features are instantiated after this one, so they must be
        // detected later
        nameTags = TAB.getInstance().getNameTagManager();
        layout = TAB.getInstance().getFeatureManager().getFeature(TabConstants.Feature.LAYOUT);
        redis = TAB.getInstance().getFeatureManager().getFeature(TabConstants.Feature.REDIS_BUNGEE);
        for (TabPlayer all : TAB.getInstance().getOnlinePlayers()) {

            onJoin(all);

        }

    }

    @Override
    public void onJoin(@NotNull TabPlayer connectedPlayer) {

        constructTeamNames(connectedPlayer);

    }

    /**
     * Compiles sorting type list into classes
     *
     * @return array of compiled sorting types
     */
    private @NotNull SortingType[] compile(@NotNull List<String> options) {

        List<SortingType> list = new ArrayList<>();
        for (String element : options) {

            String[] arr = element.split(":");
            if (!types.containsKey(arr[0].toUpperCase())) {

                TAB.getInstance().getConfigHelper().startup().invalidSortingTypeElement(arr[0].toUpperCase(),
                        types.keySet());

            } else {

                list.add(types.get(arr[0].toUpperCase()).apply(this,
                        arr.length == 1 ? "" : element.substring(arr[0].length() + 1)));

            }

        }

        return list.toArray(new SortingType[0]);

    }

    /**
     * Constructs short team names, both short (up to 16 characters long) and full
     * for specified player
     *
     * @param p player to build team name for
     */
    public void constructTeamNames(@NotNull TabPlayer p) {

        p.sortingData.teamNameNote = "";
        StringBuilder shortName = new StringBuilder();
        for (SortingType type : usedSortingTypes) {

            shortName.append(type.getChars(p));

        }

        StringBuilder fullName = new StringBuilder(shortName);
        if (layout != null) {

            // layout is enabled, start with max character to fix compatibility with plugins
            // which add empty player into a team such as LibsDisguises
            shortName.insert(0, Character.MAX_VALUE);

        }

        if (shortName.length() >= Limitations.TEAM_NAME_LENGTH) {

            shortName.setLength(Limitations.TEAM_NAME_LENGTH - 1);

        }

        String finalShortName = checkTeamName(p, shortName, 'A');
        p.sortingData.shortTeamName = finalShortName;
        p.sortingData.fullTeamName = fullName.append(finalShortName.charAt(finalShortName.length() - 1)).toString();

        // Do not randomly override note
        if (p.sortingData.forcedTeamName != null) {

            p.sortingData.teamNameNote = "Set using API";

        }

    }

    /**
     * Checks if team name is available and proceeds to try new values until free
     * name is found
     *
     * @param p           player to build team name for
     * @param currentName current up to 15 character long team name start
     * @param id          current character to check as 16th character
     * @return first available full team name
     */
    private @NotNull String checkTeamName(@NotNull TabPlayer p, @NotNull StringBuilder currentName, int id) {

        String potentialTeamName = currentName.toString() + (char) id;
        for (TabPlayer all : TAB.getInstance().getOnlinePlayers()) {

            if (all == p)
                continue;
            if (potentialTeamName.equals(all.sortingData.shortTeamName)) {

                return checkTeamName(p, currentName, id + 1);

            }

        }

        if (redis != null && redis.getRedisTeams() != null) {

            for (RedisPlayer all : redis.getRedisPlayers().values()) {

                if (all.getTeamName().equals(potentialTeamName)) {

                    return checkTeamName(p, currentName, id + 1);

                }

            }

        }

        return potentialTeamName;

    }

    /**
     * Converts sorting types into user-friendly sorting types into /tab debug
     *
     * @return user-friendly representation of sorting types
     */
    public @NotNull String typesToString() {

        return Arrays.stream(usedSortingTypes).map(SortingType::getDisplayName).collect(Collectors.joining(" -> "));

    }

    @Override
    @NotNull
    public String getFeatureName() {

        return "Sorting";

    }

    // ------------------
    // API Implementation
    // ------------------

    @Override
    public void forceTeamName(@NonNull me.neznamy.tab.api.TabPlayer player, String name) {

        ensureActive();
        TabPlayer p = (TabPlayer) player;
        p.ensureLoaded();
        if (Objects.equals(p.sortingData.forcedTeamName, name))
            return;
        if (name != null && name.length() > Limitations.TEAM_NAME_LENGTH)
            throw new IllegalArgumentException("Team name cannot be more than 16 characters long.");
        if (name != null)
            p.sortingData.teamNameNote = "Set using API";
        NameTag nametag = TAB.getInstance().getNameTagManager();
        if (nametag != null)
            nametag.unregisterTeam(p, p.sortingData.getShortTeamName());
        p.sortingData.forcedTeamName = name;
        if (nametag != null)
            nametag.registerTeam(p);
        if (layout != null)
            layout.updateTeamName(p, p.sortingData.fullTeamName);
        if (redis != null && nametag != null)
            redis.updateTeam(p, p.sortingData.getShortTeamName(),
                    (p).getProperty(TabConstants.Property.TAGPREFIX).get(),
                    (p).getProperty(TabConstants.Property.TAGSUFFIX).get(),
                    (nametag.getTeamVisibility(p, p) ? Scoreboard.NameVisibility.ALWAYS
                            : Scoreboard.NameVisibility.NEVER));

    }

    @Override
    @Nullable
    public String getForcedTeamName(@NonNull me.neznamy.tab.api.TabPlayer player) {

        ensureActive();
        return ((TabPlayer) player).sortingData.forcedTeamName;

    }

    @Override
    @NotNull
    public String getOriginalTeamName(@NonNull me.neznamy.tab.api.TabPlayer player) {

        ensureActive();
        ((TabPlayer) player).ensureLoaded();
        return ((TabPlayer) player).sortingData.shortTeamName;

    }

    /**
     * Class storing sorting data for players.
     */
    public static class PlayerData {

        /** Short team name (16 chars), used for teams */
        public String shortTeamName;

        /**
         * Full sorting string, used for sorting in Layout (and maybe for 1.18+ in the
         * future)
         */
        public String fullTeamName;

        /** Note explaining player's current team name */
        public String teamNameNote;

        /** Forced team name using API */
        @Nullable
        public String forcedTeamName;

        /**
         * Returns short team name. If forced using API, that value is returned.
         *
         * @return short team name to use
         */
        @NotNull
        public String getShortTeamName() {

            return forcedTeamName != null ? forcedTeamName : shortTeamName;

        }

    }

}
