package cpw.mods.forge.serverpacklocator.server;

import com.electronwill.nightconfig.core.file.FileConfig;
import cpw.mods.forge.serverpacklocator.cert.CertificateManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.*;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import static cpw.mods.forge.serverpacklocator.OptionalHelper.ifPresentOrElse;

public class ServerCertificateManager {
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
