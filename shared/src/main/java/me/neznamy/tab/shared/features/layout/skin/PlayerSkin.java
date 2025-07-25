package me.neznamy.tab.shared.features.layout.skin;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.config.file.ConfigurationFile;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

/**
 * Skin source using player names.
 */
public class PlayerSkin extends SkinSource {

    protected PlayerSkin(@NotNull ConfigurationFile file) {
        super(file, "players");
    }

    @Override
    @NotNull
    public List<String> download(@NotNull String input) {
        try {
            JSONObject json = getResponse("https://api.ashcon.app/mojang/v2/user/" + input);
            JSONObject textures = (JSONObject) json.get("textures");
            JSONObject raw = (JSONObject) textures.get("raw");
            String value = (String) raw.get("value");
            String signature = (String) raw.get("signature");
            return Arrays.asList(value, signature);
        } catch (FileNotFoundException e) {
            TAB.getInstance().getConfigHelper().runtime().unknownPlayerSkin(input);
        } catch (IOException | ParseException e) {
            TAB.getInstance().getErrorManager().playerSkinDownloadError(input, e);
        }
        return Collections.emptyList();
    }
}
