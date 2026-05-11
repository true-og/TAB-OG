package me.neznamy.tab.shared.hook;

import lombok.Getter;
import lombok.NonNull;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;
import me.neznamy.tab.shared.platform.TabPlayer;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.query.QueryOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

/**
 * Class that hooks into required LuckPerms API.
 */
@Getter
public class LuckPermsHook {

    /** Instance of the class */
    @Getter private static final LuckPermsHook instance = new LuckPermsHook();

    /** Function retrieving group of player from LuckPerms */
    private final Function<TabPlayer, String> groupFunction = p -> {
        if (p.luckPermsUser == null) p.luckPermsUser = LuckPermsProvider.get().getUserManager().getUser(p.getUniqueId());
        if (p.luckPermsUser == null) {
            TAB.getInstance().debug("LuckPerms returned null user for player " + p.getName() + "( " + p.getUniqueId() + ")");
            return TabConstants.NO_GROUP;
        }
        return p.luckPermsUser.getPrimaryGroup();
    };

    /**
     * Returns player's prefix configured in LuckPerms
     *
     * @param   p
     *          Player to get prefix of
     * @return  Player's prefix
     */
    @NotNull
    public String getPrefix(@NonNull TabPlayer p) {
        CachedMetaData data = getCachedMetaData(p);
        if (data == null) return "";
        String value = data.getPrefix();
        return value == null ? "" : value;
    }

    /**
     * Returns player's prefixes configured in LuckPerms separated by an empty string.
     *
     * @param   p
     *          Player to get prefixes of
     * @return  Player's prefixes
     */
    @NotNull
    public String getPrefixes(@NonNull TabPlayer p) {
        return Optional.ofNullable(getCachedMetaData(p)).map(CachedMetaData::getPrefixes).map(pr -> String.join("", pr.values())).orElse("");
    }

    /**
     * Returns player's suffix configured in LuckPerms
     *
     * @param   p
     *          Player to get suffix of
     * @return  Player's suffix
     */
    @NotNull
    public String getSuffix(@NonNull TabPlayer p) {
        CachedMetaData data = getCachedMetaData(p);
        if (data == null) return "";
        String value = data.getSuffix();
        return value == null ? "" : value;
    }

    /**
     * Returns player's suffixes configured in LuckPerms separated by an empty string.
     *
     * @param   p
     *          Player to get suffixes of
     * @return  Player's suffixes
     */
    @NotNull
    public String getSuffixes(@NonNull TabPlayer p) {
        return Optional.ofNullable(getCachedMetaData(p)).map(CachedMetaData::getSuffixes).map(s -> String.join("", s.values())).orElse("");
    }

    /**
     * Returns player's metadata from LuckPerms
     *
     * @param   p
     *          Player to get metadata of
     * @return  Player's metadata
     */
    @Nullable
    private CachedMetaData getCachedMetaData(@NonNull TabPlayer p) {
        if (p.luckPermsUser == null) p.luckPermsUser = LuckPermsProvider.get().getUserManager().getUser(p.getUniqueId());
        if (p.luckPermsUser == null) return null;
        Optional<QueryOptions> options = LuckPermsProvider.get().getContextManager().getQueryOptions(p.luckPermsUser);
        return options.map(queryOptions -> p.luckPermsUser.getCachedData().getMetaData(queryOptions)).orElse(null);
    }

    /**
     * Returns player's LuckPerms weight.
     *
     * @param   tabPlayer
     *          Player to get weight of
     * @return  Player's LuckPerms weight
     */
    public int getWeight(@NonNull TabPlayer tabPlayer) {
        CachedMetaData data = getCachedMetaData(tabPlayer);
        return data == null ? 0 : data.getWeight();
    }
}
