package cpw.mods.forge.serverpacklocator.secure;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.PrivateKey;
import java.security.Signature;

public interface Signer {
    Logger LOGGER = LogManager.getLogger();

    byte[] sign(byte[] payload);

    static Signer from(PrivateKey privateKey, String algorithmName) {
        return (payload) -> {
            try {
                Signature signature = Signature.getInstance(algorithmName);
                signature.initSign(privateKey);
                signature.update(payload);
                return signature.sign();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to sign message", exception);
            }
        };
    }
}
