package me.neznamy.tab.shared.placeholders.conditions;

import java.util.Collections;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Condition that always returns false.
 */
public class FalseCondition extends Condition {

    /** Instance of the class */
    public static final FalseCondition INSTANCE = new FalseCondition();

    private FalseCondition() {
        super(false, "FalseCondition", Collections.emptyList(), null, null);
    }

    @Override
    public boolean isMet(@NotNull TabPlayer player) {
        return false;
    }
}
