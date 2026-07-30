package com.igot.scormvalidator.authentication.util;

import com.igot.scormvalidator.authentication.model.KeyData;
import com.igot.scormvalidator.util.Constants;
import com.igot.scormvalidator.util.PropertiesCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class KeyManager {

    private static final Logger logger = LoggerFactory.getLogger(KeyManager.class.getName());

    private final PropertiesCache propertiesCache;
    private final Map<String, KeyData> keyMap = new HashMap<>();

    @PostConstruct
    public void init() {
        String basePath = propertiesCache.getProperty(Constants.ACCESS_TOKEN_PUBLICKEY_BASEPATH);
        try (Stream<Path> walk = Files.walk(Paths.get(basePath))) {
            List<String> result =
                    walk.filter(Files::isRegularFile).map(Path::toString).collect(Collectors.toList());
            result.forEach(file -> {
                try {
                    Path path = Paths.get(file);
                    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                    String content = String.join("", lines);
                    KeyData keyData = new KeyData(path.getFileName().toString(), loadPublicKey(content));
                    keyMap.put(path.getFileName().toString(), keyData);
                } catch (Exception e) {
                    logger.error("KeyManager:init: exception in reading public keys ", e);
                }
            });
        } catch (Exception e) {
            logger.error("KeyManager:init: exception in loading publickeys ", e);
        }
    }

    public KeyData getPublicKey(String keyId) {
        return keyMap.get(keyId);
    }

    /**
     * Loads a public key from a string representation.
     */
    public static PublicKey loadPublicKey(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String publicKey = new String(key.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        publicKey = publicKey.replaceAll("(-+BEGIN PUBLIC KEY-+)", "");
        publicKey = publicKey.replaceAll("(-+END PUBLIC KEY-+)", "");
        publicKey = publicKey.replaceAll("[\\r\\n]+", "");
        byte[] keyBytes = Base64Util.decode(publicKey.getBytes(StandardCharsets.UTF_8), Base64Util.DEFAULT);
        X509EncodedKeySpec x509publicKey = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(x509publicKey);
    }
}
