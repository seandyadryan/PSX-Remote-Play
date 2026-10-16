# Hasil validasi — 16 Oktober 2026

Pengujian dilakukan pada Windows dengan JBR/JDK 21.0.9, Android SDK Platform/Build Tools 35, Gradle Wrapper 8.11.1 dan AGP 8.9.2. Tanggal mengikuti Asia/Jakarta. Bagian terakhir mencatat versi terkini **0.2.0-lan-experimental**. Pengujian belum membuktikan interoperabilitas konsol fisik.

Bagian awal mempertahankan hasil baseline 0.1.0 dan tahap discovery. Bagian **Koneksi LAN 0.2.0** menggantikan angka tes, hash APK dan status implementasi historis di bagian sebelumnya.

## Build dan pemeriksaan statis

Perintah yang dijalankan dari root project:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:test --console=plain
```

Hasil akhir: **BUILD SUCCESSFUL**, 71 actionable tasks (21 executed, 50 up-to-date), 29 detik. JAR wrapper cocok dengan checksum resmi Gradle:
`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
Distribusi wrapper juga memiliki `distributionSha256Sum` dan `gradlew.bat --version` berhasil setelah unduhan.

Android Lint: **0 error, 3 warning** yang dipertahankan secara eksplisit:

- `OldTargetApi`: target 35 bukan API terbaru. Project menggunakan pasangan toolchain yang telah diuji; belum diuji untuk target lebih baru/distribusi Play.
- `GradleDependency`: AndroidX test runner 1.6.2 mempunyai versi 1.7.0.
- `GradleDependency`: AndroidX test ext JUnit 1.2.1 mempunyai versi 1.3.0.

Tidak ada baseline/suppression untuk menyembunyikan error. Kesalahan awal akses `Insets` pada minSdk 26 diperbaiki dengan version guard eksplisit. Aturan backup untuk API lama dan baru disediakan. Peringatan compiler mengenai pemakaian API deprecated masih ada pada beberapa API kompatibilitas Android; kompilasi lulus.

Project mempunyai `settings.gradle`, dua modul, Gradle Wrapper, manifest, resource dan source Java yang berhasil dibangun. Struktur siap diimpor Android Studio; **interaksi GUI Open/Gradle Sync Android Studio belum diuji langsung**.

## Unit test JVM

**10 tes lulus, 0 failure/error**:

- 4 tes SessionController: demo tidak menjadi streaming nyata, stub konsol gagal eksplisit, callback lama tidak menghidupkan sesi lama/baru, serta kegagalan input menutup transport.
- 6 tes validasi: dead zone/batas axis, penolakan URL/host/port tidak valid, alamat literal/hostname, bitrate dan nilai dead zone tidak valid.

XML/report lokal berada di `core/build/test-results/test/` dan `core/build/reports/tests/test/` (diabaikan Git).

## Instrumentation Android

APK aplikasi dan APK tes dipasang pada emulator **Android 15/API 35 x86_64**. Setelah `sys.boot_completed=1`, dijalankan:

```text
adb -s emulator-5580 shell am instrument -w \
  io.github.seandyadryan.psxremoteplay.test/androidx.test.runner.AndroidJUnitRunner
```

Hasil: **OK (5 tests)**, 60,304 detik.

| Tes | Bukti yang diperoleh | Batas bukti |
|---|---|---|
| `deviceAndProfileCrudSurvivesRepositoryRecreation` | Tambah/update/delete perangkat dan profil, pilihan profil, pembacaan ulang repository | SharedPreferences pada konteks APK tes; belum uji process death/disk-full |
| `malformedStorageDoesNotCrashOrCreateDevices` | JSON invalid ditangani tanpa crash atau perangkat palsu | Satu skenario korupsi |
| `homeAndDemoLaunchWithTruthfulLabels` | Kedua Activity terbuka, label demo/0 frame tampil, callback pause/resume berhasil | Lifecycle callback diuji langsung; belum mencakup rotasi, multi-window, process death atau seluruh form UI |
| `avcDecoderConfiguresSurfaceAndReleasesWithoutClaimingFrames` | Decoder AVC menerima konfigurasi 128×128 dan Surface; drain/close ganda berjalan | Tidak ada bitstream/frame yang dikirim; belum membuktikan decoding/rendering video atau hardware acceleration |
| `pcmSinkAcceptsGeneratedSilenceAndClosesTwice` | AudioTrack 48 kHz stereo menerima pemanggilan nonblocking write PCM hening dan close ganda | Return value dibatasi 0..960 sample; tidak membuktikan audio terdengar atau seluruh sample dimainkan |

Emulator berjalan headless tanpa output audio host. Tidak ada controller fisik atau konsol yang digunakan. Tidak ada trafik Remote Play.

Percobaan emulator awal terkendala ruang drive sistem, lalu instance pertama di drive lain mengalami restart sistem Android (`DeadSystemException`) sehingga instrumentation berakhir `Process crashed`. Pengujian lulus pada startup ulang dengan data emulator khusus tugas dan menunggu boot lengkap. Data AVD asal tidak dihapus; referensi APK tidak pernah dipasang.

Setelah kelima tes lulus, percobaan peluncuran untuk inspeksi visual mengalami timeout dan emulator menampilkan `Process system isn't responding`. Setelah memilih Wait, halaman home tampil dan screenshot diperiksa: heading, kartu demo/profil, tombol dan inset sistem terlihat utuh. Pemeriksaan ini tidak mencakup seluruh form atau membuktikan emulator stabil. Uji ulang pada emulator yang stabil/perangkat fisik tetap diperlukan.

## Artefak dan batas keseluruhan

APK debug lokal: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256 hasil build ini: `F2817ED5DF570124056A715142436AD358095497D34CB1ACF1D529DF9653B2F9`.
Hash dapat berbeda pada build berikutnya, terutama karena debug signing; ini bukan klaim reproducible binary build.

Koneksi konsol, pairing/authentication, stream video/audio, latensi, sinkronisasi A/V, kualitas jaringan, gamepad fisik, hardware decode dan kompatibilitas API 26–34/36+ **belum teruji**. Decoder/audio adalah adapter prototipe dan belum dihubungkan ke transport.

## Pemeriksaan publikasi

Source dan dokumentasi ditinjau sebelum staging. File distaging dengan path eksplisit. `git diff --cached --check` lulus. Pemeriksaan pola kredensial tidak menemukan kecocokan pada source/dokumentasi yang akan dipublikasikan; pemeriksaan pola ini bukan jaminan universal bebas rahasia.

`git check-ignore` mengonfirmasi APK referensi/hasil build, `.analysis-private/`, `local.properties`, output dekompilasi/JADX, keystore, `.env`, dan file kredensial dikecualikan. Satu-satunya binary build yang dilacak adalah JAR wrapper Gradle resmi yang checksum-nya telah diperiksa. Tidak ada file APK, hasil dekompilasi, library native reference, aset proprietari, keystore atau kredensial di staged source.

Pada tahap baseline, hash APK referensi sama sebelum dan sesudah pengerjaan; identitas, signature dan batas keaslian ada pada catatan privat lokal. REA MCP 6.5.0 berhasil dipanggil untuk readiness, tetapi provider Android saat itu belum dikonfigurasi. Tahap baseline tidak melakukan dekompilasi. Investigasi lanjutan berikut memakai provider yang sudah dikonfigurasi pengguna.

## Lanjutan: discovery LAN / pencarian IP

Tanggal: 16 Oktober 2026 (Asia/Jakarta), JDK/SDK/Gradle yang sama. REA/JADX berhasil menginspeksi APK referensi secara statis. SHA-256 referensi tetap `1b0e15f90e7e40eca07ed9239f9179eb0eaef0730c0e0da8f62c5a75bfb6434a`. APK referensi tidak dijalankan atau dipasang. Bukti metode dan anomali literal dijelaskan di [DISCOVERY.md](DISCOVERY.md), dengan bundle 39 record di direktori privat yang diabaikan Git.

Perintah gabungan `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:test` berhasil. **22 tes JVM lulus, 0 failure/error**: 10 tes lama dan 12 tes discovery baru. Enam tes codec mencakup request PS4/PS5 yang tepat, parsing status/UTF-8/CRLF/LF/terminator, offset/length datagram dan alamat sender, default/truncation nama, serta penolakan data invalid. Enam tes scanner memakai UDP loopback nyata untuk kedua versi, deduplikasi/update status, retry, deadline meskipun trafik junk/duplikat berlanjut, pembatalan/restart/penggantian run, timeout tanpa hasil dan batas konfigurasi.

APK aplikasi dan instrumentation berhasil dikompilasi. Lint: **0 error, 3 warning** yang sama dengan baseline (target API dan versi dependensi tes). Build/lint aplikasi diulang setelah perbaikan integrasi UI penyimpanan manual/identitas stabil; tidak ada perubahan core setelah 22 tes lulus. `git diff --check` lulus.

Instrumentation ditambah skenario penyimpanan discovery: penggabungan alamat manual, nama custom, deduplikasi, pembacaan ulang repository, perpindahan IP dan benturan dengan alamat manual lain. Smoke test home memeriksa keberadaan kontrol pencarian. **Tes Android terbaru belum dijalankan**: pemeriksaan `adb devices -l` tidak menemukan emulator/perangkat terhubung. Kelima hasil Android baseline di atas tidak membuktikan fitur discovery baru.

Belum dilakukan pengujian konsol fisik, routing Wi-Fi/VPN, broadcast antarperangkat, UI runtime atau interoperabilitas PS4/PS5. Loopback membuktikan pertukaran datagram dan lifecycle scanner, bukan penerimaan request oleh firmware konsol. Pairing, wake-up dan streaming tetap belum diimplementasikan.

## Koneksi LAN 0.2.0 — hasil terkini

Registrasi, wake-up dan jalur streaming LAN telah diimplementasikan dengan provider Chiaki-ng yang dibangun dari source berlisensi. Alur, bukti APK pembanding, dependency yang dipin dan batas implementasi dijelaskan di [CONNECTION.md](CONNECTION.md). Tidak ada source hasil dekompilasi atau binary library dari APK pembanding yang dipakai dalam aplikasi.

Build akhir `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` **berhasil**, 26 detik, 76 task (17 dijalankan, 59 up-to-date). Native provider dikompilasi untuk **arm64-v8a dan x86_64**, memakai NDK 28.2.13676358 dan CMake 3.22.1. Log lokal: `.analysis-private/connection-build-delivery.log`.

Lint: **0 error, 4 warning**. Tiga warning versi target/dependensi sama dengan baseline; satu `DiscouragedApi` untuk orientasi `sensorLandscape` pada layar sesi. Tidak ada error yang disembunyikan dengan suppression.

### Tes JVM

` :core:test` **berhasil: 32 tes, 0 failure/error**. Cakupan: validasi (6), lifecycle transport (4), codec discovery (6), scanner UDP loopback (6), encoding account ID (2), primitive registrasi/wake-up/controller (4), dan pengiriman/pembatalan wake-up UDP loopback (4). Log: `.analysis-private/connection-core-tests-final.log`.

Saat build native dan startup emulator berjalan bersamaan, satu tes pergantian scanner pernah melewati batas tunggu setup. Batas tunggu tes diperbesar dengan tetap mempertahankan assertion pembatalan dan kepemilikan callback; deadline produksi dan tes flood tidak diubah. Seluruh 32 tes kemudian dijalankan ulang dan lulus.

### Tes Android dan inspeksi UI

APK akhir dan APK tes dipasang pada emulator khusus tugas **emulator-5582, Android 15/API 35, x86_64**. Instrumentation terakhir: **OK (14 tests)**, 59,251 detik. Log: `.analysis-private/connection-android-tests-delivery.log`.

| Kelompok | Jumlah | Bukti dan batas |
|---|---:|---|
| `ConnectionUiTest` | 2 | Menu/form registrasi, input invalid, pairing yang sudah tersimpan, lifecycle pause/resume sesi dan status tanpa frame; fixture sintetis localhost |
| `NativeConnectionSmokeTest` | 3 | Provider termuat, validasi input/handle, kegagalan registrasi localhost yang dibatasi waktu, tiga siklus sesi/input/penutupan; tanpa konsol |
| `PrototypeSmokeTest` | 6 | CRUD, storage rusak, identitas discovery/manual dan benturan IP, home/demo, konfigurasi AVC serta PCM hening; tanpa bitstream konsol |
| `RegistrationStoreTest` | 3 | Roundtrip/delete AES-GCM, penolakan pertukaran device ID/AAD dan perubahan ciphertext |

Konteks tes memakai UID aplikasi target dengan namespace preferences dan alias Keystore unik. Ini memperbaiki kegagalan setup sebelumnya ketika konteks APK tes mencoba menulis direktori yang tidak dapat diakses UID target. Pembersihan tes dibatasi pada fixture dan namespace milik tes.

Screenshot form registrasi dengan input sintetis diperiksa pada resolusi 1080×2400: label, validasi dan tombol terlihat utuh. Artefak lokal: `.analysis-private/registration-form.png`. Ini tidak membuktikan registrasi konsol berhasil. Runtime JNI diuji pada x86_64; arm64-v8a baru diverifikasi melalui kompilasi dan inspeksi ELF.

### APK yang diserahkan dan batas validasi

- Path: `app/build/outputs/apk/debug/app-debug.apk`, ukuran **2.654.657 byte**.
- SHA-256: `D0EC9EC67426E85B5A8BB433A1E9B3B405E7D7F1FDE7E839B9363F1DCDC7B6B8`.
- Package `io.github.seandyadryan.psxremoteplay`, versionCode 2, versionName `0.2.0-lan-experimental`, target API 35.
- Kedua ELF memiliki LOAD alignment `0x4000`; pemeriksaan `zipalign -c -P 16 4` lulus. Ini pemeriksaan struktur 16 KiB, bukan uji runtime pada perangkat dengan page size 16 KiB.
- APK memuat kedua ABI, notice source dan delapan berkas lisensi. Permission aplikasi hanya `INTERNET`.
- `git diff --check` lulus. Output build, APK dan bukti privat tetap diabaikan Git. Tidak ada commit atau publikasi pada tahap ini.
- Hash APK pembanding tetap `1b0e15f90e7e40eca07ed9239f9179eb0eaef0730c0e0da8f62c5a75bfb6434a`; APK tersebut tidak dipasang atau dijalankan.

**Belum diuji dengan PS4/PS5 fisik**, kredensial nyata, stream konsol, gamepad fisik, latensi, sinkronisasi A/V atau jaringan lintas perangkat. Tes codec hanya membuktikan konfigurasi/cleanup dan penerimaan PCM hening; tes loopback tidak membuktikan firmware menerima protokol. Login PSN otomatis, koneksi internet/NAT, HEVC/HDR, PiP, touchpad gesture dan haptics belum diimplementasikan. Gunakan panduan koneksi untuk pengujian LAN pada konsol milik sendiri; keberhasilan wake-up di UI berarti datagram terkirim, bukan konfirmasi konsol menyala.
