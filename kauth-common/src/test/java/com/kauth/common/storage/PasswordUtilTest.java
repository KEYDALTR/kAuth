package com.kauth.common.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {

    @Test
    @DisplayName("hash() 3 parçalı format üretir: salt$iterations$hash")
    void hashProducesCorrectFormat() {
        String hashed = PasswordUtil.hash("testPassword123");
        assertNotNull(hashed);
        String[] parts = hashed.split("\\$");
        assertEquals(3, parts.length, "Hash formatı salt$iterations$hash olmalı");
        assertEquals("65536", parts[1], "İterasyon sayısı 65536 olmalı");
    }

    @Test
    @DisplayName("Aynı şifre farklı hash üretir (farklı salt)")
    void hashProducesDifferentHashesForSamePassword() {
        String hash1 = PasswordUtil.hash("samePassword");
        String hash2 = PasswordUtil.hash("samePassword");
        assertNotEquals(hash1, hash2, "Salt rastgele olduğu için hashler farklı olmalı");
    }

    @Test
    @DisplayName("verify() doğru şifreyi doğrular")
    void verifyCorrectPassword() {
        String password = "MySecurePass123";
        String hashed = PasswordUtil.hash(password);
        assertTrue(PasswordUtil.verify(password, hashed));
    }

    @Test
    @DisplayName("verify() yanlış şifreyi reddeder")
    void verifyWrongPassword() {
        String hashed = PasswordUtil.hash("correctPassword");
        assertFalse(PasswordUtil.verify("wrongPassword", hashed));
    }

    @Test
    @DisplayName("verify() null hash için false döner")
    void verifyNullHash() {
        assertFalse(PasswordUtil.verify("password", null));
    }

    @Test
    @DisplayName("verify() bozuk hash için false döner")
    void verifyCorruptedHash() {
        assertFalse(PasswordUtil.verify("password", "notAValidHash"));
        assertFalse(PasswordUtil.verify("password", "a$b$c$d"));
        assertFalse(PasswordUtil.verify("password", ""));
    }

    @Test
    @DisplayName("verify() boş şifreyi doğru hash ile doğrular")
    void verifyEmptyPassword() {
        String hashed = PasswordUtil.hash("");
        assertTrue(PasswordUtil.verify("", hashed));
        assertFalse(PasswordUtil.verify("notEmpty", hashed));
    }

    @Test
    @DisplayName("Özel karakterli şifreler çalışır")
    void hashAndVerifySpecialCharacters() {
        String password = "şifre!@#$%^&*()_+中文パスワード";
        String hashed = PasswordUtil.hash(password);
        assertTrue(PasswordUtil.verify(password, hashed));
    }

    @Test
    @DisplayName("Çok uzun şifre çalışır")
    void hashAndVerifyLongPassword() {
        String password = "a".repeat(1000);
        String hashed = PasswordUtil.hash(password);
        assertTrue(PasswordUtil.verify(password, hashed));
    }

    @Test
    @DisplayName("Eski SHA-256 legacy format desteği çalışır")
    void verifySha256Legacy() {
        // SHA-256 legacy format: salt$hex_hash
        // salt + password → SHA-256 → hex
        // Simüle ediyoruz: salt="testsalt", password="legacyPass"
        try {
            String salt = "dGVzdHNhbHQ="; // base64 of "testsalt" -- but legacy uses raw salt string
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(("mysalt" + "legacyPass").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) hex.append(String.format("%02x", b));
            String legacyHash = "mysalt$" + hex.toString();

            assertTrue(PasswordUtil.verify("legacyPass", legacyHash));
            assertFalse(PasswordUtil.verify("wrongPass", legacyHash));
        } catch (Exception e) {
            fail("SHA-256 legacy test başarısız: " + e.getMessage());
        }
    }
}
