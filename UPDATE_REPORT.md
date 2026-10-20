# AntiTheftDemo — Stage A–C update

## Scope and design reference

Continued the existing application; kept onboarding, the six detection rules, MVVM, encrypted PIN storage and the foreground service. SDK/Gradle/Kotlin versions were not changed. No commit or push was performed for this request.

The two referenced image attachments were not present in the received message, and Figma could not be opened. Settings follows the supplied five-row specification, colors, centered header, white surface and rounded tone dialog. Exact pixel matching to the screenshots remains unverified. Existing detection/PIN drawables and dimensions were reused.

## Stage A — stop confirmation

Changed `AppNavigation.kt`, `DetectionScreen.kt`, `DetectionViewModel.kt`, `DetectionRepository.kt` and `MonitoringService.kt`; added `DetectionPresentation.kt` and `StopConfirmationTest.kt`.

- SECURED → Deactivate → `stop_confirmation/{type}/{session}`. This renders the red drawable and “Tap to stop” while the repository remains SECURED and monitoring/alarm continue.
- Red circle → existing PIN verification. Wrong PIN retains throttling and protection. Back from PIN returns to confirmation; Back from confirmation returns to SECURED.
- Successful verification returns an ephemeral, session-bound proof through SavedStateHandle StateFlow. The proof is cleared and consumed once.
- The service synchronously releases monitoring, audio, optional effects, wake lock and foreground notification before publishing STOP. Navigation checks successful completion before popping directly to Home. It never navigates through ACTIVE or rearms a mode.
- Stage build, unit tests and lint passed. Initial compile issues during implementation were corrected before proceeding.

## Stage B — Settings and PIN change

Added `settings/AlarmSettings.kt`, `SettingsRepository.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt` and `AlarmToneDialog.kt`. Changed Home/navigation, `PinStore.kt` and `PinCodeViewModel.kt`; added `PinChangeAuthorizations.kt` and related tests.

- Home gear opens a separate Settings screen containing only PIN Code, Alarm Tone, Language, Flash Light and Vibrate. There is no Other/Rate/Share/Feedback/Privacy section.
- Language reuses the existing screen and saved selection, then returns to Settings. UI strings remain English; translations are not claimed.
- An existing PIN cannot be overwritten by Create. PIN setting first verifies the old PIN using the same persistent attempt limiter, then grants Create/Confirm a verifier-bound, single-use, five-minute authorization. Cancellation revokes the grant; expired or reused grants fail closed. This authorization cannot stop a monitoring session.
- Alarm settings are stored in a separate SharedPreferences file, observed using a callbackFlow whose listener is removed on cancellation. Writes commit on IO. They do not overwrite onboarding or PIN preferences.
- Dialog selection is a draft until Ok; Cancel leaves the saved selection unchanged. Flash defaults off; vibration retains its previous on default.
- Added four distinct original 2.4-second, mono 16-bit PCM WAVs under `res/raw/`. They are synthesized by `scripts/generate_alarm_tones.py` without third-party samples. The script is their reproducible source; no unclear audio license is involved.
- Stage build, unit tests and lint passed.

## Stage C — actual alarm behavior

Changed `AlarmController.kt`, `AndroidAlarmPlayback.kt`, `MonitoringService.kt`, repository/navigation and manifest; added `AlarmAudibility.kt`, `AlarmPreview.kt`, `AlarmEffectsController.kt`, `AndroidAlarmEffects.kt` and `FlashController.kt`.

- One MediaPlayer plays the selected WAV on `AudioAttributes.USAGE_ALARM` and loops until authorized deactivation or a system interruption. Changing tone replaces the audio source without stopping monitoring or overlapping players.
- The foreground service observes settings while running. Flash/vibration settings affect real alarm effects, including changes during a latched alarm. Settings alone do not activate effects before an alarm.
- Effects are separate from audio: a muted/paused/failed sound does not silently disarm monitoring or stop enabled visual/haptic alerts. Successful PIN stop releases everything.
- Preview is short-lived, uses application context, never flashes/vibrates and is released on Cancel/Ok, dialog disposal, Activity backgrounding or arming protection. Preview is disabled while any session is armed. Protection also preempts it before starting the real alarm.
- Audio focus supports delayed grants and transient interruption. Playback pauses for transient loss/duck requests and resumes on gain. Permanent loss/error is displayed as unavailable while protection remains armed. No API is used to force volume, repeatedly reclaim focus or bypass DND.
- Alarm volume/mute status and “Do not disturb: none” are read, not modified. Muted audio is reported as suspended rather than playing; raising volume through the system can make a still-running player audible again. Application playback state cannot prove sound reached a physical speaker under every OEM/audio policy.
- Torch uses `CameraManager.setTorchMode`, without opening a camera or capturing frames. The documented public torch API on supported Android versions does not require adding a camera-capture permission. Missing flash, an already-lit torch, camera/resource contention or device restrictions are handled with a warning. A 500 ms on/off cycle is canceled and the owned torch switched off during cleanup. No camera foreground-service type is claimed because no camera capture session is opened.
- Sensors retain the existing monitoring wake lock; any latched alarm additionally holds a service-owned partial wake lock for screen-off effects/status tracking. Wake locks are released by normal stop, service destruction and the OS when the process dies; they consume battery while active.
- The five-row Settings layout does not add a Permissions row. Home offers the existing permission screen when an arm request is blocked by missing notifications.
- Stage build, unit tests and lint passed; integration outcomes are recorded below.

## Validation

- Gradle tasks run: `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:testDebugUnitTest`, `:app:lintDebug`.
- JVM coverage includes stop-confirmation presentation, stale/one-use proofs, PIN-change grants, settings defaults/resources, effect switching/cleanup and volume/focus status. A final regression also checks changing tone before the first alarm event without starting playback.
- 8 Android instrumentation tests passed on the API 37 emulator, using isolated preference namespaces. Coverage includes encrypted storage compatibility, old-PIN-authorized replacement, rejected/reused proofs, onboarding preservation, settings persistence and successful decoding of four different WAV payloads.
- UI smoke uses a separate `com.example.antitheftdemo.validation` install. A temporary init script under ignored `app/build/validation/` changes only the validation application ID; the source Gradle files and delivered app package remain unchanged. This avoids guessing, changing or resetting the existing emulator app PIN. No app data was cleared.
- The earlier fixture did not match the existing emulator app PIN, so that UI attempt was not reported as passed. Its test-created monitoring session was cleaned up through emulator force-stop, an OS operation rather than an application deactivation path.
- The emulator is configured without host audio; decoding/PLAYING state are verified, not physical loudness. Use `scripts/settings_flow_smoke.py` for the new flow; `scripts/emulator_smoke.py` documents the older pre-change flow and has not been rerun against these navigation changes.
- Final build, unit test and lint run succeeded: 29 JVM tests; lint 0 errors, 82 warnings and 3 hints. Warnings remain and are available in the generated lint report.
- Isolated emulator UI checks passed: STOP confirmation, both Back/cancel paths, wrong PIN retaining protection, correct PIN cleanup directly to Home without rearming; five Settings rows/no Other; Language save/return; old-PIN verification before Create; tone 2 save and Cancel rollback; Flash ON/Vibrate OFF persistence across restart; no preview while armed; proximity alarm surviving screen lock; alarm continuing in confirmation and stopping through PIN; stored PIN preserved.
- The smoke run initially could not parse the platform volume command output. Inspection then confirmed this API 37 emulator exposes alarm range [1..7], so a real zero-volume scenario was not verified here. The script now recognizes both output formats and reports a nonzero-minimum limitation. Muted/zero/focus status logic is unit tested; physical volume/DND/call tests remain required.
- UI smoke ran before the final pre-event tone-update fix; that final correction is covered by the added JVM regression and the final successful build/test/lint run.

## Android limitations and remaining hardware checks

- A normal application cannot guarantee blocking hardware Power, shutdown, forced restart, force-stop or process termination. There is no root, Device Owner/Kiosk mode, accessibility interception or fake “Power blocked” implementation. Alarm cannot continue after power loss/process termination.
- Android/user policy can reduce or mute the alarm stream, impose DND, revoke audio focus or stop a foreground service. Volume keys are not intercepted globally and no volume-forcing loop is used. Correct PIN remains required for ordinary in-app deactivation; system interruption is a separate condition.
- specialUse FGS still requires Play Console review for distribution. No automatic restart/boot rearming was added.
- Test real camera contention/torch behavior, vibration hardware, audible loudness and DND/call focus on physical devices. Also test screen-off sensor delivery, OEM battery policies and headset/radio transitions. Existing detection rules were not rewritten.
- For a physical-device acceptance run: verify every Back/cancel branch of STOP flow; switch tone/flash/vibration during an alarm; observe enabled effects with screen locked; set alarm volume to zero and check the UI; restore volume; authenticate and confirm notification, torch, vibration, playback and monitoring all stop before Home appears. Repeat with an absent/busy flash and denied notification permission.

References checked: [audio focus and foreground-service constraints](https://developer.android.com/media/optimize/audio-focus), [CameraManager torch API](https://developer.android.com/reference/android/hardware/camera2/CameraManager#setTorchMode(java.lang.String,%20boolean)), [Android 17 torch service implementation](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/android17-release/services/camera/libcameraservice/CameraService.cpp), [AudioManager stream/mute controls](https://developer.android.com/reference/android/media/AudioManager), [Android key events](https://developer.android.com/reference/android/view/KeyEvent).
