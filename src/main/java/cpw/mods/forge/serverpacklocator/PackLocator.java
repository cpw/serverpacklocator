package cpw.mods.forge.serverpacklocator;

import cpw.mods.forge.serverpacklocator.utils.ModUtilityUtils;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraftforge.forgespi.locating.IModDirectoryLocatorFactory;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class PackLocator implements IModLocator
{
    private static final Logger LOGGER = LogManager.getLogger();
    private final Path serverModsPath;
    private final SidedPackHandler serverPackLocator;
    private IModLocator dirLocator;

    public PackLocator()
    {
        LOGGER.info("Loading server pack locator. Version {}", getClass().getPackage().getImplementationVersion());
        final Path gameDir = LaunchEnvironmentHandler.INSTANCE.getGameDir();
        serverModsPath = DirHandler.createOrGetDirectory(gameDir, "servermods");
        serverPackLocator = SidedPackLocator.buildFor(LaunchEnvironmentHandler.INSTANCE.getDist(), serverModsPath);
        if (!serverPackLocator.isValid())
        {
            LOGGER.warn("The server pack locator is not in a valid state, it will not load any mods");
        }
    }

    @Override
    public List<ModFileOrException> scanMods()
    {
        boolean successfulDownload = serverPackLocator.waitForDownload();

        final String serverPackLocatorUtilityModFileName = ModUtilityUtils.buildModUtilityFileName();

        final List<ModFileOrException> modFiles = dirLocator.scanMods();
        final IModFile packutil = modFiles.stream()
                                          .filter(moe -> moe.file() != null)
                                          .filter(modFile -> serverPackLocatorUtilityModFileName.equals(modFile.file().getFileName()))
                                          .findFirst()
                                          .map(ModFileOrException::file)
                                          .orElseThrow(() -> new RuntimeException("Something went wrong with the internal utility mod"));

        ArrayList<ModFileOrException> finalModList = new ArrayList<>();
        finalModList.add(new ModFileOrException(packutil, null));
        if (successfulDownload)
        {
            finalModList.addAll(serverPackLocator.processModList(modFiles));
        }

        ModAccessor.statusLine = "ServerPack: " + (successfulDownload ? "loaded" : "NOT loaded");
        return finalModList;
    }

    @Override
    public String name()
    {
        return "serverpacklocator";
    }

    @Override
    public void scanFile(final IModFile modFile, final Consumer<Path> pathConsumer)
    {
        dirLocator.scanFile(modFile, pathConsumer);
    }

    @Override
    public void initArguments(final Map<String, ?> arguments)
    {
        final IModDirectoryLocatorFactory modFileLocator = LaunchEnvironmentHandler.INSTANCE.getModFolderFactory();
        dirLocator = modFileLocator.build(serverModsPath, "serverpack");

        serverPackLocator.setForgeVersion((String) arguments.get("forgeVersion"));
        serverPackLocator.setMcVersion((String) arguments.get("mcVersion"));

        if (serverPackLocator.isValid())
        {
            serverPackLocator.initialize(dirLocator);
        }

        URL url = getClass().getProtectionDomain().getCodeSource().getLocation();

        LOGGER.info("Loading server pack locator from: " + url.toString());
        URI targetURI = LamdbaExceptionUtils.uncheck(() -> new URI("file://" + LamdbaExceptionUtils.uncheck(url::toURI).getRawSchemeSpecificPart().split("!")[0].split("\\.jar")[0] + ".jar"));

        LOGGER.info("Unpacking utility mod from: " + targetURI.toString());
        final FileSystem thiszip = LamdbaExceptionUtils.uncheck(() -> FileSystems.newFileSystem(Paths.get(targetURI), getClass().getClassLoader()));
        final Path utilModPath = thiszip.getPath("utilmod", ModUtilityUtils.buildModUtilityFileName());
        LamdbaExceptionUtils.uncheck(() -> Files.copy(utilModPath, serverModsPath.resolve(ModUtilityUtils.buildModUtilityFileName()), StandardCopyOption.REPLACE_EXISTING));
    }

    @Override
    public boolean isValid(final IModFile modFile)
    {
        return dirLocator.isValid(modFile);
    }
}
