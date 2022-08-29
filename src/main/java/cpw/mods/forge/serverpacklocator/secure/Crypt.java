package cpw.mods.forge.serverpacklocator.secure;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class Crypt {
    private static final String PEM_RSA_PRIVATE_KEY_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String PEM_RSA_PRIVATE_KEY_FOOTER = "-----END RSA PRIVATE KEY-----";
    private static final String RSA_PUBLIC_KEY_HEADER = "-----BEGIN RSA PUBLIC KEY-----";
    private static final String RSA_PUBLIC_KEY_FOOTER = "-----END RSA PUBLIC KEY-----";
    public static final String MIME_LINE_SEPARATOR = "\n";
    public static final Base64.Encoder MIME_ENCODER = Base64.getMimeEncoder(76, MIME_LINE_SEPARATOR.getBytes(StandardCharsets.UTF_8));

    public Crypt()
    {
        throw new IllegalStateException("Can not instantiate an instance of: Crypt. This is a utility class");
    }

    private static <T extends Key> T rsaStringToKey(String keyString, String header, String footer, Crypt.ByteArrayToKeyFunction<T> keyCreator) throws RuntimeException {
        int headerIndex = keyString.indexOf(header);
        if (headerIndex != -1) {
            headerIndex += header.length();
            int j = keyString.indexOf(footer, headerIndex);
            keyString = keyString.substring(headerIndex, j + 1);
        }

        try {
            return keyCreator.apply(Base64.getMimeDecoder().decode(keyString));
        } catch (IllegalArgumentException illegalargumentexception) {
            throw new RuntimeException(illegalargumentexception);
        }
    }

    public static PrivateKey stringToPemRsaPrivateKey(String keyString) throws RuntimeException {
        return rsaStringToKey(keyString, PEM_RSA_PRIVATE_KEY_HEADER, PEM_RSA_PRIVATE_KEY_FOOTER, Crypt::byteToPrivateKey);
    }

    public static PublicKey stringToRsaPublicKey(String keyString) throws RuntimeException {
        return rsaStringToKey(keyString, RSA_PUBLIC_KEY_HEADER, RSA_PUBLIC_KEY_FOOTER, Crypt::byteToPublicKey);
    }

    public static String rsaPublicKeyToString(PublicKey key) {
        if (!"RSA".equals(key.getAlgorithm())) {
            throw new IllegalArgumentException("Public key must be RSA");
        } else {
            return "-----BEGIN RSA PUBLIC KEY-----\n" + MIME_ENCODER.encodeToString(key.getEncoded()) + "\n-----END RSA PUBLIC KEY-----\n";
        }
    }

    private static PrivateKey byteToPrivateKey(byte[] keyBytes) throws RuntimeException {
        try {
            EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePrivate(spec);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public static PublicKey byteToPublicKey(byte[] keyBytes) throws RuntimeException {
        try {
            EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePublic(spec);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    interface ByteArrayToKeyFunction<T extends Key> {
        T apply(byte[] payload) throws RuntimeException;
    }
}