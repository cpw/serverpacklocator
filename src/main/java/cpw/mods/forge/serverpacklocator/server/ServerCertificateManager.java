package cpw.mods.forge.serverpacklocator.server;

import com.electronwill.nightconfig.core.file.FileConfig;
import cpw.mods.forge.serverpacklocator.OptionalHelper;
import cpw.mods.forge.serverpacklocator.cert.CertificateManager;
import sun.security.x509.*;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.function.Consumer;

import static cpw.mods.forge.serverpacklocator.OptionalHelper.ifPresentOrElse;

public class ServerCertificateManager {
    static final SecureRandom RANDOM = new SecureRandom();
    /**
     * Current time minus 1 year, just in case software clock goes back due to time synchronization
     */
    private static final Date DEFAULT_NOT_BEFORE = new Date(System.currentTimeMillis() - 86400000L * 365);
    /**
     * The maximum possible value in X.509 specification: 9999-12-31 23:59:59
     */
    private static final Date DEFAULT_NOT_AFTER = new Date(253402300799000L);
    private static final String CERT_PEM = "cacert.pem";
    private static final String SECRET_KEY = "ca.key";

    private X509Certificate cert;
    private KeyPair keyPair;

    public ServerCertificateManager(final FileConfig config, final Path serverModsDir) {
        final Optional<String> keyFile = config.getOptional("server.cakey");
        ifPresentOrElse(keyFile
                .map(serverModsDir::resolve)
                .filter(Files::exists),
                path -> CertificateManager.loadKey(path, key->this.keyPair = key),
                () -> CertificateManager.buildNewKeyPair(serverModsDir, keyFile.get(), key->this.keyPair = key)
        );

        final Optional<String> cacertificate = config.getOptional("server.cacertificate");
        ifPresentOrElse(cacertificate
                .map(serverModsDir::resolve)
                .filter(Files::exists),
                path -> CertificateManager.loadCertificates(path, certs -> this.cert = certs.get(0)),
                ()->this.generateCaCert(serverModsDir, cacertificate.get(), config.get("server.name"))
        );
    }

    private void generateCaCert(final Path serverModsDir, final String caPath, final String serverName) {
        Consumer<X509Certificate> setter = cert -> this.cert = cert;
        CertificateManager.generateCerts(()->serverName, ()->this.keyPair, setter.andThen(cert -> CertificateManager.writeCertificates(()-> Collections.singletonList(cert), serverModsDir.resolve(caPath))));
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    public X509Certificate getCertificate() {
        return cert;
    }
}
