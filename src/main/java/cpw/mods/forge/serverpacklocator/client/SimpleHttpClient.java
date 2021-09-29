package cpw.mods.forge.serverpacklocator.client;

import cpw.mods.forge.serverpacklocator.FileChecksumValidator;
import cpw.mods.forge.serverpacklocator.LaunchEnvironmentHandler;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.Base64;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SimpleHttpClient {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Path outputDir;
    private ServerManifest serverManifest;
    private Iterator<ServerManifest.ModFileData> fileDownloaderIterator;
    private final Future<Boolean> downloadJob;
    private final String          passwordHash;

    public SimpleHttpClient(final ClientSidedPackHandler packHandler, final String password) {
        this.outputDir = packHandler.getServerModsDir();

        try
        {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(password.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(Integer.toHexString(b & 0xff));
            }
            String base64 = new String(Base64.encodeBase64(sb.toString().getBytes()));
            this.passwordHash = base64.toUpperCase(Locale.ROOT);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("Missing MD5 hashing algorithm", e);
        }

        final Optional<String> remoteServer = packHandler.getConfig().getOptional("client.remoteServer");
        downloadJob = Executors.newSingleThreadExecutor().submit(() -> remoteServer.map(this::connectAndDownload).orElse(false));
    }

    private boolean connectAndDownload(final String server) {
        try {
            downloadManifest(server);
            downloadNextFile();
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed to download modpack from server: " + server, ex);
            return false;
        }
    }

    protected void downloadManifest(final String serverHost) throws IOException
    {
        var address = serverHost + "/servermanifest.json";

        LOGGER.debug("Requesting server manifest from: " + serverHost);
        LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Requesting server manifest from: " + serverHost);

        var url = new URL(address);
        var connection = url.openConnection();
        connection.setRequestProperty("Authentication", this.passwordHash);

        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream())) {
            this.serverManifest = ServerManifest.loadFromStream(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to download manifest", e);
        }
        LOGGER.debug("Received manifest");
        buildFileFetcher();
    }

    private void downloadFile(final ServerManifest.ModFileData next) throws IOException
    {
        final String existingChecksum = FileChecksumValidator.computeChecksumFor(outputDir.resolve(next.getFileName()));
        if (Objects.equals(next.getChecksum(), existingChecksum)) {
            LOGGER.debug("Found existing file {} - skipping", next.getFileName());
            downloadNextFile();
            return;
        }

        final String nextFile = next.getFileName();
        LOGGER.debug("Requesting file {}", nextFile);
        LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Requesting file "+nextFile);
        final String requestUri = LamdbaExceptionUtils.rethrowFunction((String f) -> URLEncoder.encode(f, StandardCharsets.UTF_8.name()))
          .andThen(s -> s.replaceAll("\\+", "%20"))
          .andThen(s -> "/files/"+s)
          .apply(nextFile);

        try
        {
            URLConnection connection = new URL(requestUri).openConnection();
            connection.setRequestProperty("Authentication", this.passwordHash);

            File file = outputDir.resolve(next.getFileName()).toFile();

            FileChannel download = new FileOutputStream(file).getChannel();

            long totalBytes = connection.getContentLengthLong(), time = System.nanoTime(), between, length;
            int percent;

            ReadableByteChannel channel = Channels.newChannel(connection.getInputStream());

            while (download.transferFrom(channel, file.length(), 1024) > 0)
            {
                between = System.nanoTime() - time;

                if (between < 1000000000) continue;

                length = file.length();

                percent = (int) ((double) length / ((double) totalBytes == 0.0 ? 1.0 : (double) totalBytes) * 100.0);

                LOGGER.debug("Downloaded {}% of {}", percent, nextFile);
                LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Downloaded " + percent + "% of " + nextFile);

                time = System.nanoTime();
            }

            downloadNextFile();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to download file: " + nextFile, ex);
        }
    }

    private void downloadNextFile() throws IOException
    {
        final Iterator<ServerManifest.ModFileData> fileDataIterator = fileDownloaderIterator;
        if (fileDataIterator.hasNext()) {
            downloadFile(fileDataIterator.next());
        } else {
            LOGGER.debug("Finished downloading closing channel");
        }
    }

    private void buildFileFetcher() {
        fileDownloaderIterator = serverManifest.getFiles().iterator();
    }

    boolean waitForResult() throws ExecutionException {
        try {
            return downloadJob.get();
        } catch (InterruptedException e) {
            return false;
        }
    }

    public ServerManifest getManifest() {
        return this.serverManifest;
    }
}
