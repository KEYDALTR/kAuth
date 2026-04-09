package com.kauth.common.storage;

public interface AuthDatabase {

    void initialize();

    void close();

    boolean isRegistered(String username) throws DataAccessException;

    boolean register(String username, String uuid, String hashedPassword, String ip) throws DataAccessException;

    boolean registerWithEmail(String username, String uuid, String hashedPassword, String ip, String email) throws DataAccessException;

    /** @return stored hash or null if user not registered */
    String getHashedPassword(String username) throws DataAccessException;

    void updateLastLogin(String username, String ip) throws DataAccessException;

    void setLoggedIn(String username, boolean loggedIn) throws DataAccessException;

    boolean deleteUser(String username) throws DataAccessException;

    int getAccountCountByIp(String ip) throws DataAccessException;

    /** @return formatted last login info string or null */
    String getLastLoginInfo(String username) throws DataAccessException;

    boolean changePassword(String username, String newHashedPassword) throws DataAccessException;

    // Email methods
    String getEmail(String username) throws DataAccessException;

    boolean setEmail(String username, String email) throws DataAccessException;

    boolean setEmailVerified(String username, boolean verified) throws DataAccessException;

    boolean isEmailVerified(String username) throws DataAccessException;

    boolean isEmailUsed(String email) throws DataAccessException;

    String getUsernameByEmail(String email) throws DataAccessException;
}
