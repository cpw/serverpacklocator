package cpw.mods.forge.serverpacklocator.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class WhitelistValidator {
    private static final Logger LOGGER = LogManager.getLogger();
    private static Path whitelist;

    public static void setup(final Path gameDir) {
        whitelist = gameDir.resolve("whitelist.json");
        final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> SimpleHttpServer.newDaemonThread("ServerPack Whitelist watcher - ", r));
        executorService.submit(() -> LamdbaExceptionUtils.uncheck(() -> monitorWhitelist(gameDir)));
    }

    private static void monitorWhitelist(final Path gameDir) throws IOException {
        updateWhiteList();
        final WatchService watchService = gameDir.getFileSystem().newWatchService();
        final WatchKey watchKey = gameDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
        for (;;) {
            try {
                watchService.take();
                final List<WatchEvent<?>> watchEvents = watchKey.pollEvents();
                watchEvents.stream()
                        .filter(e -> Objects.equals(((Path) e.context()).getFileName().toString(), "whitelist.json"))
                        .findAny()
                        .ifPresent(e -> updateWhiteList());
                watchKey.reset();
            } catch (InterruptedException ie) {
                // Let the interruption break us out
                LOGGER.info("Breaking out of loop due to interruption", ie);
                Thread.interrupted();
                break;
            } catch (Throwable e) {
                LOGGER.warn("Caught unexpected whitelist monitoring exception", e);
                break;
            }
        }
    }

    private static Optional<Predicate<String>> validator = Optional.empty();

    static boolean validate(final String commonName) {
        return validator.map(v -> v.test(commonName.toLowerCase(Locale.ROOT))).orElse(false);
    }

    static void setValidator(final Predicate<String> uuidTester) {
        validator = Optional.of(uuidTester);
    }

    private static void updateWhiteList() {
        try (BufferedReader br = Files.newBufferedReader(whitelist)) {
            LOGGER.debug("Detected whitelist change, reloading");
            Thread.sleep(1000);
            JsonArray array = new JsonParser().parse(br).getAsJsonArray();
            final Set<String> uuidSet = StreamSupport.stream(array.spliterator(), false)
                    .map(JsonElement::getAsJsonObject)
                    .map(element -> element.getAsJsonPrimitive("uuid").getAsString())
                    .map(s->s.replaceAll("-", ""))
                    .map(s->s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            LOGGER.debug("Found whitelisted UUIDs : {}", uuidSet);
            setValidator(uuidSet::contains);
        } catch (IOException | InterruptedException e) {
            LOGGER.debug("Failed to reload whitelist", e);
        }

    }
}