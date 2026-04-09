package com.kauth.common.storage;

import java.io.File;
import java.sql.*;
import java.util.logging.Logger;

public class SQLiteDatabase implements AuthDatabase {

    private final File dbFile;
    private final Logger logger;
    private Connection connection;
    private final Object lock = new Object();

    public SQLiteDatabase(File dbFile, Logger logger) {
        this.dbFile = dbFile;
        this.logger = logger;
    }

    @Override
    public void initialize() {
        connect();
        createTable();
        upgrade();
    }

    private void connect() {
        try {
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA busy_timeout=5000");
            }
            logger.info("SQLite veritabanı bağlantısı kuruldu (WAL mode).");
        } catch (SQLException e) {
            logger.severe("Veritabanı bağlantısı kurulamadı: " + e.getMessage());
            throw new RuntimeException("SQLite connect failed", e);
        }
    }

    private void createTable() {
        synchronized (lock) {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS kauth_users (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  username VARCHAR(64) NOT NULL UNIQUE," +
                    "  uuid VARCHAR(36)," +
                    "  password VARCHAR(255) NOT NULL," +
                    "  ip VARCHAR(45)," +
                    "  register_date BIGINT NOT NULL," +
                    "  last_login BIGINT," +
                    "  logged_in INTEGER DEFAULT 0," +
                    "  email VARCHAR(255) DEFAULT NULL," +
                    "  email_verified INTEGER DEFAULT 0" +
                    ")"
                );
            } catch (SQLException e) {
                logger.severe("Tablo oluşturulamadı: " + e.getMessage());
                throw new RuntimeException("SQLite create table failed", e);
            }
        }
    }

    private void upgrade() {
        addColumnIfNotExists("email", "VARCHAR(255) DEFAULT NULL");
        addColumnIfNotExists("email_verified", "INTEGER DEFAULT 0");
    }

    private void addColumnIfNotExists(String column, String definition) {
        synchronized (lock) {
            try (ResultSet rs = connection.getMetaData().getColumns(null, null, "kauth_users", column)) {
                if (!rs.next()) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.executeUpdate("ALTER TABLE kauth_users ADD COLUMN " + column + " " + definition);
                        logger.info("Sütun eklendi: " + column);
                    }
                }
            } catch (SQLException e) {
                logger.warning("Sütun kontrol/ekleme hatası (" + column + "): " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isRegistered(String username) throws DataAccessException {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new DataAccessException("isRegistered failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean register(String username, String uuid, String hashedPassword, String ip) throws DataAccessException {
        return registerWithEmail(username, uuid, hashedPassword, ip, null);
    }

    @Override
    public boolean registerWithEmail(String username, String uuid, String hashedPassword, String ip, String email) throws DataAccessException {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO kauth_users (username, uuid, password, ip, register_date, email) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, username);
                ps.setString(2, uuid);
                ps.setString(3, hashedPassword);
                ps.setString(4, ip);
                ps.setLong(5, System.currentTimeMillis());
                ps.setString(6, email);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new DataAccessException("register failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public String getHashedPassword(String username) throws DataAccessException {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT password FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("password");
                    return null;
                }
            } catch (SQLException e) {
                throw new DataAccessException("getHashedPassword failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void updateLastLogin(String username, String ip) throws DataAccessException {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE kauth_users SET last_login = ?, ip = ?, logged_in = 1 WHERE LOWER(username) = LOWER(?)")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, ip);
                ps.setString(3, username);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new DataAccessException("updateLastLogin failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void setLoggedIn(String username, boolean loggedIn) throws DataAccessException {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE kauth_users SET logged_in = ? WHERE LOWER(username) = LOWER(?)")) {
                ps.setInt(1, loggedIn ? 1 : 0);
                ps.setString(2, username);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new DataAccessException("setLoggedIn failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean deleteUser(String username) throws DataAccessException {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
                ps.setString(1, username);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new DataAccessException("deleteUser failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public int getAccountCountByIp(String ip) throws DataAccessException {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM kauth_users WHERE ip = ?")) {
                ps.setString(1, ip);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                    return 0;
                }
            } catch (SQLException e) {
                throw new DataAccessException("getAccountCountByIp failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public String getLastLoginInfo(String username) throws DataAccessException {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT ip, last_login FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    long lastLogin = rs.getLong("last_login");
                    if (rs.wasNull() || lastLogin == 0) return null;
                    String ip = rs.getString("ip");
                    if (ip == null) ip = "bilinmiyor";
                    long ago = (System.currentTimeMillis() - lastLogin) / 1000;
                    String timeStr;
                    if (ago < 60) timeStr = ago + " saniye önce";
                    else if (ago < 3600) timeStr = (ago / 60) + " dakika önce";
                    else if (ago < 86400) timeStr = (ago / 3600) + " saat önce";
                    else timeStr = (ago / 86400) + " gün önce";
                    return timeStr + " | IP: " + ip;
                }
            } catch (SQLException e) {
                throw new DataAccessException("getLastLoginInfo failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean changePassword(String username, String newHashedPassword) throws DataAccessException {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE kauth_users SET password = ? WHERE LOWER(username) = LOWER(?)")) {
                ps.setString(1, newHashedPassword);
                ps.setString(2, username);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new DataAccessException("changePassword failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public String getEmail(String username) throws DataAccessException {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT email FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("email");
                    return null;
                }
            } catch (SQLException e) {
                throw new DataAccessException("getEmail failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean setEmail(String username, String email) throws DataAccessException {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE kauth_users SET email = ? WHERE LOWER(username) = LOWER(?)")) {
                ps.setString(1, email);
                ps.setString(2, username);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new DataAccessException("setEmail failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean setEmailVerified(String username, boolean verified) throws DataAccessException {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE kauth_users SET email_verified = ? WHERE LOWER(username) = LOWER(?)")) {
                ps.setInt(1, verified ? 1 : 0);
                ps.setString(2, username);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new DataAccessException("setEmailVerified failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean isEmailVerified(String username) throws DataAccessException {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT email_verified FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("email_verified") == 1;
                    return false;
                }
            } catch (SQLException e) {
                throw new DataAccessException("isEmailVerified failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean isEmailUsed(String email) throws DataAccessException {
        if (email == null) return false;
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM kauth_users WHERE LOWER(email) = LOWER(?)")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new DataAccessException("isEmailUsed failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public String getUsernameByEmail(String email) throws DataAccessException {
        if (email == null) return null;
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT username FROM kauth_users WHERE LOWER(email) = LOWER(?)")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("username");
                    return null;
                }
            } catch (SQLException e) {
                throw new DataAccessException("getUsernameByEmail failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                if (connection != null && !connection.isClosed()) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.executeUpdate("UPDATE kauth_users SET logged_in = 0");
                    }
                    connection.close();
                    logger.info("Veritabanı bağlantısı kapatıldı.");
                }
            } catch (SQLException e) {
                logger.warning("Veritabanı kapatma hatası: " + e.getMessage());
            }
        }
    }
}
