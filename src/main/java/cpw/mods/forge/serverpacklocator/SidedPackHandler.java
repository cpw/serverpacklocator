package cpw.mods.forge.serverpacklocator;

import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.file.FileConfig;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public abstract class SidedPackHandler {
    private final Path serverModsDir;
    private final FileConfig packConfig;
    private boolean isValid;

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

    protected void invalidate() {
        this.isValid = false;
    }

    protected abstract boolean validateConfig();

    protected abstract boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException;

    public FileConfig getConfig() {
        return packConfig;
    }

    public Path getServerModsDir() {
        return serverModsDir;
    }

    boolean isValid() {
        return isValid;
    }

    protected abstract List<IModFile> scanMods(final List<IModFile> scannedMods);

    public abstract void initialize(final IModLocator dirLocator);
}
