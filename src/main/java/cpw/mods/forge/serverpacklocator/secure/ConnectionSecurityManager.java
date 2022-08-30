package cpw.mods.forge.serverpacklocator.secure;

import com.electronwill.nightconfig.core.file.FileConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ConnectionSecurityManager
{
    private static final ConnectionSecurityManager INSTANCE = new ConnectionSecurityManager();
    private static final Logger LOGGER = LogManager.getLogger();

    public static ConnectionSecurityManager getInstance()
    {
        return INSTANCE;
    }

    private final Map<String, IConnectionSecurityManager> securityManagers;

    private ConnectionSecurityManager()
    {
        securityManagers = Map.of(
                "password", PasswordBasedSecurityManager.getInstance(),
                "publickey", ProfileKeyPairBasedSecurityManager.getInstance()
        );
    }

    public void validateConfiguration(final FileConfig config)
    {
        final Optional<String> securityType = config.getOptional("security.type");
        if (securityType.isEmpty()) {
            LOGGER.fatal("Invalid configuration file {} found. Could not locate security type. " +
                                 "Repair or delete this file to continue", config.getNioPath().toString());
            throw new IllegalStateException("Invalid configuration file found, please delete or correct");
        }
        if (!securityManagers.containsKey(securityType.get())) {
            LOGGER.fatal("Invalid configuration file {} found. Unknown security type: {}. " +
                                 "Repair or delete this file to continue", config.getNioPath().toString(), securityType.get());
            throw new IllegalStateException("Invalid configuration file found, please delete or correct");
        }

        securityManagers.get(securityType.get()).validateConfiguration(config);
    }

    public IConnectionSecurityManager initialize(final FileConfig config) {
        final String securityType = config.get("security.type");
        final IConnectionSecurityManager connectionSecurityManager = securityManagers.get(securityType);
        connectionSecurityManager.initialize(config);
        return connectionSecurityManager;
    }

}
