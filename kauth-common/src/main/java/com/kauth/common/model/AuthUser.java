package com.kauth.common.model;

public record AuthUser(
        int id,
        String username,
        String uuid,
        String password,
        String ip,
        long registerDate,
        long lastLogin,
        boolean loggedIn,
        String email,
        boolean emailVerified
) {}
