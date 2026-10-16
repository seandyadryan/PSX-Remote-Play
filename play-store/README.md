# PSX Remote Play — English store preparation

Developer: **PT Deploy Dulu Pulang Nanti**
Support: **seandyadryan2@gmail.com**

This kit describes **0.2.1-lan-experimental**. It is preparation for testing and listing review, not evidence that physical PS4/PS5 connectivity works or that Google Play has approved the app. The English listing explicitly discloses the lack of physical-console verification. Do not remove that disclosure before obtaining real console results.

## Copy and visual assets

| Item | File | Specification |
|---|---|---|
| Short description | [en-US/short-description.txt](en-US/short-description.txt) | 66 characters, excluding trailing newline; limit 80 |
| Full description | [en-US/full-description.txt](en-US/full-description.txt) | 3,040 characters, excluding trailing newline; limit 4,000 |
| Feature graphic | `feature-graphic.png` | Opaque PNG, 1024 × 500 |
| Phone screenshots | `screenshots/phone/01-home.png` through `04-registration.png` | Four actual app captures, 1080 × 1920, 9:16 |
| Desktop/landscape previews | `screenshots/desktop/01-home.png` through `04-registration.png` | Four actual Android landscape captures, 1920 × 1080, 16:9 |
| Privacy policy | [../privacy-policy/index.html](../privacy-policy/index.html) | Responsive English HTML; no analytics, scripts or external fonts |

Screenshots show the app's real English UI. The registration screen uses a clearly labeled setup example and an RFC 5737 example IP address. No successful console discovery, registration, wake-up or streamed gameplay is fabricated. No PSN credentials are entered. The local demo is not passed off as a game stream.

The desktop images show the Android app rendered in a wide emulator viewport. They do **not** establish a native Windows/macOS application, Google Play Games on PC eligibility or tested ChromeOS support. Use this optional listing section only if the distribution surface is applicable and after testing on the relevant device. No console-owned game images or Sony/PXPlay artwork is included.

The original controller/phone illustration was generated with the built-in **imagegen** tool, then used unchanged as an image layer in the code-native [feature-graphic.html](feature-graphic.html) composition. Chromium renders the final banner at exactly 1024 × 500. The full generation prompt is saved in [artwork/prompt.txt](artwork/prompt.txt). Actual screenshots are captured by Android `screencap`; they are not AI-generated interfaces.

## GitHub runner and signing

[Workflow](../.github/workflows/android.yml): GitHub-hosted **windows-2022**, JDK 21, Gradle 8.11.1, AGP 8.10.1, platform/target API 36, Build Tools 35.0.0, NDK 28.2.13676358 and CMake 3.22.1. JavaScript actions are pinned to commit hashes. No self-hosted runner service is installed on the developer's PC.

The workflow runs core tests before native compilation, then lint and debug/test APK builds. Trusted push/manual runs also build a signed release APK and AAB using repository secrets. Pull requests build without signing secrets. Actions artifacts contain only explicit report/APK/AAB paths and expire after 14 days. The job deletes its temporary keystore even if the build fails. It does **not** publish an app or create a Google Play release.

Configured repository secret names:

- `PSX_KEYSTORE_BASE64`
- `PSX_STORE_PASSWORD`
- `PSX_KEY_ALIAS`
- `PSX_KEY_PASSWORD`

Local keystore: `D:\KEYSTORE\Psx_Remote_Play.jks`
Alias: `psx_remote_play`
Algorithm: RSA 4096 / SHA256withRSA; validity 10,000 days.
Certificate SHA-256: `36:3E:C9:7E:A4:24:6D:C3:B9:AE:0F:92:E5:14:D2:68:2C:E3:29:5E:AD:60:64:8B:2C:13:1E:00:2C:07:C6:F7`
Public certificate for upload-key registration: `D:\KEYSTORE\Psx_Remote_Play-upload-certificate.der`.

The password and private keystore are not stored in the repository. Keep a private backup of the keystore and password. With Play App Signing, this can be your **upload key**; the app-signing key used by Google Play may be different. See [Android signing documentation](https://developer.android.com/studio/publish/app-signing).

For a local signed build, set `JAVA_HOME` to your JDK 21 path and run:

```powershell
.\scripts\build-release.ps1
```

The script prompts securely if the password environment variables are not already set. It restores previous signing environment variables after the build. Gradle refuses a release build if required signing configuration is missing. Results:

- `app/build/outputs/bundle/release/app-release.aab` — upload bundle for a Play testing track.
- `app/build/outputs/apk/release/app-release.apk` — signed APK for direct testing.

Use a separate test installation when switching between debug and release certificates; Android will not update a debug-signed install with a release-signed APK.

## Privacy policy publication

Host [index.html](../privacy-policy/index.html) at a stable public HTTPS URL, then enter that exact URL in Play Console. This task supplies the file and an offline in-app copy; it does not configure a public hosting service. Google requires a publicly accessible privacy policy, including the developer/app identity, contact, data handling, retention and deletion information. The generated page reflects this app's current source: direct LAN traffic, local preferences, encrypted pairing and optional email support. Recheck it if you add analytics, ads, billing, a server, PSN sign-in or other data flows. [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)

Complete Data safety answers from the actual release and any added SDKs; the HTML is not a substitute for the Play Console questionnaire. Confirm the listing's hardware requirements and support limitations before charging users. Current target API requirements are documented by [Google Play](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-EN); preview asset rules are [here](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en-en).

## Recreate screenshots

Only run the opt-in capture utility on a disposable emulator. It creates and removes its own example console record, without registering or sending packets to that example address. Set a viewport of 1080 × 1920 with density 320 for phone images, or 1920 × 1080 with density 240 for landscape images. Disable window/transition animations for deterministic captures. Then run the instrumentation APK with:

```text
adb -s <owned-emulator> shell am instrument -w -e class com.deploydulupulangnanti.psxremoteplay.StoreScreenshotCapture -e storeScreenshots true -e capturePrefix phone com.deploydulupulangnanti.psxremoteplay.test/androidx.test.runner.AndroidJUnitRunner
```

Use `capturePrefix desktop` for the wide viewport. Pull `/data/local/tmp/psx-store-<prefix>-01-home.png` and the corresponding discovery/profile/registration files to the listed output directory. The capture utility is skipped in normal instrumentation runs.

Validation results and build limitations are retained in [VALIDATION.md](../docs/VALIDATION.md). Neither automated test success nor generated listing assets replace real-console closed testing.
