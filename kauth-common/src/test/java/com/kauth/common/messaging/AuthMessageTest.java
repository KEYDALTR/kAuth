package com.kauth.common.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class AuthMessageTest {

    @Test
    @DisplayName("LOGIN mesajı serialize/deserialize edilir")
    void loginMessageRoundTrip() {
        AuthMessage original = new AuthMessage(MessageConstants.LOGIN,
                "550e8400-e29b-41d4-a716-446655440000", "TestPlayer");
        byte[] bytes = original.toBytes();
        AuthMessage restored = AuthMessage.fromBytes(bytes);

        assertEquals(MessageConstants.LOGIN, restored.type());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", restored.playerUuid());
        assertEquals("TestPlayer", restored.playerName());
    }

    @Test
    @DisplayName("LOGOUT mesajı serialize/deserialize edilir")
    void logoutMessageRoundTrip() {
        AuthMessage original = new AuthMessage(MessageConstants.LOGOUT,
                "12345678-1234-1234-1234-123456789abc", "AnotherPlayer");
        byte[] bytes = original.toBytes();
        AuthMessage restored = AuthMessage.fromBytes(bytes);

        assertEquals(MessageConstants.LOGOUT, restored.type());
        assertEquals("12345678-1234-1234-1234-123456789abc", restored.playerUuid());
        assertEquals("AnotherPlayer", restored.playerName());
    }

    @Test
    @DisplayName("FORCE_LOGOUT mesajı serialize/deserialize edilir")
    void forceLogoutMessageRoundTrip() {
        AuthMessage original = new AuthMessage(MessageConstants.FORCE_LOGOUT,
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", "AdminTarget");
        byte[] bytes = original.toBytes();
        AuthMessage restored = AuthMessage.fromBytes(bytes);

        assertEquals(MessageConstants.FORCE_LOGOUT, restored.type());
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", restored.playerUuid());
        assertEquals("AdminTarget", restored.playerName());
    }

    @Test
    @DisplayName("Özel karakterli oyuncu adı çalışır")
    void specialCharacterPlayerName() {
        AuthMessage original = new AuthMessage(MessageConstants.LOGIN,
                "550e8400-e29b-41d4-a716-446655440000", "Player_With-Dash123");
        byte[] bytes = original.toBytes();
        AuthMessage restored = AuthMessage.fromBytes(bytes);

        assertEquals("Player_With-Dash123", restored.playerName());
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
    @DisplayName("Bozuk veri RuntimeException fırlatır")
    void corruptedDataThrowsException() {
        assertThrows(RuntimeException.class, () -> AuthMessage.fromBytes(new byte[]{0x01}));
        assertThrows(RuntimeException.class, () -> AuthMessage.fromBytes(new byte[]{}));
    }
}
