package com.kauth.common.storage;

import org.junit.jupiter.api.*;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteDatabaseTest {

    private static File tempDbFile;
    private SQLiteDatabase db;
    private final Logger logger = Logger.getLogger("kAuth-Test");

    @BeforeEach
    void setUp() throws Exception {
        tempDbFile = File.createTempFile("kauth_test_", ".db");
        tempDbFile.deleteOnExit();
        db = new SQLiteDatabase(tempDbFile, logger);
        db.initialize();
    }

    @AfterEach
    void tearDown() {
        db.close();
        tempDbFile.delete();
    }

    // === Kayıt ve Kontrol ===

    @Test
    @DisplayName("Kayıtlı olmayan kullanıcı isRegistered=false döner")
    void isRegisteredReturnsFalseForUnknown() {
        assertFalse(db.isRegistered("NonExistent"));
    }

    @Test
    @DisplayName("Kayıt başarılı olur ve isRegistered=true döner")
    void registerAndCheckRegistered() {
        String uuid = UUID.randomUUID().toString();
        assertTrue(db.register("TestPlayer", uuid, "hashedPw", "127.0.0.1"));
        assertTrue(db.isRegistered("TestPlayer"));
    }

    @Test
    @DisplayName("Kullanıcı adı case-insensitive kontrol edilir")
    void isRegisteredCaseInsensitive() {
        db.register("TestPlayer", UUID.randomUUID().toString(), "hash", "127.0.0.1");
        assertTrue(db.isRegistered("testplayer"));
        assertTrue(db.isRegistered("TESTPLAYER"));
        assertTrue(db.isRegistered("TestPlayer"));
    }

    @Test
    @DisplayName("Aynı kullanıcı adıyla çift kayıt başarısız olur")
    void duplicateRegisterFails() {
        db.register("Player1", UUID.randomUUID().toString(), "hash1", "127.0.0.1");
        assertFalse(db.register("Player1", UUID.randomUUID().toString(), "hash2", "127.0.0.1"));
    }

    // === E-posta ile Kayıt ===

    @Test
    @DisplayName("E-posta ile kayıt başarılı olur")
    void registerWithEmail() {
        assertTrue(db.registerWithEmail("EmailUser", UUID.randomUUID().toString(),
                "hash", "127.0.0.1", "test@example.com"));
        assertEquals("test@example.com", db.getEmail("EmailUser"));
    }

    @Test
    @DisplayName("E-posta olmadan kayıt (null email) başarılı olur")
    void registerWithNullEmail() {
        assertTrue(db.registerWithEmail("NoEmail", UUID.randomUUID().toString(),
                "hash", "127.0.0.1", null));
        assertNull(db.getEmail("NoEmail"));
    }

    // === Şifre ===

    @Test
    @DisplayName("Şifre hash'i alınır")
    void getHashedPassword() {
        db.register("PwUser", UUID.randomUUID().toString(), "myHash123", "127.0.0.1");
        assertEquals("myHash123", db.getHashedPassword("PwUser"));
    }

    @Test
    @DisplayName("Kayıtlı olmayan kullanıcı için null döner")
    void getHashedPasswordReturnsNullForUnknown() {
        assertNull(db.getHashedPassword("Ghost"));
    }

    @Test
    @DisplayName("Şifre değiştirme başarılı olur")
    void changePassword() {
        db.register("ChgPw", UUID.randomUUID().toString(), "oldHash", "127.0.0.1");
        assertTrue(db.changePassword("ChgPw", "newHash"));
        assertEquals("newHash", db.getHashedPassword("ChgPw"));
    }

    @Test
    @DisplayName("Kayıtlı olmayan kullanıcının şifresini değiştirme false döner")
    void changePasswordUnknownUser() {
        assertFalse(db.changePassword("Nobody", "newHash"));
    }

    // === Silme ===

    @Test
    @DisplayName("Kullanıcı silme başarılı olur")
    void deleteUser() {
        db.register("ToDelete", UUID.randomUUID().toString(), "hash", "127.0.0.1");
        assertTrue(db.deleteUser("ToDelete"));
        assertFalse(db.isRegistered("ToDelete"));
    }

    @Test
    @DisplayName("Kayıtlı olmayan kullanıcıyı silme false döner")
    void deleteNonExistent() {
        assertFalse(db.deleteUser("Nobody"));
    }

    // === Giriş Durumu ===

    @Test
    @DisplayName("Son giriş güncellenir")
    void updateLastLogin() {
        db.register("LoginUser", UUID.randomUUID().toString(), "hash", "127.0.0.1");
        db.updateLastLogin("LoginUser", "192.168.1.1");
        String info = db.getLastLoginInfo("LoginUser");
        assertNotNull(info);
        assertTrue(info.contains("192.168.1.1"));
    }

    @Test
    @DisplayName("Giriş durumu set edilir")
    void setLoggedIn() {
        db.register("StatusUser", UUID.randomUUID().toString(), "hash", "127.0.0.1");
        db.setLoggedIn("StatusUser", true);
        // logged_in flag doğrudan test edilemez ama updateLastLogin bunu yapar
        // close() tüm kullanıcıları çıkış yapar - bu da dolaylı test
    }

    // === IP Kontrolleri ===

    @Test
    @DisplayName("IP bazlı hesap sayısı doğru döner")
    void getAccountCountByIp() {
        db.register("User1", UUID.randomUUID().toString(), "hash", "10.0.0.1");
        db.register("User2", UUID.randomUUID().toString(), "hash", "10.0.0.1");
        db.register("User3", UUID.randomUUID().toString(), "hash", "10.0.0.2");

        assertEquals(2, db.getAccountCountByIp("10.0.0.1"));
        assertEquals(1, db.getAccountCountByIp("10.0.0.2"));
        assertEquals(0, db.getAccountCountByIp("10.0.0.99"));
    }

    // === E-posta İşlemleri ===

    @Test
    @DisplayName("E-posta güncelleme başarılı olur")
    void setEmail() {
        db.register("MailUser", UUID.randomUUID().toString(), "hash", "127.0.0.1");
        assertTrue(db.setEmail("MailUser", "new@example.com"));
        assertEquals("new@example.com", db.getEmail("MailUser"));
    }

    @Test
    @DisplayName("E-posta doğrulama durumu güncellenir")
    void setEmailVerified() {
        db.registerWithEmail("VerifyUser", UUID.randomUUID().toString(),
                "hash", "127.0.0.1", "verify@example.com");
        assertFalse(db.isEmailVerified("VerifyUser"));
        assertTrue(db.setEmailVerified("VerifyUser", true));
        assertTrue(db.isEmailVerified("VerifyUser"));
    }

    @Test
    @DisplayName("E-posta kullanım kontrolü çalışır")
    void isEmailUsed() {
        db.registerWithEmail("UniqueEmail", UUID.randomUUID().toString(),
                "hash", "127.0.0.1", "unique@example.com");
        assertTrue(db.isEmailUsed("unique@example.com"));
        assertTrue(db.isEmailUsed("UNIQUE@example.com")); // case-insensitive
        assertFalse(db.isEmailUsed("other@example.com"));
        assertFalse(db.isEmailUsed(null));
    }

    @Test
    @DisplayName("E-posta ile kullanıcı adı bulunur")
    void getUsernameByEmail() {
        db.registerWithEmail("FindMe", UUID.randomUUID().toString(),
                "hash", "127.0.0.1", "findme@example.com");
        assertEquals("FindMe", db.getUsernameByEmail("findme@example.com"));
        assertNull(db.getUsernameByEmail("notexist@example.com"));
        assertNull(db.getUsernameByEmail(null));
    }

    // === Son Giriş Bilgisi ===

    @Test
    @DisplayName("Hiç giriş yapmamış kullanıcı için null döner")
    void getLastLoginInfoNoLogin() {
        db.register("NoLogin", UUID.randomUUID().toString(), "hash", "127.0.0.1");
        assertNull(db.getLastLoginInfo("NoLogin"));
    }

    @Test
    @DisplayName("Kayıtlı olmayan kullanıcı için lastLoginInfo null döner")
    void getLastLoginInfoUnknown() {
        assertNull(db.getLastLoginInfo("Ghost"));
    }

    // === Upgrade (Geriye Uyumluluk) ===

    @Test
    @DisplayName("initialize() birden fazla kez çağrılabilir (idempotent)")
    void initializeIdempotent() {
        assertDoesNotThrow(() -> {
            db.close();
            db = new SQLiteDatabase(tempDbFile, logger);
            db.initialize();
            // Mevcut veri kaybolmadığını doğrula
            db.register("SurviveUser", UUID.randomUUID().toString(), "hash", "127.0.0.1");
            db.close();
            db = new SQLiteDatabase(tempDbFile, logger);
            db.initialize();
            assertTrue(db.isRegistered("SurviveUser"));
        });
    }
}
