package cpw.mods.forge.serverpacklocator.server;

import com.electronwill.nightconfig.core.ConfigFormat;
import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public class ServerSidedPackHandler extends SidedPackHandler
{
    private static final Logger LOGGER = LogManager.getLogger();
    private ServerFileManager serverFileManager;

    public ServerSidedPackHandler(final Path serverModsDir) {
        super(serverModsDir);
    }

    @Override
    protected boolean validateConfig() {
        final OptionalInt port = getConfig().getOptionalInt("server.port");

        if (port.isPresent()) {
            return true;
        } else {
            LOGGER.fatal("Invalid configuration file found: {}, please delete or correct before trying again", getConfig().getNioPath());
            throw new IllegalStateException("Invalid configuation found");
        }
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
    protected List<IModFile> processModList(List<IModFile> scannedMods) {
        serverFileManager.parseModList(scannedMods);
        return serverFileManager.getModList();
    }

    @Override
    public void initialize(final IModLocator dirLocator) {
        serverFileManager = new ServerFileManager(this);

        SimpleHttpServer.run(this);
        WhitelistValidator.setup(getServerModsDir().getParent());
    }

    public ServerFileManager getFileManager() {
        return serverFileManager;
    }
}
