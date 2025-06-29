package me.neznamy.tab.shared.event.impl;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.Data;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.event.plugin.PlaceholderRegisterEvent;
import org.jetbrains.annotations.NotNull;

@Data
public class TabPlaceholderRegisterEvent implements PlaceholderRegisterEvent {

    @NotNull
    private final String identifier;

    private Supplier<Object> serverPlaceholder;
    private Function<TabPlayer, Object> playerPlaceholder;
    private BiFunction<TabPlayer, TabPlayer, Object> relationalPlaceholder;
}
