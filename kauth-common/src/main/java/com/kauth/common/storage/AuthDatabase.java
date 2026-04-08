package com.kauth.common.storage;

public interface AuthDatabase {

    void initialize();

    void close();

    boolean isRegistered(String username);

    boolean register(String username, String uuid, String hashedPassword, String ip);

    boolean registerWithEmail(String username, String uuid, String hashedPassword, String ip, String email);

    String getHashedPassword(String username);

    void updateLastLogin(String username, String ip);

    void setLoggedIn(String username, boolean loggedIn);

    boolean deleteUser(String username);

    int getAccountCountByIp(String ip);

    String getLastLoginInfo(String username);

    boolean changePassword(String username, String newHashedPassword);

    // Email methods
    String getEmail(String username);

    boolean setEmail(String username, String email);

    boolean setEmailVerified(String username, boolean verified);

    boolean isEmailVerified(String username);

    boolean isEmailUsed(String email);

    String getUsernameByEmail(String email);
}
