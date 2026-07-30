package com.igot.scormvalidator.authentication.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoUtilTest {

    @Test
    void verifyRSASignReturnsTrueForValidSignature() throws Exception {
        KeyPair keyPair = generateKeyPair();
        String payload = "hello-world";
        byte[] signature = sign(payload, keyPair.getPrivate());

        boolean result = CryptoUtil.verifyRSASign(payload, signature, keyPair.getPublic(), "SHA256withRSA");

        assertTrue(result, "Valid signature should verify correctly");
    }

    @Test
    void verifyRSASignReturnsFalseForTamperedPayload() throws Exception {
        KeyPair keyPair = generateKeyPair();
        byte[] signature = sign("original-payload", keyPair.getPrivate());

        boolean result = CryptoUtil.verifyRSASign("tampered-payload", signature, keyPair.getPublic(), "SHA256withRSA");

        assertFalse(result, "Tampered payload should fail verification");
    }

    @Test
    void verifyRSASignReturnsFalseForMismatchedKey() throws Exception {
        KeyPair signingKeyPair = generateKeyPair();
        KeyPair otherKeyPair = generateKeyPair();
        byte[] signature = sign("hello", signingKeyPair.getPrivate());

        boolean result = CryptoUtil.verifyRSASign("hello", signature, otherKeyPair.getPublic(), "SHA256withRSA");

        assertFalse(result, "Signature verified with a mismatched key should fail");
    }

    @Test
    void verifyRSASignReturnsFalseForUnknownAlgorithm() throws Exception {
        KeyPair keyPair = generateKeyPair();
        byte[] signature = sign("hello", keyPair.getPrivate());

        boolean result = CryptoUtil.verifyRSASign("hello", signature, keyPair.getPublic(), "NoSuchAlgorithm");

        assertFalse(result, "Invalid algorithm should fail verification");
    }

    @Test
    void verifyRSASignReturnsFalseForNullKey() throws Exception {
        KeyPair keyPair = generateKeyPair();
        byte[] signature = sign("hello", keyPair.getPrivate());

        boolean result = CryptoUtil.verifyRSASign("hello", signature, null, "SHA256withRSA");

        assertFalse(result, "Null key should fail verification");
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private byte[] sign(String payload, PrivateKey key) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update(payload.getBytes(StandardCharsets.US_ASCII));
        return signature.sign();
    }
}
