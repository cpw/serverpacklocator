package cpw.mods.forge.serverpacklocator.secure;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ServicesKeyInfo;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.response.KeyPairResponse;
import cpw.mods.modlauncher.ArgumentHandler;
import cpw.mods.modlauncher.Launcher;
import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.net.Proxy;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public final class ProfileKeyPairBasedSecurityManager implements IConnectionSecurityManager
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ProfileKeyPairBasedSecurityManager INSTANCE = new ProfileKeyPairBasedSecurityManager();
    private static final UUID DEFAULT_NILL_UUID = new UUID(0L, 0L);

    public static ProfileKeyPairBasedSecurityManager getInstance()
    {
        return INSTANCE;
    }

    private final SigningHandler signingHandler;
    private final UUID sessionId;
    private final byte[] sessionIdPayload;

    private final SignatureValidator validator;

    private ProfileKeyPairBasedSecurityManager()
    {
        signingHandler = getSigningHandler();
        sessionId = getSessionId();
        validator = getSignatureValidator();

        sessionIdPayload = sessionId != null ? sessionId.toString().getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    private static ArgumentHandler getArgumentHandler() {
        try {
            final Field argumentHandlerField = Launcher.class.getDeclaredField("argumentHandler");
            argumentHandlerField.setAccessible(true);
            return (ArgumentHandler) argumentHandlerField.get(Launcher.INSTANCE);
        }
        catch (NoSuchFieldException | IllegalAccessException | ClassCastException e)
        {
            throw new RuntimeException("Failed to get the argument handler used to start the system", e);
        }
    }

    private static String[] getLaunchArguments() {
        final ArgumentHandler argumentHandler = getArgumentHandler();
        try {
            final Field argsArrayField = ArgumentHandler.class.getDeclaredField("args");
            argsArrayField.setAccessible(true);
            return (String[]) argsArrayField.get(argumentHandler);
        }
        catch (NoSuchFieldException | IllegalAccessException | ClassCastException e)
        {
            throw new RuntimeException("Failed to get the launch arguments used to start the system", e);
        }
    }

    private static String getAccessToken() {
        final String[] arguments = getLaunchArguments();
        for (int i = 0; i < arguments.length; i++)
        {
            final String arg = arguments[i];
            if (Objects.equals(arg, "--accessToken")) {
                return arguments[i+1];
            }
        }

        return "";
    }

    private static UUID getSessionId() {
        final String[] arguments = getLaunchArguments();
        for (int i = 0; i < arguments.length; i++)
        {
            final String arg = arguments[i];
            if (Objects.equals(arg, "--uuid")) {
                return UUID.fromString(arguments[i+1].replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            }
        }

        return DEFAULT_NILL_UUID;
    }

    private static YggdrasilAuthenticationService getAuthenticationService() {
        return new YggdrasilAuthenticationService(Proxy.NO_PROXY); //For now, we do not support custom proxies.
    }

    private static UserApiService getApiService() {
        final String accessToken = getAccessToken();
        final YggdrasilAuthenticationService authenticationService = getAuthenticationService();
        if (accessToken.isBlank())
            return UserApiService.OFFLINE;

        try
        {
            return authenticationService.createUserApiService(accessToken);
        }
        catch (AuthenticationException e)
        {
            throw new RuntimeException("Failed to create user api service to get profile key pair!", e);
        }
    }

    private static KeyPairResponse getKeyPair() {
        final UserApiService apiService = getApiService();
        return apiService.getKeyPair();
    }

    private static ProfileKeyPair getProfileKeyPair() {
        final KeyPairResponse keyPairResponse = getKeyPair();
        if (keyPairResponse == null)
            return null;

        return new ProfileKeyPair(Crypt.stringToPemRsaPrivateKey(keyPairResponse.getPrivateKey()),
                new PublicKeyData(
                Crypt.stringToRsaPublicKey(keyPairResponse.getPublicKey()),
                Instant.parse(keyPairResponse.getExpiresAt()),
                keyPairResponse.getPublicKeySignature().array()));
    }

    private static SigningHandler getSigningHandler() {
        final ProfileKeyPair profileKeyPair = getProfileKeyPair();
        if (profileKeyPair == null)
            return null;

        return new SigningHandler(profileKeyPair);
    }

    private static SignatureValidator getSignatureValidator() {
        final YggdrasilAuthenticationService authenticationService = getAuthenticationService();

        final ServicesKeyInfo keyInfo = authenticationService.getServicesKey();
        if (keyInfo == null)
            return SignatureValidator.ALWAYS_FAIL;

        return SignatureValidator.from(keyInfo);
    }

    private static void validatePublicKey(PublicKeyData keyData, UUID sessionId, SignatureValidator systemValidator) throws Exception
    {
        if (keyData.key() == null) {
            throw new Exception("Missing public key!");
        } else {
            if (keyData.expiresAt().isBefore(Instant.now())) {
                throw new Exception("Public key has expired!");
            }
            if (!keyData.verifySessionId(systemValidator, sessionId)) {
                throw new Exception("Invalid public key!");
            }
        }
    }

    private static byte[] buildMessageHashFromSignature(final UUID sessionId)
    {
        final byte[] sessionIdPayload = new byte[16];
        ByteBuffer.wrap(sessionIdPayload).order(ByteOrder.BIG_ENDIAN).putLong(sessionId.getMostSignificantBits()).putLong(sessionId.getLeastSignificantBits());

        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(sessionIdPayload);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException("Failed to get SHA-256 message digest", e);
        }
    }

    private static String getSignedSessionId(final UUID sessionId, final Signer signer) {
        byte[] messageHash = buildMessageHashFromSignature(sessionId);

        final byte[] signedPayload = signer.sign(messageHash);
        return Base64.getEncoder().encodeToString(signedPayload);
    }

    private static boolean validateSignedSessionId(final UUID sessionId, final SignatureValidator publicKeySignature, final byte[] encryptedSessionHashPayload) {
        return publicKeySignature.validate(buildMessageHashFromSignature(sessionId), encryptedSessionHashPayload);
    }

    @Override
    public void onClientConnectionCreation(final URLConnection connection)
    {
        if (signingHandler == null || sessionId.compareTo(DEFAULT_NILL_UUID) == 0) {
            LOGGER.warn("No signing handler is available for the current session (Missing keypair). Stuff might not work since we can not sign the requests!");
            return;
        }

        connection.setRequestProperty("Authentication", "SignedId");
        connection.setRequestProperty("AuthenticationId", sessionId.toString());
        connection.setRequestProperty("AuthenticationSignature", getSignedSessionId(sessionId, signingHandler.signer()));
        connection.setRequestProperty("AuthenticationKey", Base64.getEncoder().encodeToString(Crypt.rsaPublicKeyToString(signingHandler.keyPair().publicKeyData().key()).getBytes(StandardCharsets.UTF_8)));
        connection.setRequestProperty("AuthenticationKeyExpire", Base64.getEncoder().encodeToString(signingHandler.keyPair().publicKeyData().expiresAt().toString().getBytes(StandardCharsets.UTF_8)));
        connection.setRequestProperty("AuthenticationKeySignature", Base64.getEncoder().encodeToString(signingHandler.keyPair().publicKeyData().publicKeySignature()));
    }

    @Override
    public boolean onServerConnectionRequest(final FullHttpRequest msg)
    {
        final var headers = msg.headers();
        final String authentication = headers.get("Authentication");
        if (!Objects.equals(authentication, "SignedId")) {
            LOGGER.warn("External client attempted login without proper authentication header setup!");
            return false;
        }

        final String authenticationId = headers.get("AuthenticationId");
        if (authenticationId == null)
        {
            LOGGER.warn("External client attempted login without session id!");
            return false;
        }
        final UUID sessionId;
        try {
            sessionId = UUID.fromString(authenticationId);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("External client attempted login with invalid session id format: " + authenticationId);
            return false;
        }

        final String authenticationSignature = headers.get("AuthenticationSignature");
        if (authenticationSignature == null) {
            LOGGER.warn("External client attempted login without signature!");
            return false;
        }
        final byte[] encryptedSessionHashPayload;
        try {
            encryptedSessionHashPayload = Base64.getDecoder().decode(authenticationSignature);
        } catch (Throwable throwable) {
            LOGGER.warn("External client attempted to login with a signature which was not decode-able: " + authenticationSignature);
            return false;
        }

        final String publicKeyString = headers.get("AuthenticationKey");
        if (publicKeyString == null) {
            LOGGER.warn("External client attempted login without public key!");
            return false;
        }
        final String decodedPublicKey;
        try {
            decodedPublicKey = new String(Base64.getDecoder().decode(publicKeyString), StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            LOGGER.warn("External client attempted to login with a public key which was not decode-able: " + publicKeyString);
            return false;
        }
        final PublicKey publicKey;
        try {
            publicKey = Crypt.stringToRsaPublicKey(decodedPublicKey);
        } catch (Throwable throwable) {
            LOGGER.warn("External client attempted to login with a public key which was not in RSA format: " + decodedPublicKey);
            return false;
        }

        final String authenticationExpire = headers.get("AuthenticationKeyExpire");
        if (authenticationExpire == null) {
            LOGGER.warn("External client attempted login without expire information!");
            return false;
        }
        final String decodedAuthenticationExpire;
        try {
            decodedAuthenticationExpire = new String(Base64.getDecoder().decode(authenticationExpire), StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            LOGGER.warn("External client attempted to login with expire information which was not decode-able: " + publicKeyString);
            return false;
        }
        final Instant expire;
        try {
            expire = Instant.parse(decodedAuthenticationExpire);
        } catch (DateTimeParseException e) {
            LOGGER.warn("External client attempted login without a validly formatted expire information: " + authenticationExpire);
            return false;
        }

        final String authenticationKeySignature = headers.get("AuthenticationKeySignature");
        if (authenticationKeySignature == null) {
            LOGGER.warn("External client attempted login without a key signature!");
            return false;
        }
        final byte[] keySignature;
        try {
            keySignature = Base64.getDecoder().decode(authenticationKeySignature);
        } catch (Throwable throwable) {
            LOGGER.warn("External client attempted login with a key signature which was not decode-able: " + authenticationKeySignature);
            return false;
        }

        final PublicKeyData keyData = new PublicKeyData(
                publicKey,
                expire,
                keySignature
        );

        try {
            validatePublicKey(keyData, sessionId, validator);
            if (!validateSignedSessionId(sessionId, keyData.validator(), encryptedSessionHashPayload)) {
                LOGGER.warn("External client attempted login with an invalid signature!");
                return false;
            }
            if (!WhitelistVerificationHelper.getInstance().isAllowed(sessionId)) {
                LOGGER.warn("External client attempted login with a session id which is not on the whitelist!");
                return false;
            }
            return true;
        }
        catch (Exception e)
        {
            LOGGER.warn("External client failed to authenticate.", e);
            return false;
        }
    }

    public record PublicKeyData(PublicKey key, Instant expiresAt, byte[] publicKeySignature) {

        boolean verifySessionId(SignatureValidator validator, UUID sessionId) {
            return validator.validate(this.signedPayload(sessionId), this.publicKeySignature);
        }

        public SignatureValidator validator() {
            return SignatureValidator.from(key(), "SHA256withRSA");
        }

        private byte[] signedPayload(UUID sessionId) {
            byte[] keyPayload = this.key.getEncoded();
            byte[] idWithKeyResult = new byte[24 + keyPayload.length];
            ByteBuffer bytebuffer = ByteBuffer.wrap(idWithKeyResult).order(ByteOrder.BIG_ENDIAN);
            bytebuffer.putLong(sessionId.getMostSignificantBits()).putLong(sessionId.getLeastSignificantBits()).putLong(this.expiresAt.toEpochMilli()).put(keyPayload);
            return idWithKeyResult;
        }
    }

    public record ProfileKeyPair(PrivateKey privateKey, PublicKeyData publicKeyData) {
    }

    private record SigningHandler(ProfileKeyPair keyPair, Signer signer) {

        private SigningHandler(ProfileKeyPair keyPair)
        {
            this(keyPair, Signer.from(keyPair.privateKey(), "SHA256withRSA"));
        }
    }
}
