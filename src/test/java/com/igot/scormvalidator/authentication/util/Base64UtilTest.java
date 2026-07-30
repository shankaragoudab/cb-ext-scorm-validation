package com.igot.scormvalidator.authentication.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Base64UtilTest {

    @Test
    void testEncodeAndDecodeDefaultMode() {
        String original = "Hello, World!";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = Base64Util.encode(originalBytes, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertEquals(original, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeToStringAndDecode() {
        String original = "Test string with special chars: !@#$%^&*()";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        String encodedString = Base64Util.encodeToString(originalBytes, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encodedString, Base64Util.DEFAULT);
        assertEquals(original, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeWithWebSafeFlag() {
        String original = "This+will/be=encoded~differently";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        String encodedDefault = Base64Util.encodeToString(originalBytes, Base64Util.DEFAULT);
        String encodedWebSafe = Base64Util.encodeToString(originalBytes, Base64Util.URL_SAFE);
        assertNotEquals(encodedDefault, encodedWebSafe);
        byte[] decodedWebSafe = Base64Util.decode(encodedWebSafe, Base64Util.URL_SAFE);
        assertEquals(original, new String(decodedWebSafe, StandardCharsets.UTF_8));
    }

    @Test
    void testDecodeWithWebSafeFlag() {
        String original = "Web-safe_encoded+string/with=chars";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        String encodedWebSafe = Base64Util.encodeToString(originalBytes, Base64Util.URL_SAFE);
        byte[] decodedWebSafe = Base64Util.decode(encodedWebSafe, Base64Util.URL_SAFE);
        assertEquals(original, new String(decodedWebSafe, StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeWithNoPadding() {
        String original = "A";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        String encodedDefault = Base64Util.encodeToString(originalBytes, Base64Util.DEFAULT);
        String encodedNoPadding = Base64Util.encodeToString(originalBytes, Base64Util.NO_PADDING);
        assertTrue(encodedDefault.contains("=="), "Default encoding should have padding");
        assertFalse(encodedNoPadding.contains("="), "No padding encoding should not have padding");
    }

    @Test
    void testEncodeWithNoWrap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("Long string ");
        }
        String longString = sb.toString();
        byte[] longBytes = longString.getBytes(StandardCharsets.UTF_8);
        String encodedDefault = Base64Util.encodeToString(longBytes, Base64Util.DEFAULT);
        String encodedNoWrap = Base64Util.encodeToString(longBytes, Base64Util.NO_WRAP);
        assertTrue(encodedDefault.contains("\n"));
        assertFalse(encodedNoWrap.contains("\n"));
    }

    @Test
    void testEncodeWithCRLF() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("Long string ");
        }
        String longString = sb.toString();
        byte[] longBytes = longString.getBytes(StandardCharsets.UTF_8);
        String encodedDefault = Base64Util.encodeToString(longBytes, Base64Util.DEFAULT);
        String encodedCRLF = Base64Util.encodeToString(longBytes, Base64Util.CRLF);
        assertFalse(encodedDefault.contains("\r\n"));
        assertTrue(encodedCRLF.contains("\r\n"));
    }

    @Test
    void testDecodeInvalidInput() {
        String invalidBase64 = "===="; // Invalid Base64 format
        try {
            byte[] result = Base64Util.decode(invalidBase64, Base64Util.DEFAULT);
            assertNotNull(result);
            assertEquals(0, result.length);
        } catch (IllegalArgumentException e) {
            // also an acceptable outcome for malformed input
        }
    }

    @Test
    void testEncodeDecodeEmptyString() {
        String empty = "";
        byte[] emptyBytes = empty.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = Base64Util.encode(emptyBytes, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertEquals(0, decoded.length);
        assertEquals(empty, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeDecodeBinaryData() {
        byte[] binaryData = new byte[256];
        for (int i = 0; i < 256; i++) {
            binaryData[i] = (byte) i;
        }
        byte[] encoded = Base64Util.encode(binaryData, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertArrayEquals(binaryData, decoded);
    }

    @Test
    void testEncodeWithOffset() {
        byte[] data = "HelloWorld".getBytes(StandardCharsets.UTF_8);
        byte[] encodedFull = Base64Util.encode(data, Base64Util.DEFAULT);
        byte[] encodedPartial = Base64Util.encode(data, 5, 5, Base64Util.DEFAULT);
        assertNotEquals(new String(encodedFull), new String(encodedPartial));
        assertEquals("World", new String(Base64Util.decode(encodedPartial, Base64Util.DEFAULT), StandardCharsets.UTF_8));
    }

    @Test
    void testBasicEncodeDecode() {
        String original = "Hello World";
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);
        String encoded = Base64Util.encodeToString(bytes, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertEquals(original, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeWithOffsetAndLength() {
        byte[] input = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes(StandardCharsets.UTF_8);
        int offset = 5;
        int len = 10;
        String encoded = Base64Util.encodeToString(input, offset, len, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        byte[] expected = Arrays.copyOfRange(input, offset, offset + len);
        assertArrayEquals(expected, decoded);
    }

    @Test
    void testUrlSafeEncoding() {
        byte[] bytesForPlus = {(byte) 0xF8, (byte) 0xBF};  // These encode to "+/"
        String regularEncoded = Base64Util.encodeToString(bytesForPlus, Base64Util.DEFAULT);
        assertTrue(regularEncoded.contains("+"), "Regular encoding should contain +");
        byte[] bytesForSlash = {(byte) 0xBF, (byte) 0xF0};  // These encode to "v/"
        String regularEncodedSlash = Base64Util.encodeToString(bytesForSlash, Base64Util.DEFAULT);
        assertTrue(regularEncodedSlash.contains("/"), "Regular encoding should contain /");
        String urlSafeEncodedPlus = Base64Util.encodeToString(bytesForPlus, Base64Util.URL_SAFE);
        String urlSafeEncodedSlash = Base64Util.encodeToString(bytesForSlash, Base64Util.URL_SAFE);
        assertFalse(urlSafeEncodedPlus.contains("+"), "URL-safe encoding should not contain +");
        assertTrue(urlSafeEncodedPlus.contains("-"), "URL-safe encoding should contain - instead of +");
        assertFalse(urlSafeEncodedSlash.contains("/"), "URL-safe encoding should not contain /");
        assertTrue(urlSafeEncodedSlash.contains("_"), "URL-safe encoding should contain _ instead of /");
        byte[] decodedPlus = Base64Util.decode(urlSafeEncodedPlus, Base64Util.URL_SAFE);
        byte[] decodedSlash = Base64Util.decode(urlSafeEncodedSlash, Base64Util.URL_SAFE);
        assertArrayEquals(bytesForPlus, decodedPlus);
        assertArrayEquals(bytesForSlash, decodedSlash);
    }

    @Test
    void testNoPaddingEncoding() {
        byte[] oneByteData = {65}; // 'A'
        byte[] twoByteData = {65, 66}; // 'AB'
        String encodedWithPadding = Base64Util.encodeToString(oneByteData, Base64Util.DEFAULT);
        assertTrue(encodedWithPadding.contains("="), "Default encoding should have padding");
        String encodedNoPadding = Base64Util.encodeToString(oneByteData, Base64Util.NO_PADDING);
        assertFalse(encodedNoPadding.contains("="), "No padding encoding should not have padding");
        byte[] decodedWithPadding = Base64Util.decode(encodedWithPadding, Base64Util.DEFAULT);
        byte[] decodedNoPadding = Base64Util.decode(encodedNoPadding, Base64Util.NO_PADDING);
        assertArrayEquals(oneByteData, decodedWithPadding);
        assertArrayEquals(oneByteData, decodedNoPadding);
        String encodedTwoBytesNoPadding = Base64Util.encodeToString(twoByteData, Base64Util.NO_PADDING);
        assertFalse(encodedTwoBytesNoPadding.contains("="));
        byte[] decodedTwoBytes = Base64Util.decode(encodedTwoBytesNoPadding, Base64Util.NO_PADDING);
        assertArrayEquals(twoByteData, decodedTwoBytes);
    }

    @Test
    void testNoWrapEncoding() {
        byte[] longData = new byte[200];
        Arrays.fill(longData, (byte) 'A');
        String encodedWithWrap = Base64Util.encodeToString(longData, Base64Util.DEFAULT);
        assertTrue(encodedWithWrap.contains("\n"));
        String encodedNoWrap = Base64Util.encodeToString(longData, Base64Util.NO_WRAP);
        assertFalse(encodedNoWrap.contains("\n"));
        byte[] decodedWithWrap = Base64Util.decode(encodedWithWrap, Base64Util.DEFAULT);
        byte[] decodedNoWrap = Base64Util.decode(encodedNoWrap, Base64Util.NO_WRAP);
        assertArrayEquals(longData, decodedWithWrap);
        assertArrayEquals(longData, decodedNoWrap);
    }

    @Test
    void testCrlfLineBreaks() {
        byte[] longData = new byte[200];
        Arrays.fill(longData, (byte) 'A');
        String encodedWithCrlf = Base64Util.encodeToString(longData, Base64Util.CRLF);
        assertTrue(encodedWithCrlf.contains("\r\n"));
        String encodedWithLf = Base64Util.encodeToString(longData, Base64Util.DEFAULT);
        assertTrue(encodedWithLf.contains("\n"));
        assertFalse(encodedWithLf.contains("\r\n"));
        byte[] decodedWithCrlf = Base64Util.decode(encodedWithCrlf, Base64Util.CRLF);
        byte[] decodedWithLf = Base64Util.decode(encodedWithLf, Base64Util.DEFAULT);
        assertArrayEquals(longData, decodedWithCrlf);
        assertArrayEquals(longData, decodedWithLf);
    }

    @Test
    void testEmptyInput() {
        byte[] emptyArray = new byte[0];
        String encoded = Base64Util.encodeToString(emptyArray, Base64Util.DEFAULT);
        assertEquals("", encoded);
        byte[] decoded = Base64Util.decode("", Base64Util.DEFAULT);
        assertEquals(0, decoded.length);
    }

    @Test
    void testEncodeDecodeWithFlags() {
        String original = "Test with all flags";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        int[] flags = {
                Base64Util.DEFAULT,
                Base64Util.NO_PADDING,
                Base64Util.NO_WRAP,
                Base64Util.CRLF,
                Base64Util.URL_SAFE,
                Base64Util.NO_PADDING | Base64Util.NO_WRAP,
                Base64Util.NO_PADDING | Base64Util.URL_SAFE,
                Base64Util.NO_WRAP | Base64Util.URL_SAFE,
                Base64Util.NO_PADDING | Base64Util.NO_WRAP | Base64Util.URL_SAFE
        };
        for (int flag : flags) {
            String encoded = Base64Util.encodeToString(originalBytes, flag);
            byte[] decoded = Base64Util.decode(encoded, flag);
            assertEquals(original, new String(decoded, StandardCharsets.UTF_8), "Failed with flag: " + flag);
        }
    }

    @Test
    void testDecodeWithWhitespace() {
        String original = "Hello World";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        String encoded = Base64Util.encodeToString(originalBytes, Base64Util.DEFAULT);
        String encodedWithSpaces = encoded.charAt(0) + " " + encoded.substring(1);
        String encodedWithTabs = encoded.charAt(0) + "\t" + encoded.substring(1);
        String encodedWithNewlines = encoded.charAt(0) + "\n" + encoded.substring(1);
        String encodedWithCRLF = encoded.charAt(0) + "\r\n" + encoded.substring(1);
        byte[] decodedWithSpaces = Base64Util.decode(encodedWithSpaces, Base64Util.DEFAULT);
        byte[] decodedWithTabs = Base64Util.decode(encodedWithTabs, Base64Util.DEFAULT);
        byte[] decodedWithNewlines = Base64Util.decode(encodedWithNewlines, Base64Util.DEFAULT);
        byte[] decodedWithCRLF = Base64Util.decode(encodedWithCRLF, Base64Util.DEFAULT);
        assertEquals(original, new String(decodedWithSpaces, StandardCharsets.UTF_8));
        assertEquals(original, new String(decodedWithTabs, StandardCharsets.UTF_8));
        assertEquals(original, new String(decodedWithNewlines, StandardCharsets.UTF_8));
        assertEquals(original, new String(decodedWithCRLF, StandardCharsets.UTF_8));
    }

    @Test
    void testPartialDecoding() {
        String oneByteEncoded = "QQ=="; // 'A' in base64
        byte[] oneByteDecoded = Base64Util.decode(oneByteEncoded, Base64Util.DEFAULT);
        assertEquals("A", new String(oneByteDecoded, StandardCharsets.UTF_8));
        String twoByteEncoded = "QUI="; // 'AB' in base64
        byte[] twoByteDecoded = Base64Util.decode(twoByteEncoded, Base64Util.DEFAULT);
        assertEquals("AB", new String(twoByteDecoded, StandardCharsets.UTF_8));
        String threeByteEncoded = "QUJD"; // 'ABC' in base64
        byte[] threeByteDecoded = Base64Util.decode(threeByteEncoded, Base64Util.DEFAULT);
        assertEquals("ABC", new String(threeByteDecoded, StandardCharsets.UTF_8));
    }

    @Test
    void testBinaryData() {
        byte[] binaryData = new byte[256];
        for (int i = 0; i < 256; i++) {
            binaryData[i] = (byte) i;
        }
        String encoded = Base64Util.encodeToString(binaryData, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertArrayEquals(binaryData, decoded);
    }

    @Test
    void testLargeData() {
        byte[] largeData = new byte[10000];
        new Random(42).nextBytes(largeData);
        String encoded = Base64Util.encodeToString(largeData, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertArrayEquals(largeData, decoded);
    }

    @Test
    void testDecodeWithoutPadding() {
        String encodedWithPadding = "QUE=";
        String encodedWithoutPadding = "QUE";
        byte[] decodedWithPadding = Base64Util.decode(encodedWithPadding, Base64Util.DEFAULT);
        byte[] decodedWithoutPadding = Base64Util.decode(encodedWithoutPadding, Base64Util.DEFAULT);
        assertArrayEquals(decodedWithPadding, decodedWithoutPadding);
    }

    @Test
    void testDecodeByteArray() {
        String original = "Test decode byte array";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = Base64Util.encode(originalBytes, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertEquals(original, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    void testDecodeByteArrayWithOffset() {
        String originalStr = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        byte[] original = originalStr.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = Base64Util.encode(original, Base64Util.DEFAULT);
        byte[] paddedEncoded = new byte[encoded.length + 10];
        System.arraycopy(encoded, 0, paddedEncoded, 5, encoded.length);
        byte[] decoded = Base64Util.decode(paddedEncoded, 5, encoded.length, Base64Util.DEFAULT);
        assertEquals(originalStr, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    void decodeWithUrlSafeFlagsMatchesJdkUrlEncoder() {
        byte[] data = "hello world, scorm validation!".getBytes(StandardCharsets.UTF_8);
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data);

        int jwtFlags = Base64Util.NO_PADDING | Base64Util.NO_WRAP | Base64Util.URL_SAFE;
        byte[] decoded = Base64Util.decode(encoded, jwtFlags);

        assertArrayEquals(data, decoded);
    }
}
