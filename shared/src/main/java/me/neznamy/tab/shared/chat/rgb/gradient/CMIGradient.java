package me.neznamy.tab.shared.chat.rgb.gradient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

public class CMIGradient extends CommonGradient {

    // pattern for {#RRGGBB<>}
    private final Pattern shortcutPattern = Pattern.compile("\\{#[0-9a-fA-F]{6}<>}");

    public CMIGradient() {
        super(
                Pattern.compile("\\{#[0-9a-fA-F]{6}>}[^{]*\\{#[0-9a-fA-F]{6}<}"),
                Pattern.compile("\\{#[0-9a-fA-F]{6}\\|.>}[^{]*\\{#[0-9a-fA-F]{6}<}"),
                "{#",
                9,
                2,
                10,
                8);
    }

    @Override
    public String applyPattern(@NotNull String text, boolean ignorePlaceholders) {
        String replaced = text;
        if (replaced.contains("<>}")) {
            Matcher m = shortcutPattern.matcher(replaced);
            while (m.find()) {
                String format = m.group();
                String code = format.substring(2, 8);
                replaced = replaced.replace(format, "{#" + code + "<}{#" + code + ">}");
            }
        }
        return super.applyPattern(replaced, ignorePlaceholders);
    }
}
