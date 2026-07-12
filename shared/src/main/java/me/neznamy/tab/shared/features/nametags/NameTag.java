package me.neznamy.tab.shared.features.nametags;

import lombok.Getter;
import lombok.NonNull;
import me.neznamy.tab.api.nametag.NameTagManager;
import me.neznamy.tab.shared.Limitations;
import me.neznamy.tab.shared.Property;
import me.neznamy.tab.shared.ProtocolVersion;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;
import me.neznamy.tab.shared.config.MessageFile;
import me.neznamy.tab.shared.cpu.ThreadExecutor;
import me.neznamy.tab.shared.cpu.TimedCaughtTask;
import me.neznamy.tab.shared.data.Server;
import me.neznamy.tab.shared.data.World;
import me.neznamy.tab.shared.features.proxy.ProxyPlayer;
import me.neznamy.tab.shared.features.proxy.ProxySupport;
import me.neznamy.tab.shared.features.types.*;
import me.neznamy.tab.shared.platform.Scoreboard;
import me.neznamy.tab.shared.platform.Scoreboard.CollisionRule;
import me.neznamy.tab.shared.platform.Scoreboard.NameVisibility;
import me.neznamy.tab.shared.platform.TabPlayer;
import me.neznamy.tab.shared.platform.decorators.SafeScoreboard;
import me.neznamy.tab.shared.util.DumpUtils;
import me.neznamy.tab.shared.util.OnlinePlayers;
import me.neznamy.tab.shared.util.cache.LastColorCache;
import me.neznamy.tab.shared.util.cache.StringToComponentCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Getter
public class NameTag extends RefreshableFeature implements NameTagManager, JoinListener, QuitListener,
        Loadable, WorldSwitchListener, ServerSwitchListener, VanishListener, CustomThreaded, ProxyFeature, GroupListener, Dumpable {

    private final ThreadExecutor customThread = new ThreadExecutor("TAB NameTag Thread");
    private OnlinePlayers onlinePlayers;
    private final TeamConfiguration configuration;
    private final StringToComponentCache prefixCache = new StringToComponentCache("NameTag prefix", 1000);
    private final StringToComponentCache lastColorCache = new LastColorCache("NameTag last prefix color", 1000);
    private final StringToComponentCache suffixCache = new StringToComponentCache("NameTag suffix", 1000);
    private static final String LEGACY_NAMETAG_SEPARATOR = " ";
    private static final Pattern TRAILING_FORMAT_OR_SPACE =
            Pattern.compile("([\\s\\p{Z}\\p{Cf}]|§[0-9a-fk-orxA-FK-ORX]?)+$");
    private static final Pattern AFK_BADGE = Pattern.compile("(?<!§l)AFK");

    /**
     * Repeatedly strips trailing whitespace, zero-width/format characters (NBSP, ZWSP, BOM), and
     * trailing legacy format codes such as {@code §r} or {@code §7}.
     */
    @NotNull
    public static String stripTrailingFormat(@NotNull String input) {
        return TRAILING_FORMAT_OR_SPACE.matcher(input).replaceAll("");
    }

    /**
     * Tracks bold state through legacy color codes and reports whether the last visible
     * (non-control) character would render bold.
     */
    private static boolean lastVisibleCharIsBold(@NotNull String input) {
        boolean bold = false;
        boolean lastVisibleWasBold = false;
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c == '§' && i + 1 < len) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                if (code == 'l') {
                    bold = true;
                } else if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    bold = false;
                }
                i++;
            } else {
                lastVisibleWasBold = bold;
            }
        }
        return lastVisibleWasBold;
    }

    /**
     * Formats the over-head nametag prefix: legacy (1.8-1.12) viewers keep a bold rank only when the
     * whole tag can render bold within the 16-char team prefix limit, otherwise bold is stripped and
     * a color-protected separator kept; 1.13+ viewers only get a separating space after a bold tail.
     */
    @NotNull
    public static String compensateLegacyBoldPrefix(@NotNull TabPlayer viewer, @NotNull String prefix) {
        return compensateLegacyBoldPrefix(viewer.getVersion().getMinorVersion() < 13, prefix);
    }

    /** Viewer-independent core of {@link #compensateLegacyBoldPrefix(TabPlayer, String)}, exposed for tests. */
    @NotNull
    static String compensateLegacyBoldPrefix(boolean legacyViewer, @NotNull String prefix) {
        if (legacyViewer) {
            // 1.8-1.12 string width counts every glyph after §l as bold (+1px each) and only §r clears
            // that flag, while rendering clears it on any color code — and ViaBackwards' prefix
            // rewriter never emits §r. A bold rank followed by a differently-styled name therefore
            // makes the client overcount the name's width, padding the end of the floating nametag
            // with empty space. Keep bold only when the whole tag can render bold (rank, name and
            // suffix stay in agreement with the width calc); otherwise drop it over-head (the
            // tablist keeps it) and keep the separator.
            if (containsLegacyBold(prefix)) {
                String bold = keepLegacyBoldPrefix(prefix);
                if (bold != null) return bold;
                return capLegacyPrefix(normalizeLegacyNametagSeparator(stripLegacyBold(prefix), true));
            }
            return capLegacyPrefix(normalizeLegacyNametagSeparator(prefix, false));
        }
        if (!lastVisibleCharIsBold(prefix)) return prefix;
        return prefix.endsWith(" ") ? prefix : prefix + " ";
    }

    /** True when the legacy over-head prefix keeps its bold styling, which requires the rest of the tag to render bold too. */
    static boolean legacyPrefixKeepsBold(@NotNull String prefix) {
        return containsLegacyBold(prefix) && keepLegacyBoldPrefix(prefix) != null;
    }

    /**
     * Keeps a bold rank on the legacy over-head nametag when it fits 16 chars: re-asserts {@code §l}
     * after every color change (colors visually reset bold but the 1.8-1.12 width calc does not) and
     * ends with the name's color plus {@code §l} carried by the separator space, so the name renders
     * bold in its configured color and width matches rendering all the way through. Requires the
     * proxy to not append a team color after the prefix (ViaBackwards
     * {@code add-teamcolor-to-prefix: false}), as an appended color would visually un-bold the name
     * again. Returns {@code null} when the result would not fit, so the caller falls back to
     * stripping bold.
     */
    @Nullable
    private static String keepLegacyBoldPrefix(@NotNull String prefix) {
        String rank = stripTrailingFormat(prefix);
        String nameStyle = lastLegacyStyle(prefix, prefix.length());
        if (!nameStyle.contains("§l")) nameStyle += "§l";
        String result = propagateLegacyBold(rank, false) + nameStyle + LEGACY_NAMETAG_SEPARATOR;
        return result.length() <= Limitations.TEAM_PREFIX_SUFFIX_PRE_1_13 ? result : null;
    }

    /**
     * Re-asserts {@code §l} after every color code once bold is active: legacy clients visually reset
     * bold on a color code but keep counting glyph widths as bold ({@code §r} never reaches them —
     * ViaBackwards rewrites it to a plain color), so every glyph after the first {@code §l} must
     * render bold for width to match rendering. {@code boldActive} marks bold carried in from the
     * preceding prefix.
     */
    @NotNull
    private static String propagateLegacyBold(@NotNull String input, boolean boldActive) {
        StringBuilder out = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            out.append(c);
            if (c != '§' || i + 1 >= input.length()) continue;
            char code = Character.toLowerCase(input.charAt(++i));
            out.append(input.charAt(i));
            if (code == 'l') {
                boldActive = true;
            } else if (boldActive && (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f'))) {
                boolean alreadyReBolded = i + 2 < input.length() && input.charAt(i + 1) == '§'
                        && Character.toLowerCase(input.charAt(i + 2)) == 'l';
                if (!alreadyReBolded) out.append("§l");
            }
        }
        return out.toString();
    }

    /**
     * Truncates a legacy team prefix to 16 chars on a clean boundary (never splitting a format code or
     * surrogate pair) so an over-long base prefix is not truncated into a broken state by ViaVersion.
     */
    @NotNull
    private static String capLegacyPrefix(@NotNull String prefix) {
        return capLegacyPrefix(prefix, Limitations.TEAM_PREFIX_SUFFIX_PRE_1_13);
    }

    /** Truncates {@code prefix} to {@code limit} chars on a clean boundary (never a split code or surrogate). */
    @NotNull
    private static String capLegacyPrefix(@NotNull String prefix, int limit) {
        if (limit <= 0) return "";
        if (prefix.length() <= limit) return prefix;
        int end = limit;
        while (end > 0 && (prefix.charAt(end - 1) == '§' || Character.isHighSurrogate(prefix.charAt(end - 1)))) end--;
        return prefix.substring(0, end);
    }

    /**
     * Drops the [AFK] brackets, bolds the AFK badge for every viewer, collapses spaces, strips the
     * tail, and for legacy viewers keeps the trailing content within 16 chars while the leading
     * cosmetic yields. {@code prefix} is the same raw prefix passed to
     * {@link #compensateLegacyBoldPrefix(TabPlayer, String)}, used to detect bold carrying across
     * the name into the suffix.
     */
    @NotNull
    public static String fitLegacySuffix(@NotNull TabPlayer viewer, @NotNull String suffix, @NotNull String prefix) {
        boolean legacyViewer = viewer.getVersion().getMinorVersion() < 13;
        return fitLegacySuffix(legacyViewer, suffix, legacyViewer && legacyPrefixKeepsBold(prefix));
    }

    /** Viewer-independent core of {@link #fitLegacySuffix(TabPlayer, String, String)}, exposed for tests. */
    @NotNull
    static String fitLegacySuffix(boolean legacyViewer, @NotNull String suffix, boolean boldCarriedIn) {
        // Over-head nametag drops the [AFK] brackets (tablist keeps them) and bolds the badge for
        // every viewer for a uniform look, collapses space runs, and re-strips any trailing space or
        // format code the bracket removal leaves at the very end.
        suffix = boldAfkBadge(suffix.replace("[", "").replace("]", ""));
        // Legacy width calc counts every glyph after §l as bold even once a color code visually
        // resets it, so keep the suffix rendering bold from the first §l — or from the very start
        // when the prefix carries bold across the name — to avoid phantom pixels at the tag end.
        if (legacyViewer) suffix = propagateLegacyBold(suffix, boldCarriedIn);
        suffix = stripTrailingFormat(collapseSpacing(suffix));
        if (!legacyViewer || suffix.length() <= Limitations.TEAM_PREFIX_SUFFIX_PRE_1_13) return suffix;
        // Over budget: cut on a color/reset boundary and re-attach one leading space, so the first kept
        // glyph carries its own color (never the name's) and stays separated from the player name.
        int start = suffix.length() - Limitations.TEAM_PREFIX_SUFFIX_PRE_1_13 + 1;
        while (start < suffix.length() && !startsWithColorCode(suffix, start)) start++;
        return start >= suffix.length() ? "" : " " + suffix.substring(start);
    }

    /** Bolds the AFK badge for every viewer so it reads uniformly next to bold-kept legacy nametags. */
    @NotNull
    private static String boldAfkBadge(@NotNull String suffix) {
        return AFK_BADGE.matcher(suffix).replaceAll("§lAFK");
    }

    /** True when index {@code i} begins a legacy color or reset code ({@code §0}-{@code §f} or {@code §r}). */
    private static boolean startsWithColorCode(@NotNull String s, int i) {
        if (s.charAt(i) != '§' || i + 1 >= s.length()) return false;
        char c = Character.toLowerCase(s.charAt(i + 1));
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == 'r';
    }

    /**
     * Collapses each run of spaces down to a single space, keeping intervening format codes in place, so
     * two visible spaces never appear even when a space, a format code, and another space are adjacent.
     */
    @NotNull
    private static String collapseSpacing(@NotNull String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean lastVisibleWasSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                if (lastVisibleWasSpace) continue;
                out.append(' ');
                lastVisibleWasSpace = true;
            } else if (c == '§' && i + 1 < s.length()) {
                out.append(c).append(s.charAt(++i));
            } else {
                out.append(c);
                lastVisibleWasSpace = false;
            }
        }
        return out.toString();
    }

    private static boolean containsLegacyBold(@NotNull String input) {
        for (int i = 0; i < input.length() - 1; i++) {
            if (input.charAt(i) == '§' && Character.toLowerCase(input.charAt(i + 1)) == 'l') {
                return true;
            }
        }
        return false;
    }

    @NotNull
    private static String normalizeLegacyNametagSeparator(@NotNull String prefix, boolean addIfMissing) {
        int formatStart = prefix.length();
        while (formatStart >= 2 && prefix.charAt(formatStart - 2) == '§' && isLegacyFormatCode(prefix.charAt(formatStart - 1))) {
            formatStart -= 2;
        }

        int separatorStart = formatStart;
        while (separatorStart > 0 && isVisibleSeparator(prefix.charAt(separatorStart - 1))) {
            separatorStart--;
        }

        boolean hasSeparator = separatorStart < formatStart;
        if (!hasSeparator && !addIfMissing) return prefix;

        String head = prefix.substring(0, hasSeparator ? separatorStart : formatStart);
        String trailingFormat = prefix.substring(formatStart);
        String separator = chooseLegacySeparator(prefix, head.length() + trailingFormat.length(), trailingFormat, formatStart);
        if (!separator.isEmpty()) return head + separator + trailingFormat;
        // The space did not fit: trim the rank text minimally so a color-terminated space fits.
        String protectedSeparator = LEGACY_NAMETAG_SEPARATOR + lastLegacyStyle(head, head.length());
        int maxHead = Limitations.TEAM_PREFIX_SUFFIX_PRE_1_13 - protectedSeparator.length() - trailingFormat.length();
        return capLegacyPrefix(head, maxHead) + protectedSeparator + trailingFormat;
    }

    /** Removes every legacy bold code ({@code §l}); legacy clients width-count post-bold glyphs as bold, padding the nametag end. */
    @NotNull
    private static String stripLegacyBold(@NotNull String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '§' && i + 1 < input.length() && Character.toLowerCase(input.charAt(i + 1)) == 'l') {
                i++;
                continue;
            }
            out.append(input.charAt(i));
        }
        return out.toString();
    }

    /**
     * Chooses a prefix/name separator that keeps the team prefix within 16 chars: a bare space when a
     * trailing color already protects it, else a color-terminated space; empty when neither fits.
     */
    @NotNull
    private static String chooseLegacySeparator(@NotNull String prefix, int fixedLength, @NotNull String trailingFormat, int formatStart) {
        int budget = Limitations.TEAM_PREFIX_SUFFIX_PRE_1_13 - fixedLength;
        if (!trailingFormat.isEmpty()) {
            return budget >= LEGACY_NAMETAG_SEPARATOR.length() ? LEGACY_NAMETAG_SEPARATOR : "";
        }
        String protectedSeparator = LEGACY_NAMETAG_SEPARATOR + lastLegacyStyle(prefix, formatStart);
        return budget >= protectedSeparator.length() ? protectedSeparator : "";
    }

    /**
     * Returns the legacy color plus active magic codes at index {@code end} (a color resets earlier magic
     * codes), or {@code §r} if none; terminates a separator space so the following name keeps its style.
     */
    @NotNull
    private static String lastLegacyStyle(@NotNull String prefix, int end) {
        String color = "";
        StringBuilder formats = new StringBuilder();
        for (int i = 0; i + 1 < end; i++) {
            if (prefix.charAt(i) != '§') continue;
            char c = Character.toLowerCase(prefix.charAt(i + 1));
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == 'r') {
                color = prefix.substring(i, i + 2);
                formats.setLength(0); // a color/reset code clears active magic codes
            } else if (c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o') {
                formats.append(prefix, i, i + 2);
            }
            i++;
        }
        String style = color + formats;
        return style.isEmpty() ? "§r" : style;
    }

    private static boolean isLegacyFormatCode(char code) {
        code = Character.toLowerCase(code);
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') ||
                code == 'k' || code == 'l' || code == 'm' || code == 'n' ||
                code == 'o' || code == 'r' || code == 'x';
    }

    private static boolean isVisibleSeparator(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.getType(c) == Character.FORMAT;
    }

    private final VisibilityRefresher visibilityRefresher;
    private final CollisionManager collisionManager;
    private final int teamOptions;
    private final DisableChecker disableChecker;
    @Nullable private final ProxySupport proxy = TAB.getInstance().getFeatureManager().getFeature(TabConstants.Feature.PROXY_SUPPORT);

    /**
     * Constructs new instance and registers sub-features.
     *
     * @param   configuration
     *          Feature configuration
     */
    public NameTag(@NotNull TeamConfiguration configuration) {
        this.configuration = configuration;
        teamOptions = configuration.isCanSeeFriendlyInvisibles() ? 2 : 0;
        disableChecker = new DisableChecker(this, TAB.getInstance().getPlaceholderManager().getConditionManager().getByNameOrExpression(configuration.getDisableCondition()), this::onDisableConditionChange, p -> p.teamData.disabled);
        visibilityRefresher = new VisibilityRefresher(this);
        collisionManager = new CollisionManager(this);
        TAB.getInstance().getFeatureManager().registerFeature(TabConstants.Feature.NAME_TAGS + "-Condition", disableChecker);
        if (proxy != null) {
            proxy.registerMessage(NameTagProxyPlayerData.class, in -> new NameTagProxyPlayerData(in, this));
        }
    }

    @Override
    public void load() {
        onlinePlayers = new OnlinePlayers(TAB.getInstance().getOnlinePlayers());
        TAB.getInstance().getFeatureManager().registerFeature(TabConstants.Feature.NAME_TAGS_VISIBILITY, visibilityRefresher);
        TAB.getInstance().getFeatureManager().registerFeature(TabConstants.Feature.NAME_TAGS_COLLISION, collisionManager);
        for (TabPlayer all : onlinePlayers.getPlayers()) {
            loadProperties(all);
            all.teamData.teamName = all.sortingData.shortTeamName; // Sorting is loaded sync before nametags
            if (disableChecker.isDisableConditionMet(all)) {
                all.teamData.disabled.set(true);
                continue;
            }
            all.expansionData.setNameTagVisibility(true);
            sendProxyMessage(all);
        }
        for (TabPlayer viewer : onlinePlayers.getPlayers()) {
            for (TabPlayer target : onlinePlayers.getPlayers()) {
                if (target.isVanished() && !viewer.canSee(target)) {
                    target.teamData.vanishedFor.add(viewer.getUniqueId());
                }
                if (!target.teamData.isDisabled()) registerTeam(target, viewer);
            }
        }
        visibilityRefresher.load();
        collisionManager.load();
    }

    @NotNull
    @Override
    public String getRefreshDisplayName() {
        return "Updating prefix/suffix";
    }

    @Override
    public void refresh(@NotNull TabPlayer refreshed, boolean force) {
        if (refreshed.teamData.isDisabled()) return;
        boolean refresh;
        if (force) {
            updateProperties(refreshed);
            refresh = true;
        } else {
            boolean prefix = refreshed.teamData.prefix.update();
            boolean suffix = refreshed.teamData.suffix.update();
            refresh = prefix || suffix;
        }
        if (refresh) updatePrefixSuffix(refreshed);
    }

    @Override
    public void onGroupChange(@NotNull TabPlayer player) {
        if (updateProperties(player) && !player.teamData.isDisabled()) {
            updatePrefixSuffix(player);
        }
    }

    @Override
    public void onJoin(@NotNull TabPlayer connectedPlayer) {
        onlinePlayers.addPlayer(connectedPlayer);
        loadProperties(connectedPlayer);
        connectedPlayer.teamData.teamName = connectedPlayer.sortingData.shortTeamName; // Sorting is loaded sync before nametags
        for (TabPlayer all : onlinePlayers.getPlayers()) {
            if (all == connectedPlayer) continue; //avoiding double registration
            if (connectedPlayer.isVanished() && !all.canSee(connectedPlayer)) {
                connectedPlayer.teamData.vanishedFor.add(all.getUniqueId());
            }
            if (all.isVanished() && !connectedPlayer.canSee(all)) {
                all.teamData.vanishedFor.add(connectedPlayer.getUniqueId());
            }
            if (!all.teamData.isDisabled()) {
                registerTeam(all, connectedPlayer);
            }
        }
        connectedPlayer.expansionData.setNameTagVisibility(true);
        if (proxy != null) {
            ProxyPlayer proxyPlayer = proxy.getProxyPlayers().get(connectedPlayer.getUniqueId());
            if (proxyPlayer != null && proxyPlayer.getNametag() != null) {
                for (TabPlayer viewer : onlinePlayers.getPlayers()) {
                    ((SafeScoreboard<?>)viewer.getScoreboard()).unregisterTeamSafe(proxyPlayer.getNametag().getResolvedTeamName());
                }
                proxyPlayer.setNametag(null);
            }
        }
        if (disableChecker.isDisableConditionMet(connectedPlayer)) {
            connectedPlayer.teamData.disabled.set(true);
            return;
        }
        registerTeam(connectedPlayer);
        if (proxy != null) {
            for (ProxyPlayer proxied : proxy.getProxyPlayers().values()) {
                if (proxied.getNametag() == null) continue; // This proxy player is not loaded yet
                connectedPlayer.getScoreboard().registerTeam(
                        proxied.getNametag().getResolvedTeamName(),
                        prefixCache.get(compensateLegacyBoldPrefix(connectedPlayer, proxied.getNametag().getPrefix())),
                        suffixCache.get(fitLegacySuffix(connectedPlayer, stripTrailingFormat(proxied.getNametag().getSuffix()), proxied.getNametag().getPrefix())),
                        proxied.getNametag().getNameVisibility(),
                        CollisionRule.ALWAYS,
                        Collections.singletonList(proxied.getNickname()),
                        teamOptions,
                        lastColorCache.get(proxied.getNametag().getPrefix()).getLastStyle().toEnumChatFormat()
                );
            }
            sendProxyMessage(connectedPlayer);
        }
    }

    @Override
    public void onQuit(@NotNull TabPlayer disconnectedPlayer) {
        onlinePlayers.removePlayer(disconnectedPlayer);
        for (TabPlayer viewer : onlinePlayers.getPlayers()) {
            ((SafeScoreboard<?>)viewer.getScoreboard()).unregisterTeamSafe(disconnectedPlayer.teamData.teamName);
        }
    }

    @Override
    public void onServerChange(@NonNull TabPlayer p, @NotNull Server from, @NotNull Server to) {
        if (updateProperties(p) && !p.teamData.isDisabled()) updatePrefixSuffix(p);
    }

    @Override
    public void onWorldChange(@NotNull TabPlayer changed, @NotNull World from, @NotNull World to) {
        if (updateProperties(changed) && !changed.teamData.isDisabled()) updatePrefixSuffix(changed);
    }

    @Override
    public void onVanishStatusChange(@NotNull TabPlayer player) {
        if (player.isVanished()) {
            for (TabPlayer viewer : onlinePlayers.getPlayers()) {
                if (viewer == player) continue;
                if (!viewer.canSee(player)) {
                    player.teamData.vanishedFor.add(viewer.getUniqueId());
                    if (!player.teamData.isDisabled()) {
                        ((SafeScoreboard<?>)viewer.getScoreboard()).unregisterTeamSafe(player.teamData.teamName);
                    }
                }
            }
        } else {
            Set<UUID> ids = new HashSet<>(player.teamData.vanishedFor);
            player.teamData.vanishedFor.clear();
            if (!player.teamData.isDisabled()) {
                for (UUID id : ids) {
                    TabPlayer viewer = TAB.getInstance().getPlayer(id);
                    if (viewer != null) registerTeam(player, viewer);
                }
            }
        }
    }

    /**
     * Loads properties from config.
     *
     * @param   player
     *          Player to load properties for
     */
    private void loadProperties(@NotNull TabPlayer player) {
        player.teamData.prefix = player.loadPropertyFromConfig(this, "tagprefix", "");
        player.teamData.suffix = player.loadPropertyFromConfig(this, "tagsuffix", "");
    }

    /**
     * Loads all properties from config and returns {@code true} if at least
     * one of them either wasn't loaded or changed value, {@code false} otherwise.
     *
     * @param   p
     *          Player to update properties of
     * @return  {@code true} if at least one property changed, {@code false} if not
     */
    private boolean updateProperties(@NotNull TabPlayer p) {
        boolean changed = p.updatePropertyFromConfig(p.teamData.prefix, "");
        if (p.updatePropertyFromConfig(p.teamData.suffix, "")) changed = true;
        return changed;
    }

    public void onDisableConditionChange(TabPlayer p, boolean disabledNow) {
        if (disabledNow) {
            unregisterTeam(p.teamData.teamName);
        } else {
            registerTeam(p);
        }
    }

    /**
     * Updates team prefix and suffix of given player.
     *
     * @param   player
     *          Player to update prefix/suffix of
     */
    private void updatePrefixSuffix(@NonNull TabPlayer player) {
        for (TabPlayer viewer : onlinePlayers.getPlayers()) {
            viewer.getScoreboard().updateTeam(
                    player.teamData.teamName,
                    prefixCache.get(compensateLegacyBoldPrefix(viewer, player.teamData.prefix.getFormat(viewer))),
                    suffixCache.get(fitLegacySuffix(viewer, stripTrailingFormat(player.teamData.suffix.getFormat(viewer)), player.teamData.prefix.getFormat(viewer))),
                    lastColorCache.get(player.teamData.prefix.getFormat(viewer)).getLastStyle().toEnumChatFormat()
            );
        }
        sendProxyMessage(player);
    }

    /**
     * Updates collision of a player for everyone.
     *
     * @param   player
     *          Player to update collision of
     * @param   moveToThread
     *          Whether task should be moved to feature thread or not, because it already is
     */
    public void updateCollision(@NonNull TabPlayer player, boolean moveToThread) {
        Runnable r = () -> {
            for (TabPlayer viewer : onlinePlayers.getPlayers()) {
                viewer.getScoreboard().updateTeam(
                        player.teamData.teamName,
                        player.teamData.getCollisionRule() ? CollisionRule.ALWAYS : CollisionRule.NEVER
                );
            }
        };
        if (moveToThread) {
            customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), r, getFeatureName(), "Updating collision"));
        } else {
            r.run();
        }
    }

    /**
     * Updates visibility of a player for everyone.
     *
     * @param   player
     *          Player to update visibility of
     */
    public void updateVisibility(@NonNull TabPlayer player) {
        customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            for (TabPlayer viewer : onlinePlayers.getPlayers()) {
                viewer.getScoreboard().updateTeam(
                        player.teamData.teamName,
                        getTeamVisibility(player, viewer) ? NameVisibility.ALWAYS : NameVisibility.NEVER
                );
            }
            sendProxyMessage(player);
        }, getFeatureName(), "Updating visibility"));
    }

    /**
     * Updates visibility of a player for specified player.
     *
     * @param   player
     *          Player to update visibility of
     * @param   viewer
     *          Viewer to send update to
     */
    public void updateVisibility(@NonNull TabPlayer player, @NonNull TabPlayer viewer) {
        viewer.getScoreboard().updateTeam(
                player.teamData.teamName,
                getTeamVisibility(player, viewer) ? NameVisibility.ALWAYS : NameVisibility.NEVER
        );
    }

    private void unregisterTeam(@NonNull String teamName) {
        for (TabPlayer viewer : onlinePlayers.getPlayers()) {
            ((SafeScoreboard<?>)viewer.getScoreboard()).unregisterTeamSafe(teamName);
        }
    }

    private void registerTeam(@NonNull TabPlayer p) {
        for (TabPlayer viewer : onlinePlayers.getPlayers()) {
            registerTeam(p, viewer);
        }
    }

    private void registerTeam(@NonNull TabPlayer p, @NonNull TabPlayer viewer) {
        if (p.teamData.isDisabled() || p.teamData.vanishedFor.contains(viewer.getUniqueId())) return;
        if (!viewer.canSee(p) && p != viewer) return;
        viewer.getScoreboard().registerTeam(
                p.teamData.teamName,
                prefixCache.get(compensateLegacyBoldPrefix(viewer, p.teamData.prefix.getFormat(viewer))),
                suffixCache.get(fitLegacySuffix(viewer, stripTrailingFormat(p.teamData.suffix.getFormat(viewer)), p.teamData.prefix.getFormat(viewer))),
                getTeamVisibility(p, viewer) ? NameVisibility.ALWAYS : NameVisibility.NEVER,
                p.teamData.getCollisionRule() ? CollisionRule.ALWAYS : CollisionRule.NEVER,
                Collections.singletonList(p.getNickname()),
                teamOptions,
                lastColorCache.get(p.teamData.prefix.getFormat(viewer)).getLastStyle().toEnumChatFormat()
        );
    }

    public boolean getTeamVisibility(@NonNull TabPlayer p, @NonNull TabPlayer viewer) {
        if (p.teamData.hasHiddenNametag()) return false; // At least 1 reason for invisible nametag exists
        if (p.teamData.hasHiddenNametag(viewer)) return false; // At least 1 reason for invisible nametag for this viewer exists
        if (viewer.teamData.invisibleNameTagView) return false; // Viewer does not want to see nametags
        if (viewer.getVersion() == ProtocolVersion.V1_8 && p.hasInvisibilityPotion()) return false;
        return true;
    }

    /**
     * Updates team name for a specified player to everyone.
     *
     * @param   player
     *          Player to change team name of
     * @param   newTeamName
     *          New team name to use
     */
    public void updateTeamName(@NonNull TabPlayer player, @NonNull String newTeamName) {
        customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            // Function ran before onJoin did (super rare), drop action since onJoin will use new team name anyway
            if (player.teamData.teamName == null) return;

            if (player.teamData.isDisabled()) {
                player.teamData.teamName = newTeamName;
                return;
            }
            for (TabPlayer viewer : onlinePlayers.getPlayers()) {
                viewer.getScoreboard().renameTeam(player.teamData.teamName, newTeamName);
            }
            player.teamData.teamName = newTeamName;
            sendProxyMessage(player);
        }, getFeatureName(), "Updating team name"));
    }

    public void hideNameTag(@NonNull TabPlayer player, @NonNull NameTagInvisibilityReason reason, @NonNull String cpuReason,
                            boolean sendMessage) {
        ensureActive();
        customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            if (player.teamData.hideNametag(reason)) {
                updateVisibility(player);
            }
            if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetHidden());
        }, getFeatureName(), cpuReason));
    }

    public void hideNameTag(@NonNull TabPlayer player, @NonNull TabPlayer viewer, @NonNull NameTagInvisibilityReason reason,
                            @NonNull String cpuReason, boolean sendMessage) {
        ensureActive();
        customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            if (player.teamData.hideNametag(viewer, reason)) {
                updateVisibility(player, viewer);
            }
            if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetHidden());
        }, getFeatureName(), cpuReason));
    }

    public void showNameTag(@NonNull TabPlayer player, @NonNull NameTagInvisibilityReason reason, @NonNull String cpuReason,
                            boolean sendMessage) {
        ensureActive();
        customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            if (player.teamData.showNametag(reason)) {
                updateVisibility(player);
            }
            if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetShown());
        }, getFeatureName(), cpuReason));
    }

    public void showNameTag(@NonNull TabPlayer player, @NonNull TabPlayer viewer, @NonNull NameTagInvisibilityReason reason,
                            @NonNull String cpuReason, boolean sendMessage) {
        ensureActive();
        customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            if (player.teamData.showNametag(viewer, reason)) {
                updateVisibility(player, viewer);
            }
            if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetShown());
        }, getFeatureName(), cpuReason));
    }

    public void toggleNameTag(@NonNull TabPlayer player, @NonNull NameTagInvisibilityReason reason, @NonNull String cpuReason,
                              boolean sendMessage) {
        ensureActive();
        customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            if (player.teamData.hasHiddenNametag(reason)) {
                player.teamData.showNametag(reason);
                if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetShown());
            } else {
                player.teamData.hideNametag(reason);
                if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetHidden());
            }
            updateVisibility(player);
        }, getFeatureName(), cpuReason));
    }

    public void toggleNameTag(@NonNull TabPlayer player, @NonNull TabPlayer viewer, @NonNull NameTagInvisibilityReason reason,
                              @NonNull String cpuReason, boolean sendMessage) {
        ensureActive();
        customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            if (player.teamData.hasHiddenNametag(viewer, reason)) {
                player.teamData.showNametag(viewer, reason);
                if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetShown());
            } else {
                player.teamData.hideNametag(viewer, reason);
                if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetHidden());
            }
            updateVisibility(player, viewer);
        }, getFeatureName(), cpuReason));
    }

    // ------------------
    // ProxySupport
    // ------------------

    private void sendProxyMessage(@NotNull TabPlayer player) {
        if (proxy != null) {
            proxy.sendMessage(new NameTagProxyPlayerData(
                    this,
                    proxy.getIdCounter().incrementAndGet(),
                    player.getUniqueId(),
                    player.teamData.teamName,
                    player.teamData.prefix.get(),
                    player.teamData.suffix.get(),
                    getTeamVisibility(player, player) ? NameVisibility.ALWAYS : NameVisibility.NEVER
            ));
        }
    }

    @Override
    public void onProxyLoadRequest() {
        for (TabPlayer all : onlinePlayers.getPlayers()) {
            sendProxyMessage(all);
        }
    }

    @Override
    public void onQuit(@NotNull ProxyPlayer player) {
        if (player.getNametag() == null) {
            // One of the two options is being forcibly unregistered when real player joined
            return;
        }
        for (TabPlayer viewer : onlinePlayers.getPlayers()) {
            ((SafeScoreboard<?>)viewer.getScoreboard()).unregisterTeamSafe(player.getNametag().getResolvedTeamName());
        }
    }

    @Override
    public void onJoin(@NotNull ProxyPlayer player) {
        if (player.getNametag() == null) return; // Player not loaded yet
        for (TabPlayer viewer : onlinePlayers.getPlayers()) {
            viewer.getScoreboard().registerTeam(
                    player.getNametag().getResolvedTeamName(),
                    prefixCache.get(compensateLegacyBoldPrefix(viewer, player.getNametag().getPrefix())),
                    suffixCache.get(fitLegacySuffix(viewer, stripTrailingFormat(player.getNametag().getSuffix()), player.getNametag().getPrefix())),
                    player.getNametag().getNameVisibility(),
                    Scoreboard.CollisionRule.ALWAYS,
                    Collections.singletonList(player.getNickname()),
                    teamOptions,
                    lastColorCache.get(player.getNametag().getPrefix()).getLastStyle().toEnumChatFormat()
            );
        }
    }

    @NotNull
    @Override
    public String getFeatureName() {
        return "NameTags";
    }

    @Override
    @NotNull
    public Object dump(@NotNull TabPlayer player) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("configuration", configuration.getSection().getMap());
        map.put("disabled with condition", player.teamData.disabled.get());
        map.put("team name", player.teamData.teamName.replaceAll("\\p{C}", ""));
        map.put("team handling paused with API", player.teamData.teamHandlingPaused);
        map.put("invisible nametag view", player.teamData.invisibleNameTagView);
        map.put("collision", player.teamData.getCollisionRule());
        map.put("invisible nametag", visibilityRefresher.getInvisibleCondition().isMet(player));
        for (Property property : Arrays.asList(player.teamData.prefix, player.teamData.suffix)) {
            Map<String, Object> propertyMap = new LinkedHashMap<>();
            propertyMap.put("configured-raw-value", property.getOriginalRawValue());
            propertyMap.put("api-forced-raw-value", property.getTemporaryValue());
            propertyMap.put("current-source", property.getSource());
            propertyMap.put("replaced-value", property.get());
            map.put(property.getName(), propertyMap);
        }

        // Table of all players
        List<String> header = Arrays.asList("Player", "tagprefix", "team color", "tagsuffix", "Disabled with condition");
        List<List<String>> players = Arrays.stream(TAB.getInstance().getOnlinePlayers()).map(p -> Arrays.asList(
                p.getName(),
                "\"" + p.teamData.prefix.get() + "\"",
                lastColorCache.get(p.teamData.prefix.getFormat(p)).getLastStyle().toEnumChatFormat().name(),
                "\"" + p.teamData.suffix.get() + "\"",
                String.valueOf(p.teamData.disabled.get())
        )).collect(Collectors.toList());
        if (proxy != null) {
            players.addAll(proxy.getProxyPlayers().values().stream().map(p -> Arrays.asList(
                    "[Proxy] " + p.getName(),
                    p.getNametag() == null ? "NULL" : "\"" + p.getNametag().getPrefix() + "\"",
                    p.getNametag() == null ? "NULL" : lastColorCache.get(p.getNametag().getPrefix()).getLastStyle().toEnumChatFormat().name(),
                    p.getNametag() == null ? "NULL" : "\"" + p.getNametag().getSuffix() + "\"",
                    "N/A"
            )).collect(Collectors.toList()));
        }
        map.put("current values for all players (without applying relational placeholders)", DumpUtils.tableToLines(header, players));
        return map;
    }

    // ------------------
    // API Implementation
    // ------------------

    @Override
    public void hideNameTag(@NonNull me.neznamy.tab.api.TabPlayer player) {
        hideNameTag((TabPlayer) player, NameTagInvisibilityReason.API_HIDE, "Processing API call (hideNameTag)", false);
    }

    @Override
    public void hideNameTag(@NonNull me.neznamy.tab.api.TabPlayer player, @NonNull me.neznamy.tab.api.TabPlayer viewer) {
        hideNameTag((TabPlayer) player, (TabPlayer) viewer, NameTagInvisibilityReason.API_HIDE, "Processing API call (hideNameTag)", false);
    }

    @Override
    public void showNameTag(@NonNull me.neznamy.tab.api.TabPlayer player) {
        showNameTag((TabPlayer) player, NameTagInvisibilityReason.API_HIDE, "Processing API call (showNameTag)", false);
    }

    @Override
    public void showNameTag(@NonNull me.neznamy.tab.api.TabPlayer player, @NonNull me.neznamy.tab.api.TabPlayer viewer) {
        showNameTag((TabPlayer) player, (TabPlayer) viewer, NameTagInvisibilityReason.API_HIDE, "Processing API call (showNameTag)", false);
    }

    @Override
    public boolean hasHiddenNameTag(@NonNull me.neznamy.tab.api.TabPlayer player) {
        ensureActive();
        return ((TabPlayer)player).teamData.hasHiddenNametag(NameTagInvisibilityReason.API_HIDE);
    }

    @Override
    public boolean hasHiddenNameTag(@NonNull me.neznamy.tab.api.TabPlayer player, @NonNull me.neznamy.tab.api.TabPlayer viewer) {
        ensureActive();
        return ((TabPlayer)player).teamData.hasHiddenNametag((TabPlayer) viewer, NameTagInvisibilityReason.API_HIDE);
    }

    @Override
    public void pauseTeamHandling(@NonNull me.neznamy.tab.api.TabPlayer player) {
        ensureActive();
        customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            TabPlayer p = (TabPlayer) player;
            p.ensureLoaded();
            if (p.teamData.teamHandlingPaused) return;
            if (!p.teamData.isDisabled()) unregisterTeam(p.teamData.teamName);
            p.teamData.teamHandlingPaused = true; //setting after, so unregisterTeam method runs
        }, getFeatureName(), "Processing API call (pauseTeamHandling)"));
    }

    @Override
    public void resumeTeamHandling(@NonNull me.neznamy.tab.api.TabPlayer player) {
        ensureActive();
        customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            TabPlayer p = (TabPlayer) player;
            p.ensureLoaded();
            if (!p.teamData.teamHandlingPaused) return;
            p.teamData.teamHandlingPaused = false; //setting before, so registerTeam method runs
            if (!p.teamData.isDisabled()) registerTeam(p);
        }, getFeatureName(), "Processing API call (resumeTeamHandling)"));
    }

    @Override
    public boolean hasTeamHandlingPaused(@NonNull me.neznamy.tab.api.TabPlayer player) {
        return ((TabPlayer)player).teamData.teamHandlingPaused;
    }

    @Override
    public void setCollisionRule(@NonNull me.neznamy.tab.api.TabPlayer player, Boolean collision) {
        ensureActive();
        customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            TabPlayer p = (TabPlayer) player;
            p.ensureLoaded();
            if (Objects.equals(p.teamData.forcedCollision, collision)) return;
            p.teamData.forcedCollision = collision;
            updateCollision(p, true);
        }, getFeatureName(), "Processing API call (setCollisionRule)"));
    }

    @Override
    public Boolean getCollisionRule(@NonNull me.neznamy.tab.api.TabPlayer player) {
        ensureActive();
        TabPlayer p = (TabPlayer) player;
        p.ensureLoaded();
        return p.teamData.forcedCollision;
    }

    @Override
    public void setPrefix(@NonNull me.neznamy.tab.api.TabPlayer player, @Nullable String prefix) {
        ensureActive();
        customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            TabPlayer p = (TabPlayer) player;
            p.ensureLoaded();
            p.teamData.prefix.setTemporaryValue(prefix);
            updatePrefixSuffix(p);
        }, getFeatureName(), "Processing API call (setPrefix)"));
    }

    @Override
    public void setSuffix(@NonNull me.neznamy.tab.api.TabPlayer player, @Nullable String suffix) {
        ensureActive();
        customThread.execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            TabPlayer p = (TabPlayer) player;
            p.ensureLoaded();
            p.teamData.suffix.setTemporaryValue(suffix);
            updatePrefixSuffix(p);
        }, getFeatureName(), "Processing API call (setSuffix)"));
    }

    @Override
    public String getCustomPrefix(@NonNull me.neznamy.tab.api.TabPlayer player) {
        ensureActive();
        TabPlayer p = (TabPlayer) player;
        p.ensureLoaded();
        return p.teamData.prefix.getTemporaryValue();
    }

    @Override
    public String getCustomSuffix(@NonNull me.neznamy.tab.api.TabPlayer player) {
        ensureActive();
        TabPlayer p = (TabPlayer) player;
        p.ensureLoaded();
        return p.teamData.suffix.getTemporaryValue();
    }

    @Override
    @NotNull
    public String getOriginalRawPrefix(@NonNull me.neznamy.tab.api.TabPlayer player) {
        ensureActive();
        TabPlayer p = (TabPlayer) player;
        p.ensureLoaded();
        return p.teamData.prefix.getOriginalRawValue();
    }

    @Override
    @NotNull
    public String getOriginalRawSuffix(@NonNull me.neznamy.tab.api.TabPlayer player) {
        ensureActive();
        TabPlayer p = (TabPlayer) player;
        p.ensureLoaded();
        return p.teamData.suffix.getOriginalRawValue();
    }

    @Override
    @NotNull
    public String getOriginalReplacedPrefix(@NonNull me.neznamy.tab.api.TabPlayer player) {
        ensureActive();
        TabPlayer p = (TabPlayer) player;
        p.ensureLoaded();
        return p.teamData.prefix.getOriginalReplacedValue();
    }

    @Override
    @NotNull
    public String getOriginalReplacedSuffix(@NonNull me.neznamy.tab.api.TabPlayer player) {
        ensureActive();
        TabPlayer p = (TabPlayer) player;
        p.ensureLoaded();
        return p.teamData.suffix.getOriginalReplacedValue();
    }

    @Override
    @NotNull
    public String getOriginalPrefix(@NonNull me.neznamy.tab.api.TabPlayer player) {
        return getOriginalRawPrefix(player);
    }

    @Override
    @NotNull
    public String getOriginalSuffix(@NonNull me.neznamy.tab.api.TabPlayer player) {
        return getOriginalRawSuffix(player);
    }

    @Override
    public void toggleNameTagVisibilityView(@NonNull me.neznamy.tab.api.TabPlayer p, boolean sendToggleMessage) {
        setNameTagVisibilityView((TabPlayer) p, ((TabPlayer) p).teamData.invisibleNameTagView, sendToggleMessage);
    }

    @Override
    public void showNameTagVisibilityView(@NonNull me.neznamy.tab.api.TabPlayer p, boolean sendToggleMessage) {
        setNameTagVisibilityView((TabPlayer) p, true, sendToggleMessage);
    }

    @Override
    public void hideNameTagVisibilityView(@NonNull me.neznamy.tab.api.TabPlayer p, boolean sendToggleMessage) {
        setNameTagVisibilityView((TabPlayer) p, false, sendToggleMessage);
    }

    private void setNameTagVisibilityView(@NonNull TabPlayer player, boolean visible, boolean sendToggleMessage) {
        ensureActive();
        if (player.teamData.invisibleNameTagView != visible) return;
        player.teamData.invisibleNameTagView = !visible;
        if (sendToggleMessage) {
            MessageFile messageFile = TAB.getInstance().getConfiguration().getMessages();
            player.sendMessage(visible ? messageFile.getNameTagViewShown() :messageFile.getNameTagViewHidden());
        }
        player.expansionData.setNameTagVisibility(visible);
        for (TabPlayer all : onlinePlayers.getPlayers()) {
            updateVisibility(all, player);
        }
    }

    @Override
    public boolean hasHiddenNameTagVisibilityView(@NonNull me.neznamy.tab.api.TabPlayer player) {
        ensureActive();
        return ((TabPlayer)player).teamData.invisibleNameTagView;
    }
}
