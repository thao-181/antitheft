# AntiTheftDemo — Stabilization report

Ngày thực hiện: 2026-10-21. Workspace: `D:\AntiTheftDemo\Thao`.

## 1. Baseline và phạm vi

- HEAD thực tế: `aef5f1f5e9711bd8c7085861e05c3dbe3ea2f6d9`, trùng commit được cung cấp.
- Trước khi sửa đã có thay đổi tại `.idea/deploymentTargetSelector.xml`, `.idea/vcs.xml` và hai log `emulator-test.*.log`. Giữ nguyên nội dung của chúng.
- Commit/push chỉ thực hiện theo yêu cầu tiếp theo của người dùng sau khi hoàn tất stabilization. Không reset/clean Git, clear app data hoặc reset PIN. Không xóa XML, drawable, icon, bản dịch hay ảnh Figma.
- Giữ package/applicationId, minSdk 24, target/compileSdk 37, AGP 9.1.1 và các phiên bản toolchain hiện có. Không thêm permission, framework hay module.
- Giữ luồng **ACTIVE → SECURED → Deactivate → PIN → STOP → HOME**. PIN đúng chỉ cấp quyền hiển thị STOP; monitoring/alarm tiếp tục cho đến thao tác STOP.

## 2. Audit trước khi sửa

Các phát hiện dưới đây dựa trên code baseline. Phân biệt đường code có vấn đề với sự cố đã tái hiện trên phần cứng.

| Mức | Phát hiện xác nhận từ source | Xử lý |
| --- | --- | --- |
| P0 | Proof được truyền bằng `SavedStateHandle`, có thể được serialize vào saved instance state | Bỏ hoàn toàn kênh này; giao trực tiếp cho ViewModel của Detection entry |
| P0 | PIN `completed` gọi lại success; coroutine giữ callback mà không ràng buộc màn hình/session đang hiện | Completion một lần, lifecycle RESUMED, generation và entry/session guards |
| P1 | Nhấn vòng SECURED vẫn gọi activate | Vòng SECURED và nút Deactivate dùng chung thao tác mở PIN |
| P1 | Card Home tô màu bằng state click cục bộ | Lấy SECURED từ repository, state hoisting |
| P1 | Cleanup service tuần tự không bảo vệ từng bước; exception ở bước trước bỏ qua các bước sau | Cleanup độc lập, vô hiệu hóa ownership trước khi release |
| P2 | Route composable chứa logic trao proof và lifecycle STOP | Tách `AppRoutes` và `ProtectionNavigation` |
| P3 | Trùng khai báo Material 3/Foundation Layout, danh sách Home/Language dùng `var` toàn cục | Bỏ khai báo trùng, chuyển danh sách sang `val` |

Chưa tái hiện trước khi sửa: vendor sensor/torch lỗi giữa chừng, callback Android đến sau unregister, process bị kill khi đang alarm, FGS bị hệ điều hành dừng, notification nhấn liên tục, notification đổi locale. Không coi các tình huống này là lỗi phần cứng đã xác nhận. Một số đã có regression tests bên dưới; các trường hợp còn lại nằm trong checklist thiết bị thật.

Nguy cơ regression chính: trao proof khi chuyển route, Back/recreation, hoàn tất lưu PIN, và cleanup nhiều tài nguyên. Vì vậy ưu tiên các nhánh này trước cleanup dependency. Không tìm thấy lý do để đổi sáu detection rules.

## 3. Architecture overview

Compose screens quan sát `StateFlow` theo lifecycle. `DetectionRepository` là cầu nối application-scoped giữa UI và started service; `DetectionController` giữ session, trạng thái và grants trong bộ nhớ. `MonitoringService` cùng process với Activity, sở hữu tài nguyên monitoring/alarm. Repository chỉ chuyển SECURED sau khi backend đăng ký thành công.

`AndroidMonitor` giữ các Android registrations, còn `DetectionRules.kt` giữ sáu rules Kotlin. `AlarmController` giữ một playback session; `AndroidAlarmPlayback` sở hữu MediaPlayer/audio focus. `AlarmEffectsController` quản lý flash/vibration độc lập với âm thanh.

`AppNavigation` giữ onboarding/settings. `ProtectionNavigation` giữ Detection/PIN/STOP, thu hồi proof và xử lý notification. `PinAccess` là interface nhỏ để test ViewModel với verifier giả; implementation production vẫn là `PinStore`, không thêm DI framework. Tên mode được đọc từ metadata enum, service không còn tra danh sách card Home.

Không chuyển sang Clean Architecture/multi-module. `HomeItemType` vẫn ở package `home` để tránh đổi tên/import diện rộng; đây là debt nhỏ có thể xử lý riêng.

## 4. Stage 1 — Detection UI và Home

**Root cause:** click handler không phân biệt presentation SECURED; Home tự nhớ thao tác click thay cho trạng thái monitoring.

**Files:** `DetectionScreen.kt`, `HomeScreen.kt`, `HomeItem.kt`; thêm `DetectionUiTest.kt`.

**Trước/sau:** ACTIVE gửi yêu cầu start và vẫn ACTIVE trong lúc starting. SECURED chỉ xuất hiện sau acknowledge. Cả vòng xanh lá và Deactivate mở PIN; vòng đỏ mới gọi confirmStop. Home chỉ tô card mà snapshot xác nhận SECURED, không tô card vì vừa click. Danh sách bất biến, state card được hoist từ lifecycle-aware repository collection.

**Kiểm thử:** bốn Compose instrumentation tests kiểm tra hai lối vào PIN, start acknowledgement, STOP/Home/reopen và selected card. Các controller/presentation unit tests kiểm tra reset ACTIVE, single session và alarm vẫn chạy khi đang STOP. Kết quả tổng hợp tại mục 11.

**Giới hạn:** UI tests dùng controller/runtime cục bộ; không chứng minh hoạt động cảm biến trên điện thoại thật.

## 5. Stage 2 — Navigation, PIN và proof

**Files:** `AppNavigation.kt`, `AppRoutes.kt` (mới), `ProtectionNavigation.kt` (mới), `DetectionViewModel.kt`, `PinCodeViewModel.kt`, `PinCodeScreen.kt`, interface trong `PinStore.kt`; tests Navigation/PIN lifecycle và controller.

**Root cause:** saved-state result bus không phù hợp cho secret ngắn hạn; success callback có thể được gọi lại hoặc trở về một entry đã rời luồng.

**Trước/sau:** proof chỉ nằm trong controller và Detection ViewModel, không đi qua Bundle/saved state/route/log. PIN completion là event trong bộ nhớ, consume một lần ở RESUMED. Verification bị vô hiệu hóa khi ON_STOP/dispose; kết quả coroutine thuộc generation cũ bị bỏ, grant đổi PIN trả về muộn được revoke. Completion sau khi lưu PIN mới được giữ qua recreation của ViewModel để không lưu lặp hoặc mất thao tác hoàn tất. Không persist PIN input hay completion.

Trước khi trao proof, kiểm tra PIN entry đang hiện/RESUMED, previous entry đúng Detection, mode và session còn khớp. `acceptProof` kiểm tra mode và proof, xử lý delivery trùng cùng proof an toàn. Notification trùng khi đã ở đúng PIN không tạo thêm màn hình. Session kết thúc khi PIN còn hiện sẽ đóng luồng cũ và về Home.

**Security invariants:** proof gắn session, dùng một lần, TTL 60 giây bằng monotonic clock; grant cũ không dừng session mới. Back từ STOP thu hồi quyền và trở lại SECURED; ON_STOP/dispose/onCleared thu hồi quyền. Confirm kiểm tra lại proof; hết hạn không dừng service, cần xác thực lại. Process mới không có proof/session đã lưu. Luồng PIN → STOP không thay đổi.

**Kiểm thử:** Navigation instrumentation dùng NavHost/ViewModels thật với verifier/runtime giả; kiểm tra PIN sai/đúng, STOP/Home/reopen, Back, notification lặp/cũ và session bị ngắt khi PIN đang hiện. PIN lifecycle tests dùng deferred verifier kiểm tra completion một lần, kết quả muộn và creation completion trong lúc UI dừng. Unit tests kiểm tra expiry/revoke/reuse, grant chưa dùng của session cũ và controller mới sau giả lập mất state process.

**Giới hạn:** không phải mọi test Navigation đều dùng Keystore thật; crypto/lockout/PIN change được kiểm chứng riêng bằng `PinStoreInstrumentedTest`. Chưa thực hiện kill process thật hoặc chờ TTL qua UI trên thiết bị.

## 6. Stage 3 — Service và resource safety

**Files:** `MonitoringService.kt`, `DetectionRepository.kt`, `AndroidMonitor.kt`, `BroadCastManager.kt`, `AlarmController.kt`, `AndroidAlarmPlayback.kt`, `AlarmEffectsController.kt`, `FlashController.kt`, `util/ResourceCleanup.kt` (mới); tests service/cleanup/alarm.

**Root cause/đường lỗi được bảo vệ:** một release exception bỏ qua cleanup còn lại; callback cũ có thể đến sau stop; registration/wake-lock ownership được ghi nhận sau thao tác có khả năng ném lỗi; startup timeout còn tồn tại sau acknowledge.

**Trước/sau:** service đánh dấu finishing và gỡ references trước cleanup. Mỗi owner được release độc lập và lỗi release được log theo tên tài nguyên. Repository reset trong `finally` kể cả khi xóa interruption marker lỗi. Timeout được hủy khi started/ended/failed. Attach kiểm tra session + mode + starting và chỉ cho một runtime owner. Service hỗ trợ start mới nếu Android giao intent mới vào instance đã cleanup nhưng chưa destroy. Monitor failure callback kiểm tra session; AlarmController có generation guard; torch callback riêng cho từng registration. Đăng ký cleanup trước các Android callback registrations và giữ wake-lock reference trước acquire.

| Tài nguyên | Owner / khi tạo hoặc đăng ký | Khi giải phóng / callback muộn |
| --- | --- | --- |
| Sensor listener, dynamic sensor callback | AndroidMonitor, lúc start mode cảm biến | stop/start failure, unregister; generation chặn alarm/failure cũ |
| BroadcastReceiver | BroadCastManager thuộc monitor, start charger/battery/Wi-Fi | close lặp an toàn; mỗi receiver được unregister độc lập |
| NetworkCallback | AndroidMonitor, start Wi-Fi | cleanup reverse order; callback về handler kiểm tra generation |
| AudioDeviceCallback | AndroidMonitor, start handsfree | unregister; callback cũ không lên lịch recheck mới |
| Handler của monitor | AndroidMonitor | remove callbacks khi stop; generation chặn công việc đến muộn |
| Startup timeout | DetectionRepository | remove khi started/failed/ended; kiểm tra session trước failure |
| MediaPlayer | AndroidAlarmPlayback, start hoặc đổi tone | release player cũ trước prepare mới; identity guard; stop xóa handler |
| Audio focus | AndroidAlarmPlayback, start alarm/preview | abandon độc lập kể cả khi player release ném lỗi; focus callback có generation |
| Vibrator | AndroidAlarmEffects, chỉ khi alarm và setting bật | cancel khi setting tắt/STOP; lỗi vibration không chặn flash cleanup |
| Camera torch/callback/blink | FlashController, chỉ khi alarm và setting bật | tắt torch thuộc ownership, remove handler, unregister; callback có generation |
| WakeLock | MonitoringService, sensor mode hoặc alarm | release mọi nhánh finish/onDestroy/onTimeout; giữ reference trước acquire |
| Foreground notification | MonitoringService sau attach hợp lệ | remove trong finish, kể cả owner khác cleanup lỗi |
| Settings collector/listener | Service scope và SettingsRepository.callbackFlow | scope cancel → awaitClose unregister listener |
| Locale preference listener | MonitoringService | unregister trong finish; locale task kiểm tra finishing |
| UI coroutine/lifecycle observer | Compose effects / ViewModel scope | effect dispose hoặc lifecycle stop; ViewModel scope hủy khi cleared |

Alarm một session không tạo player chồng do repeated events. Đổi tone giải phóng player cũ. Audio unavailable/focus loss chỉ cập nhật trạng thái audio; không tạo quyền stop hoặc disarm monitoring. Flash/vibration không phụ thuộc playback success. Không thêm volume loop, Power interception hay cơ chế restart FGS.

**Kiểm thử:** real battery-mode FGS/receiver trên emulator, duplicate service start, recreate Activity, background/resume, proof hợp lệ/sai/reuse, notification removal, unexpected service destruction và rearm. Unit tests inject cleanup failure, stale playback callbacks và vibrator exception. Không trực tiếp đếm mọi sensor registration; source guard chặn duplicate start, lifecycle tests kiểm tra session/behavior.

**Giới hạn:** release là best effort nếu Android/vendor API ném lỗi; không thể bảo đảm phần cứng đã giải phóng chỉ từ unit tests. Torch/vibration/audio-focus arbitration và wake lock cảm biến cần thiết bị thật.

## 7. Application-scoped state và interruption

| Tình huống | Hành vi / bằng chứng |
| --- | --- |
| Activity recreate, rotate, đổi locale | Cùng process/service nên repository vẫn giữ session thật; không gọi activate từ composition |
| Background, khóa màn hình | Service sở hữu tài nguyên độc lập UI; quyền STOP chưa dùng bị revoke khi UI dừng |
| Service bị destroy/timeout | Cleanup và ended reset ACTIVE, hiển thị thông báo service stopped |
| Process bị kill | Memory session/grants mất; process mới khởi tạo ACTIVE, không restore SECURED |
| Có marker `monitor_runtime.active_type` từ lần chạy trước | initialize thông báo interruption và xóa evidence đã đọc; marker không khôi phục monitoring |
| Force-stop | Chấp nhận giới hạn Android; không auto-restart. Thông báo interruption ở lần mở sau nếu marker đã được ghi |

`apply()` của interruption marker có cửa sổ chưa flush nếu process chết ngay. Khi không có evidence, không thể kết luận lần trước monitoring đã bị ngắt. Không thêm persistent armed state hoặc tuyên bố protection đang chạy từ marker. Real process kill/force-stop/lock screen chưa chạy trong đợt này.

## 8. Stage 4 — Maintainability của monitor

Giữ một AndroidMonitor: sáu rules đã ở `DetectionRules.kt`, registrations chung nằm gọn trong một owner với cleanup/generation. Chia thành sáu class lúc này tăng thay đổi mà chưa có lợi ích kiểm chứng đủ lớn.

Không thay thresholds, baseline, debounce hay trigger conditions. Diff `DetectionRules.kt` bằng không; sáu regression rule tests chạy lại. Chỉ đổi ownership registration và chặn callback handsfree cũ lên lịch. Các hành vi cảm biến thật vẫn cần checklist bên dưới.

## 9. Stage 5 — Dependency, XML và Git hygiene

**Files:** `app/build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`.

- Bỏ một khai báo Foundation Layout thấp hơn bị khai báo trùng; giữ resolved `1.11.4` và xác nhận bằng dependencyInsight.
- Gộp Material 3 về một alias với `1.4.0`, giữ version đã resolve trước cleanup.
- Bỏ `navigation-ui-ktx` và alias/version riêng vì không có NavigationUI/setupWithNavController hoặc XML dùng thành phần Navigation UI.
- Giữ navigation-fragment-ktx/FragmentContainerView cho legacy XML; giữ ConstraintLayout và viewBinding để generated bindings vẫn compile; giữ Material XML vì theme/appbar/FAB còn tham chiếu. Giữ AppCompat cho Activity/locale support.
- Không nâng AGP/Kotlin/Compose/Gradle/SDK. Build/lint sau cleanup được chạy thật, không coi việc xóa declaration là đủ kiểm chứng.

Phân loại XML (chưa xóa file nào):

| File | Phân loại |
| --- | --- |
| `res/layout/activity_language.xml` | Không có runtime entry từ Activity/manifest/Kotlin hiện tại; legacy root cần đề xuất xóa cùng nhóm sau khi chủ dự án duyệt |
| `res/layout/content_language.xml` | Được include bởi legacy root; không reachable từ UI Compose hiện tại; giữ cùng nhóm |
| `res/navigation/nav_graph.xml` | Được content_language tham chiếu; tên FirstFragment/SecondFragment không còn class implementation; giữ để tránh mất draft cũ |
| `res/layout/fragment_first.xml`, `fragment_second.xml` | Không có Kotlin/runtime consumer; legacy draft, giữ |
| `res/layout/activity_language_screen.xml` | Không có runtime consumer; tools context không tạo runtime use; giữ |
| XML theme/backup/data-extraction/locale config | Đang dùng bởi manifest/theme/Android; giữ |
| Drawables/icons/images/strings khác | Giữ toàn bộ; lint UnusedResources không tự động đủ lý do để xóa |

Đây là danh sách đề xuất dọn XML cho một thay đổi riêng, không phải yêu cầu người dùng duyệt để hoàn tất đợt sửa này.

Giữ cấu hình team `.idea/compiler.xml`, inspectionProfiles, gradle/misc/vcs/runConfigurations và các cấu hình còn lại. deploymentTargetSelector chứa lựa chọn thiết bị theo máy; deviceManager/appInsights là state máy, được thêm ignore. File đã tracked vẫn tracked; không chạy `git rm --cached` và không xóa `.idea` trên máy. Ignore thêm Kotlin cache và local diagnostic logs. Hai file `.idea` sửa trước đợt này được giữ nguyên.

## 10. Stage 6 — Localization/UI quality

**Files:** `LanguageItem.kt`, `LanguageScreen.kt`; thêm `LocalizationResourcesTest.kt`, `LanguageSelectionTest.kt`.

Giữ 7 ngôn ngữ `en`, `hi`, `es`, `fr`, `de`, `id` (resource qualifier `in`), `pt`. Danh sách language bất biến; lựa chọn chưa save dùng rememberSaveable theo mã thay cho object remember, phục hồi qua saved state. Tên bản ngữ Unicode hiện tại được giữ nguyên.

Kiểm tra XML/Kotlin: không thiếu translated keys, không có extra keys hoặc placeholder mismatch, không có Kotlin message reference thiếu mapping/resource. Unit tests biến kiểm tra này thành regression checks. Instrumentation xác thực resource Android ở nhiều locale, persistence mã ngôn ngữ không đổi PIN/onboarding, và saved-state restoration của lựa chọn chưa lưu.

`AppLocale.context/text` tiếp tục lấy resource context mới thay vì giữ Activity. Service đã có configuration callback + setup preference listener để refresh notification; giữ cơ chế này. Không thay hàng loạt MessageResources vì API String/reference vẫn được nhiều state/callback sử dụng. Kiểm tra key không đánh giá đầy đủ chất lượng ngôn ngữ; câu French `Protection active` giống tiếng Anh không tự động là lỗi dịch. Đổi locale thật kèm Activity recreation/notification trên thiết bị chưa được test end-to-end.

## 11. Stage 7 — Kết quả kiểm thử

Lệnh Gradle đã thực thi trên bản code cuối:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

Sau lần chạy này, chỉ điều chỉnh cách instrumentation test đợi kết quả PIN; chạy lại `assembleDebugAndroidTest` và `lintDebug`. Production code và unit-test inputs không đổi trong bước đồng bộ test đó.

| Check | Kết quả |
| --- | --- |
| assembleDebug | PASS |
| testDebugUnitTest | PASS — 40 tests, 0 failures/errors/skipped |
| lintDebug | PASS — 0 errors/fatal, 90 warnings và 1 hint |
| assembleDebugAndroidTest | PASS |
| Instrumentation trên bản cuối | PASS — 26 tests, 0 failures, 57.384 giây |
| git diff --check | PASS |

Warnings gồm legacy unused resources/icon location, gợi ý dependency version/KTX/TOML, app-bundle locale delivery và một số style hints. Không tự nâng dependency hoặc xóa ảnh để làm sạch warning count. Không chạy lint baseline độc lập trước chỉnh sửa nên không quy toàn bộ 90 warnings là lỗi có sẵn. Compiler còn cảnh báo deprecation của Compose test rule v1; chưa di chuyển sang test scheduler API mới trong đợt này. Công cụ SDK cũng có cảnh báo XML metadata version; build thực tế vẫn thành công.

Môi trường instrumentation: AVD `Pixel_7a`, `emulator-5554`, x86_64, API 37. Khởi động không wipe-data. Dùng `adb install -r` cho APK app/test và `am instrument`; không dùng clear-data/uninstall để kết thúc tests. Không dùng connectedAndroidTest trong đợt này.

```powershell
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r com.example.antitheftdemo.test/androidx.test.runner.AndroidJUnitRunner
```

Các test storage dùng namespace preferences riêng và chỉ xóa namespace test của chính chúng. Test service tạm dùng/restores interruption evidence; không đổi PIN/setup/alarm settings của người dùng. Kiểm tra sau suite cuối: hash `pin_security.xml` và `setup.xml` trước/sau không đổi; `alarm_settings.xml` chưa tồn tại cả trước/sau. `dumpsys activity services` không còn service của app; `dumpsys power` báo `Wake Locks: size=0` trên emulator ở thời điểm kiểm tra. Điều này không thay thế kiểm thử wake lock/torch/alarm với phần cứng thật.

Artifacts cục bộ:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `stabilization-final-gradle.log`, `stabilization-instrumentation-final.log` (ignored)
- `stabilization-test-build.log`, `stabilization-final-lint.log` (build/lint sau chỉnh đồng bộ test)
- `stabilization-foundation-dependency.log`, user-data hash logs (ignored; không chứa PIN/proof)

Lịch sử kiểm thử: một lần chạy suite 26 tests có 1 failure khi assert STOP ngay sau Compose idle; chạy riêng test lại PASS. Test được đổi sang đợi semantics STOP/PIN error cụ thể vì completion chỉ được giao ở RESUMED. Sau thay đổi, test Back/PIN/STOP PASS 5 lần liên tiếp. Giữ log failure ban đầu ở `stabilization-instrumentation-26-first.log` và các log `stabilization-navigation-repeat-*.log`; kết quả suite cuối ở bảng trên. Không thay production để che failure này.

Coverage và giới hạn:

| Nhóm | Đã kiểm chứng | Chưa kiểm chứng |
| --- | --- | --- |
| Detection/UI | Acknowledge, SECURED circle/button, PIN sai/đúng, STOP/Home/reopen, Home selected, chống replace mode bằng controller | Sáu mode với phần cứng thực |
| Lifecycle | FGS battery mode thật, duplicate starts, Activity recreate, background/resume, service destruction/rearm; late PIN/playback results | Lock screen/Doze, OEM task killer, process kill/force-stop thật, restore toàn bộ Navigation sau OS process death |
| Security | TTL/revoke/reuse/session binding unit tests; encrypted PIN, wrong PIN, persistent lockout, old-PIN-required change bằng Android Keystore tests | Expiry UI theo thời gian thật, STOP recreation trên mọi API/OEM |
| Alarm | One-session/repeated trigger/stale callbacks/effect independence bằng unit tests; cả 4 tone decode qua MediaPlayer thật | Real audio-focus arbitration/DND/ringer, Bluetooth routing, torch và vibration phần cứng, tất cả resources sau alarm thật |
| Settings/language | Save/recreate repositories, rollback, giữ PIN/onboarding, Android translated resources, saved selection restoration | OS locale switch + notification refresh end-to-end, relaunch app thật qua đủ 7 locale, Play app-bundle language split delivery |
| Platform | Build target/compileSdk 37, emulator API 37 | API 24–36 và thiết bị thật: NOT TESTED |

## 12. Remaining risks và physical-device checklist

Không có bằng chứng để khẳng định ứng dụng đã ổn định trên điện thoại thật. Các mục dưới là phần cần kiểm chứng tiếp; không phải danh sách lỗi đã tái hiện.

- [ ] Chạy từng mode Proximity/Motion/Charger/Handsfree/Wi-Fi/Battery Full trên thiết bị có hardware phù hợp; đối chiếu baseline trigger/debounce.
- [ ] ACTIVE → SECURED, thử cả circle/Deactivate, PIN sai/lockout, PIN đúng vẫn alarm → STOP → Home → mở lại ACTIVE.
- [ ] Rotate khi SECURED/PIN/STOP; Back ở PIN/STOP; background/khóa màn hình/Doze rồi mở lại; xác nhận một session và không đăng ký trùng.
- [ ] Đợi proof quá 60 giây, thử STOP; cần PIN mới. Thử notification lặp và notification cũ sau rearm.
- [ ] Mô phỏng OS process interruption/force-stop rồi mở app; ACTIVE, thông báo interruption nếu marker tồn tại, PIN/onboarding còn nguyên. Không clear data.
- [ ] Trigger alarm liên tiếp, đổi cả bốn tone; thử audio focus loss/gain, DND, alarm volume 0 và Bluetooth. Không disarm khi audio bị hạn chế.
- [ ] Bật/tắt Flash/Vibrate khi alarm; camera/torch đang được app khác dùng; không tắt torch ngoài ownership. Xác thực STOP rồi kiểm tra không còn player/focus/torch/vibration/wake lock/notification/receiver.
- [ ] Save settings, relaunch, đổi PIN bằng PIN cũ, xác nhận không có plaintext PIN persistence và lockout còn hiệu lực qua relaunch.
- [ ] Chuyển đủ 7 locale, rotate/relaunch, giữ PIN/onboarding/settings và kiểm tra notification/channel text. Kiểm tra bundle install nếu phát hành Play.
- [ ] Test tối thiểu API 24, 26, 33 và các Android mới có FGS/audio-focus restrictions; ít nhất hai OEM với background policy khác nhau.

Technical debt còn lại: enum mode ở package home, message-reference String API, legacy XML/generated bindings/dependencies đi kèm, lint warnings. Cleanup không bảo đảm hardware API sẽ luôn thành công; log hỗ trợ chẩn đoán từng release failure. Không thêm cơ chế vượt giới hạn nền tảng, Security Question, chặn Volume/Power hoặc thay thiết kế UI.
