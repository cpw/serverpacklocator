package cpw.mods.forge.serverpacklocator.secure;

import com.google.gson.Gson;
import cpw.mods.forge.serverpacklocator.ModAccessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

public final class WhitelistVerificationHelper
{
    private static final WhitelistVerificationHelper INSTANCE = new WhitelistVerificationHelper();

    public static WhitelistVerificationHelper getInstance()
    {
        return INSTANCE;
    }

    private WhitelistVerificationHelper()
    {
    }
    public boolean isAllowed(final UUID sessionId) {
        return !ModAccessor.getIsWhiteListEnabled().get().join() || ModAccessor.getIsWhiteListed().apply(sessionId).join();
    }
}
