package cpw.mods.forge.serverpacklocator.client;

import com.electronwill.nightconfig.core.file.FileConfig;
import cpw.mods.forge.serverpacklocator.cert.CertificateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import static cpw.mods.forge.serverpacklocator.OptionalHelper.*;

@SuppressWarnings("OptionalGetWithoutIsPresent")
class ClientCertificateManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private final boolean hasCertificate;
    private KeyPair keyPair;
    private List<X509Certificate> certs;

    ClientCertificateManager(final FileConfig config, final Path serverModsPath, final String uuid) {
        final Optional<String> certificate = config.getOptional("client.certificate");
        final Optional<String> key = config.getOptional("client.key");
        ifPresentOrElse(key.map(serverModsPath::resolve)
                .filter(Files::exists),
                ky -> CertificateManager.loadKey(ky, k->this.keyPair = k),
                () -> CertificateManager.buildNewKeyPair(serverModsPath, key.get(), k -> this.keyPair = k));

        ifPresentOrElse(certificate.map(serverModsPath::resolve)
                .filter(Files::exists),
                cert -> CertificateManager.loadCertificates(cert, cl -> this.certs = cl),
                () -> newCertSigningRequest(serverModsPath, uuid, certificate.get()));

        this.hasCertificate = certs != null;
    }

    private void newCertSigningRequest(final Path serverModsPath, final String uuid, final String outputCertPath) {
        LOGGER.info("Generating new certificate signing request for UUID {} at 'serverrequest.csr'", uuid);
        CertificateManager.generateCSR(()->uuid, ()->this.keyPair, csr->CertificateManager.writeCSR(()->csr, serverModsPath.resolve("serverrequest.csr")));
        LOGGER.warn("NEW certificate signing request and private key generated at 'serverrequest.csr'. " +
                "You will need to get this approved by the server before you can proceed. " +
                "Once you have the certificate from the server, place it in the file {} and restart the client. " +
                "Make sure you configure the remoteServer URL as well.", outputCertPath);
    }

    KeyPair getKeyPair() {
        return keyPair;
    }

    X509Certificate[] getCerts() {
        return certs.toArray(new X509Certificate[0]);
    }

    boolean isValid() {
        return hasCertificate;
    }
}
