# Arsitektur original

## Batas modul dan data

```mermaid
flowchart TD
    UI[MainActivity / StreamActivity] --> Repo[AppRepository: alamat dan profil lokal]
    UI --> Session[SessionController: core Java]
    UI --> Discovery[ConsoleDiscovery: UDP worker / deadline / cancel]
    Discovery --> Protocol[DiscoveryProtocol: SRCH dan parser bounded]
    Input[AndroidController] --> Normalize[ControllerInput]
    Normalize --> Session
    Session --> Contract[SessionTransport]
    Contract --> Demo[DemoTransport: simulasi tanpa jaringan]
    Contract --> Stub[ConsoleTransport: error belum tersedia]
    UI --> Canvas[DemoCanvas: animasi lokal]
    Remote[RemoteSessionActivity] --> Native[NativeStreamConnection / JNI / Chiaki]
    Native --> Video[MediaCodecVideoDecoder ke Surface]
    Native --> Audio[Opus / PcmAudioSink ke AudioTrack]
    Register[RegistrationActivity] --> Vault[RegistrationStore / Android Keystore]
    Vault --> Native
```

`Device` adalah entri buku alamat, bukan konsol terdaftar. Host menerima IPv4, IPv6 hex tanpa zone/bracket/IPv4 suffix, atau hostname ASCII tanpa port/URL. Pemeriksaan tidak melakukan DNS. Profile memuat resolusi/FPS/bitrate/audio/dead zone; hanya dead zone yang dipakai demo. Dua preset selalu tersedia. Profil custom dapat dihapus; preset dapat diubah tanpa menghilangkan default fallback.

AppRepository menyimpan JSON buku alamat/profil dalam SharedPreferences privat, tanpa rahasia. Record tidak valid dilewati dengan event log aman. `apply()` memperbarui memori langsung dan menulis disk asinkron; belum ada pelaporan disk-full/durability buku alamat. Hasil pairing disimpan terpisah oleh RegistrationStore memakai AES-GCM, Android Keystore dan commit yang diperiksa hasilnya. Backup/transfer ditolak melalui manifest dan aturan ekstraksi. Pemetaan identitas discovery mempertahankan ID perangkat yang menjadi AAD ciphertext pairing.

## State sesi

`IDLE → CONNECTING → DEMO_ACTIVE / STREAMING / ERROR`. `close()` menaikkan generation dan menghasilkan `STOPPED`; sesi dapat dibuka kembali. Callback dari generation lama tidak boleh menghidupkan sesi yang telah dihentikan. Kegagalan transport atau input menutup transport dan menghasilkan pesan aman.

Semua pemanggilan SessionController/SessionTransport dan callback harus dijalankan di satu owner thread (main thread dalam app). Penyedia jaringan mendatang harus melakukan I/O di worker dan memarshal hasil ke owner. Transport juga harus mengatur cancellation, timeout dan cleanup; generation guard mencegah callback basi mengubah UI, tetapi tidak menggantikan pembatalan I/O.

DemoTransport hanya memanggil readiness lokal dan menerima snapshot input. ConsoleTransport adalah stub lama yang dipertahankan untuk kontrak/test demo; MainActivity tidak memakainya untuk alur koneksi nyata. RemoteSessionActivity menggunakan NativeStreamConnection, dengan lifecycle native/media dan callback yang terpisah dari SessionController demo. Label streaming hanya dipasang setelah unit video nyata diterima codec; koneksi loopback tanpa konsol tidak menghasilkan frame.

StreamActivity menutup demo dan listener controller pada pause, mematikan animasi/keep-screen-on dan membuka kembali pada resume. Kehilangan focus dan controller menyebabkan reset input. Kebijakan ini konservatif untuk demo; PiP, multi-window aktif dan sesi background belum didukung. Belum ada Service, WorkManager atau koneksi persisten. Pengulangan paket discovery berjalan terbatas pada worker scanner, terpisah dari sesi demo.

## Input

AndroidController memfilter sumber gamepad/joystick, memetakan A/B/X/Y, shoulder/trigger digital, select/start, stick click, D-pad dan axis X/Y/Z/RZ. Trigger analog memakai LTRIGGER/RTRIGGER atau BRAKE/GAS. D-pad HAT dan key digabungkan. Dead zone adalah maksimum preferensi profil dan `MotionRange.flat`, dengan rescaling kontinu ke [-1,1]. Perangkat aktif berganti dengan reset input; ini bukan multi-controller terpisah.

Bit buttons 0..15 berturut-turut: A, B, X, Y, L1, R1, L2, R2, SELECT, START, THUMBL, THUMBR, UP, DOWN, LEFT, RIGHT. Mapping ini internal, bukan format paket konsol. Belum ada remapping pengguna, adaptive trigger, rumble, HID/USB khusus atau kontrol sentuh virtual.

## Video dan audio

VideoDecoder adalah kontrak worker thread, terpisah dari transport dan UI. MediaCodecVideoDecoder memilih decoder yang mendukung seluruh MediaFormat, menerima Surface valid serta complete compressed access unit dengan timestamp mikrodetik dan flag MediaCodec. Caller menyediakan codec-specific data (misalnya AVC SPS/PPS), framing dan format yang benar. ByteBuffer input caller tidak dimutasi. Bila input codec belum tersedia `submit()` mengembalikan false, caller harus retry/menjalankan drop policy. `drain()` harus dipanggil berkala meski tidak ada input baru sampai EOS selesai; adapter saat ini tidak mengekspos event EOS/format/output-frame ke caller dan belum cocok untuk pipeline produksi.

KEY_LOW_LATENCY hanya dipakai pada API 30+ bila capability mendukung. Pada API 29+ akselerasi bisa dibaca dari codec; pada API 26–28 belum diketahui. Pemilihan decoder tidak memaksa hardware. `close()` harus dipanggil sebelum Surface dihancurkan dan setelah worker berhenti; caller bertanggung jawab terhadap ownership Surface.

PcmAudioSink menerima PCM16 44,1/48 kHz mono/stereo, nonblocking write dan mengembalikan jumlah sample terpakai. NativeStreamConnection menerima hasil decode Opus dari provider, memakai antrean terbatas serta retry short-write dengan deadline; RemoteSessionActivity mengelola audio focus dan berhenti ketika focus hilang. Demo tetap tidak memutar audio. Resampling, clock sync dan pengukuran latensi belum tersedia.

## Logging dan ekstensi

AppLog hanya dipanggil dengan event yang dikendalikan source, seperti state sesi dan record penyimpanan yang tidak valid. Tidak log host, nama, PIN/token, raw exception atau payload. Error pengguna tidak mengekspos internal transport.

Provider streaming LAN sudah diintegrasikan melalui JNI Chiaki. Video AVC, audio Opus, input, PIN login dan cleanup worker ditangani pada jalur koneksi; interoperabilitas fisik dan sinkronisasi produksi belum diverifikasi. Detail alur, batas, build source dan bukti APK ada di [CONNECTION.md](CONNECTION.md). Discovery tetap terpisah dari sesi, dengan laporan [DISCOVERY.md](DISCOVERY.md).

## Discovery lokal

DiscoveryProtocol dan ConsoleDiscovery berada di core Java tanpa dependensi Android. Scanner menjalankan I/O pada worker; listener harus cepat dan memarshal UI sendiri. Close membatalkan run dan menutup socket; deadline monotonic membatasi scan 15 detik. MainActivity memakai Handler dan generation guard; pause membatalkan scan. Hasil adalah advertisement tanpa autentikasi, bukan bukti pairing. Penyimpanan eksplisit mengaitkan host-id dengan ID perangkat yang tetap, menggabungkan alamat manual dan mempertahankan nama custom. Discovery tidak mengubah state sesi atau menyalakan konsol.
