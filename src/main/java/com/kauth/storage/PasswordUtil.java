package com.kauth.storage;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private PasswordUtil() {}

    public static String hash(String password) {
        byte[] salt = new byte[32];
        RANDOM.nextBytes(salt);
        String saltBase64 = Base64.getEncoder().encodeToString(salt);

        byte[] hash = pbkdf2(password.toCharArray(), salt);
        String hashBase64 = Base64.getEncoder().encodeToString(hash);

        return saltBase64 + "$" + ITERATIONS + "$" + hashBase64;
    }

    public static boolean verify(String password, String storedHash) {
        if (storedHash == null || !storedHash.contains("$")) return false;
        String[] parts = storedHash.split("\\$");

        // Eski SHA-256 formatı desteği (geriye uyumluluk)
        if (parts.length == 2) {
            return verifySha256Legacy(password, storedHash);
        }

        if (parts.length != 3) return false;
        try {
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            int iterations = Integer.parseInt(parts[1]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[2]);

            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] actualHash = factory.generateSecret(spec).getEncoded();

            return slowEquals(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    // Timing-safe karşılaştırma (side-channel attack koruması)
    private static boolean slowEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private static byte[] pbkdf2(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 hatası", e);
        }
    }

    // Eski SHA-256 hash desteği - migration için
    private static boolean verifySha256Legacy(String password, String storedHash) {
        String[] parts = storedHash.split("\\$", 2);
        if (parts.length != 2) return false;
        String salt = parts[0];
        String expectedHash = parts[1];
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest((salt + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) hex.append(String.format("%02x", b));
            return expectedHash.equals(hex.toString());
        } catch (Exception e) {
            return false;
        }
    }
}
