package com.kauth.common.messaging;

public final class MessageConstants {

    public static final String CHANNEL = "kauth:sync";

    public static final byte LOGIN = 0x01;
    public static final byte LOGOUT = 0x02;
    public static final byte FORCE_LOGOUT = 0x03;

    private MessageConstants() {}
}
