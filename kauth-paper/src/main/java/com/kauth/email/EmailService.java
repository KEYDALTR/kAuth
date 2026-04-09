package com.kauth.email;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.bukkit.plugin.Plugin;

import java.util.Properties;
import java.util.concurrent.*;
import java.util.logging.Level;

public class EmailService {

    private final Plugin plugin;
    private Session mailSession;
    private boolean enabled;
    private final ExecutorService executor;

    public EmailService(Plugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("email.enabled", false);

        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "kAuth-Email");
            t.setDaemon(true);
            return t;
        });

        if (enabled) initSession();
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
            plugin.getLogger().log(Level.SEVERE, "SMTP oturumu oluşturulamadı", e);
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
                plugin.getLogger().log(Level.WARNING,
                        "[E-posta] Gönderilemedi (" + to + "): " + e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> sendVerificationCode(String to, String code) {
        if (!enabled) return CompletableFuture.completedFuture(false);
        int expiry = plugin.getConfig().getInt("email.verification.code-expiry-minutes", 10);
        String subject = plugin.getConfig().getString("email.templates.verification-subject", "kAuth - E-Posta Doğrulama");
        String body = plugin.getConfig().getString("email.templates.verification-body",
                "Doğrulama kodunuz: %code%\nBu kod %expiry% dakika geçerlidir.");
        body = body.replace("%code%", code).replace("%expiry%", String.valueOf(expiry));
        return sendAsync(to, subject, body);
    }

    public CompletableFuture<Boolean> sendPasswordResetCode(String to, String code) {
        if (!enabled) return CompletableFuture.completedFuture(false);
        int expiry = plugin.getConfig().getInt("email.verification.code-expiry-minutes", 10);
        String subject = plugin.getConfig().getString("email.templates.reset-subject", "kAuth - Şifre Sıfırlama");
        String body = plugin.getConfig().getString("email.templates.reset-body",
                "Şifre sıfırlama kodunuz: %code%\nBu kod %expiry% dakika geçerlidir.");
        body = body.replace("%code%", code).replace("%expiry%", String.valueOf(expiry));
        return sendAsync(to, subject, body);
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("email.enabled", false);
        if (enabled) initSession();
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
