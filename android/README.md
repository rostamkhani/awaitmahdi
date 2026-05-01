# Await Mahdi Android

Native Android Studio client for the Await Mahdi salavat counter.

## What it includes

- Kotlin + Jetpack Compose single-activity app.
- RTL Persian UI with the same dark theme, counters, salavat button, info dialog, login/register, logout, and user share flow as the web frontend.
- Local persistence for `guest_uuid`, JWT user data, cached stats, and API base URL using DataStore.
- Backend integration with the existing FastAPI endpoints:
  - `POST /heartbeat`
  - `POST /login`
  - `POST /register`
  - fallback `GET /stats`/`POST /sync` if `/heartbeat` is missing.
- 12-second heartbeat sync and monotonic stats update behavior matching the web frontend.

## Open in Android Studio

1. Open Android Studio.
2. Choose **Open** and select the `android/` folder.
3. Let Gradle sync finish.
4. Run the `app` configuration on an emulator or device.

## Build APK

From this folder:

```bash
./gradlew assembleDebug
```

The debug APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For a release APK:

```bash
./gradlew assembleRelease
```

The unsigned release APK will be generated at:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

## Backend URL

The default API URL is:

```text
http://10.0.2.2:8000
```

That works for the Android emulator when the backend is running on the host machine at port `8000`.

For a physical phone, open the gear button in the app and set the backend URL to the server IP/domain, for example:

```text
http://192.168.1.20:8000
```

Cleartext HTTP is enabled because the current backend development setup uses HTTP. For production, use HTTPS and restrict cleartext traffic.
