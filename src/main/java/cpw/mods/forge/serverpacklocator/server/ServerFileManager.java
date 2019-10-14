package cpw.mods.forge.serverpacklocator.server;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.file.FileConfigBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import cpw.mods.forge.serverpacklocator.DirHandler;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ServerFileManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static Method modInfos;
    private static Map<IModFile, IModFileInfo> infos;
    private ServerManifest manifest;
    private final Path modsDir;
    private List<IModFile> modList;
    private Path manifestFile;

    ServerFileManager(ServerSidedPackHandler packHandler) {
        modsDir = packHandler.getServerModsDir();
        manifestFile = modsDir.resolve("servermanifest.json");
    }

    public String buildManifest() {
        return manifest.toJson();
    }

    public byte[] findFile(final String fileName) {
        try {
            return Files.readAllBytes(modsDir.resolve(fileName));
        } catch (IOException e) {
            LOGGER.warn("Failed to read file {}", fileName);
            return null;
        }
    }

    public static List<IModInfo> getModInfos(final IModFile modFile) {
        if (modInfos == null) {
            Class<?> clazz = LamdbaExceptionUtils.uncheck(() -> Class.forName("net.minecraftforge.fml.loading.moddiscovery.ModFileParser"));
            Class<?> mfClass = LamdbaExceptionUtils.uncheck(() -> Class.forName("net.minecraftforge.fml.loading.moddiscovery.ModFile"));
            modInfos = LamdbaExceptionUtils.uncheck(() -> clazz.getMethod("readModList", mfClass));
            infos = new HashMap<>();
        }
        IModFileInfo info = infos.computeIfAbsent(modFile, LamdbaExceptionUtils.rethrowFunction(junk->(IModFileInfo)modInfos.invoke(null, modFile)));
        return info.getMods();
    }

    public void parseModList(final List<IModFile> modList) {
        final Map<String, IModFile> modFileMap = modList.stream()
                .collect(Collectors.toMap(IModFile::getFileName, Function.identity()));
        try {
            manifest = ServerManifest.load(manifestFile);
            // if there's a missing file in the manifest, regenerate it
            manifest.getFiles().stream().filter(md -> !modFileMap.containsKey(md.getFileName())).findAny().ifPresent(md->this.generateManifest(modList));
            this.modList = manifest.getFiles().stream()
                    .map(modFileData -> modFileMap.get(modFileData.getFileName()))
                    .collect(Collectors.toList());
        } catch (UncheckedIOException e) {
            this.generateManifest(modList);
        }
    }

    private void generateManifest(final List<IModFile> modList) {
        final Map<String, List<IModFile>> filesbyfirstId = modList.stream()
                .filter(mf -> mf.getType() == IModFile.Type.MOD)
                .collect(Collectors.groupingBy(mf -> getModInfos(mf).get(0).getModId()));
        final List<IModFile> nonModFiles = modList.stream()
                .filter(mf -> mf.getType() != IModFile.Type.MOD)
                .collect(Collectors.toList());

        final ServerManifest manifest = new ServerManifest();
        final List<ServerManifest.ModFileData> nonModFileData = nonModFiles
                .stream()
                .map(ServerManifest.ModFileData::new)
                .collect(Collectors.toList());
        manifest.addAll(nonModFileData);
        final List<ServerManifest.ModFileData> modFileDataList = filesbyfirstId.entrySet().stream()
                .map(this::selectNewest)
                .map(ServerManifest.ModFileData::new)
                .collect(Collectors.toList());
        manifest.addAll(modFileDataList);
        manifest.setForgeVersion("1.14.4-28.1.45");
        this.manifest = manifest;
        this.manifest.save(this.manifestFile);
        this.modList = Stream.concat(nonModFileData.stream(), modFileDataList.stream())
                .map(ServerManifest.ModFileData::getModFile)
                .collect(Collectors.toList());
    }

    private IModFile selectNewest(final Map.Entry<String, List<IModFile>> modListEntry) {
        List<IModFile> modFiles = modListEntry.getValue();
        modFiles.sort(Comparator.comparing(mf->getModInfos(mf).get(0).getVersion()));
        return modFiles.get(0);
    }

    public List<IModFile> getModList() {
        return this.modList;
    }
}
