package cpw.mods.forge.serverpacklocator;

import cpw.mods.modlauncher.Environment;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.TypesafeMap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.forgespi.locating.IModLocator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class LaunchEnvironmentHandler {
    public static final LaunchEnvironmentHandler INSTANCE = new LaunchEnvironmentHandler();
    private final Optional<Environment> environment;

    private LaunchEnvironmentHandler() {
        environment = Optional.ofNullable(Launcher.INSTANCE).map(Launcher::environment);
    }

    private <T> Optional<T> getValue(final Supplier<TypesafeMap.Key<T>> key) {
        return environment.flatMap(e -> e.getProperty(key.get()));
    }

    Path getGameDir() {
        return getValue(IEnvironment.Keys.GAMEDIR).orElseGet(()-> Paths.get("."));
    }

    public String getUUID() {
        return getValue(IEnvironment.Keys.UUID).orElse("");
    }

    Dist getDist() {
        return getValue(net.minecraftforge.forgespi.Environment.Keys.DIST).orElse(Dist.CLIENT);
    }

    Function<Path, IModLocator> getModFolderFactory() {
        return getValue(net.minecraftforge.forgespi.Environment.Keys.MODFOLDERFACTORY).orElseThrow(()->new IllegalStateException("Missing MODFOLDERFACTORY, wut?"));
    }
}
