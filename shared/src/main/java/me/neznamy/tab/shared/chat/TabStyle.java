package me.neznamy.tab.shared.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class representing a component style.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TabStyle {

    @Nullable private TabTextColor color;
    @Nullable private Integer shadowColor; // ARGB
    @Nullable private Boolean bold;
    @Nullable private Boolean italic;
    @Nullable private Boolean underlined;
    @Nullable private Boolean strikethrough;
    @Nullable private Boolean obfuscated;
    @Nullable private String font;
    @Nullable private TabClickEvent clickEvent;

    /**
     * Constructs a copy of the provided modifier.
     *
     * @param   modifier
     *          Modifier to copy
     */
    public TabStyle(@NotNull TabStyle modifier) {
        color = modifier.color;
        shadowColor = modifier.shadowColor;
        bold = modifier.bold;
        italic = modifier.italic;
        obfuscated = modifier.obfuscated;
        strikethrough = modifier.strikethrough;
        underlined = modifier.underlined;
        font = modifier.font;
        clickEvent = modifier.clickEvent;
    }

    /**
     * Returns a String consisting of magic codes (color symbol + character) of
     * each magic code used. If none are used, empty String is returned.
     *
     * @return  Magic codes of this modifier as String
     */
    @NotNull
    public String getMagicCodes() {
        StringBuilder builder = new StringBuilder();
        if (Boolean.TRUE.equals(bold)) builder.append("§l");
        if (Boolean.TRUE.equals(italic)) builder.append("§o");
        if (Boolean.TRUE.equals(obfuscated)) builder.append("§k");
        if (Boolean.TRUE.equals(strikethrough)) builder.append("§m");
        if (Boolean.TRUE.equals(underlined)) builder.append("§n");
        return builder.toString();
    }

    /**
     * Converts this style to EnumChatFormat for determining team color.
     *
     * @return  EnumChatFormat to show to represent this style
     */
    @NotNull
    public EnumChatFormat toEnumChatFormat() {
        // Scoreboard team color slot only accepts ordinals 0-15 (colors) or 21
        // (RESET). Format codes 16-20 (OBFUSCATED/BOLD/STRIKETHROUGH/UNDERLINE/
        // ITALIC) are protocol-invalid as team colors: ViaBackwards 1.13->1.12
        // only clamps ordinal 21 to byte -1, so 16-20 reach 1.8 clients as a
        // byte the client's EnumChatFormatting.getValueByIndex cannot resolve,
        // storing null chatFormat on the team and NPEing at render time in
        // GuiIngameForge when the overlay dereferences getChatFormat(). Fall
        // back to WHITE (ordinal 15) rather than RESET (21): WHITE survives
        // ViaBackwards unchanged as byte 15 and matches the vanilla default
        // team color, while RESET on 1.8 via ViaBackwards lands as byte -1
        // which some client-side tablist overlays treat as "strip this entry"
        // depending on mods present.
        if (color != null) return color.getLegacyColor();
        return EnumChatFormat.WHITE;
    }
}