# AntiTheftDemo project notes

Updated 2026-10-20 after phased implementation. See IMPLEMENTATION_REPORT.md for decisions, phase results, limits and physical-device checks. Recheck source before future edits.

## Build and workspace

- Single Android app module, package com.example.antitheftdemo, Kotlin/Compose/Material 3/Navigation Compose, MVVM.
- Existing versions retained: AGP 9.1.1, Gradle 9.4.1, Kotlin Compose plugin 2.2.10, daemon JDK 21, Java 11, minSdk 24, compile/targetSdk 37.
- Added a direct lifecycle-runtime-compose dependency using the existing lifecycle version. No datastore/crypto/media dependency was needed.
- Many user changes were already staged/unstaged before this task. No reset, clean, commit or push was performed. Retain existing XML/resources even if lint reports them unused.

## App flow and persistence

- MainActivity -> AppNavigation -> Splash -> first unfinished setup step: Language -> Intro -> Permission -> PIN create/confirm -> Home.
- AppPreferences stores language choice and setup checkpoints. Returning users go Splash -> Home. Completed setup destinations are removed from the back stack.
- Settings icon opens a small menu for the existing Language and Permission screens. No unverified standalone Settings layout was invented.
- Language choice is saved/preselected; translations were not provided, so UI strings remain English.
- Figma was inaccessible. Existing visuals are the design baseline; exact design matching is unverified.

## PIN security

- Keep PinCodeScreen and PinCodeViewModel. No Security Question and no default PIN.
- PinStore uses a random salt and PBKDF2 verifier encrypted with an Android Keystore AES-GCM key; no plaintext PIN persistence/logging.
- Four ASCII digits, explicit confirmation/errors, IO dispatcher for storage/derivation, serialized verification, persisted throttling from the fifth failure (30 seconds up to 15 minutes).
- Credentials/setup/runtime/request-history preferences excluded from backup/device transfer. Missing/invalid key fails closed.
- PIN window uses FLAG_SECURE and NumberPassword keyboard. The existing visible four-digit display is retained; imePadding fixes API 37 keyboard overlap.
- Navigation consumes an ephemeral session-bound proof once via SavedStateHandle StateFlow. Wrong PIN or cancel never stops monitoring. Consumed proofs cannot stop later sessions.

## Detection architecture

- DetectionScreen -> DetectionViewModel -> application-scoped DetectionRepository -> MonitoringService -> AndroidMonitor -> AlarmController/AndroidAlarmPlayback.
- DetectionController is pure Kotlin with StateFlow. UI states ACTIVE, SECURED, STOP are distinct from starting, monitoring and actual alarm playback states.
- One armed mode at a time. Another mode cannot replace it without PIN deactivation. Screen changes do not own monitoring resources.
- ACTIVE tap requests service; SECURED only after registration succeeds; PIN stop -> STOP; STOP tap -> ACTIVE as in original behavior.
- Six implementations: proximity near-to-far, filtered accelerometer motion, charger disconnect, loss of headphone outputs, Wi-Fi adapter/network loss, full-battery crossing.
- Initial observations are baselines. Battery is single-trigger per charging cycle. Network/headphone handoffs are debounced. Sensor support/registration errors surface without false SECURED UI.
- BroadCastDetection/Manager are service-owned managed receiver helpers. Screens do not register receivers. No manifest-exported receiver remains.

## Service and alarm

- MonitoringService is non-exported, specialUse FGS with subtype explanation, visible ongoing notification and PendingIntent opening the protected screen/PIN.
- User-triggered starts only, START_NOT_STICKY, no boot/restart receiver. Process loss is reported on next open and requires reactivation.
- Motion/proximity hold a partial wake lock while armed; this costs battery. Sensor/broadcast/network/audio callbacks, wake lock, playback and vibration are released on stop/failure/destruction.
- Alarm playback uses the system alarm sound because no raw audio asset exists. One playback per session; repeated events cannot reset PLAYING to PREPARING.
- Audio failure is shown separately while monitoring stays armed and still requires PIN. Android audio policy, mute/DND and device restrictions can prevent audible alerts.
- Notification permission is real and lifecycle-refreshed. Denial permits finishing setup but blocks arming until notification access is allowed. No location/Bluetooth scan/storage/microphone permissions were added.

## Validation

- Gradle Wrapper assembleDebug, testDebugUnitTest and lintDebug have passed after phased changes. Lint has zero errors; legacy/resource/dependency warnings remain.
- Five instrumentation tests passed on API 37 emulator for real Keystore/storage/lockout/onboarding persistence using isolated preference namespaces.
- scripts/emulator_smoke.py exercises onboarding, PIN mismatch/correctness, relaunch, background monitoring and alarm transitions. It refuses an existing user PIN and never clears app data. --resume is for its own fixture only.
- Final emulator smoke outcomes and current test counts are in IMPLEMENTATION_REPORT.md. Physical sensors/headsets/OEM background behavior still need the documented hardware checks.
