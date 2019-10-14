package cpw.mods.forge.serverpacklocator;

import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackLocator implements IModLocator {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Path serverModsPath;
    private final Dist locatorSide;
    private final SidedPackHandler serverPackLocator;
    private Optional<IModLocator> dirLocator;

    public PackLocator() {
        LOGGER.info("Loading server pack locator. Version {}", getClass().getPackage().getImplementationVersion());
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
        final List<IModFile> modFiles = dirLocator.map(IModLocator::scanMods).orElse(Collections.emptyList());
        final IModFile packutil = modFiles.stream()
                .filter(modFile -> "serverpackutility.jar".equals(modFile.getFileName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Something went wrong with the internal utility mod"));
        return Stream.concat(Stream.of(packutil), serverPackLocator.scanMods(modFiles).stream()).collect(Collectors.toList());
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
            ModAccessor.statusLine = "ServerPack: NOT loaded";
        } else {
            final Function<Path, IModLocator> modFileLocator = LaunchEnvironmentHandler.INSTANCE.getModFolderFactory();
            dirLocator = Optional.of(modFileLocator.apply(serverModsPath));
            serverPackLocator.initialize(dirLocator.get());
            ModAccessor.statusLine = "ServerPack: loaded";
        }
        URL url = getClass().getProtectionDomain().getCodeSource().getLocation();
        URI targetURI = LamdbaExceptionUtils.uncheck(() -> new URI("file://"+LamdbaExceptionUtils.uncheck(url::toURI).getRawSchemeSpecificPart().split("!")[0]));
        final FileSystem thiszip = LamdbaExceptionUtils.uncheck(() -> FileSystems.newFileSystem(Paths.get(targetURI), getClass().getClassLoader()));
        final Path utilModPath = thiszip.getPath("utilmod", "serverpackutility.jar");
        LamdbaExceptionUtils.uncheck(()->Files.copy(utilModPath, serverModsPath.resolve("serverpackutility.jar"), StandardCopyOption.REPLACE_EXISTING));
    }

    @Override
    public boolean isValid(final IModFile modFile) {
        return dirLocator.map(dl->dl.isValid(modFile)).orElse(false);
    }
}
