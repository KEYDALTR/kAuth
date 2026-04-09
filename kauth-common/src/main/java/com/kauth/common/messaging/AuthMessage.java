package com.kauth.common.messaging;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public record AuthMessage(byte type, String playerUuid, String playerName, long timestamp) {

    public static final int MAX_SIZE = 256;
    public static final long MAX_AGE_MS = 30_000L;

    public AuthMessage {
        if (playerUuid == null) throw new IllegalArgumentException("playerUuid null olamaz");
        if (playerName == null) throw new IllegalArgumentException("playerName null olamaz");
        if (playerUuid.isEmpty()) throw new IllegalArgumentException("playerUuid boş olamaz");
        if (playerName.isEmpty()) throw new IllegalArgumentException("playerName boş olamaz");
    }

    public AuthMessage(byte type, String playerUuid, String playerName) {
        this(type, playerUuid, playerName, System.currentTimeMillis());
    }

    public byte[] toBytes(byte[] secret) {
        if (secret == null) throw new IllegalArgumentException("Secret null olamaz");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeByte(type);
            dos.writeUTF(playerUuid);
            dos.writeUTF(playerName);
            dos.writeLong(timestamp);
            byte[] payload = baos.toByteArray();
            byte[] signature = hmac(secret, payload);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(payload);
            out.write(signature);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Mesaj serileştirme hatası", e);
        }
    }

    public static AuthMessage fromBytes(byte[] data, byte[] secret) throws MessageVerificationException {
        if (data == null) throw new MessageVerificationException("Null mesaj");
        if (data.length > MAX_SIZE) throw new MessageVerificationException("Mesaj çok büyük: " + data.length);
        if (data.length < 32 + 3) throw new MessageVerificationException("Mesaj çok küçük");
        if (secret == null) throw new MessageVerificationException("Secret null");
        if (secret.length == 0) throw new MessageVerificationException("Secret boş");

        int sigStart = data.length - 32;
        byte[] payload = Arrays.copyOfRange(data, 0, sigStart);
        byte[] receivedSig = Arrays.copyOfRange(data, sigStart, data.length);
        byte[] expectedSig = hmac(secret, payload);

        if (!MessageDigest.isEqual(receivedSig, expectedSig)) {
            throw new MessageVerificationException("HMAC imza geçersiz");
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(payload);
             DataInputStream dis = new DataInputStream(bais)) {
            byte type = dis.readByte();
            String uuid = dis.readUTF();
            String name = dis.readUTF();
            long ts = dis.readLong();

            long age = System.currentTimeMillis() - ts;
            if (age > MAX_AGE_MS || age < -MAX_AGE_MS) {
                throw new MessageVerificationException("Mesaj zaman aşımı (age=" + age + "ms)");
            }

            return new AuthMessage(type, uuid, name, ts);
        } catch (IOException e) {
            throw new MessageVerificationException("Parse hatası: " + e.getMessage(), e);
        }
    }

    private static byte[] hmac(byte[] secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC hesaplama hatası", e);
        }
    }

    public static byte[] parseSecret(String secretString) {
        if (secretString == null || secretString.isEmpty()) {
            throw new IllegalArgumentException("Velocity secret boş olamaz! config.yml'de velocity.secret ayarlayın.");
        }
        return secretString.getBytes(StandardCharsets.UTF_8);
    }

    public static class MessageVerificationException extends Exception {
        public MessageVerificationException(String msg) { super(msg); }
        public MessageVerificationException(String msg, Throwable cause) { super(msg, cause); }
    }
}
