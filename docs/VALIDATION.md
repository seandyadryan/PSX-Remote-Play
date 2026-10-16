# Hasil validasi — 16 Oktober 2026

Pengujian awal dilakukan pada Windows dengan JBR/JDK 21.0.9, Android SDK Platform/Build Tools 35, Gradle Wrapper 8.11.1 dan AGP 8.9.2. Tanggal mengikuti Asia/Jakarta. Bagian terakhir mencatat versi terkini **0.2.1-lan-experimental**, package baru dan target API 36. Pengujian belum membuktikan interoperabilitas konsol fisik.

Bagian awal mempertahankan hasil baseline 0.1.0, tahap discovery dan koneksi 0.2.0. Bagian **Persiapan Play Store 0.2.1** menggantikan identitas package, angka tes, hash artefak dan toolchain historis di bagian sebelumnya.

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

## Koneksi LAN 0.2.0 — hasil sebelum persiapan Play Store

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

## Persiapan Play Store 0.2.1 — hasil terkini

Atas permintaan pengguna, application ID, namespace, package Java/core, JNI exports dan referensi instrumentation diganti menjadi **`com.deploydulupulangnanti.psxremoteplay`**. Package Android ini terpisah dari instalasi prototipe lama. VersionCode **3**, versionName **`0.2.1-lan-experimental`**. Compile/target SDK **36**, AGP **8.10.1**, Gradle **8.11.1**, JDK **21**, Build Tools **35.0.0**, NDK dan CMake tetap seperti bagian sebelumnya. Target API diperbarui mengikuti [ketentuan Google Play](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-EN).

### Runner dan pengujian

[GitHub Actions run 38879409159](https://github.com/seandyadryan/PSX-Remote-Play/actions/runs/38879409159) **selesai dengan success**, source commit `540673dc39e074d570e30a756ac6bf5217453abc`. Runner GitHub-hosted **windows-2022** memasang toolchain, memverifikasi wrapper, menjalankan **32 tes JVM dengan 0 failure/error/skipped**, menjalankan lint, membangun APK debug/instrumentation, serta membangun dan memverifikasi APK/AAB release bertanda tangan. Laporan XML dan artefak diunduh kembali untuk pemeriksaan. Keystore dipulihkan dari repository secrets hanya untuk push/manual run dan dihapus dari runner pada cleanup; tidak ada upload ke Google Play.

Lint terbaru: **0 error, 2 warning**: `AndroidGradlePluginVersion` (Gradle 8.14.6 tersedia; versi 8.11.1 dipertahankan sesuai toolchain AGP 8.10) dan `DiscouragedApi` (orientasi `sensorLandscape`). Tidak ada error yang disembunyikan. Local lint berhasil setelah konfigurasi SDK lokal diperbaiki agar colon drive ter-escape dan analisis diulang untuk menghapus hasil cache lama.

Tes JVM lokal pada package baru juga lulus. Pada percobaan awal yang bersamaan dengan startup emulator dan native build, tiga tes discovery dengan deadline pendek mengalami receive timeout. Tes dijalankan terpisah dari native compilation; 32 tes lulus tanpa memperlonggar assertion atau mengubah scanner produksi. Workflow memakai urutan terpisah yang sama. Build native lokal yang redundan dihentikan setelah build hosted berhasil; artefak release yang diserahkan berasal dari runner, bukan dari proses lokal yang dibatalkan.

APK debug dan instrumentation **hasil runner yang sama** dipasang pada emulator khusus tugas **emulator-5582, Android 15/API 35 x86_64**. Empat kelas tes koneksi/UI/native/repository/Keystore dijalankan eksplisit: **OK (14 tests)**, **162,999 detik**, 0 kegagalan. Kelas pengambil screenshot dikecualikan dari run ini dan hanya dipanggil saat pengambilan gambar. Log lokal: `.analysis-private/store-android-tests.log`. Emulator sempat lambat karena tekanan resource host saat native compilation; pengujian menunggu boot selesai. Tidak ada konsol fisik, data akun pengguna atau gameplay konsol.

### Identitas dan signature artefak release

| Artefak lokal hasil runner | SHA-256 |
|---|---|
| `release/0.2.1-lan-experimental/psx-remote-play.aab` | `B2D73F26189FB700666E629F87A8C61E1BB0E44E7E342D77A3F1A1522CE131E1` |
| `release/0.2.1-lan-experimental/psx-remote-play.apk` | `6B344E3FA8DD6A0BF060D3D90F37717E310FDA6B0E61487B394990DB9868E6C9` |

`aapt2 dump badging` mengonfirmasi package baru, versionCode 3, compile/target 36, kedua ABI dan tidak adanya flag debuggable pada APK release. `apksigner verify --print-certs` lulus; SHA-256 sertifikat cocok dengan keystore pengguna: `363ec97ea4246dc3b9ae0f92e514d2682ce3295ead60648b2c131e002c07c6f7`. `jarsigner -verify` melaporkan **jar verified** untuk AAB; peringatan sertifikat self-signed normal untuk kunci upload Android. `zipalign -c -P 16 4` lulus pada APK release. Kedua ELF pada APK maupun AAB memiliki minimum LOAD alignment **0x4000**. Ini belum merupakan uji runtime perangkat dengan page size 16 KiB.

Kebijakan privasi `assets/privacy/index.html` pada kedua artefak diverifikasi memuat package baru. HTML publik dan versi offline aplikasi berasal dari satu source `privacy-policy/index.html`. Developer dan kontak yang dicantumkan: **PT Deploy Dulu Pulang Nanti**, **seandyadryan2@gmail.com**. Tidak ada password/private key pada source atau artefak publik. Keystore berada di luar repository dan file build/ZIP lokal dikecualikan Git.

Listing Inggris, gambar fitur dan tangkapan aplikasi dijelaskan di [play-store/README.md](../play-store/README.md). Halaman privasi diperiksa pada viewport desktop dan lebar 390 px, tanpa overflow horizontal dan tanpa script. Publikasi URL kebijakan privasi, konfigurasi Play Console dan pengujian konsol fisik tetap belum dilakukan. APK/AAB yang bertanda tangan dan runner yang sukses tidak membuktikan kesiapan produksi atau persetujuan Google Play.

### Validasi aset Play Store

Deskripsi Inggris berjumlah **66 karakter** dan **3.040 karakter**, tanpa newline penutup. Gambar fitur merupakan PNG opaque RGB 24-bit **1024×500**, memakai ilustrasi original dari imagegen dan komposisi HTML. Empat screenshot ponsel **1080×1920** dan empat screenshot Android lanskap **1920×1080** diambil dengan `screencap` dari aplikasi sebenarnya. Seluruh file di bawah batas ukuran yang diminta; `play-store/validate-assets.py` lulus dan menyimpan dimensi, ukuran serta SHA-256 di `play-store/asset-manifest.json`.

Pemeriksaan visual menolak capture awal karena tertutup dialog **System UI isn't responding** dari emulator. Setelah memilih Wait dan menunggu UI bersih, delapan screenshot diambil ulang dan diperiksa satu per satu; tidak ada dialog error pada gambar akhir. Capture final selesai dengan `OK (1 test)` masing-masing untuk lanskap (46,493 detik) dan ponsel (53,271 detik). Log: `.analysis-private/store-capture-desktop-final.log` dan `.analysis-private/store-capture-phone-final.log`. Capture yang ditolak hanya disimpan pada direktori privat. Tes UI otomatis berinteraksi dengan Activity aplikasi dan tidak mendeteksi dialog sistem tersebut; kelulusannya tidak membuktikan stabilitas seluruh emulator.

Layar registrasi memakai alamat contoh RFC 5737 dan label **Setup example · no console connected**, tanpa account ID/PIN. Screenshot tidak menampilkan discovery, pairing, wake-up atau gameplay palsu. Gambar desktop adalah preview Android lanskap, bukan bukti aplikasi Windows/macOS atau kompatibilitas ChromeOS. Belum ada uji touch/gamepad fisik atau pengujian konsol tambahan pada tahap aset ini.
