package cpw.mods.forge.serverpacklocator;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class ModAccessor {
    static String statusLine = "ServerPack: unknown";
    static boolean needsCertificate = true;

    public static Supplier<String> status() {
        return ()->statusLine;
    }

    public static BooleanSupplier needsCert() { return () -> needsCertificate; }
}
