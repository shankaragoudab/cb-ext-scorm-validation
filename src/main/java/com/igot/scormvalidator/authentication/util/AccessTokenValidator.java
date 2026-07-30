package com.igot.scormvalidator.authentication.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.scormvalidator.util.Constants;
import com.igot.scormvalidator.util.PropertiesCache;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.common.util.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AccessTokenValidator {

    private final KeyManager keyManager;

    // Base64Util flags: NO_PADDING(1) | NO_WRAP(2) | URL_SAFE(8) — JWT segments are base64url-encoded, unpadded.
    private static final int JWT_BASE64_FLAGS = Base64Util.NO_PADDING | Base64Util.NO_WRAP | Base64Util.URL_SAFE;

    private static final Logger logger = LoggerFactory.getLogger(AccessTokenValidator.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final PropertiesCache cache = PropertiesCache.getInstance();
    private static final String REALM_URL = cache.getProperty(Constants.SSO_URL) + "realms/" + cache.getProperty(Constants.SSO_REALM);

    /**
     * Validates the provided JWT token.
     */
    private Map<String, Object> validateToken(String token) {
        try {
            String[] tokenElements = token.split("\\.");
            if (tokenElements.length < 3) {
                throw new IllegalArgumentException("Invalid token format");
            }
            String header = tokenElements[0];
            String body = tokenElements[1];
            String signature = tokenElements[2];
            String payload = header + Constants.DOT_SEPARATOR + body;
            Map<String, Object> headerData = mapper.readValue(new String(decodeFromBase64(header)), new TypeReference<Map<String, Object>>() {
            });
            String keyId = headerData.get("kid").toString();
            boolean isValid = CryptoUtil.verifyRSASign(payload, decodeFromBase64(signature), keyManager.getPublicKey(keyId).getPublicKey(), Constants.SHA_256_WITH_RSA);
            if (isValid) {
                Map<String, Object> tokenBody = mapper.readValue(new String(decodeFromBase64(body)), new TypeReference<Map<String, Object>>() {
                });
                if (isExpired((Integer) tokenBody.get("exp"))) {
                    logger.error("Token expired: {}", token);
                    return Collections.emptyMap();
                }
                return tokenBody;
            }
        } catch (IOException | IllegalArgumentException e) {
            logger.error("Error validating token: {}", e.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error validating token: {}", ex.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * Verifies the user token and extracts the user ID from it.
     */
    public String verifyUserToken(String token) {
        String userId = Constants.UNAUTHORIZED;
        try {
            Map<String, Object> payload = validateToken(token);
            if (!payload.isEmpty() && checkIss((String) payload.get("iss"))) {
                userId = (String) payload.get(Constants.SUB);
                if (StringUtils.isNotBlank(userId)) {
                    userId = userId.substring(userId.lastIndexOf(":") + 1);
                }
            }
        } catch (Exception ex) {
            logger.error("Exception in verifyUserAccessToken: verify ", ex);
        }
        return userId;
    }

    private boolean checkIss(String iss) {
        if (StringUtils.isBlank(REALM_URL) || !REALM_URL.equalsIgnoreCase(iss)) {
            logger.warn("Issuer does not match the expected realm URL. Issuer: {}, Expected: {}", iss, REALM_URL);
            return false;
        }
        logger.info("Issuer validation successful. Issuer: {}", iss);
        return true;
    }

    private boolean isExpired(Integer expiration) {
        return (Time.currentTime() > expiration);
    }

    private byte[] decodeFromBase64(String data) {
        return Base64Util.decode(data, JWT_BASE64_FLAGS);
    }

    /**
     * Fetches the user ID from the provided access token.
     */
    public String fetchUserIdFromAccessToken(String accessToken) {
        String clientAccessTokenId = null;
        if (accessToken != null) {
            try {
                clientAccessTokenId = verifyUserToken(accessToken);
                if (Constants.UNAUTHORIZED.equalsIgnoreCase(clientAccessTokenId)) {
                    clientAccessTokenId = null;
                }
            } catch (Exception ex) {
                String errMsg = "Exception occurred while fetching the userid from the access token. Exception: " + ex.getMessage();
                logger.error(errMsg, ex);
                clientAccessTokenId = null;
            }
        }
        return clientAccessTokenId;
    }
}
