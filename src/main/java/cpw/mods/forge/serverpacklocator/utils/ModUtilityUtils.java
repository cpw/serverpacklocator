package cpw.mods.forge.serverpacklocator.utils;

public final class ModUtilityUtils
{

    private ModUtilityUtils()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ModUtilityUtils. This is a utility class");
    }

    public static String buildModUtilityFileName() {
        return "SPL-Mod-" + ModUtilityUtils.class.getPackage().getImplementationVersion() + ".jar";
    }
}
