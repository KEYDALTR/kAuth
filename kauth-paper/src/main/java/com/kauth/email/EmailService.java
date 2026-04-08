package com.kauth.email;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.bukkit.plugin.Plugin;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class EmailService {

    private final Plugin plugin;
    private Session mailSession;
    private boolean enabled;

    public EmailService(Plugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("email.enabled", false);

        if (enabled) {
            initSession();
        }
    }

    private void initSession() {
        try {
            String host = plugin.getConfig().getString("email.smtp.host", "");
            int port = plugin.getConfig().getInt("email.smtp.port", 587);
            String username = plugin.getConfig().getString("email.smtp.username", "");
            String password = plugin.getConfig().getString("email.smtp.password", "");
            boolean starttls = plugin.getConfig().getBoolean("email.smtp.starttls", true);
            boolean ssl = plugin.getConfig().getBoolean("email.smtp.ssl", false);

            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", String.valueOf(port));
            props.put("mail.smtp.auth", "true");

            if (ssl) {
                props.put("mail.smtp.socketFactory.port", String.valueOf(port));
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.ssl.enable", "true");
            } else if (starttls) {
                props.put("mail.smtp.starttls.enable", "true");
            }

            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");

            mailSession = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            plugin.getLogger().info("SMTP oturumu oluşturuldu: " + host + ":" + port);
        } catch (Exception e) {
            plugin.getLogger().severe("SMTP oturumu oluşturulamadı: " + e.getMessage());
            enabled = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public CompletableFuture<Boolean> sendAsync(String to, String subject, String body) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String from = plugin.getConfig().getString("email.smtp.from", "noreply@example.com");

                MimeMessage message = new MimeMessage(mailSession);
                message.setFrom(new InternetAddress(from));
                message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
                message.setSubject(subject, "UTF-8");
                message.setText(body, "UTF-8");

                Transport.send(message);
                plugin.getLogger().info("[E-posta] Gönderildi: " + to + " - " + subject);
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("[E-posta] Gönderilemedi (" + to + "): " + e.getMessage());
                return false;
            }
        });
    }

    public void sendVerificationCode(String to, String code) {
        if (!enabled) return;
        int expiry = plugin.getConfig().getInt("email.verification.code-expiry-minutes", 10);
        String subject = plugin.getConfig().getString("email.templates.verification-subject", "kAuth - E-Posta Doğrulama");
        String body = plugin.getConfig().getString("email.templates.verification-body",
                "Doğrulama kodunuz: %code%\nBu kod %expiry% dakika geçerlidir.");
        body = body.replace("%code%", code).replace("%expiry%", String.valueOf(expiry));
        sendAsync(to, subject, body);
    }

    public void sendPasswordResetCode(String to, String code) {
        if (!enabled) return;
        int expiry = plugin.getConfig().getInt("email.verification.code-expiry-minutes", 10);
        String subject = plugin.getConfig().getString("email.templates.reset-subject", "kAuth - Şifre Sıfırlama");
        String body = plugin.getConfig().getString("email.templates.reset-body",
                "Şifre sıfırlama kodunuz: %code%\nBu kod %expiry% dakika geçerlidir.");
        body = body.replace("%code%", code).replace("%expiry%", String.valueOf(expiry));
        sendAsync(to, subject, body);
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("email.enabled", false);
        if (enabled) initSession();
    }
}
