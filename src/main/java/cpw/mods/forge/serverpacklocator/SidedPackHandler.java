package cpw.mods.forge.serverpacklocator;

import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.file.FileConfig;
import net.minecraftforge.forgespi.locating.IModLocator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public abstract class SidedPackHandler {
    private final Path serverModsDir;
    private final FileConfig packConfig;
    private boolean isValid;
    private String forgeVersion = "UNKNOWN";
    private String mcVersion = "UNKNOWN";

    protected SidedPackHandler(final Path serverModsDir) {
        this.serverModsDir = serverModsDir;
        this.packConfig = FileConfig
                .builder(serverModsDir.resolve("serverpacklocator.toml"))
                .onFileNotFound(this::handleMissing)
                .build();
        packConfig.load();
        packConfig.close();
        this.isValid = validateConfig();
    }

    protected abstract boolean validateConfig();

    protected abstract boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException;

    public FileConfig getConfig() {
        return packConfig;
    }

    public Path getServerModsDir() {
        return serverModsDir;
    }

    protected boolean isValid() {
        return isValid;
    }

    protected abstract List<IModLocator.ModFileOrException> processModList(final List<IModLocator.ModFileOrException> scannedMods);

    public abstract void initialize(final IModLocator dirLocator);

    protected abstract boolean waitForDownload();

    public String getForgeVersion()
    {
        return forgeVersion;
    }

    public void setForgeVersion(final String forgeVersion)
    {
        this.forgeVersion = forgeVersion;
    }

    public String getMcVersion()
    {
        return mcVersion;
    }

    public void setMcVersion(final String mcVersion)
    {
        this.mcVersion = mcVersion;
    }
}
