package cpw.mods.forge.serverpacklocator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cpw.mods.forge.serverpacklocator.server.ServerFileManager;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ServerManifest {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private String forgeVersion;
    private List<ModFileData> files = new ArrayList<>();

    public static ServerManifest loadFromStream(final InputStream stream) {
        return GSON.fromJson(new InputStreamReader(stream), ServerManifest.class);
    }

    public String getForgeVersion() {
        return forgeVersion;
    }

    public void setForgeVersion(final String forgeVersion) {
        this.forgeVersion = forgeVersion;
    }

    public List<ModFileData> getFiles() {
        return files;
    }

    public void setFiles(final List<ModFileData> files) {
        this.files = files;
    }

    public void addAll(final List<ModFileData> nonModFileData) {
        files.addAll(nonModFileData);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static class ModFileData {
        private String rootModId;
        private String checksum;
        private String fileName;
        private transient IModFile modFile;

        public ModFileData() {
        }

        public ModFileData(final IModFile modFile) {
            this.modFile = modFile;
            this.rootModId = modFile.getType() == IModFile.Type.MOD ? ServerFileManager.getModInfos(modFile).get(0).getModId() : modFile.getFileName();
            this.fileName = modFile.getFileName();
            this.checksum = FileChecksumValidator.computeChecksumFor(modFile.getFilePath());
            if (this.checksum == null) {
                throw new IllegalStateException("Invalid checksum for file "+modFile.getFileName());
            }
        }

        public String getRootModId() {
            return this.rootModId;
        }

        public String getChecksum() {
            return checksum;
        }

        public String getFileName() {
            return fileName;
        }

        public IModFile getModFile() {
            return modFile;
        }
    }

    public static ServerManifest load(final Path path) {
        try (BufferedReader json = Files.newBufferedReader(path)) {
            return GSON.fromJson(json, ServerManifest.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void save(final Path path) {
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path)) {
            bufferedWriter.write(toJson());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
