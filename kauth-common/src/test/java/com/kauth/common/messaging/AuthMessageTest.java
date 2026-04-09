package com.kauth.common.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class AuthMessageTest {

    private static final byte[] SECRET = "test-secret-key-123".getBytes();
    private static final byte[] WRONG_SECRET = "different-secret".getBytes();

    @Test
    @DisplayName("LOGIN mesajı imzala/doğrula round-trip")
    void loginMessageRoundTrip() throws Exception {
        AuthMessage original = new AuthMessage(MessageConstants.LOGIN,
                "550e8400-e29b-41d4-a716-446655440000", "TestPlayer");
        byte[] bytes = original.toBytes(SECRET);
        AuthMessage restored = AuthMessage.fromBytes(bytes, SECRET);

        assertEquals(MessageConstants.LOGIN, restored.type());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", restored.playerUuid());
        assertEquals("TestPlayer", restored.playerName());
    }

    @Test
    @DisplayName("LOGOUT mesajı round-trip")
    void logoutMessageRoundTrip() throws Exception {
        AuthMessage original = new AuthMessage(MessageConstants.LOGOUT,
                "12345678-1234-1234-1234-123456789abc", "AnotherPlayer");
        byte[] bytes = original.toBytes(SECRET);
        AuthMessage restored = AuthMessage.fromBytes(bytes, SECRET);

        assertEquals(MessageConstants.LOGOUT, restored.type());
        assertEquals("AnotherPlayer", restored.playerName());
    }

    @Test
    @DisplayName("FORCE_LOGOUT mesajı round-trip")
    void forceLogoutMessageRoundTrip() throws Exception {
        AuthMessage original = new AuthMessage(MessageConstants.FORCE_LOGOUT,
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", "AdminTarget");
        byte[] bytes = original.toBytes(SECRET);
        AuthMessage restored = AuthMessage.fromBytes(bytes, SECRET);

        assertEquals(MessageConstants.FORCE_LOGOUT, restored.type());
    }

    @Test
    @DisplayName("Yanlış secret → imza hatası")
    void wrongSecretRejected() {
        AuthMessage original = new AuthMessage(MessageConstants.LOGIN,
                "550e8400-e29b-41d4-a716-446655440000", "TestPlayer");
        byte[] bytes = original.toBytes(SECRET);

        assertThrows(AuthMessage.MessageVerificationException.class,
                () -> AuthMessage.fromBytes(bytes, WRONG_SECRET));
    }

    @Test
    @DisplayName("Bozuk payload → imza hatası (tampering)")
    void tamperedMessageRejected() {
        AuthMessage original = new AuthMessage(MessageConstants.LOGIN,
                "550e8400-e29b-41d4-a716-446655440000", "TestPlayer");
        byte[] bytes = original.toBytes(SECRET);
        // Payload içinde bir byte'ı değiştir
        bytes[5] = (byte) (bytes[5] ^ 0xFF);

        assertThrows(AuthMessage.MessageVerificationException.class,
                () -> AuthMessage.fromBytes(bytes, SECRET));
    }

    @Test
    @DisplayName("Sadece payload (imzasız) → reddedilir")
    void unsignedMessageRejected() {
        // Direkt payload oluştur (imzasız)
        assertThrows(AuthMessage.MessageVerificationException.class,
                () -> AuthMessage.fromBytes(new byte[]{0x01, 0x00, 0x00}, SECRET));
    }

    @Test
    @DisplayName("Çok büyük mesaj → reddedilir (DoS koruması)")
    void oversizeMessageRejected() {
        byte[] huge = new byte[500];
        assertThrows(AuthMessage.MessageVerificationException.class,
                () -> AuthMessage.fromBytes(huge, SECRET));
    }

    @Test
    @DisplayName("Çok küçük mesaj → reddedilir")
    void undersizeMessageRejected() {
        assertThrows(AuthMessage.MessageVerificationException.class,
                () -> AuthMessage.fromBytes(new byte[]{0x01}, SECRET));
    }

    @Test
    @DisplayName("Eski mesaj (replay) → reddedilir")
    void replayAttackRejected() {
        AuthMessage old = new AuthMessage(MessageConstants.LOGIN,
                "550e8400-e29b-41d4-a716-446655440000", "TestPlayer",
                System.currentTimeMillis() - 60_000L); // 60 saniye eski
        byte[] bytes = old.toBytes(SECRET);

        assertThrows(AuthMessage.MessageVerificationException.class,
                () -> AuthMessage.fromBytes(bytes, SECRET));
    }

    @Test
    @DisplayName("Null mesaj → reddedilir")
    void nullMessageRejected() {
        assertThrows(AuthMessage.MessageVerificationException.class,
                () -> AuthMessage.fromBytes(null, SECRET));
    }

    @Test
    @DisplayName("Null secret → reddedilir")
    void nullSecretRejected() {
        AuthMessage msg = new AuthMessage(MessageConstants.LOGIN,
                "550e8400-e29b-41d4-a716-446655440000", "TestPlayer");
        byte[] bytes = msg.toBytes(SECRET);

        assertThrows(AuthMessage.MessageVerificationException.class,
                () -> AuthMessage.fromBytes(bytes, null));
    }

    @Test
    @DisplayName("Boş secret string → parseSecret exception")
    void emptySecretStringRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthMessage.parseSecret(""));
        assertThrows(IllegalArgumentException.class,
                () -> AuthMessage.parseSecret(null));
    }

    @Test
    @DisplayName("Mesaj sabitleri doğru değerlere sahip")
    void messageConstantsValues() {
        assertEquals(0x01, MessageConstants.LOGIN);
        assertEquals(0x02, MessageConstants.LOGOUT);
        assertEquals(0x03, MessageConstants.FORCE_LOGOUT);
        assertEquals("kauth:sync", MessageConstants.CHANNEL);
    }

    @Test
    @DisplayName("Özel karakterli oyuncu adı çalışır")
    void specialCharacterPlayerName() throws Exception {
        AuthMessage msg = new AuthMessage(MessageConstants.LOGIN,
                "550e8400-e29b-41d4-a716-446655440000", "Player_With-Dash123");
        byte[] bytes = msg.toBytes(SECRET);
        AuthMessage restored = AuthMessage.fromBytes(bytes, SECRET);
        assertEquals("Player_With-Dash123", restored.playerName());
    }
}
