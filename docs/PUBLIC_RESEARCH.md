# Riset publik untuk PSX Remote Play

Tanggal penelusuran: 16 Oktober 2026 (Asia/Jakarta).

Catatan lanjutan: dokumen ini mempertahankan riset publik tahap awal. Investigasi APK, discovery dan integrasi koneksi berikutnya dicatat di [DISCOVERY.md](DISCOVERY.md) serta [CONNECTION.md](CONNECTION.md).

Dokumen ini memakai dokumentasi penerbit dan Android Developers. Tidak ada APK yang diakses, diekstrak, dijalankan, atau didekompilasi untuk menyusun dokumen ini. Temuan fitur publik **tidak membuktikan implementasi internal atau perilaku APK PXPlay v6.6.0** yang disediakan pengguna. Sumber daring dapat menjelaskan versi yang lebih baru; tanggal akses bukan tanggal rilis fitur.

## 1. Fitur produk yang didokumentasikan publik

**Observasi dokumentasi, bukan hasil pengujian aplikasi:** manual penerbit menjelaskan registrasi konsol, koneksi lokal dan jarak jauh, beberapa profil akun/konsol, layout kontrol sentuh, pemetaan gamepad, dan mode controller tanpa pemrosesan audio/video. Manual juga menjelaskan koneksi otomatis melalui internet dan pilihan konfigurasi jaringan manual. Penerbit menyebut pengaruh jaringan, resolusi, FPS, serta koneksi controller terhadap latensi. Informasi ini berguna sebagai daftar kebutuhan produk, bukan spesifikasi format paket jaringan atau bukti kelas dan metode APK. [Manual resmi PXPlay](https://streamingdv.github.io/pxplay/index.html).

Listing Google Play milik penerbit menyebut Android TV, konfigurasi layout, pemetaan tombol, beberapa profil, PiP, dan multi-window. Listing saat ditelusuri juga menyebut upscaling FSR dan versi 8.1.1; fitur itu tidak boleh diasumsikan tersedia di referensi v6.6.0. [Listing resmi PXPlay](https://play.google.com/store/apps/details?id=psplay.grill.com).

Ada perbedaan antar dokumen publik: manual yang ditelusuri masih menyebut minimum Android 5.0 dan registrasi pada jaringan lokal; halaman distribusi menyebut Android 6.0+, sementara catatan rilis Android 8.0.0 menyebut alur registrasi jarak jauh tertentu. Karena itu, persyaratan versi dan alur tersebut perlu diverifikasi terhadap versi produk tertentu sebelum dijadikan kontrak kompatibilitas. [Distribusi resmi](https://www.streamingdv.com/shop-list-ns.html), [catatan rilis resmi](https://github.com/streamingdv/PSPlay-Application-Hosting/releases).

**Batasan:** dokumentasi tersebut tidak menyediakan implementasi lengkap protokol konsol, registrasi, autentikasi, enkripsi, format transport, maupun mekanisme pemulihan sesi. Fitur konsol nyata dalam proyek ini memerlukan penyedia protokol independen yang sumber, lisensi, serta interoperabilitasnya dapat diperiksa.

## 2. Video melalui API Android publik

`MediaCodec` menyediakan antarmuka codec tingkat rendah. Decoder dapat dikonfigurasi dengan `MediaFormat` dan output `Surface`; ini memungkinkan frame ditampilkan tanpa aplikasi harus membaca setiap frame mentah ke Java. API memiliki aturan state, kepemilikan buffer, dan pelepasan resource. `setOutputSurface()` tersedia sejak API 23 dengan batasan kompatibilitas surface. `setParameters()` dapat gagal menerapkan sebagian parameter tanpa indikasi eksplisit, sehingga keberhasilan pemanggilan bukan bukti penurunan latensi. [Referensi MediaCodec](https://developer.android.com/reference/android/media/MediaCodec).

Android 11/API 30 menambahkan kontrol decoding latensi rendah. Dukungan perlu diperiksa melalui `CodecCapabilities.isFeatureSupported(FEATURE_LowLatency)`; konfigurasi memakai `MediaFormat.KEY_LOW_LATENCY` atau parameter runtime terkait. Fitur dapat memakai lebih banyak daya dan tidak menjamin semua perangkat mendukungnya. [Android 11: low-latency decoding](https://developer.android.com/about/versions/11/features#low-latency-decoding).

**Pilihan desain original:** pisahkan `VideoDecoder` dari transport. Terima unit data terkompresi beserta timestamp dari penyedia sesi, enumerasi kemampuan codec, dan gunakan surface milik UI. Jangan mengklaim codec yang dipilih pasti memakai hardware atau menjamin latensi tertentu tanpa pemeriksaan kemampuan serta pengukuran perangkat. Dukungan codec dan proses negotiation server tetap tanggung jawab penyedia protokol, bukan `MediaCodec`.

## 3. Audio melalui API Android publik

`AudioTrack.MODE_STREAM` menerima aliran audio terus-menerus melalui `write()`. Format buffer harus sesuai konfigurasi. `write()` bisa mengembalikan jumlah transfer parsial; aplikasi perlu menangani sisa data dan kode error. `getMinBufferSize()` memperkirakan batas minimum pembuatan track, bukan jaminan bebas underrun. `getUnderrunCount()` (API 24+) membantu mengamati kekurangan pasokan data. Mode performa latensi rendah tersedia, tetapi hasil tetap bergantung pada jalur audio perangkat. [Referensi AudioTrack](https://developer.android.com/reference/android/media/AudioTrack).

Aplikasi media/game perlu mengelola audio focus dan berhenti atau menyesuaikan playback saat focus berubah. Untuk target Android 15/API 35+, permintaan focus mensyaratkan aplikasi berada di posisi teratas atau menjalankan foreground service. [Panduan audio focus](https://developer.android.com/media/optimize/audio-focus).

**Pilihan desain original:** komponen output menerima PCM dengan sample rate, channel, encoding, serta timestamp yang eksplisit. Codec audio terkompresi, dekripsi, dan jitter buffer berada sebelum output. Jalankan penulisan audio di worker; batasi antrean dan tangani short write. Sinkronisasi A/V dan latensi harus diuji dengan media sendiri sebelum menghubungkan protokol konsol.

## 4. Controller Android

Panduan Android memisahkan tombol (`KeyEvent`, misalnya `onKeyDown`/`onKeyUp`) dari gerak joystick (`MotionEvent`, `onGenericMotionEvent`). Filter sumber input gamepad/joystick, baca axis yang tersedia, dan gunakan rentang `flat` perangkat untuk menangani dead zone. Variasi D-pad/trigger dan mapping antarperangkat perlu ditangani. [Panduan tindakan controller](https://developer.android.com/games/sdk/game-controller/controller-input).

`InputManager.InputDeviceListener` menyediakan callback perangkat ditambah, diubah, dan dihapus. [Referensi InputDeviceListener](https://developer.android.com/reference/android/hardware/input/InputManager.InputDeviceListener).

**Pilihan desain original:** normalisasi event menjadi satu snapshot kontrol, terapkan mapping profil, kemudian kirim lewat interface sesi. Saat perangkat hilang atau sesi ditutup, kosongkan tombol dan axis agar input tidak tertahan. UI diagnostik lokal dapat menunjukkan nilai input tanpa mengklaim input telah diterima konsol. Haptic, trigger adaptif, HID khusus, dan touchpad konsol memerlukan implementasi serta pengujian terpisah.

## 5. Lifecycle dan kesalahan

`onPause()` tidak berarti activity sudah tidak terlihat; activity dapat tetap terlihat dalam multi-window. Android menyarankan pekerjaan penghentian berat di `onStop()` dan melarang ketergantungan pada `onPause()` untuk network call atau transaksi berat. Resource harus mengikuti lifecycle yang sesuai pemakaiannya. [Panduan lifecycle Activity](https://developer.android.com/guide/components/activities/activity-lifecycle).

**Pilihan desain original:** definisikan state sesi (`IDLE`, `CONNECTING`, `STREAMING`, `STOPPING`, `FAILED`) di luar kode tampilan. Cleanup harus dapat dipanggil berulang dengan aman dan menghentikan worker serta media output secara terkoordinasi. Kehilangan surface, audio focus, controller, dan koneksi adalah peristiwa berbeda. Error yang dapat dipulihkan memerlukan kebijakan retry terbatas; error protokol yang belum didukung harus terlihat jelas. Log tidak boleh menyimpan PIN registrasi, token, kunci, atau payload sensitif.

## 6. Implikasi implementasi dan verifikasi

| Area | Dapat dibangun mandiri sekarang | Yang belum dibuktikan riset ini |
|---|---|---|
| UI/profil/settings | Form, validasi, penyimpanan lokal, pilihan kualitas | Kecocokan profil dengan konsol nyata |
| Controller | Pembacaan event, dead zone, normalisasi, diagnostik | Penerimaan paket kontrol oleh konsol |
| Video/audio | Wrapper API publik dan cleanup resource | Streaming konsol, sinkronisasi, latensi, stabilitas perangkat |
| Koneksi | Kontrak transport dan state sesi | Discovery, registrasi, autentikasi, wake-up, NAT traversal konsol |
| Error/log | Kategori error, pesan pengguna, logging tersanitasi | Pemulihan seluruh kegagalan jaringan/protokol nyata |

Untuk tahap awal, gunakan penyedia sesi demonstrasi yang diberi label jelas dan penyedia konsol yang mengembalikan error "belum diimplementasikan". Penyimpanan alamat konsol atau tampilan animasi demo bukan bukti koneksi Remote Play. Pemeriksaan build membuktikan kompilasi; pengujian perangkat diperlukan untuk membuktikan rendering, audio, input, lifecycle, dan koneksi konsol nyata.

Dokumen ini tidak menyimpulkan keaslian APK, komponen internal PXPlay, permission manifest, format jaringan, ataupun lisensi reverse engineering. Penilaian tersebut berada di luar sumber dan lingkup riset publik ini.
