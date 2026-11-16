# SavAudio Separator

Send each app's sound to its own output on Android 10 and later. Pin your music app to a Bluetooth speaker while everything else stays on the phone.

**Download:** [SavAudio-Separator-v1.0.0.apk](https://github.com/saharrison2100/savaudio-separator/releases/download/v1.0.0/SavAudio-Separator-v1.0.0.apk)

## How it works

Android gives third party apps no public API to redirect another app's audio. That needs system permissions. SavAudio Separator uses the one route that is open to a normal app:

1. You pick an app and an output (speaker, wired, Bluetooth, Bluetooth LE Audio, USB, HDMI).
2. A foreground service captures that app's playback by UID with `AudioPlaybackCapture` (the system asks for your consent each session).
3. The captured sound is replayed on the output you picked with `AudioTrack.setPreferredDevice`.
4. An `AudioDeviceCallback` watches outputs. If the pinned output disconnects the route is unpinned, and it comes back when the device reconnects.

Nothing is recorded, stored or sent anywhere. The app has no internet permission.

## Limits you should know

- Android still plays the original sound on the default output. If you hear it twice, lower the media volume there.
- Apps that block capture (many DRM video and some music apps) and phone calls cannot be routed.
- Expect a small delay on the routed output, so it suits music and podcasts better than video.
- Routing and replay add no mixing: one app, one output.
- v1.0.0 has been compiled, linted, unit tested and signed. It has not yet been run on a physical phone, so treat the first install as a test.

## Project layout

| Module | What it holds |
| --- | --- |
| `core` | Device kinds, route rules, the route planner and the rule codec. Pure logic with unit tests. |
| `service` | Foreground service, per-app capture and replay sessions, device monitor, rule storage. |
| `app` | Jetpack Compose Material 3 screen: app list, output picker, start and stop. |

## Build

```
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Signed release (JDK 17):

```
export KEYSTORE_FILE=/path/release.keystore KEYSTORE_PASSWORD=... KEYSTORE_ALIAS=... KEY_ALIAS_PASSWORD=...
./gradlew assembleRelease
```

## CI

`ci/github-workflows/release.yml` runs lint, unit tests, signs the APK and publishes a GitHub Release on every `v*` tag. Move it to `.github/workflows/release.yml` to switch it on, and add the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEYSTORE_ALIAS`, `KEY_ALIAS_PASSWORD`.

## Release signing (read this before shipping an update)

Every release must be signed with the same key as v1.0.0, otherwise Android treats the update as a different app and users have to uninstall first.

- The key is stored as GitHub Actions secrets on this repo (Settings, Secrets and variables, Actions): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEYSTORE_ALIAS`, `KEY_ALIAS_PASSWORD`.
- Key alias: `savaudio`. Certificate SHA-256: `58:9A:2E:A5:E0:45:07:92:70:E1:6D:EC:A0:04:16:96:5E:DC:B0:A6:2E:1D:47:04:EB:A9:98:34:55:5F:65:4B`.
- GitHub secrets are write-only. They cannot be downloaded or viewed by anyone; only a workflow running in this repo can use them.
- To build a signed release, move `ci/github-workflows/release.yml` to `.github/workflows/release.yml` and push a tag such as `v1.0.1`. The workflow decodes the keystore from the secrets, signs the APK and publishes the release.
- `app/build.gradle.kts` reads the signing values from the environment variables `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEYSTORE_ALIAS` and `KEY_ALIAS_PASSWORD`.
- Check a built APK against the fingerprint above with `apksigner verify --print-certs`.
