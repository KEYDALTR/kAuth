package com.kauth.common.storage;

import java.io.File;
import java.sql.*;
import java.util.logging.Logger;

public class SQLiteDatabase implements AuthDatabase {

    private final File dbFile;
    private final Logger logger;
    private Connection connection;

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
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            logger.info("SQLite veritabanı bağlantısı kuruldu.");
        } catch (SQLException e) {
            logger.severe("Veritabanı bağlantısı kurulamadı: " + e.getMessage());
        }
    }

    private void createTable() {
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
        }
    }

    private void upgrade() {
        // Mevcut tabloya email sütunları ekle (yoksa)
        addColumnIfNotExists("email", "VARCHAR(255) DEFAULT NULL");
        addColumnIfNotExists("email_verified", "INTEGER DEFAULT 0");
    }

    private void addColumnIfNotExists(String column, String definition) {
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

    @Override
    public boolean isRegistered(String username) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
            ps.setString(1, username);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            logger.warning("Kayıt kontrol hatası: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean register(String username, String uuid, String hashedPassword, String ip) {
        return registerWithEmail(username, uuid, hashedPassword, ip, null);
    }

    @Override
    public boolean registerWithEmail(String username, String uuid, String hashedPassword, String ip, String email) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO kauth_users (username, uuid, password, ip, register_date, email) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, uuid);
            ps.setString(3, hashedPassword);
            ps.setString(4, ip);
            ps.setLong(5, System.currentTimeMillis());
            ps.setString(6, email);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.warning("Kayıt hatası: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getHashedPassword(String username) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT password FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("password");
        } catch (SQLException e) {
            logger.warning("Şifre alma hatası: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void updateLastLogin(String username, String ip) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE kauth_users SET last_login = ?, ip = ?, logged_in = 1 WHERE LOWER(username) = LOWER(?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, ip);
            ps.setString(3, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Son giriş güncelleme hatası: " + e.getMessage());
        }
    }

    @Override
    public void setLoggedIn(String username, boolean loggedIn) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE kauth_users SET logged_in = ? WHERE LOWER(username) = LOWER(?)")) {
            ps.setInt(1, loggedIn ? 1 : 0);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Giriş durumu güncelleme hatası: " + e.getMessage());
        }
    }

    @Override
    public boolean deleteUser(String username) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
            ps.setString(1, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Kullanıcı silme hatası: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int getAccountCountByIp(String ip) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM kauth_users WHERE ip = ?")) {
            ps.setString(1, ip);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.warning("IP hesap sayısı hatası: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public String getLastLoginInfo(String username) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT ip, last_login FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String ip = rs.getString("ip");
                long lastLogin = rs.getLong("last_login");
                if (lastLogin == 0) return null;
                long ago = (System.currentTimeMillis() - lastLogin) / 1000;
                String timeStr;
                if (ago < 60) timeStr = ago + " saniye önce";
                else if (ago < 3600) timeStr = (ago / 60) + " dakika önce";
                else if (ago < 86400) timeStr = (ago / 3600) + " saat önce";
                else timeStr = (ago / 86400) + " gün önce";
                return timeStr + " | IP: " + ip;
            }
        } catch (SQLException e) {
            logger.warning("Son giriş bilgisi hatası: " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean changePassword(String username, String newHashedPassword) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE kauth_users SET password = ? WHERE LOWER(username) = LOWER(?)")) {
            ps.setString(1, newHashedPassword);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Şifre değiştirme hatası: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getEmail(String username) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT email FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("email");
        } catch (SQLException e) {
            logger.warning("E-posta alma hatası: " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean setEmail(String username, String email) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE kauth_users SET email = ? WHERE LOWER(username) = LOWER(?)")) {
            ps.setString(1, email);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("E-posta güncelleme hatası: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean setEmailVerified(String username, boolean verified) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE kauth_users SET email_verified = ? WHERE LOWER(username) = LOWER(?)")) {
            ps.setInt(1, verified ? 1 : 0);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("E-posta doğrulama durumu hatası: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isEmailVerified(String username) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT email_verified FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("email_verified") == 1;
        } catch (SQLException e) {
            logger.warning("E-posta doğrulama kontrol hatası: " + e.getMessage());
        }
        return false;
    }

    @Override
    public boolean isEmailUsed(String email) {
        if (email == null) return false;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM kauth_users WHERE LOWER(email) = LOWER(?)")) {
            ps.setString(1, email);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            logger.warning("E-posta kullanım kontrol hatası: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getUsernameByEmail(String email) {
        if (email == null) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT username FROM kauth_users WHERE LOWER(email) = LOWER(?)")) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("username");
        } catch (SQLException e) {
            logger.warning("E-posta ile kullanıcı arama hatası: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void close() {
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
