package me.neznamy.tab.shared.chat.rgb.format;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Formatter for #&lt;RRGGBB&gt;
 */
public class HtmlFormat implements RGBFormatter {

    private final Pattern pattern = Pattern.compile("#<[0-9a-fA-F]{6}>");

    @Override
    public @NotNull String reformat(@NotNull String text) {
        if (!text.contains("#<")) return text;
        Matcher m = pattern.matcher(text);
        String replaced = text;
        while (m.find()) {
            String hexCode = m.group();
            String fixed = hexCode.substring(2, 8);
            replaced = replaced.replace(hexCode, "#" + fixed);
        }
        return replaced;
    }
}
