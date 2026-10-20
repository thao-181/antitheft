# AntiTheftDemo — six physical-testing fixes

Reference HEAD: `585b1652ad7504578c204191d596b2325abe9f72`. Existing IDE changes and emulator logs were retained. No commit, push, reset, PIN reset or application-data clearing was performed. SDK, Gradle and dependencies are unchanged. The six detection rules remain unchanged; AndroidMonitor changes only replace user-visible error text with resource references.

## Stage 1 — issues 4 and 6

Root causes: `DetectionController.ended()` retained STOP, and `request()` consumed the next tap resetting it to ACTIVE. Navigation displayed red confirmation before PIN and consumed authorization immediately after PIN. Proofs had no expiry.

Changed DetectionController, DetectionPresentation, DetectionViewModel, DetectionScreen and AppNavigation; updated controller/presentation tests and both smoke scripts.

- Deactivate now opens PIN directly. Incorrect PIN and cancellation retain monitoring/alarm and the existing persistent PIN throttling.
- Successful PIN delivers a session-bound, one-use proof through the existing SavedStateHandle StateFlow. The result slot is immediately cleared and the proof held only in the detection ViewModel's memory. Red AUTHORIZED_STOP is presentation while the repository remains SECURED.
- Tapping red STOP consumes the proof, invokes the existing synchronous service cleanup, and only after successful cleanup navigates Home. Monitoring, playback, effects, sensors, receivers, wake lock and notification are released before Home.
- A grant expires after 60 seconds using a monotonic clock. Back/header Back, Activity backgrounding, route disposal and ViewModel clearing revoke unused authorization. Expired authorization returns SECURED; stopping requires PIN again. A stale/reused proof cannot stop a new session. Process recreation cannot recover an authorization from the controller's empty in-memory grant table.
- Ended sessions reset to ready ACTIVE. This does not request a new session or register listeners. Opening any of the six modes after stopping needs only the normal ACTIVE activation tap.
- A route-local completion flag keeps the outgoing STOP screen red during the Home transition, even though the repository has already reset the next visit to ACTIVE. Its circle is disabled after completion; the flag is not persisted or reused by a new route.
- Stage build and unit tests passed, including all six reopen states, expiry, revocation and single-use/session binding.

## Stage 2 — issue 1

Root cause: only the selected English language name was persisted; UI and notification text were hardcoded English and no locale was applied.

Changed MainActivity, AppPreferences, LanguageItem/Screen, HomeItem/Screen, IntroItem/Screen, PermissionScreen, DetectionScreen, PIN ViewModel/Screen, Settings/ViewModel/Dialog/tone labels, Navigation, service/repository/monitor/alarm messages and manifest. Added AppLocale, LanguageCodes, MessageResources, locale_config and resource tables for hi/es/fr/de/in/pt, with English defaults.

- 117 application string keys have real translations in all seven languages, including errors, throttling messages, controls, descriptions, accessibility labels and notification text. Language choices use native names so users can find their language.
- Compose resolves `stringResource()` directly or through `userText()` for context-free resource references held in existing String callback/state interfaces. Dynamic lockout seconds are resource-format arguments. ViewModels retain no Activity context or pretranslated UI snapshot.
- Existing AppCompat 1.7.1 is reused; MainActivity extends AppCompatActivity (still a ComponentActivity subclass). AppCompat per-app locales recreate the Activity, with native LocaleManager integration on Android 13+. Older Android reads existing custom locale storage during base-context attachment. No dependency was added.
- Old persisted language names migrate to canonical en/hi/es/fr/de/id/pt codes without touching PIN/setup checkpoints. Notification strings use a fresh localized application configuration. Locale preference/configuration listeners refresh active notifications and are unregistered on cleanup.
- The PNGs contain baked English ACTIVE/SECURED/STOP lettering. Their original outer rings remain; translated locales render a localized inner label surface. English keeps the original image. Pixel-perfect matching of these translated labels to Figma remains unverified.
- Brand name and unused legacy XML placeholder/lorem resources are explicitly nontranslatable. They are not part of the active Compose flow and were retained rather than deleted.
- Stage build/tests passed. Added canonical-code migration/persistence tests and Android resource tests for Home/Settings/PIN/Detection/status text across all translated locales. A resource-table check confirmed complete keys and matching format placeholders.

## Stage 3 — issues 2 and 3

Confirmed code defects: Settings switches inherited dynamic Material colors, and vibrator requests omitted ALARM usage. The latter can affect platform background/policy handling; this alone does not establish the exact cause on the user's particular physical device.

Changed SettingsScreen, SettingsRepository, AndroidAlarmEffects, FlashController; expanded effects and failed-persistence tests.

- Checked switch: white thumb / #0075FF track; unchecked: white thumb / gray track. Thumb position follows the persisted boolean. Disabled states also have explicit colors.
- A failed SharedPreferences commit is reported and rolls back the in-memory value, since Android can update memory even if disk commit fails. Existing preferences/PIN are preserved.
- Only ALARMING starts effects. SECURED alone does not vibrate or flash. Live setting changes apply through the existing service/controller flow. Stop calls vibrator.cancel(), torch stop and listener cleanup.
- VibratorManager is used on API 31+, Vibrator below it. API 33+ requests VibrationAttributes.USAGE_ALARM; API 26–32 uses waveform plus AudioAttributes.USAGE_ALARM; API 24–25 uses the compatible pattern/attributes overload. The repeating pattern is unchanged. VIBRATE was already declared and remains present.
- Missing hardware, API failure and detected total-DND restriction produce a localized warning. Public Android APIs do not provide a universal acknowledgment that a motor physically vibrated or expose every OEM suppression; a successful request is not reported as physical proof.
- `AntiTheftEffects` logs vibrator requests/cancellation and torch request/release/failure. No PIN or authorization proof is logged. Torch selection prefers a back flash; busy/unavailable/already-owned torch remains guarded without opening camera capture or changing global settings.
- Stage build/unit tests passed. Physical torch, vibration and OEM policy acceptance still require a phone test.

## Stage 4 — issue 5

Root cause: Splash's root Column had no explicit background and inherited theme/system-bar appearance; starting window had no matching explicit splash colors.

Changed Splash.kt, values/themes.xml and API-specific values-v27/values-v31 themes.

- Compose root is white before inset padding, with black text; logo, timing/progress logic and routing remain intact.
- Splash temporarily applies dark system-bar icons and white status/navigation backgrounds, restoring prior settings on disposal. On enforced edge-to-edge versions the white root is drawn under transparent bars; navigation contrast scrim is disabled during Splash.
- The starting window background is white; Android 12+ system splash background/icon background are white. API-specific navigation attributes are qualified rather than raising minSdk.
- These changes do not force the other Compose screens to white or disable their theme.

## Validation and device checklist

Final validation:

- `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` and `:app:assembleDebugAndroidTest` succeeded. Final JVM count and lint outcome are listed below; **11 Android instrumentation tests passed**. Warnings are retained in the generated report, not suppressed by a new baseline.
- Instrumentation ran only on the API 37 emulator with isolated preference namespaces. It confirmed encrypted PIN compatibility, language/settings persistence, actual translated resource lookup, failed-settings-write rollback, and existing PIN security. The attached physical phone was not used or modified.
- `python -X utf8 scripts/settings_flow_smoke.py` passed on a separate validation app ID: direct Deactivate → PIN, incorrect/canceled PIN protection, PIN → red STOP without disarm, Back revocation, STOP → cleanup → Home, ACTIVE on reopening; Settings persistence; Spanish/French/Hindi/English UI across Home/Settings/PIN/Detection; French after kill/relaunch; alarm surviving screen lock and Settings; proof revoked on Activity backgrounding without stopping alarm.
- `python -X utf8 scripts/settings_flow_smoke.py --effects-only` passed API-invocation checks: no vibration at SECURED, repeating ALARM vibration requested after a screen-off event, live Vibrate OFF calling cancel, Flash ON reaching the torch controller, and final STOP cleanup. Original validation preferences and emulator sensor/night mode were restored.
- Settled screenshots and pixel checks confirmed blue/right ON and gray/left OFF switch states, plus white Splash backgrounds in Light/Dark mode. Generated artifacts: `app/build/validation/settings-switch-on.png`, `settings-switch-off.png`, `splash-light.png`, `splash-dark.png` and `effects-invocations.log`. These are local ignored build outputs, not source assets.
- The emulator alarm volume range is [1..7]; an actual zero-volume UI scenario was unavailable. The policy logic is unit tested. The emulator runs without host audio, so no physical sound, LED or motor output is claimed.
- A temporary ignored init script produced `com.example.antitheftdemo.validation` for UI testing without clearing data or changing source build configuration. The delivered final `app/build/outputs/apk/debug/app-debug.apk` was rebuilt normally and has package **com.example.antitheftdemo**.
- API 24–32 locale/vibration branches and OEM torch/DND/background behavior still require device coverage; only API 37 was exercised at runtime. `git diff --check` passed and HEAD remains `585b165` with no new commit/push.
- The final outgoing-red transition flag was added after emulator UI checks; its outgoing-red/reopened-ACTIVE behavior has a dedicated JVM regression. The final build/test/lint includes this correction.
- Final rerun succeeded: **34 JVM tests, 0 failures; lint 0 errors, 92 warnings, 3 hints; assembleDebug successful**. The delivered APK uses the original package. No additional device test is claimed for the final transition-only correction.

On a physical phone:

1. Change English → Spanish → French → Hindi → English; check Home, Settings, PIN, Detection, notification and restart persistence. Review longer translated text at large font scale. Native-language proofreading is recommended before release.
2. Flash ON is blue / OFF gray; no torch until a genuine event. During ALARMING toggle flash and vibration independently, including screen locked. Check no-flash and camera-busy cases. Monitor `adb logcat -s AntiTheftEffects` for request/cancel diagnostics.
3. Test Vibrate ON/OFF under normal policy and DND without changing policy from the app. Check both actual motor output and `adb shell dumpsys vibrator_manager` where the device exposes it. A request log alone does not prove output.
4. Trigger each supported mode. Deactivate → wrong PIN/cancel retains protection. Correct PIN → red STOP still alarms. Tap STOP → all effects/service/notification stop before Home. Reopen → ACTIVE; no automatic activation. Leave red STOP without tapping or wait 60 seconds → PIN is required again.
5. Cold-start in Light/Dark mode and inspect both system starting splash and Compose splash, including status/navigation bars and gesture/three-button navigation.

References: [per-app locales](https://developer.android.com/guide/topics/resources/app-languages), [Vibrator API and alarm attributes](https://developer.android.com/reference/android/os/Vibrator), [VibrationAttributes](https://developer.android.com/reference/android/os/VibrationAttributes). Platform volume/DND/process/Power limits documented in UPDATE_REPORT.md remain applicable.
