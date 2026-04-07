# kAuth

KEYDAL Network tarafından geliştirilen, Minecraft sunucuları için modern ve güvenli giriş/kayıt sistemi.

## Özellikler

- **Dialog GUI** - Paper 1.21.5+ istemcilerde görsel giriş ekranı
- **Chat Fallback** - Eski istemcilerde otomatik chat tabanlı giriş
- **PBKDF2-SHA256 Şifreleme** - 65536 iterasyonluk endüstri standardı şifreleme
- **Timing-safe Doğrulama** - Side-channel saldırılarına karşı koruma
- **IP Brute-force Koruması** - IP başına deneme limiti ve otomatik engelleme
- **Hesap Limiti** - IP başına maksimum hesap sayısı sınırlaması
- **Zayıf Şifre Engelleme** - Yaygın şifreleri ve oyuncu adını şifre olarak engeller
- **Oturum Yönetimi** - IP bazlı oturum önbelleği ile tekrar giriş gerektirmez
- **Kural Onay Sistemi** - İlk kayıtta sunucu kuralları gösterilir
- **Son Giriş Bilgisi** - Giriş yapıldığında son giriş IP ve zamanı gösterilir
- **Detaylı Loglama** - Tüm giriş, kayıt, başarısız deneme ve çıkış işlemleri loglanır
- **ViaVersion Desteği** - ViaVersion + ViaBackwards ile tüm istemci sürümleri desteklenir
- **Efekt Sistemi** - Title, subtitle, actionbar, ses ve parçacık efektleri

## Gereksinimler

- Paper 1.20+ (Dialog GUI için 1.21.5+ sunucu önerilir)
- Java 21+
- ViaVersion + ViaBackwards (opsiyonel, eski istemci desteği için)

## Kurulum

1. `kAuth.jar` dosyasını `plugins/` klasörüne atın
2. Sunucuyu başlatın
3. `plugins/kAuth/config.yml` dosyasından ayarları düzenleyin
4. `/kauth reload` komutu ile yeniden yükleyin

## Komutlar

| Komut | Açıklama | Yetki |
|---|---|---|
| `/giris <şifre>` | Hesaba giriş yap | `kauth.use` |
| `/kayit <şifre> <tekrar>` | Yeni hesap oluştur | `kauth.use` |
| `/cikis` | Hesaptan çıkış yap | `kauth.use` |
| `/sifredegistir <eski> <yeni> <tekrar>` | Şifre değiştir | `kauth.use` |
| `/kauth reload` | Config yeniden yükle | `kauth.admin` |
| `/kauth kayitsil <oyuncu>` | Hesap sil | `kauth.admin` |
| `/kauth sifredegistir <oyuncu> <yeni>` | Şifre değiştir (admin) | `kauth.admin` |

## Sürüm Uyumluluğu

| İstemci Sürümü | Giriş Modu |
|---|---|
| 1.21.5+ | Dialog GUI |
| 1.21.0 - 1.21.4 | Chat tabanlı |
| 1.20.x | Chat tabanlı |

> Eski istemcilerin bağlanabilmesi için ViaVersion + ViaBackwards gereklidir.

## Güvenlik

- **PBKDF2WithHmacSHA256** - 65536 iterasyon, 256-bit anahtar, 32-byte rastgele salt
- **Timing-safe karşılaştırma** - Hash doğrulamasında sabit zamanlı karşılaştırma
- **IP brute-force koruması** - Yapılandırılabilir deneme limiti ve engelleme süresi
- **IP bazlı hesap limiti** - Aynı IP'den açılabilecek hesap sayısı sınırlanabilir
- **Zayıf şifre kontrolü** - 123456, qwerty, oyuncu adı gibi zayıf şifreler engellenir
- **Thread-safe** - ConcurrentHashMap ile eşzamanlı erişim güvenliği
- **Geriye uyumluluk** - Eski SHA-256 hash formatı otomatik tanınır

## Derleme

```bash
javac -cp paper-api.jar -d build --release 21 -encoding UTF-8 $(find src -name "*.java")
cp src/main/resources/*.yml build/
cd build && jar cf kAuth.jar .
```

## Yapılandırma

Tüm ayarlar `config.yml` dosyasından düzenlenebilir:

- Giriş/kayıt mesajları ve efektleri
- Şifre gereksinimleri (min/max uzunluk)
- Oturum süresi ve IP kontrolü
- Brute-force koruması ayarları
- Sunucu kuralları metni
- Loglama formatları

## Lisans

MIT License - Detaylar için [LICENSE](LICENSE) dosyasına bakın.

## Geliştirici

**Egemen KEYDAL** - [KEYDAL Network](https://github.com/KEYDAL-Network)
