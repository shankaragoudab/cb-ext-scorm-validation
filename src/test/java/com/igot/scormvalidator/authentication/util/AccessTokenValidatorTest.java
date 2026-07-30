package com.igot.scormvalidator.authentication.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.scormvalidator.authentication.model.KeyData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The realm URL AccessTokenValidator checks the "iss" claim against is computed once, at class-load
 * time, from sso.url + sso.realm in src/test/resources/application.properties
 * (https://example.com/auth/ + realms/ + test-realm), so every valid token built below uses that
 * exact issuer.
 */
@ExtendWith(MockitoExtension.class)
class AccessTokenValidatorTest {

    private static final String VALID_ISSUER = "https://example.com/auth/realms/test-realm";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @InjectMocks
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private KeyManager keyManager;

    @Test
    void fetchUserIdFromAccessTokenReturnsNullForNullToken() {
        assertNull(accessTokenValidator.fetchUserIdFromAccessToken(null));
    }

    @Test
    void fetchUserIdFromAccessTokenReturnsNullForMalformedToken() {
        assertNull(accessTokenValidator.fetchUserIdFromAccessToken("not-a-jwt"));
    }

    @Test
    void fetchUserIdFromAccessTokenReturnsNullWhenKeyIdIsUnknown() throws Exception {
        KeyPair keyPair = generateKeyPair();
        lenient().when(keyManager.getPublicKey("unknown-kid")).thenReturn(null);

        String token = buildSignedJwt(keyPair.getPrivate(), "unknown-kid", VALID_ISSUER, "user-abc", futureExpiry());

        assertNull(accessTokenValidator.fetchUserIdFromAccessToken(token));
    }

    @Test
    void fetchUserIdFromAccessTokenReturnsNullWhenSignatureDoesNotMatchRegisteredKey() throws Exception {
        KeyPair signingKeyPair = generateKeyPair();
        KeyPair otherKeyPair = generateKeyPair();
        when(keyManager.getPublicKey("test-kid")).thenReturn(new KeyData("test-kid", otherKeyPair.getPublic()));

        String token = buildSignedJwt(signingKeyPair.getPrivate(), "test-kid", VALID_ISSUER, "user-abc", futureExpiry());

        assertNull(accessTokenValidator.fetchUserIdFromAccessToken(token));
    }

    @Test
    void fetchUserIdFromAccessTokenReturnsNullForWrongIssuer() throws Exception {
        KeyPair keyPair = generateKeyPair();
        when(keyManager.getPublicKey("test-kid")).thenReturn(new KeyData("test-kid", keyPair.getPublic()));

        String token = buildSignedJwt(keyPair.getPrivate(), "test-kid", "https://wrong-issuer/realms/other", "user-abc", futureExpiry());

        assertNull(accessTokenValidator.fetchUserIdFromAccessToken(token));
    }

    @Test
    void fetchUserIdFromAccessTokenReturnsNullForExpiredToken() throws Exception {
        KeyPair keyPair = generateKeyPair();
        when(keyManager.getPublicKey("test-kid")).thenReturn(new KeyData("test-kid", keyPair.getPublic()));

        int expiredExpiry = (int) (Instant.now().getEpochSecond() - 3600);
        String token = buildSignedJwt(keyPair.getPrivate(), "test-kid", VALID_ISSUER, "user-abc", expiredExpiry);

        assertNull(accessTokenValidator.fetchUserIdFromAccessToken(token));
    }

    @Test
    void fetchUserIdFromAccessTokenExtractsSubjectAfterLastColonForValidToken() throws Exception {
        KeyPair keyPair = generateKeyPair();
        when(keyManager.getPublicKey("test-kid")).thenReturn(new KeyData("test-kid", keyPair.getPublic()));

        String token = buildSignedJwt(keyPair.getPrivate(), "test-kid", VALID_ISSUER, "f:org-1:user-abc", futureExpiry());

        assertEquals("user-abc", accessTokenValidator.fetchUserIdFromAccessToken(token));
    }

    @Test
    void fetchUserIdFromAccessTokenReturnsPlainSubjectWhenNoColonPresent() throws Exception {
        KeyPair keyPair = generateKeyPair();
        when(keyManager.getPublicKey("test-kid")).thenReturn(new KeyData("test-kid", keyPair.getPublic()));

        String token = buildSignedJwt(keyPair.getPrivate(), "test-kid", VALID_ISSUER, "user-xyz", futureExpiry());

        assertEquals("user-xyz", accessTokenValidator.fetchUserIdFromAccessToken(token));
    }

    private int futureExpiry() {
        return (int) (Instant.now().getEpochSecond() + 3600);
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String buildSignedJwt(PrivateKey privateKey, String kid, String issuer, String subject, int expiry) throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("kid", kid);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("iss", issuer);
        body.put("sub", subject);
        body.put("exp", expiry);

        String encodedHeader = base64UrlEncode(MAPPER.writeValueAsBytes(header));
        String encodedBody = base64UrlEncode(MAPPER.writeValueAsBytes(body));
        String payload = encodedHeader + "." + encodedBody;

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(payload.getBytes(StandardCharsets.US_ASCII));
        String encodedSignature = base64UrlEncode(signature.sign());

        return payload + "." + encodedSignature;
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
