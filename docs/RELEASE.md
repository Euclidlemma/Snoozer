# Releasing Snoozer

## The upload key (once)

Google Play signs the app people install with its own key (Play App Signing). You sign each
upload with an **upload key** that only you have. Create it once, outside this repository:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore "$HOME\keys\snoozer-upload.jks" -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

It asks for a password and your name or organization. Then:

1. Copy `keystore.properties.example` to `keystore.properties` at the project root and fill it in.
   `storeFile` can be an absolute path, such as `C:/Users/<you>/keys/snoozer-upload.jks`.
   Git ignores `keystore.properties`, `*.jks` and `*.keystore`; never commit them.
2. Back up the `.jks` file and its password somewhere safe, apart from this PC. If the upload
   key is lost, Google can reset it, but only through a support request.

## Building an upload

```powershell
.\gradlew bundleRelease
```

The bundle is `app/build/outputs/bundle/release/app-release.aab`, signed with the upload key.
Raise `versionCode` in `app/build.gradle.kts` for every upload (Play refuses a number it has
seen), and `versionName` as you like ("1.0.1").

## Database migration tests

When the database changes, raise `version` in `AlarmDatabase`, add a `Migration` to
`MIGRATIONS`, commit the new `app/schemas/.../<version>.json`, and extend `MigrationTest`.
Run the tests on an **emulator only**: Gradle's `connectedAndroidTest` installs on every
attached device and uninstalls the app afterwards, which would delete a phone's snoozers.

```powershell
.\gradlew assembleDebug assembleDebugAndroidTest
adb -s emulator-5554 install -r -t app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class com.example.snoozer.MigrationTest com.inseokim.snoozer.test/androidx.test.runner.AndroidJUnitRunner
```

## Trying the shrunk build on a phone without the upload key

```powershell
.\gradlew -PlocalRelease assembleRelease
```

This signs the release APK with the debug key, so it installs over a debug build and keeps the
phone's data. Play rejects debug-signed uploads, so this build can't be shipped by mistake.
