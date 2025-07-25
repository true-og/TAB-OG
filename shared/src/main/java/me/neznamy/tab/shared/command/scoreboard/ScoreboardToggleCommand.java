package me.neznamy.tab.shared.command.scoreboard;

import java.util.Collections;
import java.util.List;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;
import me.neznamy.tab.shared.command.SubCommand;
import me.neznamy.tab.shared.features.scoreboard.ScoreboardManagerImpl;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handler for "/tab scoreboard toggle [player] [options]" subcommand
 */
public class ScoreboardToggleCommand extends SubCommand {

    /**
     * Constructs new instance
     */
    public ScoreboardToggleCommand() {
        super("toggle", TabConstants.Permission.COMMAND_SCOREBOARD_TOGGLE);
    }

    @Override
    public void execute(@Nullable TabPlayer sender, @NotNull String[] args) {
        ScoreboardManagerImpl scoreboard =
                TAB.getInstance().getFeatureManager().getFeature(TabConstants.Feature.SCOREBOARD);
        if (scoreboard == null) {
            sendMessage(sender, getMessages().getScoreboardFeatureNotEnabled());
            return;
        }
        TabPlayer target = sender;
        if (args.length > 0) {
            if (hasPermission(sender, TabConstants.Permission.COMMAND_SCOREBOARD_TOGGLE_OTHER)) {
                target = TAB.getInstance().getPlayer(args[0]);
                if (target == null) {
                    sendMessage(sender, getMessages().getPlayerNotFound(args[0]));
                    return;
                }
            } else {
                sendMessage(sender, getMessages().getNoPermission());
                return;
            }
        } else if (target == null) {
            sendMessage(null, getMessages().getCommandOnlyFromGame());
            return;
        }
        boolean silent = args.length == 2 && args[1].equals("-s");
        if (target.scoreboardData.otherPluginScoreboard != null) return; // not overriding other plugins
        scoreboard.toggleScoreboard(target, !silent);
    }

    @Override
    public @NotNull List<String> complete(@Nullable TabPlayer sender, @NotNull String[] arguments) {
        if (arguments.length == 1) return getOnlinePlayers(arguments[0]);
        if (arguments.length == 2) return getStartingArgument(Collections.singletonList("-s"), arguments[1]);
        return Collections.emptyList();
    }
}
