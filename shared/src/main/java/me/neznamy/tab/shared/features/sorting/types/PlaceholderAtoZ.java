package me.neznamy.tab.shared.features.sorting.types;

import me.neznamy.tab.shared.features.sorting.Sorting;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Sorting by a placeholder alphabetically
 */
public class PlaceholderAtoZ extends SortingType {

    /**
     * Constructs new instance with given parameters.
     *
     * @param sorting            Sorting feature
     * @param sortingPlaceholder Placeholder to sort by
     */
    public PlaceholderAtoZ(Sorting sorting, String sortingPlaceholder) {

        super(sorting, "PLACEHOLDER_A_TO_Z", sortingPlaceholder);

    }

    @Override
    public String getChars(@NotNull TabPlayer p) {

        String output = setPlaceholders(p);
        p.sortingData.teamNameNote += "\n-> " + sortingPlaceholder.getIdentifier() + " returned \"&e" + output
                + "&r\". &r";
        return sorting.isCaseSensitiveSorting() ? output : output.toLowerCase();

    }

}
