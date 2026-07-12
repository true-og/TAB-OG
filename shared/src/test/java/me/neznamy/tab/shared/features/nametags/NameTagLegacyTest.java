package me.neznamy.tab.shared.features.nametags;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static me.neznamy.tab.shared.features.nametags.NameTag.compensateLegacyBoldPrefix;
import static me.neznamy.tab.shared.features.nametags.NameTag.fitLegacySuffix;
import static me.neznamy.tab.shared.features.nametags.NameTag.legacyPrefixKeepsBold;
import static me.neznamy.tab.shared.features.nametags.NameTag.stripTrailingFormat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure floating-nametag helpers: rank brackets stay on the prefix, the [AFK] tag
 * loses its brackets and gets bolded everywhere, legacy spacing/16-char fit hold, bold survives only
 * when the whole tag can render bold (width calc matches rendering), and nothing trails at the end.
 */
class NameTagLegacyTest {

    private static final int LEGACY_LIMIT = 16;
    private static final String S = "§";
    private static final String NBSP = "\u00A0";
    private static final String AFK_IN = S + "8[" + S + "cAFK" + S + "8]";        // config badge, with brackets
    private static final String AFK_OUT = S + "8" + S + "c" + S + "lAFK";         // nametag badge: brackets gone, bold added
    private static final String AFK_BOLD_TAIL = S + "c" + S + "lAFK";             // survives any truncation/re-bolding

    // ---- compensateLegacyBoldPrefix: rank keeps its brackets ---------------------------------

    @Test
    @DisplayName("Rank brackets stay on the over-head prefix on every client")
    void prefixKeepsBrackets() {
        assertTrue(compensateLegacyBoldPrefix(true, S + "5" + S + "l[Admin]").contains("["));
        assertTrue(compensateLegacyBoldPrefix(false, S + "5" + S + "l[Admin]").contains("]"));
    }

    @Test
    @DisplayName("Legacy: trailing space becomes a color-terminated real space, not a visible NBSP")
    void prefixTrailingSpaceIsProtectedNotNbsp() {
        String out = compensateLegacyBoldPrefix(true, S + "7[Member] ");
        assertEquals(S + "7[Member] " + S + "7", out);
        assertFalse(out.contains(NBSP), "must not emit a visible NBSP");
    }

    @Test
    @DisplayName("Legacy: multiple trailing spaces collapse to a single protected separator")
    void prefixMultipleTrailingSpacesCollapse() {
        assertEquals(S + "7[Member] " + S + "7", compensateLegacyBoldPrefix(true, S + "7[Member]   "));
    }

    @Test
    @DisplayName("Legacy: non-bold prefix without a trailing separator is unchanged")
    void prefixNonBoldNoSeparator() {
        assertEquals(S + "7[Member]", compensateLegacyBoldPrefix(true, S + "7[Member]"));
    }

    @Test
    @DisplayName("Legacy: a fitting bold rank keeps bold and hands the name its color plus bold")
    void prefixBoldRankKeepsBoldWithBoldName() {
        String out = compensateLegacyBoldPrefix(true, S + "2" + S + "lPlayer " + S + "3");
        assertEquals(S + "2" + S + "lPlayer" + S + "3" + S + "l ", out);
        assertTrue(out.length() <= LEGACY_LIMIT);
        assertTrue(legacyPrefixKeepsBold(S + "2" + S + "lPlayer " + S + "3"));
    }

    @Test
    @DisplayName("Legacy: bold Owner rank keeps bold; name renders green bold so width matches rendering")
    void prefixOwnerRankNoPhantomTrailingSpace() {
        String out = compensateLegacyBoldPrefix(true, S + "2" + S + "lOwner " + S + "a");
        assertEquals(S + "2" + S + "lOwner" + S + "a" + S + "l ", out);
        assertTrue(out.length() <= LEGACY_LIMIT);
        assertTrue(out.endsWith(S + "l "), "separator space must carry the name's color and bold");
    }

    @Test
    @DisplayName("Legacy: bold-only prefix keeps bold through to the name")
    void prefixBoldOnlyKeepsBold() {
        String out = compensateLegacyBoldPrefix(true, S + "lBOLD ");
        assertEquals(S + "lBOLD" + S + "l ", out);
        assertFalse(out.contains(NBSP));
    }

    @Test
    @DisplayName("Legacy: a color change mid-rank is re-bolded so no glyph renders plain while counted bold")
    void prefixMidRankColorChangeReBolds() {
        String out = compensateLegacyBoldPrefix(true, S + "lA" + S + "2B ");
        assertEquals(S + "lA" + S + "2" + S + "lB" + S + "2" + S + "l ", out);
        assertTrue(out.length() <= LEGACY_LIMIT, "capped: " + out);
    }

    @Test
    @DisplayName("Legacy: bold rank stays within 16 chars and keeps its color and brackets")
    void prefixBoldRankFitsLimit() {
        String out = compensateLegacyBoldPrefix(true, S + "5" + S + "l[Admin]");
        assertEquals(S + "5" + S + "l[Admin]" + S + "5" + S + "l ", out);
        assertTrue(out.length() <= LEGACY_LIMIT, "prefix over 16 chars would truncate: " + out);
    }

    @Test
    @DisplayName("Modern (1.13+): non-bold prefix is returned untouched")
    void prefixModernUntouched() {
        assertEquals(S + "7[Member] ", compensateLegacyBoldPrefix(false, S + "7[Member] "));
    }

    @Test
    @DisplayName("Legacy: a bold rank too long to keep bold falls back to stripping it (no NBSP)")
    void prefixBoldRankAtLimitDropsBoldForCleanSpace() {
        String out = compensateLegacyBoldPrefix(true, S + "6" + S + "l[Moderator]");
        assertEquals(S + "6[Moderator] " + S + "6", out);
        assertTrue(out.length() <= LEGACY_LIMIT);
        assertFalse(out.contains(NBSP), "no NBSP box");
        assertFalse(out.contains(S + "l"), "bold dropped on the over-head nametag");
        assertFalse(legacyPrefixKeepsBold(S + "6" + S + "l[Moderator]"));
    }

    @Test
    @DisplayName("Legacy: a bold rank ending in a trailing color that cannot keep bold uses a bare space")
    void prefixBoldRankWithTrailingColorKeepsText() {
        String out = compensateLegacyBoldPrefix(true, S + "6" + S + "l[Moderator]" + S + "6");
        assertEquals(S + "6[Moderator] " + S + "6", out);
        assertTrue(out.length() <= LEGACY_LIMIT);
        assertFalse(out.contains(NBSP));
    }

    @Test
    @DisplayName("Legacy: a non-bold prefix too long for a protected space trims text for a clean space (no NBSP)")
    void prefixNonBoldAtLimitTrimsForCleanSpace() {
        String out = compensateLegacyBoldPrefix(true, S + "7WWWWWWWWWWWWW ");
        assertTrue(out.length() <= LEGACY_LIMIT, "capped: " + out);
        assertFalse(out.contains(NBSP), "no NBSP box");
        assertTrue(out.endsWith(S + "7") && out.contains(" " + S + "7"), "clean color-terminated space");
    }

    @Test
    @DisplayName("Legacy: an over-long base prefix is capped to 16 on a clean boundary")
    void prefixOverLongIsCapped() {
        String out = compensateLegacyBoldPrefix(true, S + "a123456789012345678901234567890");
        assertTrue(out.length() <= LEGACY_LIMIT, "must be capped, got: " + out);
        assertFalse(endsWithDanglingSection(out), "cap must not leave a dangling section sign");
    }

    @Test
    @DisplayName("Legacy: capping backs off past adjacent codes, never leaving a dangling section sign")
    void prefixCapNeverDanglesSection() {
        String out = compensateLegacyBoldPrefix(true, "12345678901234" + S + S + "aTAIL");
        assertTrue(out.length() <= LEGACY_LIMIT, "capped: " + out);
        assertFalse(endsWithDanglingSection(out), "cap must back off past every trailing section sign");
    }

    @Test
    @DisplayName("Legacy: a full-length bold prefix drops bold and trims minimally for a clean space (no NBSP)")
    void prefixFullBoldRankGetsCleanSpace() {
        String out = compensateLegacyBoldPrefix(true, S + "c" + S + "lADMINISTRATO");
        assertTrue(out.length() <= LEGACY_LIMIT, "capped: " + out);
        assertFalse(out.contains(NBSP), "no NBSP box");
        assertTrue(out.endsWith(S + "c") && out.contains(" " + S + "c"), "clean color-terminated space: " + out);
        assertFalse(out.contains(S + "l"), "bold dropped on the over-head nametag");
    }

    // ---- fitLegacySuffix: AFK loses brackets and turns bold, nothing trails -------------------

    @Test
    @DisplayName("Over-head nametag drops the [AFK] brackets and bolds the badge on every client")
    void suffixRemovesAfkBracketsAndBolds() {
        String out = fitLegacySuffix(false, " " + AFK_IN, false);
        assertEquals(" " + AFK_OUT, out);
        assertFalse(out.contains("[") || out.contains("]"), "AFK brackets removed");
        assertEquals(" " + AFK_OUT, fitLegacySuffix(true, " " + AFK_IN, false), "same badge on legacy");
    }

    @Test
    @DisplayName("Bolding the AFK badge is idempotent when the badge already carries bold")
    void suffixAfkBoldIdempotent() {
        assertEquals(" " + AFK_OUT, fitLegacySuffix(false, " " + S + "8[" + S + "c" + S + "lAFK" + S + "8]", false));
    }

    @Test
    @DisplayName("Suffix never ends with a trailing space, NBSP, or dangling format code")
    void suffixNeverTrails() {
        assertClean(fitLegacySuffix(true, " " + AFK_IN, false));
        assertClean(fitLegacySuffix(false, " " + AFK_IN, false));
        assertClean(fitLegacySuffix(true, S + "r" + S + "4♥ " + AFK_IN, false));
        assertClean(fitLegacySuffix(true, S + "r" + S + "4♥ ", false));
        assertClean(fitLegacySuffix(true, S + "r" + S + "4♥ " + AFK_IN, true));
    }

    @Test
    @DisplayName("Legacy: a double space collapses to one before the AFK tag")
    void suffixCollapsesDoubleSpace() {
        String out = fitLegacySuffix(true, S + "r  " + AFK_IN, false);
        assertEquals(S + "r " + AFK_OUT, out);
        assertFalse(out.contains("  "));
    }

    @Test
    @DisplayName("Legacy: a short suffix keeps its heart color and loses the AFK brackets")
    void suffixShortKeepsRedHeart() {
        String out = fitLegacySuffix(true, " " + S + "r" + S + "4♥ " + AFK_IN, false);
        assertEquals(" " + S + "r" + S + "4♥ " + AFK_OUT, out);
        assertTrue(out.contains(S + "4♥"), "heart keeps its own color");
    }

    @Test
    @DisplayName("Legacy: an over-limit suffix is cut on a color boundary with a re-attached leading space")
    void suffixTruncatedStaysColoredAndSpaced() {
        String out = fitLegacySuffix(true, " " + S + "r" + S + "4 ♥ " + S + "6λ " + S + "d✦ " + AFK_IN, false);
        assertTrue(out.length() <= LEGACY_LIMIT, "must fit 16: " + out);
        assertTrue(out.startsWith(" " + S) && isLegacyColor(out.charAt(2)),
                "leading space then a real color code, so the first glyph is colored: " + out);
        assertTrue(out.endsWith(AFK_BOLD_TAIL), "AFK tag survives bold, got: " + out);
        assertFalse(out.contains("  "), "no double space");
        assertClean(out);
    }

    @Test
    @DisplayName("Legacy: a glyph whose color is a plain color code before bold is not left colorless")
    void suffixTruncatedNeverColorlessGlyph() {
        String out = fitLegacySuffix(true, " " + S + "r" + S + "4abcd " + S + "l♥ " + AFK_IN, false);
        assertTrue(out.startsWith(" " + S) && isLegacyColor(out.charAt(2)),
                "kept content must start with a real color, not a format code: " + out);
        assertTrue(out.endsWith(AFK_BOLD_TAIL));
        assertTrue(out.length() <= LEGACY_LIMIT);
    }

    @Test
    @DisplayName("Spaces split by a format code still collapse to one visible space")
    void suffixCollapsesSpacesAcrossCodes() {
        String out = fitLegacySuffix(false, S + "r" + S + "4♥ " + S + "r " + AFK_IN, false);
        assertTrue(out.endsWith(AFK_BOLD_TAIL));
        assertFalse(out.contains("  "));
    }

    @Test
    @DisplayName("Legacy: mid-suffix bold re-bolds every later color so no phantom width pads the tag end")
    void suffixMidBoldReBoldsTail() {
        String out = fitLegacySuffix(true, " " + S + "l♥ " + AFK_IN, false);
        assertEquals(" " + S + "l♥ " + S + "8" + S + "l" + S + "c" + S + "lAFK", out);
        // 1.13+ clients measure bold correctly, so no re-bolding happens there
        assertEquals(" " + S + "4" + S + "l♥ " + AFK_OUT, fitLegacySuffix(false, " " + S + "4" + S + "l♥ " + AFK_IN, false));
    }

    @Test
    @DisplayName("Legacy: bold carried in from a bold-kept prefix bolds the whole suffix")
    void suffixBoldCarriedInBoldsEverything() {
        String out = fitLegacySuffix(true, " " + S + "4♥", true);
        assertEquals(" " + S + "4" + S + "l♥", out);
        // without carry-in the heart stays plain
        assertEquals(" " + S + "4♥", fitLegacySuffix(true, " " + S + "4♥", false));
    }

    @Test
    @DisplayName("Not AFK: cosmetic suffix keeps no trailing space and no AFK tag")
    void suffixNotAfk() {
        assertEquals(S + "r" + S + "4♥", fitLegacySuffix(true, stripTrailingFormat(S + "r" + S + "4♥ "), false));
        assertEquals("", fitLegacySuffix(true, stripTrailingFormat(" "), false));
    }

    // ---- stripTrailingFormat ------------------------------------------------------------------

    @Test
    @DisplayName("stripTrailingFormat removes a trailing space, reset code, and a lone section sign")
    void stripTrailing() {
        assertEquals(S + "r " + AFK_IN, stripTrailingFormat(S + "r " + AFK_IN + " "));
        assertEquals("", stripTrailingFormat(S + "r "));
        assertEquals("x", stripTrailingFormat("x" + S));
        assertEquals("x", stripTrailingFormat("x" + S + "8" + S));
    }

    private static void assertClean(String s) {
        assertFalse(s.endsWith(" ") || s.endsWith(NBSP), "no trailing space: [" + s + "]");
        assertFalse(endsWithDanglingSection(s), "no dangling section sign: [" + s + "]");
        if (s.length() >= 2) {
            char last = s.charAt(s.length() - 1);
            assertFalse(s.charAt(s.length() - 2) == '§' && last != '§', "no trailing format code: [" + s + "]");
        }
    }

    private static boolean endsWithDanglingSection(String s) {
        return !s.isEmpty() && s.charAt(s.length() - 1) == '§';
    }

    private static boolean isLegacyColor(char c) {
        c = Character.toLowerCase(c);
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == 'r';
    }
}
