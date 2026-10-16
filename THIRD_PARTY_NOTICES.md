# Dependensi dan provenance

Semua source aplikasi, test dan ikon vector di project ini ditulis original. Tidak ada source/aset APK referensi yang dimasukkan. Nama produk pihak lain hanya dipakai untuk menjelaskan konteks dan tidak menyatakan afiliasi.

| Komponen | Versi | Penggunaan | Lisensi / sumber primer |
|---|---|---|---|
| Gradle Wrapper | 8.11.1 | Script/JAR build resmi di repo; distribusi diunduh sesuai checksum | [Apache-2.0](https://github.com/gradle/gradle/blob/v8.11.1/LICENSE) |
| Android Gradle Plugin | 8.9.2 | Build saja, diambil Google Maven | [Android tools base, Apache-2.0](https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/NOTICE) |
| JUnit | 4.13.2 | Unit test, tidak dibundel ke APK aplikasi | [EPL-1.0](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt) |
| Hamcrest Core | 1.3 (transitif JUnit) | Unit test | [BSD](https://github.com/hamcrest/JavaHamcrest/blob/v1.3/LICENSE.txt) |
| AndroidX Test runner / ext JUnit | 1.6.2 / 1.2.1 | Instrumentation APK terpisah | [Apache-2.0](https://github.com/android/android-test/blob/main/LICENSE) |
| Android SDK/platform | API 35 | Compile dan API perangkat, tidak disalin ke repo | [SDK terms](https://developer.android.com/studio/terms) |

Dependensi transitif dikelola Gradle melalui Google Maven/Maven Central. Repo tidak memuat binary SDK, APK tes/aplikasi atau cache dependensi. Lisensi source original project ada di LICENSE dan tidak menggantikan ketentuan komponen di atas. Untuk distribusi binary/penambahan library, tinjau ulang dependency report dan kewajiban lisensi versi yang dipakai.
