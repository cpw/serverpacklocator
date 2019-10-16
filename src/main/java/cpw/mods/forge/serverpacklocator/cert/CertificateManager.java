package cpw.mods.forge.serverpacklocator.cert;

import com.electronwill.nightconfig.core.file.FileConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.security.pkcs10.PKCS10;
import sun.security.x509.*;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class CertificateManager {
    private static final Logger LOGGER = LogManager.getLogger();
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

    public static void loadCertificates(final Path certPath, Consumer<List<X509Certificate>> certConsumer) {
        List<X509Certificate> certs;
        try {
            final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            certs = certificateFactory.generateCertificates(Files.newInputStream(certPath))
                    .stream()
                    .map(X509Certificate.class::cast)
                    .collect(Collectors.toList());
            LOGGER.debug("Loaded {} certificates from {}", certs.size(), certPath.getFileName().toString());
        } catch (CertificateException | IOException e) {
            LOGGER.catching(e);
            throw new IllegalArgumentException(e);
        }
        certConsumer.accept(certs);
    }

    /**
     * Loads a key in DER format from the specified path
     * @param keyPath Path for the key
     * @param keyConsumer consumer for the key
     */
    public static void loadKey(final Path keyPath, Consumer<KeyPair> keyConsumer) {
        RSAPrivateCrtKey privateKey;
        PublicKey pubKey;
        try {
            byte[] bytes = Files.readAllBytes(keyPath);
            final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(bytes);
            privateKey = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(keySpec);
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
            pubKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
            LOGGER.debug("Loaded private key from {}", keyPath);
        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
            LOGGER.catching(e);
            throw new IllegalArgumentException(e);
        }
        final KeyPair keyPair = new KeyPair(pubKey, privateKey);
        keyConsumer.accept(keyPair);
    }

    public static void buildNewKeyPair(final Path serverModsPath, final String keyPath, Consumer<KeyPair> keyPairConsumer) {
        LOGGER.info("Generating new private key for new installation at {}", keyPath);
        CertificateManager.generateKeyPair(keyPairConsumer
                .andThen(kp ->
                        CertificateManager.writeKeyPair(() -> kp, serverModsPath.resolve(keyPath))));
    }

    public static void generateKeyPair(Consumer<KeyPair> keyPairConsumer) {
        KeyPair keypair;
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(4096, RANDOM);
            keypair = keyGen.generateKeyPair();
            LOGGER.debug("Generated new key pair");
        } catch (NoSuchAlgorithmException e) {
            LOGGER.catching(e);
            throw new RuntimeException("Unpossible?!", e);
        }
        keyPairConsumer.accept(keypair);
    }

    public static void generateCSR(Supplier<String> canonicalName, Supplier<KeyPair> keySupplier, Consumer<PKCS10> csrConsumer) {
        PKCS10 pkcs10;
        try {
            X500Name name = new X500Name("CN=" + canonicalName.get());
            final Signature sha256WithRSA = Signature.getInstance("SHA256WithRSA");
            sha256WithRSA.initSign(keySupplier.get().getPrivate(), RANDOM);
            pkcs10 = new PKCS10(keySupplier.get().getPublic());
            pkcs10.encodeAndSign(name, sha256WithRSA);
            LOGGER.debug("Generated new CSR with name {}", name.getCommonName());
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException | CertificateException | SignatureException e) {
            LOGGER.catching(e);
            throw new RuntimeException("Failed to generate CSR", e);
        }
        csrConsumer.accept(pkcs10);
    }

    public static void generateCerts(Supplier<String> canonicalName, Supplier<KeyPair> keyPairSupplier, Consumer<X509Certificate> certConsumer) {
        KeyPair keypair = keyPairSupplier.get();
        X509CertImpl cert;
        try {
            // Prepare the information required for generating an X.509 certificate.
            X509CertInfo info = new X509CertInfo();
            X500Name owner = new X500Name("CN=" + canonicalName.get());
            info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
            info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new BigInteger(64, RANDOM)));
            try {
                info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(owner));
            } catch (CertificateException ignore) {
                info.set(X509CertInfo.SUBJECT, owner);
            }
            try {
                info.set(X509CertInfo.ISSUER, new CertificateIssuerName(owner));
            } catch (CertificateException ignore) {
                info.set(X509CertInfo.ISSUER, owner);
            }
            info.set(X509CertInfo.VALIDITY, new CertificateValidity(DEFAULT_NOT_BEFORE, DEFAULT_NOT_AFTER));
            info.set(X509CertInfo.KEY, new CertificateX509Key(keypair.getPublic()));
            info.set(X509CertInfo.ALGORITHM_ID,
                    new CertificateAlgorithmId(new AlgorithmId(AlgorithmId.sha1WithRSAEncryption_oid)));

            // Sign the cert to identify the algorithm that's used.
            cert = new X509CertImpl(info);
            cert.sign(keypair.getPrivate(), "SHA256withRSA");

            // Update the algorithm and sign again.
            info.set(CertificateAlgorithmId.NAME + '.' + CertificateAlgorithmId.ALGORITHM, cert.get(X509CertImpl.SIG_ALG));
            cert = new X509CertImpl(info);
            cert.sign(keypair.getPrivate(), "SHA256withRSA");
            cert.verify(keypair.getPublic());
        } catch (NoSuchAlgorithmException | IOException | CertificateException | InvalidKeyException | NoSuchProviderException | SignatureException e) {
            throw new RuntimeException(e);
        }
        certConsumer.accept(cert);
    }

    public static void writeCSR(Supplier<PKCS10> csrSupplier, Path outputFile) {
        try (PrintStream writer = new PrintStream(Files.newOutputStream(outputFile))) {
            csrSupplier.get().print(writer);
            LOGGER.debug("Wrote CSR to {}", outputFile);
        } catch (IOException e) {
            LOGGER.catching(e);
            throw new UncheckedIOException(e);
        } catch (SignatureException e) {
            LOGGER.catching(e);
            throw new RuntimeException("WTF who sent me an unsigned CSR", e);
        }
    }

    public static void writeCertificates(Supplier<List<X509Certificate>> certificates, final Path outputFile) {
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.US_ASCII)) {
            writeCertChain(()->writer, certificates.get().toArray(new Certificate[0]));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeKeyPair(final Supplier<KeyPair> keyPairSupplier, final Path outPath) {
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyPairSupplier.get().getPrivate().getEncoded());
        try (OutputStream writer = Files.newOutputStream(outPath)) {
            writer.write(keySpec.getEncoded());
            LOGGER.debug("Wrote private key to {}", outPath);
        } catch (IOException e) {
            LOGGER.catching(e);
            throw new UncheckedIOException(e);
        }
    }

    static void writeCertChain(final Supplier<BufferedWriter> output, final java.security.cert.Certificate... certs) {
        final Base64.Encoder mimeEncoder = Base64.getMimeEncoder(64, new byte[]{10});
        final StringBuilder certText = new StringBuilder();
        for (Certificate cert: certs) {
            try {
                certText.append("-----BEGIN CERTIFICATE-----").append('\n')
                        .append(mimeEncoder.encodeToString(cert.getEncoded())).append('\n')
                        .append("-----END CERTIFICATE-----").append('\n');
            } catch (CertificateEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            output.get().write(certText.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
