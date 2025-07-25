package me.neznamy.tab.shared.chat.rgb;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Getter;
import me.neznamy.tab.shared.chat.TabComponent;
import me.neznamy.tab.shared.chat.rgb.format.BukkitFormat;
import me.neznamy.tab.shared.chat.rgb.format.CMIFormat;
import me.neznamy.tab.shared.chat.rgb.format.HtmlFormat;
import me.neznamy.tab.shared.chat.rgb.format.KyoriFormat;
import me.neznamy.tab.shared.chat.rgb.format.MiniMessageFormat;
import me.neznamy.tab.shared.chat.rgb.format.RGBFormatter;
import me.neznamy.tab.shared.chat.rgb.format.UnnamedFormat1;
import me.neznamy.tab.shared.chat.rgb.gradient.CMIGradient;
import me.neznamy.tab.shared.chat.rgb.gradient.CommonGradient;
import me.neznamy.tab.shared.chat.rgb.gradient.GradientPattern;
import me.neznamy.tab.shared.chat.rgb.gradient.NexEngineGradient;
import me.neznamy.tab.shared.util.ReflectionUtils;
import org.jetbrains.annotations.NotNull;

/**
 * A helper class to reformat all RGB formats into the default #RRGGBB and apply gradients
 */
public class RGBUtils {

    /** Instance of the class */
    @Getter
    private static final RGBUtils instance = new RGBUtils();

    /** Registered RGB formatters */
    private final RGBFormatter[] formats;

    /** Registered gradient patterns */
    private final GradientPattern[] gradients;

    /**
     * Constructs new instance and loads all RGB patterns and gradients
     */
    public RGBUtils() {
        List<RGBFormatter> list = new ArrayList<>();
        if (ReflectionUtils.classExists("net.kyori.adventure.text.minimessage.MiniMessage")
                && ReflectionUtils.classExists(
                        "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer")) {
            list.add(new MiniMessageFormat());
        }
        list.add(new BukkitFormat());
        list.add(new CMIFormat());
        list.add(new UnnamedFormat1());
        list.add(new HtmlFormat());
        list.add(new KyoriFormat());
        formats = list.toArray(new RGBFormatter[0]);

        gradients = new GradientPattern[] {
            // {#RRGGBB>}text{#RRGGBB<}
            new CMIGradient(),
            // <#RRGGBB>Text</#RRGGBB>
            new CommonGradient(
                    Pattern.compile("<#[0-9a-fA-F]{6}>[^<]*</#[0-9a-fA-F]{6}>"),
                    Pattern.compile("<#[0-9a-fA-F]{6}\\|.>[^<]*</#[0-9a-fA-F]{6}>"),
                    "<#",
                    9,
                    2,
                    9,
                    7),
            // <$#RRGGBB>Text<$#RRGGBB>
            new CommonGradient(
                    Pattern.compile("<\\$#[0-9a-fA-F]{6}>[^<]*<\\$#[0-9a-fA-F]{6}>"),
                    Pattern.compile("<\\$#[0-9a-fA-F]{6}\\|.>[^<]*<\\$#[0-9a-fA-F]{6}>"),
                    "<$",
                    10,
                    3,
                    10,
                    7),
            new NexEngineGradient()
        };
    }

    /**
     * Applies all RGB formats and gradients to text and returns it.
     *
     * @param   text
     *          original text
     * @return  text where everything is converted to #RRGGBB
     */
    public @NotNull String applyFormats(@NotNull String text) {
        String replaced = text;
        for (GradientPattern pattern : gradients) {
            replaced = pattern.applyPattern(replaced, false);
        }
        for (RGBFormatter formatter : formats) {
            replaced = formatter.reformat(replaced);
        }
        return replaced;
    }

    /**
     * Applies all gradient formats to text and returns it. This only affects
     * usage where no placeholder is used inside.
     *
     * @param   text
     *          original text
     * @return  text where all gradients with static text are converted to #RRGGBB
     */
    public @NotNull String applyCleanGradients(@NotNull String text) {
        String replaced = text;
        for (GradientPattern pattern : gradients) {
            replaced = pattern.applyPattern(replaced, true);
        }
        return replaced;
    }

    /**
     * Converts all hex codes in given string to legacy codes.
     * Also removes redundant color codes caused by this operation
     * to properly fit in limits.
     *
     * @param   text
     *          text to convert
     * @return  translated text
     */
    public @NotNull String convertRGBtoLegacy(@NotNull String text) {
        return TabComponent.fromColoredText(text).toLegacyText();
    }
}
