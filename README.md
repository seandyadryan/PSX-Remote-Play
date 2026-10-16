# PSX Remote Play

Client Android eksperimental berbasis Java dengan discovery dan integrasi mesin Chiaki untuk registrasi, wake-up serta streaming LAN. Source UI/adaptor ditulis original; interoperabilitas konsol fisik belum diverifikasi. Tidak berafiliasi dengan Sony, PlayStation atau PXPlay.

## Fitur dan batasan

| Komponen | Status |
|---|---|
| UI Android bertema gelap, ikon vector original | Diimplementasikan |
| Buku alamat perangkat: tambah, ubah, hapus | Lokal; hasil discovery dapat disimpan tanpa registrasi |
| Cari PS4 / PS5 di LAN atau IPv4 tertentu | UDP discovery, hasil/status, cancel dan batas 15 detik; belum diuji dengan konsol fisik |
| Profil: pilih, tambah, ubah, hapus profil custom | Disimpan di SharedPreferences privat; dua preset bawaan |
| Resolusi 720/1080p, 30/60 fps, bitrate 2–50 Mbps, audio | Tersimpan dan diteruskan ke sesi native; performa konsol belum diukur |
| Dead zone controller | Digunakan di demo dan input sesi konsol |
| Demo sesi dan diagnostik controller | Animasi Canvas lokal; frame jaringan diterima tetap 0 |
| Transport/lifecycle/error | Guard callback, cleanup worker native/media, Surface dan audio focus |
| Input controller Android | Tombol, stick, trigger, D-pad key/HAT, hotplug dan reset; perlu uji gamepad fisik |
| Registrasi LAN PS4 / PS5 | Mesin native Chiaki; account ID PSN dan PIN konsol; belum diuji dengan konsol fisik |
| Penyimpanan pairing | AES-GCM / Android Keystore; PIN tidak disimpan |
| Wake-up | UDP dengan kredensial pairing; pengiriman paket tidak menjamin konsol bangun |
| Streaming LAN | Sesi native → AVC/H.264 → MediaCodec/Surface; Opus → PCM/AudioTrack, gamepad dan tombol PS |
| Login PSN otomatis, koneksi internet/NAT | Belum diimplementasikan |
| Opus dan pemulihan paket/FEC | Ditangani provider native; performa belum diuji pada konsol fisik |
| A/V sync produksi, haptic, swipe touchpad | Belum diimplementasikan; klik touchpad tersedia |

Demo tetap memakai Canvas dan tidak mengirim data ke konsol. Layar streaming konsol terpisah menerapkan profil melalui provider native. Nilai profil bukan pengukuran performa; pemilihan codec tidak menjamin hardware acceleration/latensi tertentu. Buku alamat dan profil menyimpan preferensi saja; hasil pairing disimpan terpisah dan terenkripsi. Panduan alur registrasi → wake-up → streaming ada di [docs/CONNECTION.md](docs/CONNECTION.md).

## Membuka dan membangun

1. Buka direktori root ini lewat **Android Studio → Open** dan lakukan Gradle Sync. Ini project Gradle standar; tidak membutuhkan file `.idea` yang dibagikan.
2. Sediakan JDK 21 (JBR Android Studio dapat dipakai), Android SDK Platform 36 / Build Tools 35.0.0, NDK 28.2.13676358, CMake 3.22.1 dan Windows PowerShell. Bytecode Java ditargetkan ke 17; minimum Android 8.0/API 26. ABI yang dibangun: arm64-v8a dan x86_64.
3. Android Studio membuat `local.properties`. Untuk terminal, isi `sdk.dir=C:/Users/NAMA/AppData/Local/Android/Sdk` sesuai mesin, atau gunakan `ANDROID_HOME`. Jangan commit konfigurasi lokal.
4. Jalankan pada PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :core:test
```

Gradle Wrapper 8.11.1 dipasangkan dengan Android Gradle Plugin 8.10.1 dan compile/target SDK 36 sesuai [matriks AGP 8.10](https://developer.android.com/build/releases/agp-8-10-0-release-notes). Distribusi wrapper diverifikasi dengan SHA-256. Unduhan pertama membutuhkan jaringan ke Google Maven, Maven Central, Gradle, dan host source native yang dipin.

Workflow Windows GitHub Actions, signing release, deskripsi Inggris, gambar fitur, screenshot dan kebijakan privasi dijelaskan di [panduan persiapan Play Store](play-store/README.md). Bahasa UI default sekarang Inggris. Penyelesaian artefak ini tidak membuktikan koneksi konsol fisik atau menjamin persetujuan Google Play.

APK debug ada di `app/build/outputs/apk/debug/app-debug.apk`; semua APK diabaikan Git. Pilih modul `app` di Android Studio untuk Run pada emulator/perangkat sendiri. Tidak ada konfigurasi signing release atau keystore dalam repo.

Untuk instrumentation, sambungkan emulator/perangkat yang dipilih:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Perintah ini bisa menargetkan semua perangkat yang terhubung; gunakan `ANDROID_SERIAL` bila perlu memilih satu. Tes menyimpan data CRUD di konteks APK tes, terpisah dari data aplikasi. Hasil pengujian aktual dan batas cakupannya ada di [docs/VALIDATION.md](docs/VALIDATION.md).

## Struktur

```text
app/
  src/main/AndroidManifest.xml
  src/main/java/com/deploydulupulangnanti/psxremoteplay/
    data/       AppRepository (JSON/SharedPreferences)
    platform/   AndroidController, AppLog
    media/      VideoDecoder, MediaCodecVideoDecoder, PcmAudioSink
    ui/         MainActivity, StreamActivity, DemoCanvas, Ui
  src/main/res/ drawable, values, backup rules
  src/androidTest/ PrototypeSmokeTest
core/
  src/main/java/.../core/
    Device, StreamProfile, ControllerInput
    SessionTransport, SessionController
    DemoTransport, ConsoleTransport
  src/test/ SessionControllerTest, ValidationTest
docs/           Riset publik, arsitektur, validasi
gradle/wrapper/ Wrapper resmi Gradle
```

`core` tidak bergantung pada Android. `app` mengadaptasi API publik ke lifecycle dan UI. Detail kontrak thread/media, state, cleanup, dan extension point ada di [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Sumber primer dan batas riset tersedia di [docs/PUBLIC_RESEARCH.md](docs/PUBLIC_RESEARCH.md).

## Provenance dan publikasi

Source UI/adaptor, test dan aset ditulis original. Mesin native dibangun dari source publik Chiaki-ng dan dependensi yang dipin; build pertama memerlukan jaringan untuk source archive yang checksum-nya diverifikasi. Tidak ada kode hasil dekompilasi, aset, branding atau library APK PXPlay yang disalin ke aplikasi. APK referensi dianalisis statis dengan REA/JADX; tidak dipasang, dijalankan atau diubah. Bukti dan batas kesimpulan ada di [docs/DISCOVERY.md](docs/DISCOVERY.md) dan [docs/CONNECTION.md](docs/CONNECTION.md), dengan bundle lengkap di `.analysis-private/`. Pairing menggunakan PIN dan akun konsol sendiri.

Manifest menyediakan layar demo, koneksi, registrasi, streaming dan lisensi, fitur gamepad opsional serta `INTERNET` untuk protokol LAN. Tidak meminta microphone atau akses penyimpanan bersama. Native logging dimatikan; log aplikasi tidak memuat nama perangkat, alamat, kredensial atau payload.

`.gitignore` mengecualikan seluruh APK/AAB, analisis privat, decompiled/JADX/apktool/extracted output, build/cache/IDE, `local.properties`, keystore, private key, `.env`, konfigurasi kredensial, dan log. Jangan menaruh hasil ekstraksi dalam folder arbitrary di luar pola ini. Sebelum commit, tinjau `git diff --cached` dan daftar staged; gunakan daftar file eksplisit.

Source original memakai [MIT](LICENSE); provider Chiaki memakai AGPL dengan ketentuan upstream yang disertakan. Lisensi komponen dan source build dicatat di [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) dan dapat dibaca di aplikasi.

## Pengembangan berikutnya

1. Uji controller fisik dan lifecycle pada API 26, 29, 35, serta SDK lebih baru; tambahkan mapping vendor bila ada bukti kebutuhan.
2. Uji decoder dengan bitstream AVC/HEVC buatan sendiri, Surface lifecycle, timestamp, EOS, backpressure, error, dan sinkronisasi A/V; ukur frame drop serta latensi pada perangkat fisik.
3. Tambahkan audio focus, decoder audio, jitter buffer dan antrian terbatas sebelum mengaktifkan playback sesi.
4. Uji integrasi Chiaki pada konsol sendiri: registrasi, perubahan IP, mode istirahat, PIN login, audio focus, pencabutan controller dan pemutusan jaringan. Tambahkan pemulihan sesi hanya setelah lifecycle dan interoperabilitas terukur.
5. Uji interoperabilitas pada konsol milik sendiri dan dokumentasikan hasil sebelum menyatakan Remote Play bekerja.
