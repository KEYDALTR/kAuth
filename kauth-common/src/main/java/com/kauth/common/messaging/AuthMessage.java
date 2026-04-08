package com.kauth.common.messaging;

import java.io.*;

public record AuthMessage(byte type, String playerUuid, String playerName) {

    public byte[] toBytes() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeByte(type);
            dos.writeUTF(playerUuid);
            dos.writeUTF(playerName);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Mesaj serileştirme hatası", e);
        }
    }

    public static AuthMessage fromBytes(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {
            byte type = dis.readByte();
            String uuid = dis.readUTF();
            String name = dis.readUTF();
            return new AuthMessage(type, uuid, name);
        } catch (IOException e) {
            throw new RuntimeException("Mesaj deserileştirme hatası", e);
        }
    }
}
