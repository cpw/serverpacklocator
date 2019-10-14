package cpw.mods.forge.serverpacklocator;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.Manifest;

public class PackLocator implements IModLocator {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Path serverModsPath;
    private final Dist locatorSide;
    private final SidedPackHandler serverPackLocator;
    private Optional<IModLocator> dirLocator;

    public PackLocator() {
        LOGGER.info("Loading server pack locator!");
        final Path gameDir = LaunchEnvironmentHandler.INSTANCE.getGameDir();
        serverModsPath = DirHandler.createOrGetDirectory(gameDir, "servermods");
        locatorSide = LaunchEnvironmentHandler.INSTANCE.getDist();
        serverPackLocator = SidedPackLocator.buildFor(locatorSide, serverModsPath);
        if (!serverPackLocator.isValid()) {
            LOGGER.warn("The server pack locator is not in a valid state, it will not load any mods");
        }
    }
    @Override
    public List<IModFile> scanMods() {
        return dirLocator.map(serverPackLocator::scanMods).orElse(Collections.emptyList());
    }

    @Override
    public String name() {
        return "serverpacklocator";
    }

    @Override
    public Path findPath(final IModFile modFile, final String... path) {
        return dirLocator.map(dl->dl.findPath(modFile, path)).orElse(null);
    }

    @Override
    public void scanFile(final IModFile modFile, final Consumer<Path> pathConsumer) {
        dirLocator.ifPresent(dl->dl.scanFile(modFile, pathConsumer));
    }

    @Override
    public Optional<Manifest> findManifest(final Path file) {
        return dirLocator.flatMap(dl -> dl.findManifest(file));
    }

    @Override
    public void initArguments(final Map<String, ?> arguments) {
        if (!serverPackLocator.isValid()) {
            dirLocator = Optional.empty();
        } else {
            final Function<Path, IModLocator> modFileLocator = LaunchEnvironmentHandler.INSTANCE.getModFolderFactory();
            dirLocator = Optional.of(modFileLocator.apply(serverModsPath));
            serverPackLocator.initialize(dirLocator.get());
        }
    }

    @Override
    public boolean isValid(final IModFile modFile) {
        return dirLocator.map(dl->dl.isValid(modFile)).orElse(false);
    }
}
