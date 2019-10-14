package cpw.mods.forge.serverpacklocator;

import cpw.mods.forge.serverpacklocator.client.ClientSidedPackHandler;
import cpw.mods.forge.serverpacklocator.server.ServerSidedPackHandler;
import net.minecraftforge.api.distmarker.Dist;

import java.nio.file.Path;
import java.util.function.Function;

enum SidedPackLocator {
    CLIENT(ClientSidedPackHandler::new), DEDICATED_SERVER(ServerSidedPackHandler::new);
    private final Function<Path, SidedPackHandler> handler;

    SidedPackLocator(final Function<Path, SidedPackHandler> handler) {
        this.handler = handler;
    }

    public static SidedPackHandler buildFor(Dist side, final Path serverModsPath) {
        return valueOf(side.toString()).withServerDir(serverModsPath);
    }

    private SidedPackHandler withServerDir(final Path serverModsPath) {
        return handler.apply(serverModsPath);
    }
}
