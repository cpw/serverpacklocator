package cpw.mods.forge.serverpacklocator.cert;

import cpw.mods.forge.serverpacklocator.server.ServerCertificateManager;
import sun.security.pkcs10.PKCS10;
import sun.security.util.DerValue;
import sun.security.x509.*;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class CertSigner {
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";


    public static X509Certificate sign(PKCS10 csr, X509Certificate certificate, PrivateKey signerPrivKey) throws CertificateException, IOException, InvalidKeyException, SignatureException {

        /*
         * The code below is partly taken from the KeyTool class in OpenJDK7.
         */
        final X509CertImpl signerCert = X509CertImpl.toImpl(certificate);
        X509CertInfo signerCertInfo = (X509CertInfo) signerCert.get(X509CertImpl.NAME + "." + X509CertImpl.INFO);
        X500Name issuer = (X500Name) signerCertInfo.get(X509CertInfo.SUBJECT + "." + CertificateSubjectName.DN_NAME);

        /*
         * Set the certificate's validity:
         * From now until infinity
         */
        Date firstDate = new Date();
        Date lastDate = new Date();
        // LAST POSSIBLE DATE 9999-12-31 23:59:59
        lastDate.setTime(253402300799000L);
        CertificateValidity interval = new CertificateValidity(firstDate, lastDate);

        /*
         * Initialize the signature object
         */
        Signature signature;
        try {
            signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        signature.initSign(signerPrivKey);

        /*
         * Add the certificate information to a container object
         */
        X509CertInfo certInfo = new X509CertInfo();
        certInfo.set(X509CertInfo.VALIDITY, interval);
        certInfo.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new BigInteger(64, CertificateManager.RANDOM)));
        certInfo.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        try {
            certInfo.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(AlgorithmId.get(SIGNATURE_ALGORITHM)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        certInfo.set(X509CertInfo.ISSUER, issuer);
        certInfo.set(X509CertInfo.KEY, new CertificateX509Key(csr.getSubjectPublicKeyInfo()));
        certInfo.set(X509CertInfo.SUBJECT, csr.getSubjectName());

        /*
         * Add x509v3 extensions to the container
         */
        CertificateExtensions extensions = new CertificateExtensions();

        // Example extension.
        // See KeyTool source for more.
        boolean[] keyUsagePolicies = new boolean[9];
        keyUsagePolicies[0] = true; // Digital Signature
        keyUsagePolicies[2] = true; // Key encipherment
        KeyUsageExtension kue = new KeyUsageExtension(keyUsagePolicies);
        byte[] keyUsageValue = new DerValue(DerValue.tag_OctetString, kue.getExtensionValue()).toByteArray();
        extensions.set(KeyUsageExtension.NAME, new Extension(
                kue.getExtensionId(),
                true, // Critical
                keyUsageValue));


        /*
         * Create the certificate and sign it
         */
        X509CertImpl cert = new X509CertImpl(certInfo);
        try {
            cert.sign(signerPrivKey, SIGNATURE_ALGORITHM);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException(e);
        }

        return cert;
    }

    private static List<X509Certificate> caCert;
    private static KeyPair key;
    public static void main(String... args) throws Exception {
        final Path caPath = Paths.get(args[0]);
        final Path keyPath = Paths.get(args[1]);
        CertificateManager.loadCertificates(caPath, certs -> caCert = certs);
        CertificateManager.loadKey(keyPath, keyPair -> key = keyPair);
        String derString;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.US_ASCII))) {
            derString = br.lines().filter(l -> !l.startsWith("----")).collect(Collectors.joining());
        }
        final PKCS10 csr = new PKCS10(Base64.getDecoder().decode(derString));
        final X509Certificate cert = sign(csr, caCert.get(0), key.getPrivate());

        try (BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(System.out))) {
            CertificateManager.writeCertChain(() -> bufferedWriter, cert, caCert.get(0));
        }
    }
}
