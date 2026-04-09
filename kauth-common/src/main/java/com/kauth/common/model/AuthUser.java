package com.kauth.common.model;

public record AuthUser(
        String username,
        String uuid,
        String hashedPassword,
        String ip,
        long registerDate,
        long lastLogin,
        boolean loggedIn,
        String email,
        boolean emailVerified
) {}
