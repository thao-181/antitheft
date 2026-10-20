# AntiTheftDemo — implementation and validation

## Scope

Continued the existing project and its Compose screens/assets. No commit or push. Existing staged/unstaged work was preserved. SDK 37, minSdk 24, AGP 9.1.1, Gradle 9.4.1, Kotlin Compose plugin 2.2.10 and JDK 21 were retained. Added a direct lifecycle-runtime-compose dependency using the existing lifecycle version.

Figma could not be opened by the available browser. Existing layout, illustrations, state drawables, backgrounds and PIN digit display were used as the design baseline. Exact Figma spacing, typography, Settings design and transitions have not been independently verified. Functional additions are error/status messages, an existing-screen Settings menu, notification navigation and keyboard insets on PIN.

## Phase record

| Phase | Main files | Result |
| --- | --- | --- |
| 1 | AppNavigation, Splash, LanguageScreen, PermissionScreen, AppPreferences, PIN setup | Connected onboarding; checkpoint persistence; returning users skip completed setup; completed screens removed from back stack. Build/unit tests passed. |
| 2 | PinStore, PinPolicy, PinCodeViewModel/Screen, navigation, backup XML | Real PIN creation/confirmation/verification, salted encrypted verifier, persistent throttling, one-use verification result. Build/unit tests passed. |
| 3 | DetectionController/Repository/ViewModel/Screen, Home, navigation | Application-scoped state, one active mode, startup acknowledgement, session-bound authorization, lifecycle-aware state collection. Build/unit tests passed. |
| 4 | AndroidMonitor, DetectionRules, BroadCastDetection/Manager, manifest | Six monitor implementations, managed registration/cleanup and transition-based conditions. Build/unit tests passed; physical hardware coverage remains below. |
| 5 | AlarmController, AndroidAlarmPlayback | Single system-alarm playback, vibration, actual playback status and resource cleanup. Build/unit tests passed. |
| 6 | MonitoringService, repository, MainActivity, manifest | User-started specialUse FGS, visible notification, PIN navigation, wake-lock cleanup, no automatic restart. Build/unit tests passed. |
| 7 | PermissionScreen | Actual notification permission, denial/settings handling, refresh on resume. Build/unit tests passed. |
| 8 | HomeScreen/HomeItem, navigation | Six mapped cards, existing Language/Permission menu, protected-session shortcut. Build/unit tests passed. |
| 9 | Lifecycle collection, PIN IME handling, callbacks and imports | Duplicate receivers removed, stale network callbacks guarded, secure PIN window, setup retry handling. Lint errors fixed; resources/XML retained. |
| 10 | JVM tests, PinStoreInstrumentedTest, emulator_smoke.py | See the validation record below. |

## Runtime decisions

- Flow: Splash → first unfinished onboarding step → PIN setup when missing → Home. Completion survives process recreation. Language is saved and preselected; the existing English UI has no supplied translations, so choosing a language does **not** claim to translate the application.
- Small preference records use SharedPreferences; security/setup writes commit atomically on Dispatchers.IO. No raw PIN is persisted. Setup screens remain blocked until writes succeed and errors can be retried.
- PIN: exactly four ASCII digits; create then confirm; PBKDF2-HMAC-SHA1 with 210,000 iterations and 32-byte random salt, available on API 24. The resulting 256-bit verifier is encrypted with AES-GCM and a non-exportable Android Keystore key. Comparisons are constant-time. Credentials/setup/runtime markers are excluded from backup and device transfer. No default PIN and no Security Question.
- Verification serializes attempts across store instances. From failure 5, delay is 30 seconds, doubling up to 15 minutes. Deadlines and failure count persist; monotonic time protects against clock-forward bypass within a boot. Missing/corrupt credentials or a lost Keystore key fail closed; no recovery bypass was added.
- PIN contents are not logged or saved in navigation state. The PIN window blocks screenshots; the existing visible digit slots remain. Keyboard insets keep controls above the IME on edge-to-edge Android.
- ACTIVE → tap → start request → SECURED only after monitoring registration succeeds. PIN-authorized stop → STOP; next tap resets STOP → ACTIVE, following the original behavior. The existing STOP caption is retained even though its wording is imperfect.
- Only one mode is armed at a time. Back, Home, another card, Settings or canceling PIN never silently disarms it. A different mode is rejected until the armed one is stopped with PIN. Verification proofs are ephemeral, session-bound and consumed once through SavedStateHandle StateFlow.
- UI state, starting/running monitoring, latched detection and actual audio playback status are separate. Register/start/sensor errors transition to STOP with an explanation. Audio-only failure is reported separately and keeps monitoring armed; it does not become a PIN bypass. Stale callbacks/proofs cannot affect a new session.
- Foreground service uses specialUse with an explicit manifest explanation. Play Console review is still required for distribution; runtime success is not store approval. No boot receiver, sticky restart, full-screen intent, battery-optimization exemption or automatic rearming.
- Motion/proximity hold a partial wake lock while monitoring so non-wakeup sensors can run with the display off. This consumes extra battery. Resources are released with service termination. OEM restrictions, force stop, reboot or process termination can still interrupt monitoring; reopening reports interruption and requires explicit reactivation.
- Notification access is required by this app before arming so a usable entry back to protection is available. Onboarding can continue after denial. Foreground service, network state and vibration permissions are manifest permissions; no unnecessary location, microphone, Bluetooth scan or storage permission is requested.
- No project audio file existed. AlarmPlayback is replaceable; AndroidAlarmPlayback uses the system alarm URI and alarm stream, respects system audio policy and never changes the user's volume/DND configuration.

## Detection conditions

| Mode | Trigger and baseline behavior |
| --- | --- |
| Proximity | A real near → far transition after a baseline sample. Far at registration does not alarm. Requires TYPE_PROXIMITY. This is a distance change, not proof of theft. |
| Motion | TYPE_ACCELEROMETER, time-aware low-pass gravity estimate, 2-second settling interval, linear magnitude ≥ 2.5 m/s² for ≥ 150 ms and ≥ 3 samples. Noise/single spikes do not trigger. Threshold needs tuning on physical devices. |
| Charger | Requires a charger connected before arming; connected → disconnected triggers. Initial/sticky state and connecting do not alarm. |
| Handsfree | Requires a supported wired, USB, hearing-aid or Bluetooth audio endpoint at arm time. AudioDeviceCallback checks that the last headphone output is gone after a 750 ms handoff grace period; no headset means arming is refused. Physical profile/route behavior needs device testing. |
| Wi-Fi | Requires enabled adapter. Enabled → disabled, or an observed Wi-Fi connection lost for 1 second, triggers. Initial callback and unrelated cellular connectivity do not alarm. Switching SSIDs without a confirmed outage is not an identity-change alarm; SSID/location access is not requested. |
| Full battery | Observed below-full → 100% while plugged in; once per cycle. Arming at 100% does not immediately alarm. Disconnecting and observing below 100% rearms the rule for a later cycle. |

## Validation record

- Final combined run of `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`: BUILD SUCCESSFUL on 2026-10-20, after all production-code changes.
- `:app:assembleDebug`: passed repeatedly during phase implementation.
- `:app:testDebugUnitTest`: 19 tests passed at the latest recorded full run; includes PIN format/confirmation/verifier/lockout clock logic, state transitions/session proofs, six detection rules and fake alarm playback.
- `:app:connectedDebugAndroidTest`: 5 tests passed on Pixel_7a emulator, Android API 37 (Android 17): isolated Keystore/encrypted persistence, incorrect PIN, lockout across store instances, invalid PIN rejection, onboarding persistence and the existing package check.
- `:app:lintDebug`: passed with 0 errors. 82 warnings and 3 hints remain for legacy resources, assets and dependency suggestions; versions were not upgraded to silence them.
- Initial UI smoke testing found an actual IME overlap on API 37. `imePadding()` fixed it; create/confirm mismatch/confirm success were then observed through the real UI.
- Emulator UI smoke passed: onboarding with actual notification grant, Create/Confirm mismatch and success, relaunch skipping setup, incorrect PIN/cancellation retaining protection, background Wi-Fi adapter-off alarm, PIN-authorized cleanup, and requiring PIN again for a new session.
- Additional emulator smoke passed: charger disconnect, battery 99-to-100, proximity near-to-far, stationary-versus-sustained motion, rejecting handsfree activation without an endpoint, notification opening PIN and returning to STOP, preventing a second mode replacing an armed mode, and force-stop interruption reporting without automatic rearming.
- Smoke testing also found and fixed repeated Wi-Fi events resetting actual playback status; a unit regression test now covers it. Audio-only failure keeps monitoring armed with an explicit unavailable status and still requires PIN.
- Emulator events validate Android API wiring, not real sensor sensitivity, physical Bluetooth profiles, radio roaming or audible speaker output. The host emulator was started without host audio. No claim of physical-device certification is made.

## Physical-device checklist

1. Fresh install: observe Splash, select/save Language, swipe Intro, grant or deny notification permission, create and confirm PIN. Restart; verify Home and no setup back stack. From Settings, revisit Language and Permission; test denial, permanent denial and returning from App Settings.
2. Arm each supported mode. Back to Home, open another card and rotate; verify the same session remains active and another mode cannot replace it. On unsupported hardware, require an error and no SECURED state.
3. Deactivate: try a wrong PIN, cancel, then verify correctly. Wrong/cancel must keep monitoring/alarm; correct PIN must show STOP, remove notification, stop audio/vibration and release listeners/wake lock. Rearm and verify that the earlier success cannot be reused. Reach five failures, close/reopen PIN and verify the delay remains.
4. Proximity: arm while uncovered; no alarm initially. Cover, then uncover; expect alarm. Repeat with the display off and device in a real pocket/bag; evaluate false positives.
5. Motion: leave stationary through the 2-second settling interval; small vibration should not alarm. Move/lift firmly; expect alarm. Test low/high-end devices, display off and real battery consumption.
6. Charger: unplugged arming must fail. Connect, arm, leave connected (no alarm), unplug (alarm). Include USB and wireless power where supported.
7. Headphones: no endpoint → cannot arm. Test wired, USB, A2DP, SCO/calls, BLE and multiple audio endpoints; confirm route changes do not produce unintended disconnect alarms. Disconnect all headphone outputs; verify alarm can play through the available output.
8. Wi-Fi: initial enabled/connected state must be quiet. Turn adapter off, then separately lose access-point connection while adapter stays enabled. Confirm alarm after the defined transition; cellular-only changes must not trigger. Test roaming and VPN behavior.
9. Battery: arm below 100% while charging; reach 100%, expect one alarm. Staying at 100% must not create overlapping playback. Test arming already full, unplug/replug and the next charging cycle.
10. Background: arm, switch apps, turn display off and trigger each condition. Use the notification to enter PIN and stop. Test media/call audio focus, alarm volume zero, vibration unavailable, channel blocked, Android task-manager stop, process kill, force stop and reboot. Do not expect protection to survive OS-enforced process termination.

## Reproduction

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
python scripts/emulator_smoke.py
python scripts/emulator_smoke.py --resume --extended-only
```

The smoke script is restricted to emulators. It refuses an existing user PIN, does not clear app data and restores simulated battery/Wi-Fi state. `--resume` is only for its own previously configured emulator test fixture. Build artifacts, lint results and test reports are under `app/build/`.

Android references checked: [foreground service types and specialUse](https://developer.android.com/develop/background-work/services/fgs/service-types), [background launch restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview), [network callbacks](https://developer.android.com/develop/connectivity/network-ops/reading-network-state), [Android cryptography](https://developer.android.com/privacy-and-security/cryptography).
