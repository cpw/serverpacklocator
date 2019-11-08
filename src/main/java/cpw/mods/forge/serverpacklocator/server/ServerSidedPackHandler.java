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
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

public class ServerSidedPackHandler extends SidedPackHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private ServerCertificateManager certManager;
    private SimpleHttpServer simpleHttpServer;
    private ServerFileManager serverFileManager;
    private WhitelistValidator whitelistMonitor;

    public ServerSidedPackHandler(final Path serverModsDir) {
        super(serverModsDir);
    }

    @Override
    protected boolean validateConfig() {
        final Optional<String> certificate = getConfig().getOptional("server.cacertificate");
        final Optional<String> key = getConfig().getOptional("server.cakey");
        final Optional<String> servername = getConfig().getOptional("server.name");
        final OptionalInt port = getConfig().getOptionalInt("server.port");

        if (certificate.isPresent() && key.isPresent() && servername.isPresent() && port.isPresent()) {
            this.certManager = new ServerCertificateManager(getConfig(), getServerModsDir());
            return true;
        } else {
            LOGGER.fatal("Invalid configuration file found: {}, please delete or correct before trying again", getConfig().getNioPath());
            throw new IllegalStateException("Invalid configuation found");
        }
    }

    @Override
    protected boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException {
        Files.copy(getClass().getResourceAsStream("/defaultserverconfig.toml"), path);
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
        simpleHttpServer = new SimpleHttpServer(this);
        serverFileManager = new ServerFileManager(this);
        whitelistMonitor = new WhitelistValidator(getServerModsDir().getParent());
    }

    public ServerCertificateManager getCertificateManager() {
        return certManager;
    }

    public ServerFileManager getFileManager() {
        return serverFileManager;
    }
}
