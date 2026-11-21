# Building & installing Free Global VPN

Everything used is free: Android Studio, the Android SDK/NDK, Gradle, and open-source libraries.

## 1. Requirements

| Tool | Version used & verified |
|---|---|
| Android Studio | a 2026 release that supports AGP 9.5 (or command line only) |
| JDK | 17 or newer (Android Studio's bundled JBR is fine; verified with JDK 21) |
| Android SDK Platform | **android-37.2** (`compileSdk 37.2`) – also android-37.0 for the `openvpn` module |
| Build-Tools | 37.0.0 |
| **NDK** | **30.0.14904198** (needed to compile OpenVPN + OpenSSL) |
| **CMake** | **3.31.6** (CMake 4.x does not accept the upstream `cmake_minimum_required(3.4.1)`) |
| Gradle | 9.7.1 (wrapper included) |

Android Studio installs the NDK and CMake automatically the first time you sync if you accept the
licenses. From the command line:

```bash
sdkmanager "platforms;android-37.0" "platforms;android-37.2" "build-tools;37.0.0" \
           "ndk;30.0.14904198" "cmake;3.31.6" "platform-tools"
```

No SWIG, no OpenVPN3 and no extra git submodules are needed: the `openvpn` module builds ics-openvpn's
UI-less "skeleton" variant with the OpenVPN 2.x engine only (`-DFGV_SKELETON=ON`, see
`openvpn/upstream/cmake-skeleton.patch`).

## 2. Open & build in Android Studio

1. *File → Open…* → select the `FreeGlobalVPN` folder.
2. Let Gradle sync (first sync downloads dependencies; first build compiles OpenSSL + OpenVPN for
   4 ABIs – expect 10–30 minutes once, later builds are incremental).
3. Select the `app` run configuration and press ▶ on a device or emulator.

## 3. Build an APK from the command line

```bash
# Linux / macOS
./gradlew :app:assembleRelease
# Windows
gradlew.bat :app:assembleRelease
```

Outputs in `app/build/outputs/apk/release/`:

| File | Use |
|---|---|
| `app-universal-release.apk` | works on every phone |
| `app-arm64-v8a-release.apk` | modern phones (smallest download) |
| `app-armeabi-v7a-release.apk` | older 32-bit phones |
| `app-x86_64-release.apk`, `app-x86-release.apk` | emulators |

Debug build: `./gradlew :app:assembleDebug` (application id `com.moni.freeglobalvpn.debug`, can be
installed next to the release build).

Unit tests: `./gradlew :app:testDebugUnitTest`

## 4. Release signing (required before publishing)

Without a keystore the release APK is signed with the **debug key** – fine for testing, NOT for the
Play Store. Create your own key once and keep it safe:

```bash
keytool -genkeypair -v -keystore ~/freeglobalvpn-release.jks -alias fgv \
        -keyalg RSA -keysize 4096 -validity 10000
```

Then add to `~/.gradle/gradle.properties` (never commit this):

```
FGV_KEYSTORE=/home/you/freeglobalvpn-release.jks
FGV_KEYSTORE_PASSWORD=********
FGV_KEY_ALIAS=fgv
FGV_KEY_PASSWORD=********
```

For Google Play upload an App Bundle: `./gradlew :app:bundleRelease`.

## 5. Install on a phone

**Via USB (adb):**
1. On the phone: *Settings → About phone →* tap *Build number* 7× → *Developer options → USB debugging*.
2. `adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk`

**Without a computer:** copy the APK to the phone, open it in Files, allow *Install unknown apps* for
that file manager when prompted, then *Install*.

**First run:** accept the disclosure → tap the big button → Android shows *"Connection request"* → *OK*.
On Android 13+ also allow notifications (the VPN status notification).

## 6. Licensing reminder

The app links ics-openvpn and OpenVPN, which are **GPL v2**. If you distribute the APK (Play Store,
website, sharing the file) you must also make the complete source code of your version available
under the GPL v2. The whole project is therefore licensed GPL v2 (see `LICENSE`).
