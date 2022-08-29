package cpw.mods.forge.serverpacklocator.secure;

import com.electronwill.nightconfig.core.file.FileConfig;
import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;

public final class PasswordBasedSecurityManager implements IConnectionSecurityManager
{
    private static final PasswordBasedSecurityManager INSTANCE = new PasswordBasedSecurityManager();
    private static final Logger LOGGER = LogManager.getLogger();
    private String passwordHash = "";

    public static PasswordBasedSecurityManager getInstance()
    {
        return INSTANCE;
    }

    private PasswordBasedSecurityManager()
    {
    }

    @Override
    public void onClientConnectionCreation(final URLConnection connection)
    {
        connection.setRequestProperty("Authentication", "Basic " + passwordHash);
    }

    @Override
    public boolean onServerConnectionRequest(final FullHttpRequest msg)
    {
        final String authHeader = msg.headers().get("Authentication");
        if (!authHeader.startsWith("Basic "))
        {
            LOGGER.warn("User tried to login with different authentication scheme: " + authHeader);
            return false;
        }

        final String auth = authHeader.substring(6);
        if (!auth.equals(passwordHash))
        {
            LOGGER.warn("User tried to login with wrong password: " + auth);
            return false;
        }
        return true;
    }

    @Override
    public void validateConfiguration(final FileConfig config)
    {
        final Optional<String> password = config.getOptional("security.password");
        if (password.isEmpty()) {
            LOGGER.fatal("Invalid configuration file {} found. Could not locate server password. " +
                                 "Repair or delete this file to continue", config.getNioPath().toString());
            throw new IllegalStateException("Invalid configuration file found, please delete or correct");
        }
    }

    @Override
    public void initialize(final FileConfig config)
    {
        final String password = config.get("security.password");

        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(password.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(Integer.toHexString(b & 0xff));
            }
            this.passwordHash = sb.toString().toUpperCase(Locale.ROOT);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("Missing MD5 hashing algorithm", e);
        }
    }
}
