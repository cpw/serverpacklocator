package cpw.mods.forge.serverpacklocator;

import java.util.function.Supplier;

public class ModAccessor {
    static String statusLine = "ServerPack: unknown";
    public static Supplier<String> status() {
        return ()->statusLine;
    }
}
