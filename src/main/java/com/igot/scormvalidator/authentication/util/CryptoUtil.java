package com.igot.scormvalidator.authentication.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;

public class CryptoUtil {
    private static final Charset US_ASCII = StandardCharsets.US_ASCII;
    private static final Logger logger = LoggerFactory.getLogger(CryptoUtil.class.getName());

    private CryptoUtil() {
    }

    /**
     * Verifies an RSA signature using the provided payload, signature, public key, and algorithm.
     */
    public static boolean verifyRSASign(String payLoad, byte[] signature, PublicKey key, String algorithm) {
        Signature sign;
        try {
            sign = Signature.getInstance(algorithm);
            sign.initVerify(key);
            sign.update(payLoad.getBytes(US_ASCII));
            return sign.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            logger.error("An error occurred during RSA signature verification: {}", e.getMessage(), e);
            return false;
        }
    }
}
