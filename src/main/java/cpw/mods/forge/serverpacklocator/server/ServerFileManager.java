package cpw.mods.forge.serverpacklocator.server;

import cpw.mods.forge.serverpacklocator.ServerManifest;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.ArtifactVersion;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ServerFileManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static Map<IModFile, IModFileInfo> infos;
    private ServerManifest manifest;
    private final Path modsDir;
    private List<IModFile> modList;
    private final Path manifestFile;
    private final ServerSidedPackHandler serverSidedPackHandler;
    private final List<String> excludedModIds;

    ServerFileManager(ServerSidedPackHandler packHandler, final List<String> excludedModIds) {
        modsDir = packHandler.getServerModsDir();
        this.excludedModIds = excludedModIds;
        manifestFile = modsDir.resolve("servermanifest.json");
        this.serverSidedPackHandler = packHandler;
    }

    private String getForgeVersion()
    {
        return serverSidedPackHandler.getMcVersion() + "-" + serverSidedPackHandler.getForgeVersion();
    }

    String buildManifest() {
        return manifest.toJson();
    }

    byte[] findFile(final String fileName) {
        try {
            return Files.readAllBytes(modsDir.resolve(fileName));
        } catch (IOException e) {
            LOGGER.warn("Failed to read file {}", fileName);
            return null;
        }
    }

    private static Field modInfoParser;
    private static Method modFileParser;
    public static List<IModInfo> getModInfos(final IModFile modFile) {
        if (modInfoParser == null) {
            Class<?> mfClass = LamdbaExceptionUtils.uncheck(() -> Class.forName("net.minecraftforge.fml.loading.moddiscovery.ModFile"));
            modInfoParser = LamdbaExceptionUtils.uncheck(() -> mfClass.getDeclaredField("parser"));
            modInfoParser.setAccessible(true);
            Class<?> mfpClass = LamdbaExceptionUtils.uncheck(() -> Class.forName("net.minecraftforge.fml.loading.moddiscovery.ModFileParser"));
            modFileParser = Arrays.stream(mfpClass.getMethods()).filter(m -> m.getName().equals("readModList")).findAny().orElseThrow(() -> new RuntimeException("BARFY!"));
            infos = new HashMap<>();
        }
        IModFileInfo info = infos.computeIfAbsent(modFile, LamdbaExceptionUtils.rethrowFunction(junk->(IModFileInfo)modFileParser.invoke(null, modFile, modInfoParser.get(modFile))));
        return info.getMods();
    }

    void parseModList(final List<IModFile> modList) {
        // NOT SURE ABOUT whether I should just always generate the latest mod list or not.
//        final Map<String, List<IModFile>> filesbyfirstId = modList.stream()
//                .filter(mf -> mf.getType() == IModFile.Type.MOD)
//                .collect(Collectors.groupingBy(mf -> getModInfos(mf).get(0).getModId()));
//        final Map<String, IModFile> modFileMap = modList.stream()
//                .collect(Collectors.toMap(IModFile::getFileName, Function.identity()));
//        try {
//            manifest = ServerManifest.load(manifestFile);
//            // if there's a missing file in the manifest, regenerate it
//            manifest.getFiles().stream().filter(md -> !modFileMap.containsKey(md.getFileName())).findAny().ifPresent(junk->this.generateManifest(modList));
//            // if there's a new modid in the files on disk, regenerate
//            final Set<String> knownModIds = manifest.getFiles().stream().map(ServerManifest.ModFileData::getRootModId).collect(Collectors.toSet());
//            filesbyfirstId.keySet().stream().filter(modId -> !knownModIds.contains(modId)).findAny().ifPresent(junk->this.generateManifest(modList));
//            this.modList = manifest.getFiles().stream()
//                    .map(modFileData -> modFileMap.get(modFileData.getFileName()))
//                    .collect(Collectors.toList());
//        } catch (UncheckedIOException e) {
//            this.generateManifest(modList);
//        }
        this.generateManifest(modList);
    }

    private void generateManifest(final List<IModFile> modList) {
        LOGGER.debug("Generating manifest");
        final Map<String, List<IModFile>> filesbyfirstId = modList.stream()
                .filter(mf -> mf.getType() == IModFile.Type.MOD)
                .filter(mf -> !"serverpackutility.jar".equals(mf.getFileName()))
                .collect(Collectors.groupingBy(mf -> getModInfos(mf).get(0).getModId()));
        final List<IModFile> nonModFiles = modList.stream()
          .filter(mf -> mf.getType() != IModFile.Type.MOD).toList();

        final ServerManifest manifest = new ServerManifest();
        final List<ServerManifest.ModFileData> nonModFileData = nonModFiles
                .stream()
                .map(ServerManifest.ModFileData::new)
                .filter(modInfo -> !this.excludedModIds.contains(modInfo.getRootModId()))
                .collect(Collectors.toList());
        manifest.addAll(nonModFileData);
        final List<ServerManifest.ModFileData> modFileDataList = filesbyfirstId.entrySet().stream()
                .map(this::selectNewest)
                .map(ServerManifest.ModFileData::new)
                .filter(modInfo -> !this.excludedModIds.contains(modInfo.getRootModId()))
                .collect(Collectors.toList());
        manifest.addAll(modFileDataList);
        manifest.setForgeVersion(LamdbaExceptionUtils.uncheck(this::getForgeVersion));
        this.manifest = manifest;
        this.manifest.save(this.manifestFile);
        this.modList = Stream.concat(nonModFileData.stream(), modFileDataList.stream())
                .map(ServerManifest.ModFileData::getModFile)
                .collect(Collectors.toList());
    }

    private IModFile selectNewest(final Map.Entry<String, List<IModFile>> modListEntry) {
        List<IModFile> modFiles = modListEntry.getValue();
        if (modFiles.size() > 1) {
            LOGGER.debug("Selecting newest by artifact version for modid {}", modListEntry.getKey());
            modFiles.sort(Comparator.<IModFile, ArtifactVersion>comparing(mf -> getModInfos(mf).get(0).getVersion()).reversed());
            LOGGER.debug("Newest file by artifact version for modid {} is {} ({})", modListEntry.getKey(), modFiles.get(0).getFileName(), getModInfos(modFiles.get(0)).get(0).getVersion());
        }
        return modFiles.get(0);
    }

    List<IModFile> getModList() {
        return this.modList;
    }
}
