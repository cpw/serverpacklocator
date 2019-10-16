package cpw.mods.forge.serverpacklocator.client;

import com.electronwill.nightconfig.core.ConfigFormat;
import cpw.mods.forge.serverpacklocator.LaunchEnvironmentHandler;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class ClientSidedPackHandler extends SidedPackHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private ClientCertificateManager certManager;
    private SimpleHttpClient clientDownloader;

    public ClientSidedPackHandler(final Path serverModsDir) {
        super(serverModsDir);
    }

    @Override
    protected boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException {
        Files.copy(getClass().getResourceAsStream("/defaultclientconfig.toml"), path);
        return true;
    }

    @Override
    protected boolean validateConfig() {
        final String uuid = LaunchEnvironmentHandler.INSTANCE.getUUID();
        if (uuid == null || uuid.length() == 0) {
            // invalid UUID - probably offline mode. not supported
            LaunchEnvironmentHandler.INSTANCE.addProgressMessage("NO UUID found. Offline mode does not work. No server mods will be downloaded");
            LOGGER.error("There was not a valid UUID present in this client launch. You are probably playing offline mode. Trivially, there is nothing for us to do.");
            return false;
        }
        final Optional<String> certificate = getConfig().getOptional("client.certificate");
        final Optional<String> key = getConfig().getOptional("client.key");
        final Optional<String> remoteServer = getConfig().getOptional("client.remoteServer");

        if (certificate.isPresent() && key.isPresent() && remoteServer.isPresent()) {
            this.certManager = new ClientCertificateManager(getConfig(), getServerModsDir(), uuid);
            return this.certManager.isValid();
        } else {
            LOGGER.fatal("Invalid configuration file {} found. Could not locate valid values. " +
                    "Repair or delete this file to continue", getConfig().getNioPath().toString());
            throw new IllegalStateException("Invalid configuation file found, please delete or correct");
        }
    }

    ClientCertificateManager getCertificateManager() {
        return certManager;
    }

    @Override
    protected List<IModFile> scanMods(List<IModFile> scannedMods) {
        boolean validList = false;
        try {
            validList = clientDownloader.waitForResult();
        } catch (ExecutionException e) {
            LOGGER.error("Caught exception downloading mods from server", e);
            invalidate();
        }

        if (!validList) {
            LOGGER.info("There was a problem with the connection, there will not be any server mods");
            invalidate();
            return Collections.emptyList();
        }

        final Set<String> manifestFileList = clientDownloader.getManifest().getFiles().stream().map(ServerManifest.ModFileData::getFileName).collect(Collectors.toSet());
        return scannedMods.stream().filter(f-> manifestFileList.contains(f.getFileName())).collect(Collectors.toList());
    }

    @Override
    public void initialize(final IModLocator dirLocator) {
        clientDownloader = new SimpleHttpClient(this);
    }
}
