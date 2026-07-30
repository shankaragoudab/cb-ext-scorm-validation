package com.igot.scormvalidator.authentication.util;

import com.igot.scormvalidator.authentication.model.KeyData;
import com.igot.scormvalidator.util.Constants;
import com.igot.scormvalidator.util.PropertiesCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyManagerTest {

    private static final String TEMP_PUBLIC_KEY_FILE = "temp_public_key.pem";

    @InjectMocks
    private KeyManager keyManager;

    @Mock
    private PropertiesCache propertiesCache;

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("keymanager-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Test
    void initLoadsPublicKeysFromConfiguredBasePath() throws Exception {
        String publicKeyContent = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(generateTestKey().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        Path pubKeyFile = tempDir.resolve(TEMP_PUBLIC_KEY_FILE);
        Files.write(pubKeyFile, publicKeyContent.getBytes(StandardCharsets.UTF_8));

        when(propertiesCache.getProperty(Constants.ACCESS_TOKEN_PUBLICKEY_BASEPATH))
                .thenReturn(tempDir.toString());

        keyManager.init();

        KeyData keyData = keyManager.getPublicKey(TEMP_PUBLIC_KEY_FILE);
        assertNotNull(keyData);
        assertEquals(TEMP_PUBLIC_KEY_FILE, keyData.getKeyId());
        assertNotNull(keyData.getPublicKey());
    }

    @Test
    void initDoesNotThrowWhenBasePathIsInvalid() {
        when(propertiesCache.getProperty(Constants.ACCESS_TOKEN_PUBLICKEY_BASEPATH))
                .thenReturn("/no/such/directory/exists");

        keyManager.init();

        assertNull(keyManager.getPublicKey("definitely-not-a-real-kid"));
    }

    @Test
    void loadPublicKeyParsesArmoredPemIntoEquivalentPublicKey() throws Exception {
        PublicKey originalPublicKey = generateTestKey();
        String base64 = Base64.getEncoder().encodeToString(originalPublicKey.getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";

        PublicKey parsed = KeyManager.loadPublicKey(pem);

        assertEquals(originalPublicKey, parsed);
    }

    @Test
    void loadPublicKeyToleratesMissingArmorMarkers() throws Exception {
        PublicKey originalPublicKey = generateTestKey();
        String base64 = Base64.getEncoder().encodeToString(originalPublicKey.getEncoded());

        PublicKey parsed = KeyManager.loadPublicKey(base64);

        assertEquals(originalPublicKey, parsed);
    }

    @Test
    void loadPublicKeyThrowsForInvalidKeyContent() {
        String invalidKey = "-----BEGIN PUBLIC KEY-----\nNotValidBase64Key\n-----END PUBLIC KEY-----";

        assertThrows(Exception.class, () -> KeyManager.loadPublicKey(invalidKey));
    }

    @Test
    void getPublicKeyReturnsNullForUnknownKeyIdOnFreshInstance() {
        assertNull(keyManager.getPublicKey("unknown-kid"));
    }

    private PublicKey generateTestKey() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair().getPublic();
    }
}
