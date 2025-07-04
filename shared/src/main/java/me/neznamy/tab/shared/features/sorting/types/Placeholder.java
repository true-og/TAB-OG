package me.neznamy.tab.shared.features.sorting.types;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.chat.EnumChatFormat;
import me.neznamy.tab.shared.features.sorting.Sorting;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Sorting by a placeholder by values defined in list
 */
public class Placeholder extends SortingType {

    /** Map of priorities for each output */
    private final LinkedHashMap<String, Integer> sortingMap;

    /**
     * Constructs new instance with given parameters
     *
     * @param   sorting
     *          sorting feature
     * @param   options
     *          options used by this sorting type
     */
    public Placeholder(Sorting sorting, String options) {
        super(sorting, "PLACEHOLDER", getPlaceholder(options));
        String[] args = options.split(":");
        String elements = args[args.length - 1];
        if (args.length > 1) {
            String[] array = elements.split(",");
            if (elements.endsWith(",")) {
                // Allow empty string as output
                array = Arrays.copyOf(array, array.length + 1);
                array[array.length - 1] = "";
            }
            sortingMap = convertSortingElements(array);
        } else {
            TAB.getInstance().getConfigHelper().startup().incompleteSortingLine("PLACEHOLDER:" + options);
            sortingMap = new LinkedHashMap<>();
        }
    }

    /**
     * Returns placeholder identifier used in provided options. This allows
     * support for placeholders that contain ":", such as conditions or animations.
     *
     * @param   options
     *          Configured sorting options in "%placeholder%:values" format
     * @return  Placeholder configured in options
     */
    private static String getPlaceholder(String options) {
        String[] args = options.split(":");
        if (args.length == 1) return args[0]; // Missing predefined values
        return options.substring(0, options.length() - args[args.length - 1].length() - 1);
    }

    @Override
    public String getChars(@NotNull TabPlayer p) {
        String output = EnumChatFormat.color(setPlaceholders(p));
        p.sortingData.teamNameNote += "\n-> " + sortingPlaceholder.getIdentifier() + " returned \"&e" + output + "&r\"";
        int position;
        String cleanOutput = output.trim().toLowerCase(Locale.US);
        if (!sortingMap.containsKey(cleanOutput)) {
            TAB.getInstance()
                    .getConfigHelper()
                    .runtime()
                    .valueNotInPredefinedValues(
                            sortingPlaceholder.getIdentifier(), sortingMap.keySet(), cleanOutput, p);
            position = sortingMap.size() + 1;
            p.sortingData.teamNameNote += "&c (not in list)&r. ";
        } else {
            position = sortingMap.get(cleanOutput);
            p.sortingData.teamNameNote += "&r &a(#" + position + " in list). &r";
        }
        return String.valueOf((char) (position + 47));
    }
}
