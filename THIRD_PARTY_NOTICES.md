# Dependensi dan provenance

Source Java aplikasi, test, ikon vector, dan JNI adapter di project ini ditulis original. Native protocol provider dibangun terpisah dari source publik Chiaki-ng dan dependensinya. Tidak ada source/aset/library APK referensi yang dimasukkan. Nama produk pihak lain hanya dipakai untuk menjelaskan konteks dan tidak menyatakan afiliasi.

| Komponen | Versi | Penggunaan | Lisensi / sumber primer |
|---|---|---|---|
| Gradle Wrapper | 8.11.1 | Script/JAR build resmi di repo; distribusi diunduh sesuai checksum | [Apache-2.0](https://github.com/gradle/gradle/blob/v8.11.1/LICENSE) |
| Android Gradle Plugin | 8.9.2 | Build saja, diambil Google Maven | [Android tools base, Apache-2.0](https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/NOTICE) |
| JUnit | 4.13.2 | Unit test, tidak dibundel ke APK aplikasi | [EPL-1.0](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt) |
| Hamcrest Core | 1.3 (transitif JUnit) | Unit test | [BSD](https://github.com/hamcrest/JavaHamcrest/blob/v1.3/LICENSE.txt) |
| AndroidX Test runner / ext JUnit | 1.6.2 / 1.2.1 | Instrumentation APK terpisah | [Apache-2.0](https://github.com/android/android-test/blob/main/LICENSE) |
| Android SDK/platform | API 35 | Compile dan API perangkat, tidak disalin ke repo | [SDK terms](https://developer.android.com/studio/terms) |
| Chiaki-ng | 1.6.5, `ca5379f4504af6e63cf5d32b5626de2425b2382e` | Native registrasi/sesi Remote Play | [AGPL-3.0 + OpenSSL exception](https://github.com/streetpea/chiaki-ng/blob/ca5379f4504af6e63cf5d32b5626de2425b2382e/LICENSES/AGPL-3.0-only-OpenSSL.txt) |
| Mbed TLS | 2.28.10 | Kriptografi native | [Apache-2.0](https://github.com/Mbed-TLS/mbedtls/blob/v2.28.10/LICENSE) |
| Nanopb | `afc499f9a410fc9bbf6c9c48cdd8d8b199d49eb4` | Protobuf native | [zlib](https://github.com/nanopb/nanopb/blob/afc499f9a410fc9bbf6c9c48cdd8d8b199d49eb4/LICENSE.txt) |
| Jerasure / GF-Complete | Commit dipin di `dependencies.json` | Koreksi paket media | [BSD-3-Clause Jerasure](https://github.com/ceph/jerasure/blob/de1739cc8483696506829b52e7fda4f6bb195e6a/COPYING), [GF-Complete](https://github.com/ceph/gf-complete/blob/a6862d10c9db467148f20eef2c6445ac9afd94d8/COPYING) |
| Opus | 1.5.2 | Decoder audio native | [BSD-3-Clause + additional notices](https://github.com/xiph/opus/blob/v1.5.2/COPYING) |

Dependensi transitif dikelola Gradle melalui Google Maven/Maven Central. Repo tidak memuat binary SDK, APK tes/aplikasi atau cache dependensi. Lisensi source original project ada di LICENSE dan tidak menggantikan ketentuan komponen di atas. Untuk distribusi binary/penambahan library, tinjau ulang dependency report dan kewajiban lisensi versi yang dipakai.

Native source archives diambil oleh `scripts/bootstrap-native.ps1` dari URL dan SHA-256 pada `app/src/main/cpp/dependencies.json`, lalu dibangun oleh Android NDK. Full license text dan petunjuk corresponding source dibundel di `app/src/main/assets/licenses` dan `NATIVE_SOURCE_NOTICE.txt`. Generated `takion.pb.c/.h` berasal dari schema Chiaki (AGPL) dan generator Nanopb yang dipin. Distribusi aplikasi gabungan yang mengandung provider ini harus memenuhi AGPL termasuk penyediaan complete corresponding application/build source; LICENSE MIT existing tetap berlaku untuk source originalnya.
