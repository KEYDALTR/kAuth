package com.kauth.storage;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public class DatabaseManager {

    private final Plugin plugin;
    private Connection connection;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
        connect();
        createTable();
    }

    private void connect() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "kauth.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            plugin.getLogger().info("SQLite veritabanı bağlantısı kuruldu.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Veritabanı bağlantısı kurulamadı: " + e.getMessage());
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
                "  logged_in INTEGER DEFAULT 0" +
                ")"
            );
        } catch (SQLException e) {
            plugin.getLogger().severe("Tablo oluşturulamadı: " + e.getMessage());
        }
    }

    public boolean isRegistered(String username) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
            ps.setString(1, username);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            plugin.getLogger().warning("Kayıt kontrol hatası: " + e.getMessage());
            return false;
        }
    }

    public boolean register(String username, UUID uuid, String hashedPassword, String ip) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO kauth_users (username, uuid, password, ip, register_date) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, uuid != null ? uuid.toString() : null);
            ps.setString(3, hashedPassword);
            ps.setString(4, ip);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Kayıt hatası: " + e.getMessage());
            return false;
        }
    }

    public String getHashedPassword(String username) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT password FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("password");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Şifre alma hatası: " + e.getMessage());
        }
        return null;
    }

    public void updateLastLogin(String username, String ip) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE kauth_users SET last_login = ?, ip = ?, logged_in = 1 WHERE LOWER(username) = LOWER(?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, ip);
            ps.setString(3, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Son giriş güncelleme hatası: " + e.getMessage());
        }
    }

    public void setLoggedIn(String username, boolean loggedIn) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE kauth_users SET logged_in = ? WHERE LOWER(username) = LOWER(?)")) {
            ps.setInt(1, loggedIn ? 1 : 0);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Giriş durumu güncelleme hatası: " + e.getMessage());
        }
    }

    public boolean deleteUser(String username) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM kauth_users WHERE LOWER(username) = LOWER(?)")) {
            ps.setString(1, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("Kullanıcı silme hatası: " + e.getMessage());
            return false;
        }
    }

    public int getAccountCountByIp(String ip) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM kauth_users WHERE ip = ?")) {
            ps.setString(1, ip);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("IP hesap sayısı hatası: " + e.getMessage());
        }
        return 0;
    }

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
            plugin.getLogger().warning("Son giriş bilgisi hatası: " + e.getMessage());
        }
        return null;
    }

    public boolean changePassword(String username, String newHashedPassword) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE kauth_users SET password = ? WHERE LOWER(username) = LOWER(?)")) {
            ps.setString(1, newHashedPassword);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("Şifre değiştirme hatası: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                // Tüm oyuncuları çıkış yaptır
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("UPDATE kauth_users SET logged_in = 0");
                }
                connection.close();
                plugin.getLogger().info("Veritabanı bağlantısı kapatıldı.");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Veritabanı kapatma hatası: " + e.getMessage());
        }
    }
}
