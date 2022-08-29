package cpw.mods.forge.serverpacklocator.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class FileWatchdogThread extends Thread {

    public static void watching(Path file, final Consumer<Path> onChange)
    {
        new FileWatchdogThread(file, onChange);
    }

    private static final Logger LOGGER = LogManager.getLogger();

    private final Path file;
    private final Consumer<Path> onChange;

    private FileWatchdogThread(Path file, final Consumer<Path> onChange) {
        this.file = file;
        this.onChange = onChange;

        this.setDaemon(true);
        this.setName("FileWatchDog-" + file.getFileName().toString());
        this.start();
    }

    public void doOnChange() {
        onChange.accept(file);
    }

    @Override
    public void run() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            Path path = file.getParent();
            path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
            while (true) {
                WatchKey key;
                try { key = watcher.poll(25, TimeUnit.MILLISECONDS); }
                catch (InterruptedException e) { return; }
                if (key == null) { Thread.yield(); continue; }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        Thread.yield();
                        continue;
                    } else if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
                                       && filename.toString().equals(file.getFileName().toString())) {
                        doOnChange();
                    }
                    boolean valid = key.reset();
                    if (!valid) { break; }
                }
                Thread.yield();
            }
        } catch (Throwable e) {
            LOGGER.error("Error while watching file: " + file, e);
        }
    }
}
