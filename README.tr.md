# ByteLore

**Programlama dilleri ve framework'ler için önce-çevrimdışı çalışan bir öğrenme
platformu.** Uçakta okunabilen dersler, gönderilmeden önce gerçekten çalıştırılmış kod
örnekleri, etkileşimli akıl haritaları ve hiçbir makinenin kendi başına yayınlayamadığı
bir blog.

> [🇬🇧 English](README.md) · 🇹🇷 Türkçe

---

## Durum

Sunucu, masaüstü istemcisi ve web sitesi özellik olarak tamam ve üç sürekli entegrasyon
şeridinde de yeşil; masaüstü uygulaması `main`'e her push'ta Windows ve Linux için
paketleniyor.

**Kütüphane yazıldı ama henüz yayınlanmadı.** On yedi izleğin tamamı depoda ve her
derlemede veritabanına yükleniyor; her biri, kendisini yayınlayan migration commit'lenmeden
önce baştan sona okunmayı bekliyor. Bu bir eksiklik değil, tasarımın çalışması —
[İçerik nasıl giriyor](#i̇çerik-nasıl-giriyor) bölümüne bakın.

Henüz bir sürüm çıkılmadı ve barındırılan bir örnek yok.

---

## İçindekiler

- [Nedir](#nedir) · [Kütüphane](#kütüphane) · [İçerik nasıl giriyor](#i̇çerik-nasıl-giriyor)
- [Mimari](#mimari) · [Çevrimdışı modeli](#çevrimdışı-modeli) · [Tasarım değişmezleri](#tasarım-değişmezleri)
- [Başlangıç](#başlangıç) · [Denetimleri çalıştırma](#denetimleri-çalıştırma)
- [Depo düzeni](#depo-düzeni) · [Dokümantasyon](#dokümantasyon)

---

## Nedir

ByteLore, **tek bir Angular kod tabanından üretilen iki istemci** olarak geliyor:

| | |
|---|---|
| **Masaüstü uygulaması** *(birincil)* | Tauri 2. Tek bir dersi, bütün bir modülü ya da tüm bir izleği indir, sonra hiç ağ olmadan çalış. İndirmeler yeniden başlatmadan sonra kaldığı yerden devam eder, her paket tamamlanmış sayılmadan önce SHA-256 ile doğrulanır, ve kütüphaneyi güncellemek yalnızca gerçekten değişeni indirir. |
| **Web sitesi** *(ikincil)* | Aynı arayüzün indirme özellikleri olmayan hâli — içeriğe göz atmak ve blogu okumak için. |

İkisi birbirinin portu değil. Bir `PlatformService` soyutlaması, dersin yerel SQLite
replikasından mı yoksa REST API'den mi geldiğini gizler; bileşenler hangi hedefte
koştuklarını hiç öğrenmez ve iki derleme aynı kaynaktan, derleme zamanında yapılan bir
dosya değişimiyle üretilir.

### Ne yapabilirsin

- Bir izleği **bir yol olarak oku**: üç modül, her birinde üç ders; her ders düzyazı,
  çalıştırılabilir listing'ler ve bir "kendini sına" bölümü taşıyor.
- **Konunun şeklini gör** — izleğin kendi yapısından türetilen etkileşimli bir akıl
  haritası; her yaprak adını verdiği derse bağlanıyor.
- Tek bir derse kadar her şeyi **indir**, kuyruğu izle, duraklat, ve o çalışırken
  okumaya devam et.
- **İlerlemeni koru** — işaretlenen dersler cihazlar arasında senkronlanır, ve
  çevrimdışıyken ya da oturumun süresi dolmuşken de çalışmaya devam eder.
- Zamanlanmış bir hat tarafından derlenen ama **yalnızca bir insan tarafından
  yayınlanan** blogu oku.
- **Dili ve temayı çalışma zamanında değiştir** — İngilizce, Türkçe, Fransızca ve
  Almanca; sayfa yenilenmeden ve açılışta yanlış tema parlaması olmadan.

---

## Kütüphane

On yedi izlek, elli bir modül, yüz elli üç ders ve dört yüz elli sekiz kod listing'i,
artı izlek başına bir akıl haritası.

**Her listing onu yazan makinede derlendi ya da çalıştırıldı ve çıktısı byte byte
kaydedildi.** Bir dilin bu depoda çalıştırılamadığı yerde — React ve Vue kurulu değil
ve kurulamaz — listing bunu kendi ilk yorumunda söylüyor ve kayıtlı çıktı taşımıyor;
öyleymiş gibi yapmıyor.

| # | İzlek | Öğrettiği |
|---:|---|---|
| 1 | The Angular Path | Angular 22 |
| 2 | The Spring Boot Path | Spring Boot 4.1 |
| 3 | The TypeScript Path | TypeScript 5.9 |
| 4 | The Java Path | Java 21 LTS |
| 5 | The PostgreSQL Path | PostgreSQL 16 |
| 6 | The Python Path | Python 3.13 |
| 7 | The Django Path | Django 6.1 |
| 8 | The Rust Path | Rust 1.98, edition 2024 |
| 9 | The Node.js Path | Node.js 22 LTS |
| 10 | The React Path | React 19 |
| 11 | The Vue Path | Vue 3.5 |
| 12 | The Go Path | Go 1.27 |
| 13 | The C# Path | .NET 10 üzerinde C# 14 |
| 14 | The Kotlin Path | Kotlin 2.4.20 |
| 15 | The PHP Path | PHP 8.2 |
| 16 | The Ruby Path | Ruby 3.4.10 |
| 17 | The Modern C++ Path | C++23 |

Her ders öğrettiği sürümü adlandırıyor ve davranışa dair her iddiasını o sürümün resmî
dokümantasyonuna bağlıyor. Ders gövdesinde *latest*, *currently* ve *as of writing*
ifadeleri yasak: bir okuyucu dersin ne zaman yazıldığını bilemez, dolayısıyla ne zaman
yazıldığına bağlı olan bir ders zaten yanlıştır.

---

## İçerik nasıl giriyor

İçerik, bir öğrenme platformunun derleyicisi olmayan parçasıdır. Yanlış şey öğreten bir
ders kusursuz render edilir, kararlı bir hash üretir, ve sistemdeki her mekanizma için
doğru bir dersten ayırt edilemez. Bunu yalnızca onu okuyan bir insan söyleyebilir.

Bu yüzden hat, verimlilik etrafında değil bu olgu etrafında kuruldu.

**Öğretim içeriği sıradan dosyalar olarak yaşıyor.** Ders başına bir markdown gövdesi,
yanında metadata, ve her kod listing'i bir derleyicinin okuyabileceği gerçek bir kaynak
dosyası — mekanik olarak denetlenebilir tek iddiayı, *örnekler çalışıyor*, denetlenebilir
kılan da bu. Bir Flyway migration'ı o ağacı okuyup satırları yazıyor.

**Yazma sınırı atlanmıyor, taşınıyor.** Yükleyici her gövdeyi ve etiketi, yönetim
API'sinin çağırdığı **aynı** statik predicate'lerden geçiriyor: markdown doğrulayıcısı,
düz metin doğrulayıcısı ve kapalı bir dil listesi. API'nin reddedeceği içerik
migration'ı düşürür, migration da build'i. Bir entegrasyon testi her koşumda gönderilen
korpusu kendi veritabanına yüklüyor — yani bu bir söz değil, bir kapı.

**Hiçbir şey kendini yayınlamıyor.** Her izlek yayınlanmamış olarak geliyor ve genel
okuma yolları yalnızca yayınlanmış izlekleri servis ediyor; taze migrate edilmiş bir
veritabanı okuyucuya hiçbir şey göstermiyor. Yayın, izleği okuduktan sonra bir insanın
commit'lediği ayrı bir migration ve sürüm kontrolü bunu o kişiye atfediyor. Aynı kural
blog için de geçerli: zamanlanmış bir çekim yalnızca izin listesindeki kaynaklardan
okuyabilir, her sürüm dizesi ikinci bağımsız bir istekle teyit edilir, ve bir yazı
`DRAFT → PENDING_REVIEW → PUBLISHED` yolunu izler; her geçiş bir denetim kaydına yazılır.

---

## Mimari

Sunucu tek doğruluk kaynağıdır. İçeriği, her varlık için bir SHA-256 özeti ve bir sürüm
numarası taşıyan bir manifest ile birlikte yayınlar. Masaüstü istemcisi yerel bir SQLite
okuma replikası ve kendi indirme kuyruğunu tutar, ve neyi indireceğine yerel sürümleri
manifest ile karşılaştırarak karar verir.

```
server/     Java 21 · Spring Boot 4.1 · PostgreSQL 16 · Flyway · JWT · MapStruct
              içerik modeli: Track → Module → Lesson → CodeExample, artı MindMap
              varlık başına deterministik özet taşıyan manifest uç noktaları
              ilerleme senkronu, çeviri fallback'i, hız sınırlama
              blog hattı: izin listeli çekim, iki adımlı teyit, insan onayı

desktop/    Tauri 2 · Rust 1.98 · rusqlite · reqwest · sha2
              yerel SQLite deposu, gerçek bir durum makinesi olan indirme kuyruğu,
              SHA-256 doğrulaması, HTTP range ile devam, arayüze ilerleme olayları,
              imzalı otomatik güncellemeler

frontend/   Angular 22 (standalone + signals) · Tailwind 4 · shiki · Jest 30
              tek kod tabanı, iki hedef; bir PlatformService soyutlaması verinin
              yerel SQLite'tan mı REST API'den mi geldiğini gizliyor
              çalışma zamanında değişen dört arayüz dili (en, tr, fr, de)
```

**Bilinçli olarak Redis yok, mesaj kuyruğu yok.** İş kuyruğu `FOR UPDATE SKIP LOCKED`
kullanıyor, tam metin arama `tsvector`, önbellek PostgreSQL'de kalıyor. Yerel depo
SQLite. Bu listeye harici bir bağımlılık eklemek bir kolaylık değil, mimari bir karardır.

### Çevrimdışı modeli

1. İstemci bir **manifest** ister: tutabileceği her varlık, sürümü ve özetiyle.
2. Bunu yerel replikasıyla karşılaştırır ve yalnızca farkları kuyruğa alır.
3. Her paket indirilir — önceki bir deneme kesildiyse bir bayt konumundan devam
   ederek — ve **kabul edilmeden önce özete karşı doğrulanır**. Doğru hash'lemeyen bir
   paket silinir ve üç kez yeniden denenir, sonra başarısız işaretlenir. Hiçbir zaman
   okunabilir içeriğe dönüşmez.
4. Okumak ve ilerleme kaydetmek ağsız ve süresi dolmuş bir oturumla da çalışır.
   Bağlantı döndüğünde ilerleme, istemci damgası üzerinden son-yazan-kazanır ile
   uzlaşır; damga, kötü ayarlanmış bir saatin iyi veriyi ezmemesi için kırpılır.

Özetler, UTF-8 ve LF satır sonuna normalize edilmiş içerik üzerinden **servis
katmanında** hesaplanır, asla bir veritabanı trigger'ıyla değil — böylece aynı ders
Windows'ta, Linux'ta ve CI'da aynı hash'i üretir.

---

## Tasarım değişmezleri

Bunlar projenin her fazında korunur ve her birinin arkasında bir test vardır.

| Değişmez | Nerede zorlanıyor |
|---|---|
| Aynı içerik her zaman aynı SHA-256'yı üretir | CRLF ve LF girdilerini kapsayan determinizm testleri |
| İnsan onayı olmadan hiçbir içerik yayınlanmaz | hat testleri, denetim kaydı, migration ile yayınlama |
| Blog yalnızca izin listesinden ve ikinci bir teyit isteğiyle çeker | ret, tekilleştirme, bozuk feed ve başarısız teyit testleri |
| Platform soyutlaması sızmaz — hiçbir bileşen hedefini bilmez | sınır denetimleri artı yeşil kalan web derlemesi |
| Özeti tutmayan hiçbir şey tamamlanmış sayılmaz | doğrulama, devam, yeniden deneme ve kuyruk geçişi testleri |
| Hard-code arayüz metni yok; dört dil aynı anahtar kümesini taşır | `npm run i18n:check` |
| Yığın kilitli — Redis yok, broker yok, harici SaaS yok | bağımlılık denetimleri |
| Markdown iki tarafta da sanitize edilir, ve yeniden yazılmaz reddedilir | yazma sınırındaki XSS testleri |
| Tauri yetkileri minimum — joker yok, kapsamsız shell yok | yetki denetimleri |
| Renk ve aralık tasarım token'larından gelir, ham hex'ten değil | frontend denetimleri ve bir tema testi |
| Uygulama tamamen çevrimdışı çalışır | çevrimdışı auth ve senkron testleri |

---

## Başlangıç

### Ön koşullar

| Araç | Sürüm | Nerede sabit |
|---|---|---|
| JDK | 21 (Temurin LTS) | `server/pom.xml` |
| Node.js | 22.23.2 LTS | `.nvmrc` |
| Rust | 1.98.0 | `rust-toolchain.toml` |
| Docker | Compose v2+ ile | — |

Sabitlenmiş üç araç zincirinden birini değiştirmek üçü hakkında birden karar vermektir:
CI iş akışları aynı dosyaları okur.

### 1. Veritabanını başlat

```bash
docker compose up -d --wait
```

Varsayılan 5432'yi değil **5433 numaralı portu** dinler, böylece makinede zaten çalışan
başka bir PostgreSQL ile çakışmaz. Host'tan:

```
jdbc:postgresql://localhost:5433/bytelore
```

Kimlik bilgileri varsayılan olarak `bytelore` / `bytelore_local_dev`; `POSTGRES_USER`,
`POSTGRES_PASSWORD`, `POSTGRES_DB` ve `POSTGRES_PORT` ile değiştirilebilir. Veritabanı
`C` locale ile başlatılır, böylece indeks ve `ORDER BY` davranışı host'a bağlı olmaz.

`docker compose stop` ile durdurulur. Dikkat: `docker compose down -v` veri hacmini ve
içindeki her satırı siler.

### 2. Sunucuyu çalıştır

```bash
cd server
./mvnw spring-boot:run
```

Flyway, korpus yükleyicisi dahil bütün migration zincirini açılışta uygular. API
`/api/v1` altında servis edilir; iki istemcinin de kullandığı temel adresler
[`config/api-endpoints.json`](config/api-endpoints.json) içinde yaşar — frontend onu
import eder, masaüstü derlemesi derleme zamanında okur.

### 3. Bir istemci çalıştır

```bash
cd frontend
npm ci

npm start            # web sitesi, http://localhost:4200
npm run start:tauri  # masaüstü hedefinin dev sunucusu, port 4300
```

Masaüstü kabuğunun kendisi için:

```bash
cd desktop
npx tauri dev
```

Pencere gizli başlar ve yalnızca tema uygulandıktan sonra gösterilir; böylece yanlış
renklerin parladığı bir kare olmaz.

---

## Denetimleri çalıştırma

Buradaki hiçbir şey ağ ya da çalışan bir sunucu istemez ve hiçbiri geliştirme
veritabanına dokunmaz: sunucu suite'i Testcontainers ile kendi tek kullanımlık
PostgreSQL'ini başlatır, Rust testleri geçici bir dizin kullanır.

```bash
# server
cd server
./mvnw spotless:check
./mvnw verify              # birim + entegrasyon, gerçek bir korpus yüklemesi dahil

# desktop
cd desktop/src-tauri
cargo fmt --all -- --check
cargo clippy --all-targets -- -D warnings
cargo test

# frontend
cd frontend
npx eslint .
npm run typecheck          # tüm kaynak, yalnızca giriş noktasından erişilen değil
npx jest
npm run i18n:check         # en, tr, fr, de anahtar paritesi ve boş değer yok
npm run build:web
npm run build:tauri        # her seferinde iki hedef de — burada tek build hiçbir şey kanıtlamaz
```

Üç GitHub Actions iş akışı aynı üç şeridi koşturur; masaüstü olanı ayrıca uygulamayı
Windows ve Linux için paketler.

---

## Depo düzeni

```
server/              Spring Boot uygulaması
  src/main/resources/content/v13/    yazılmış korpus: dersler ve listing'ler
  src/main/resources/db/migration/   Flyway migration'ları
desktop/             Tauri 2 uygulaması (src-tauri)
frontend/            iki hedef için derlenen Angular uygulaması
config/              iki istemcinin paylaştığı API temel adresleri
docs/adr/            mimari karar kayıtları
docs/protocol/       dondurulmuş sözleşmeler: REST API, içerik senkronu, korpus
                     formatı, Tauri komutları, PlatformService
.github/workflows/   CI: server · frontend · desktop
```

---

## Dokümantasyon

Sözleşmeler, onları uygulayan koddan **önce** yazılıp donduruldu; çünkü yanlış
dondurulmuş bir sözleşme tek şeride değil üç şeride birden mal olur.

| Doküman | Neyi sabitliyor |
|---|---|
| [`docs/protocol/rest-api.md`](docs/protocol/rest-api.md) | Uç nokta şekilleri, `{code, message}` hata sözleşmesi, auth akışı, sayfalama, dil müzakeresi |
| [`docs/protocol/content-sync.md`](docs/protocol/content-sync.md) | Manifest ve paket şeması, hash kuralı, delta algoritması, kuyruk durumları |
| [`docs/protocol/tauri-commands.md`](docs/protocol/tauri-commands.md) | `invoke` komut imzaları ve olay payload'ları |
| [`docs/protocol/platform-service.md`](docs/protocol/platform-service.md) | İki platform implementasyonunun da karşıladığı soyutlama |
| [`docs/protocol/content-corpus.md`](docs/protocol/content-corpus.md) | Korpus formatı: düzen, kimlikler, limitler, kaynaklar, ve bir listing'i neyin kanıtladığı |
| [`docs/content-corpus-plan.md`](docs/content-corpus-plan.md) | On yedi izlek ve her ders başlığı |

Mimari karar kayıtları [`docs/adr/`](docs/adr/) altında: akıl haritası kütüphanesi,
yazma sınırında markdown güvenliği, son-yazan-kazanır ilerleme senkronu, manifest
protokolü, SQLite şema sürümleme, çift hedefli derleme, ve korpusun neden bir
migration'ın yüklediği depo markdown'ı olduğu.

---

## Satır sonları

`.gitattributes`, içerik ve kaynak dosyalarını yalnızca depoda değil **çalışma
ağacında da** LF'e sabitler. Bu kozmetik değil, taşıyıcı bir karardır: senkronizasyon
protokolü aynı içeriğin her zaman aynı SHA-256'yı ürettiğini vaat eder, ve CRLF ile
yapılan bir checkout aynı ders için LF'tekinden sessizce farklı bir özet üretirdi —
üstelik bu, yalnızca tek bir platformda ve ancak bir indirme geçerli diye ilan
edildikten sonra ortaya çıkardı.

---

## Lisans

[MIT](LICENSE) © 2026 Ayberk Arda.

Korpus da kodla aynı lisans altında. Her ders dayandığı resmî dokümantasyona atıf
veriyor; o kaynaklar kendi şartlarını korur ve bu depoda hiçbiri, bir atfın gerektirdiği
kısa alıntıların ötesinde çoğaltılmıyor.
