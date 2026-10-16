# PSX Remote Play

Prototipe Android **original** berbasis Java untuk riset aplikasi Remote Play independen. Belum dapat terhubung atau melakukan streaming dari konsol. Tidak berafiliasi dengan Sony, PlayStation, atau PXPlay.

## Fitur dan batasan

| Komponen | Status |
|---|---|
| UI Android bertema gelap, ikon vector original | Diimplementasikan |
| Buku alamat perangkat: tambah, ubah, hapus | Lokal, tanpa discovery/registrasi |
| Profil: pilih, tambah, ubah, hapus profil custom | Disimpan di SharedPreferences privat; dua preset bawaan |
| Resolusi 720/1080p, 30/60 fps, bitrate 2–50 Mbps, audio | Konfigurasi tersimpan; belum diterapkan pada stream nyata |
| Dead zone controller | Digunakan di demo lokal |
| Demo sesi dan diagnostik controller | Animasi Canvas lokal; frame jaringan diterima tetap 0 |
| Transport/lifecycle/error | State machine, callback guard, demo dan stub konsol eksplisit |
| Input controller Android | Tombol, stick, trigger, D-pad key/HAT, hotplug dan reset; perlu uji gamepad fisik |
| Video `MediaCodec` ke `Surface` | Adapter prototipe; butuh format/CSD dan access unit dari penyedia stream |
| Audio `AudioTrack` PCM16 | Adapter prototipe; butuh decoder, audio focus dan buffering dari penyedia sesi |
| Login, pairing, enkripsi, discovery, wake-up, NAT, koneksi konsol | Belum diimplementasikan |
| A/V sync, packet recovery, audio terkompresi, haptic, touchpad | Belum diimplementasikan |

Demo tidak memakai adapter decoder/audio: semua gerakan dibuat oleh Canvas. Nilai kualitas profil bukan pengukuran performa. Pemilihan codec oleh Android tidak menjamin hardware acceleration atau latensi tertentu. Profil menyimpan nama/alamat dan preferensi saja, tanpa PIN, token atau kunci.

## Membuka dan membangun

1. Buka direktori root ini lewat **Android Studio → Open** dan lakukan Gradle Sync. Ini project Gradle standar; tidak membutuhkan file `.idea` yang dibagikan.
2. Sediakan JDK 21 (JBR Android Studio dapat dipakai), Android SDK Platform 35 dan Build Tools 35.0.0. Bytecode Java ditargetkan ke 17; minimum perangkat Android 8.0/API 26.
3. Android Studio membuat `local.properties`. Untuk terminal, isi `sdk.dir=C:/Users/NAMA/AppData/Local/Android/Sdk` sesuai mesin, atau gunakan `ANDROID_HOME`. Jangan commit konfigurasi lokal.
4. Jalankan pada PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :core:test
```

Gradle Wrapper 8.11.1 dipasangkan dengan Android Gradle Plugin 8.9.2 dan compile/target SDK 35 sesuai [matriks AGP 8.9](https://developer.android.com/build/releases/agp-8-9-0-release-notes). Versi dipin untuk reproduksibilitas; SDK 36+ dan kebijakan distribusi terbaru belum divalidasi. Distribusi wrapper diverifikasi dengan SHA-256. Unduhan pertama membutuhkan jaringan ke Google Maven, Maven Central, dan Gradle.

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
  src/main/java/io/github/seandyadryan/psxremoteplay/
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

Tidak ada kode hasil dekompilasi, aset, branding, kunci, atau implementasi proprietari yang dipakai. Tidak dilakukan bypass lisensi, DRM, atau autentikasi. APK referensi tidak dipasang, dijalankan, diubah atau didekompilasi. Tidak ada bukti izin reverse engineering yang disediakan, sehingga riset memakai informasi publik. Readiness REA berhasil; provider Android membutuhkan JAR JADX Headless yang belum dikonfigurasi. Data identitas dan pemeriksaan APK disimpan terpisah dalam `.analysis-private/` dan tidak dipublikasikan.

Manifest aplikasi ini hanya berisi dua Activity dan fitur gamepad opsional. Tidak meminta permission jaringan, microphone, penyimpanan, atau akun. Adapter transport nyata perlu tinjauan protokol/lisensi dan perubahan permission yang sesuai. Log berisi event/status, tanpa nama perangkat, alamat, kredensial, atau payload.

`.gitignore` mengecualikan seluruh APK/AAB, analisis privat, decompiled/JADX/apktool/extracted output, build/cache/IDE, `local.properties`, keystore, private key, `.env`, konfigurasi kredensial, dan log. Jangan menaruh hasil ekstraksi dalam folder arbitrary di luar pola ini. Sebelum commit, tinjau `git diff --cached` dan daftar staged; gunakan daftar file eksplisit.

Source original memakai [MIT](LICENSE). Dependensi build/tes tercatat dalam [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Pengembangan berikutnya

1. Uji controller fisik dan lifecycle pada API 26, 29, 35, serta SDK lebih baru; tambahkan mapping vendor bila ada bukti kebutuhan.
2. Uji decoder dengan bitstream AVC/HEVC buatan sendiri, Surface lifecycle, timestamp, EOS, backpressure, error, dan sinkronisasi A/V; ukur frame drop serta latensi pada perangkat fisik.
3. Tambahkan audio focus, decoder audio, jitter buffer dan antrian terbatas sebelum mengaktifkan playback sesi.
4. Pilih penyedia protokol independen yang dapat ditinjau sumber/lisensinya. Baru kemudian implementasikan pairing, autentikasi resmi yang diperlukan, discovery, jaringan dan pemulihan sesi; jangan memasukkan kunci dari aplikasi lain.
5. Uji interoperabilitas pada konsol milik sendiri dan dokumentasikan hasil sebelum menyatakan Remote Play bekerja.
