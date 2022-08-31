package cpw.mods.forge.serverpacklocator.secure;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.forge.serverpacklocator.utils.FileWatchdogThread;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WhitelistVerificationHelper
{
    private static final WhitelistVerificationHelper INSTANCE = new WhitelistVerificationHelper();

    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogManager.getLogger();

    public static WhitelistVerificationHelper getInstance()
    {
        return INSTANCE;
    }

    private final AtomicBoolean isEnabled = new AtomicBoolean(false);
    private final List<UUID> allowedSessionIds = Collections.synchronizedList(new ArrayList<>());

    private WhitelistVerificationHelper()
    {
    }

    public void setup(final Path serverModsDirPath) {
        final Path whitelistJsonFile = serverModsDirPath.getParent().resolve("whitelist.json");
        final Path serverPropertiesFile = serverModsDirPath.getParent().resolve("server.properties");

        LOGGER.warn("Starting whitelist verification helper");
        LOGGER.warn("  + Whitelist file:         {}", whitelistJsonFile.toString());
        LOGGER.warn("  + Server properties file: {}", serverPropertiesFile.toString());

        FileWatchdogThread.watching(whitelistJsonFile, this::onWhitelistChange);
        FileWatchdogThread.watching(serverPropertiesFile, this::onServerPropertiesChange);

        onWhitelistChange(whitelistJsonFile);
        onServerPropertiesChange(serverPropertiesFile);
    }

    public boolean isAllowed(final UUID sessionId) {
        return !isEnabled.get() || allowedSessionIds.contains(sessionId);
    }

    private void onWhitelistChange(final Path path)
    {
        if (Files.exists(path))
            return;

        LOGGER.info("Reloading whitelist file: {}", path.toString());

        try
        {
            final JsonArray array = GSON.fromJson(Files.newBufferedReader(path), JsonArray.class);
            allowedSessionIds.clear();

            if (array == null) {
                LOGGER.warn("Whitelist file is empty, clearing allowed sessions and aborting refresh! Whitelist file: {}", path.toString());
                return;
            }
            for (int i = 0; i < array.size(); i++)
            {
                final JsonObject obj = array.get(i).getAsJsonObject();
                final String uuid = obj.get("uuid").getAsString();
                allowedSessionIds.add(UUID.fromString(uuid));
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Failed to read whitelist.json", e);
        }
    }

    private void onServerPropertiesChange(final Path path)
    {
        LOGGER.info("Reloading server properties file: {}", path.toString());

        try
        {
            final List<String> lines = Files.readAllLines(path);
            isEnabled.set(lines.contains("white-list=true"));
        }
        catch (IOException e)
        {
            LOGGER.error("Failed to read server.properties", e);
        }
    }
}
