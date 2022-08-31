package cpw.mods.forge.serverpacklocator;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public class ModAccessor {
    private static String statusLine = "ServerPack: unknown";
    private static Function<UUID, CompletableFuture<Boolean>> isWhiteListed = (uuid) -> CompletableFuture.completedFuture(false);
    private static Supplier<CompletableFuture<Boolean>> isWhiteListEnabled = () -> CompletableFuture.completedFuture(false);

    public static void setStatusLine(final String statusLine)
    {
        ModAccessor.statusLine = statusLine;
    }
    public static String getStatusLine()
    {
        return statusLine;
    }

    public static Function<UUID, CompletableFuture<Boolean>> getIsWhiteListed()
    {
        return isWhiteListed;
    }

    public static Supplier<CompletableFuture<Boolean>> getIsWhiteListEnabled()
    {
        return isWhiteListEnabled;
    }

    public static void setIsWhiteListed(final Function<UUID, CompletableFuture<Boolean>> isWhiteListed)
    {
        ModAccessor.isWhiteListed = isWhiteListed;
    }

    public static void setIsWhiteListEnabled(final Supplier<CompletableFuture<Boolean>> isWhiteListEnabled)
    {
        ModAccessor.isWhiteListEnabled = isWhiteListEnabled;
    }
}
