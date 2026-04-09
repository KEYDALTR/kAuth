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

    @Test
    @DisplayName("Kayıtlı olmayan kullanıcı isRegistered=false döner")
    void isRegisteredReturnsFalseForUnknown() throws Exception {
        assertFalse(db.isRegistered("NonExistent"));
    }

    @Test
    @DisplayName("Kayıt başarılı olur ve isRegistered=true döner")
    void registerAndCheckRegistered() throws Exception {
        String uuid = UUID.randomUUID().toString();
        assertTrue(db.register("TestPlayer", uuid, "hashedPw", "127.0.0.1"));
        assertTrue(db.isRegistered("TestPlayer"));
    }

    @Test
    @DisplayName("Kullanıcı adı case-insensitive kontrol edilir")
    void isRegisteredCaseInsensitive() throws Exception {
        db.register("TestPlayer", UUID.randomUUID().toString(), "hash", "127.0.0.1");
        assertTrue(db.isRegistered("testplayer"));
        assertTrue(db.isRegistered("TESTPLAYER"));
        assertTrue(db.isRegistered("TestPlayer"));
    }

    @Test
    @DisplayName("Aynı kullanıcı adıyla çift kayıt DataAccessException fırlatır")
    void duplicateRegisterThrows() throws Exception {
        db.register("Player1", UUID.randomUUID().toString(), "hash1", "127.0.0.1");
        assertThrows(DataAccessException.class,
                () -> db.register("Player1", UUID.randomUUID().toString(), "hash2", "127.0.0.1"));
    }

    @Test
    @DisplayName("E-posta ile kayıt başarılı olur")
    void registerWithEmail() throws Exception {
        assertTrue(db.registerWithEmail("EmailUser", UUID.randomUUID().toString(),
                "hash", "127.0.0.1", "test@example.com"));
        assertEquals("test@example.com", db.getEmail("EmailUser"));
    }

    @Test
    @DisplayName("E-posta olmadan kayıt (null email) başarılı olur")
    void registerWithNullEmail() throws Exception {
        assertTrue(db.registerWithEmail("NoEmail", UUID.randomUUID().toString(),
                "hash", "127.0.0.1", null));
        assertNull(db.getEmail("NoEmail"));
    }

    @Test
    @DisplayName("Şifre hash'i alınır")
    void getHashedPassword() throws Exception {
        db.register("PwUser", UUID.randomUUID().toString(), "myHash123", "127.0.0.1");
        assertEquals("myHash123", db.getHashedPassword("PwUser"));
    }

    @Test
    @DisplayName("Kayıtlı olmayan kullanıcı için null döner")
    void getHashedPasswordReturnsNullForUnknown() throws Exception {
        assertNull(db.getHashedPassword("Ghost"));
    }

    @Test
    @DisplayName("Şifre değiştirme başarılı olur")
    void changePassword() throws Exception {
        db.register("ChgPw", UUID.randomUUID().toString(), "oldHash", "127.0.0.1");
        assertTrue(db.changePassword("ChgPw", "newHash"));
        assertEquals("newHash", db.getHashedPassword("ChgPw"));
    }

    @Test
    @DisplayName("Kayıtlı olmayan kullanıcının şifresini değiştirme false döner")
    void changePasswordUnknownUser() throws Exception {
        assertFalse(db.changePassword("Nobody", "newHash"));
    }

    @Test
    @DisplayName("Kullanıcı silme başarılı olur")
    void deleteUser() throws Exception {
        db.register("ToDelete", UUID.randomUUID().toString(), "hash", "127.0.0.1");
        assertTrue(db.deleteUser("ToDelete"));
        assertFalse(db.isRegistered("ToDelete"));
    }

    @Test
    @DisplayName("Kayıtlı olmayan kullanıcıyı silme false döner")
    void deleteNonExistent() throws Exception {
        assertFalse(db.deleteUser("Nobody"));
    }

    @Test
    @DisplayName("Son giriş güncellenir")
    void updateLastLogin() throws Exception {
        db.register("LoginUser", UUID.randomUUID().toString(), "hash", "127.0.0.1");
        db.updateLastLogin("LoginUser", "192.168.1.1");
        String info = db.getLastLoginInfo("LoginUser");
        assertNotNull(info);
        assertTrue(info.contains("192.168.1.1"));
    }

    @Test
    @DisplayName("Giriş durumu set edilir")
    void setLoggedIn() throws Exception {
        db.register("StatusUser", UUID.randomUUID().toString(), "hash", "127.0.0.1");
        db.setLoggedIn("StatusUser", true);
    }

    @Test
    @DisplayName("IP bazlı hesap sayısı doğru döner")
    void getAccountCountByIp() throws Exception {
        db.register("User1", UUID.randomUUID().toString(), "hash", "10.0.0.1");
        db.register("User2", UUID.randomUUID().toString(), "hash", "10.0.0.1");
        db.register("User3", UUID.randomUUID().toString(), "hash", "10.0.0.2");

        assertEquals(2, db.getAccountCountByIp("10.0.0.1"));
        assertEquals(1, db.getAccountCountByIp("10.0.0.2"));
        assertEquals(0, db.getAccountCountByIp("10.0.0.99"));
    }

    @Test
    @DisplayName("E-posta güncelleme başarılı olur")
    void setEmail() throws Exception {
        db.register("MailUser", UUID.randomUUID().toString(), "hash", "127.0.0.1");
        assertTrue(db.setEmail("MailUser", "new@example.com"));
        assertEquals("new@example.com", db.getEmail("MailUser"));
    }

    @Test
    @DisplayName("E-posta doğrulama durumu güncellenir")
    void setEmailVerified() throws Exception {
        db.registerWithEmail("VerifyUser", UUID.randomUUID().toString(),
                "hash", "127.0.0.1", "verify@example.com");
        assertFalse(db.isEmailVerified("VerifyUser"));
        assertTrue(db.setEmailVerified("VerifyUser", true));
        assertTrue(db.isEmailVerified("VerifyUser"));
    }

    @Test
    @DisplayName("E-posta kullanım kontrolü çalışır")
    void isEmailUsed() throws Exception {
        db.registerWithEmail("UniqueEmail", UUID.randomUUID().toString(),
                "hash", "127.0.0.1", "unique@example.com");
        assertTrue(db.isEmailUsed("unique@example.com"));
        assertTrue(db.isEmailUsed("UNIQUE@example.com"));
        assertFalse(db.isEmailUsed("other@example.com"));
        assertFalse(db.isEmailUsed(null));
    }

    @Test
    @DisplayName("E-posta ile kullanıcı adı bulunur")
    void getUsernameByEmail() throws Exception {
        db.registerWithEmail("FindMe", UUID.randomUUID().toString(),
                "hash", "127.0.0.1", "findme@example.com");
        assertEquals("FindMe", db.getUsernameByEmail("findme@example.com"));
        assertNull(db.getUsernameByEmail("notexist@example.com"));
        assertNull(db.getUsernameByEmail(null));
    }

    @Test
    @DisplayName("Hiç giriş yapmamış kullanıcı için null döner")
    void getLastLoginInfoNoLogin() throws Exception {
        db.register("NoLogin", UUID.randomUUID().toString(), "hash", "127.0.0.1");
        assertNull(db.getLastLoginInfo("NoLogin"));
    }

    @Test
    @DisplayName("Kayıtlı olmayan kullanıcı için lastLoginInfo null döner")
    void getLastLoginInfoUnknown() throws Exception {
        assertNull(db.getLastLoginInfo("Ghost"));
    }

    @Test
    @DisplayName("initialize() birden fazla kez çağrılabilir (idempotent)")
    void initializeIdempotent() {
        assertDoesNotThrow(() -> {
            db.close();
            db = new SQLiteDatabase(tempDbFile, logger);
            db.initialize();
            db.register("SurviveUser", UUID.randomUUID().toString(), "hash", "127.0.0.1");
            db.close();
            db = new SQLiteDatabase(tempDbFile, logger);
            db.initialize();
            assertTrue(db.isRegistered("SurviveUser"));
        });
    }
}
