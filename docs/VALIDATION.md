# Hasil validasi — 16 Oktober 2026

Pengujian dilakukan pada Windows dengan JBR/JDK 21.0.9, Android SDK Platform/Build Tools 35, Gradle Wrapper 8.11.1 dan AGP 8.9.2. Tanggal mengikuti Asia/Jakarta. Hasil ini berlaku untuk prototipe 0.1.0 dan tidak membuktikan interoperabilitas konsol.

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

Hash APK referensi sama sebelum dan sesudah pengerjaan; identitas, signature dan batas keaslian ada pada catatan privat lokal. REA MCP 6.5.0 berhasil dipanggil untuk readiness. Provider analisis Android tidak tersedia karena `REA_JADX_MCP_JAR` belum dikonfigurasi; tidak ada dekompilasi yang dilakukan. Riset publik tidak mengklaim mengetahui internal APK.
