package cpw.mods.forge.serverpacklocator.server;

import com.electronwill.nightconfig.core.ConfigFormat;
import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import cpw.mods.forge.serverpacklocator.secure.ConnectionSecurityManager;
import cpw.mods.forge.serverpacklocator.secure.WhitelistVerificationHelper;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public class ServerSidedPackHandler extends SidedPackHandler
{
    private static final Logger LOGGER = LogManager.getLogger();
    private ServerFileManager serverFileManager;

    public ServerSidedPackHandler(final Path serverModsDir) {
        super(serverModsDir);
        WhitelistVerificationHelper.getInstance().setup(serverModsDir);
    }

    @Override
    protected boolean validateConfig() {
        final OptionalInt port = getConfig().getOptionalInt("server.port");

        if (port.isEmpty())
        {
            LOGGER.fatal("Invalid configuration file found: {}, please delete or correct before trying again", getConfig().getNioPath());
            throw new IllegalStateException("Invalid configuation found");
        }

        ConnectionSecurityManager.getInstance().validateConfiguration(getConfig());

        return true;
    }

    @Override
    protected boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException {
        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/defaultserverconfig.toml")), path);
        return true;
    }

    @Override
    protected boolean waitForDownload() {
        return true;
    }

    @Override
    protected List<IModLocator.ModFileOrException> processModList(List<IModLocator.ModFileOrException> scannedMods) {
        serverFileManager.parseModList(scannedMods);
        return serverFileManager.getModList();
    }

    @Override
    public void initialize(final IModLocator dirLocator) {
        serverFileManager = new ServerFileManager(this, getConfig().<List<String>>getOptional("server.excludedModIds").orElse(Collections.emptyList()));
        SimpleHttpServer.run(this, getConfig().get("server.password"));
    }

    public ServerFileManager getFileManager() {
        return serverFileManager;
    }
}
