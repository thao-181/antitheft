# Audio focus & PIN latency — AntiTheftDemo

## Phạm vi và môi trường

- HEAD trước sửa: `d5e5c4746b68461269a9fca587ce1db632470465`, branch `main`.
- Working tree ban đầu chỉ có hai file `.idea/deploymentTargetSelector.xml`, `.idea/vcs.xml` sửa sẵn; giữ nguyên.
- Không clear app data, reset PIN, đổi permissions/dependencies/SDK hay sáu detection rules. Commit/push chỉ thực hiện theo yêu cầu tiếp theo của người dùng sau khi hoàn tất bản sửa và kiểm thử.
- Điện thoại kết nối ban đầu: model `2412DPC0AG`, API 36. Trước thử nghiệm không có MonitoringService chạy. App profiling ban đầu cài bằng `install -r`; APK instrumentation bị máy từ chối với `INSTALL_FAILED_USER_RESTRICTED`. Đã yêu cầu người dùng cho phép, không bypass hạn chế cài đặt. Sau đó điện thoại ngắt kết nối; chưa cài bản audio fix cuối lên máy và không có fingerprint dữ liệu sau cùng để đối chiếu. Không chạy crypto/storage test trên PIN thật.
- Các phép đo thực thi được hiện tại: emulator `Pixel_7a`, x86_64 API 37, port 5580, không wipe-data. Số đo emulator không được coi là số đo điện thoại thật.

## 1. Root cause xác nhận

### Audio

Ở baseline, `AUDIOFOCUS_LOSS` gọi `fail()` → `stop()` → release player/focus và xóa health runnable. Service nhận failure lại stop AlarmController. Monitoring vẫn ALARMING, repeated sensor events bị gate nên không khởi động audio lần nữa. Đây là đường code xác nhận được, phù hợp triệu chứng người dùng quan sát trên điện thoại; không có log TikTok trên máy thật từ lần kiểm thử này.

Transient/duck loss pause và chờ GAIN là hợp lệ. Permanent loss không hứa hẹn GAIN trở lại. `AUDIOFOCUS_GAIN_TRANSIENT` vẫn phù hợp cho alarm chờ dismiss, không đổi thành GAIN hoặc EXCLUSIVE chỉ để tranh focus. Xem [Android audio focus guide](https://developer.android.com/media/optimize/audio-focus) và [AudioFocusRequest reference](https://developer.android.com/reference/android/media/AudioFocusRequest).

### PIN

Phép đo emulator xác nhận PBKDF2 là phần lớn thời gian; không có bằng chứng coroutine/lifecycle delivery gây chậm vài giây. Khi đang có playback load, PBKDF2 median khoảng 2.007 giây, trong khi success delivery khoảng 0.524 ms và navigation khoảng 0.799 ms. Không suy diễn các giá trị này thành nguyên nhân đã đo trên điện thoại API 36.

Reset các counters vốn đã bằng 0 là thao tác dư thừa xác nhận được. Bỏ thao tác này chỉ tiết kiệm phần nhỏ; không giải quyết phần lớn thời gian PBKDF2.

## 2. Audio trước/sau

| Tình huống | Baseline | Sau sửa |
| --- | --- | --- |
| GRANTED | Prepare/start | Prepare/start, chỉ báo PLAYING khi còn focus và native player đang playing |
| DELAYED | Prepare, chờ gain | Chuẩn bị một player, SUSPENDED đến khi GAIN; không request lặp |
| DENIED | Fail, release toàn bộ audio | Alarm loop giữ SUSPENDED và có recovery budget; preview ngắn vẫn báo failure |
| TRANSIENT_LOSS / DUCK | Pause và chờ gain | Pause, SUSPENDED, giữ player; resume ngay trên callback GAIN |
| PERMANENT_LOSS | Stop audio, không recovery | Pause, bỏ focus request cũ, giữ player và chờ điều kiện recovery |
| MediaPlayer error | Failure/release | Vẫn là failure/release thật, không retry codec vô hạn |
| STOP | Release | Hủy health, budget, player/focus và cả callback generations; không thể tự phát lại |

Recovery chỉ áp dụng alarm loop còn active. Kiểm tra công khai `AudioManager.mode == MODE_NORMAL` và `!isMusicActive`, đợi yên lặng ít nhất 750 ms, backoff 2/5/15 giây, tối đa 3 lần tự request trong một playback session. Budget không reset vì gain/loss hoặc đổi tone. Poll health 500 ms nhưng không poll bằng focus requests. Khi DELAYED hoặc transient loss thì chỉ đợi callback; khi budget hết vẫn SUSPENDED, không tranh focus vô hạn. Sự yên lặng là heuristic, không chứng minh quyền phát; chỉ request GRANTED/GAIN hợp lệ mới cho resume.

Guide cho media thông thường khuyến nghị thao tác play rõ ràng của người dùng sau permanent loss. Chính sách auto-recovery ở đây là lựa chọn riêng theo yêu cầu của alarm còn được người dùng arm, không phải cam kết hay cơ chế ưu tiên do Android cung cấp. Việc request có giới hạn qua public API vẫn có thể bị OS từ chối.

Mỗi focus request có generation riêng ngoài playback generation: callback cũ sau permanent loss, STOP hoặc session mới bị bỏ. Đổi tone vẫn release player trước prepare player mới. MediaPlayer errors đi vào failure, không giữ player lỗi để health khởi động lại. Flash/vibration tiếp tục do owner riêng của MonitoringService; focus interruption không gọi service failure/disarm. Không restart monitoring, chỉnh volume, DND hoặc chiếm quyền hệ thống.

## 3. Profiling PIN và giới hạn số đo

`PinVerificationTimings` dùng `SystemClock.elapsedRealtimeNanos()`, chỉ nhận enum tên bước + duration. Log opt-in bằng `Log.isLoggable`, không log PIN, salt, key, verifier/hash, session hay proof. Observer nội bộ phục vụ test chỉ nhận cùng dữ liệu duration. UI trace tồn tại trong bộ nhớ, không đi vào route/saved state/prefs.

Các bước: click → coroutine launch, IO dispatch, mutex wait, preferences read (bao gồm lockout), Keystore retrieval, AES-GCM init/decrypt, PBKDF2 match, counters write/reset, IO resume, result → success delivery, navigation và first STOP draw. `FIRST_STOP_DRAW` là draw callback Compose đầu tiên, không phải thời điểm photon trên màn hình. `IO_RESUME` gồm unlock và dispatch trở lại caller.

Mỗi benchmark có 21 lần verify: lần đầu báo riêng, 20 warm samples báo median (lower middle) và p95 nearest-rank. Lần đầu sau provisioning không phải cold boot: quá trình tạo fixture đã chạm crypto/Keystore. Chưa có thống kê true cold-start/cold-boot trên điện thoại. Không coi n=1 là một phân phối cold đáng tin cậy.

Baseline có instrumentation timing, audio fix và loading feedback; bảng trước/sau dưới đây so sánh trước/sau tối ưu redundant counters commit, giữ nguyên KDF, không phải benchmark pristine APK d5e5c47. Benchmark dùng preferences riêng, Keystore và format verifier thực, không biết/đọc PIN của người dùng.

### Trước tối ưu: Check → STOP khi alarm đang phát (emulator, ms)

| Bước | First | Warm median | Warm p95 |
| --- | ---: | ---: | ---: |
| Click → launch | 2.070 | 0.182 | 0.512 |
| IO dispatch | 0.655 | 0.634 | 6.898 |
| Mutex wait | 0.021 | 0.018 | 0.032 |
| Preferences read | 0.010 | 0.014 | 0.019 |
| Keystore lookup | 3.424 | 1.718 | 8.304 |
| AES decrypt | 11.889 | 5.363 | 17.201 |
| PBKDF2 match | 2393.959 | 2007.070 | 2281.043 |
| Counters write | 0.095 | 0.078 | 0.118 |
| IO resume | 0.258 | 0.350 | 0.405 |
| Verify total | 2421.017 | 2024.807 | 2312.272 |
| Success delivery | 0.771 | 0.524 | 1.135 |
| Navigation | 1.896 | 0.799 | 1.144 |
| Navigation complete → first STOP draw | 106.277 | 71.292 | 95.481 |
| Check → first STOP draw | 2532.905 | 2099.289 | 2404.922 |

Không chạy alarm: baseline verify total median/p95 **1567.122 / 1844.482 ms**; PBKDF2 **1562.523 / 1839.294 ms**. Sau tối ưu redundant write, cùng benchmark không alarm đo verify total **1552.499 / 1843.115 ms**, counters write **0.019 / 0.025 ms**. Chênh lệch tổng khoảng 15 ms nằm trong noise giữa runs; không có căn cứ tuyên bố PBKDF2 nhanh hơn.

### Sau tối ưu: cùng benchmark alarm đang phát (emulator, ms)

| Bước | First | Warm median | Warm p95 |
| --- | ---: | ---: | ---: |
| Click → launch | 0.327 | 0.320 | 0.569 |
| IO dispatch | 5.506 | 1.076 | 9.538 |
| Mutex wait | 0.035 | 0.024 | 0.037 |
| Preferences read | 0.118 | 0.039 | 0.047 |
| Keystore lookup | 9.140 | 3.200 | 15.169 |
| AES decrypt | 9.576 | 5.234 | 27.410 |
| PBKDF2 match | 2126.823 | 2069.001 | 2176.215 |
| Counters write/reset | 0.024 | 0.019 | 0.021 |
| IO resume | 0.492 | 0.360 | 0.473 |
| Verify total | 2152.308 | 2083.093 | 2216.783 |
| Success delivery | 0.517 | 0.563 | 0.800 |
| Navigation | 0.677 | 0.720 | 1.530 |
| First STOP draw | 72.076 | 74.671 | 91.331 |
| Check → first STOP draw | 2226.636 | 2165.524 | 2294.212 |

Chưa đạt mục tiêu dưới một giây. Tổng thời gian sau không nhanh hơn baseline: các runs không kiểm soát CPU scheduling/thermal/GPU như một phòng benchmark chuyên dụng, và phần tiết kiệm write nhỏ hơn biến động KDF. Không dùng các số này để tuyên bố giảm latency trên điện thoại. Bảng sau dùng run cuối hoàn thành đủ 21 samples với Check semantic action; thời gian bắt đầu tại Check handler, không bao gồm touch dispatch. Các test UI thường vẫn dùng touch để kiểm tra tương tác.

## 4. Thay đổi PIN và bảo mật

- Giữ nguyên `PBKDF2WithHmacSHA1`, 210.000 iterations, 256-bit output, salt/IV/schema hiện có, AES-GCM và Android Keystore. `PinPolicy.kt` không đổi.
- Mỗi lần verify vẫn decrypt và derive thật trên Dispatchers.IO; không cache PIN, verifier plaintext, key để bỏ qua xác thực, hoặc kết quả PIN đúng.
- Nếu failures/blocked_until/blocked_elapsed đã bằng 0 thì không tạo Editor/commit reset nữa. Nếu có counter cần reset thì vẫn commit và chỉ trả Verified sau khi commit thành công. Wrong PIN luôn persist failure/lockout như cũ.
- Check đặt busy đồng bộ, spinner nhỏ nằm trong button hiện tại, không đổi layout màn hình. Chặn submit khi busy hoặc completion đang chờ giao. Không có delay giả.
- Completion vẫn consume một lần ở RESUMED. Generation guard hủy verification khi UI dừng/dispose; target route/mode/session và proof được kiểm tra trước khi hiển thị STOP.
- PIN đúng chỉ hiển thị STOP. Service, sensor và alarm còn hoạt động; chỉ STOP với proof hợp lệ mới cleanup → Home. Back, PIN sai, proof cũ/hết hạn không dừng monitoring.

Mục tiêu dưới 1 giây **chưa đạt trên emulator và chưa được đo trên điện thoại**. Giảm iterations hoặc tự đổi sang KDF khác để đạt số đẹp sẽ thay đổi security budget nên không thực hiện. Cache Keystore lookup không được chọn vì số đo chỉ vài ms, không giải quyết nút thắt.

Lựa chọn cho đợt riêng nếu cần nhanh hơn: benchmark một implementation/provider PBKDF2 được kiểm toán và vẫn cho output byte-for-byte giống format cũ; cần review dependency/native build và test vectors. Nếu đổi thuật toán/work factor, cần phê duyệt security tradeoff trước, schema có version, migration sau khi PIN cũ được verify thành công, ghi atomic, backward reader và kế hoạch rollback. Không có migration trong bản sửa này; không coi một KDF ít iterations hơn là tương đương bảo mật.

| Lựa chọn | Tradeoff / compatibility và rollback |
| --- | --- |
| Giữ KDF hiện tại | Giữ security budget và tất cả records cũ; chấp nhận chi phí derive hiện đo được |
| Implementation native được kiểm toán, cùng PBKDF2/output/iterations | Không giảm work factor và không cần đổi record nếu test vectors byte-for-byte đúng; có chi phí dependency/ABI/NDK, supply-chain và review native memory safety. Rollback binary dùng records cũ bình thường |
| KDF/work factor khác | Chưa được phép triển khai; phải đánh giá chi phí tấn công và hiệu năng trên máy chậm, không so iterations giữa hai thuật toán một cách trực tiếp. Chỉ migrate sau xác thực cũ, có version/atomic commit và retained encrypted legacy record trong cửa sổ rollback có giới hạn, không xóa/reset PIN |

## 5. Files changed

- Audio: `alarm/AndroidAlarmPlayback.kt`, thêm `AlarmFocusRecovery.kt`, `AndroidAlarmAudioFocus.kt`. AlarmController/MonitoringService/DetectionController không cần đổi để giữ monitoring và effects độc lập.
- PIN: `security/PinStore.kt`, thêm `PinVerificationTimings.kt`; `pincode/PinCodeViewModel.kt`, `PinCodeScreen.kt`.
- Trace UI: `navigation/ProtectionNavigation.kt`, `detection/DetectionViewModel.kt`, `DetectionScreen.kt`.
- Tests: thêm `AlarmFocusRecoveryTest`, `AudioFocusPlaybackTest`, `SystemAudioFocusTest`, `PinVerificationBenchmark`, `PinEndToEndBenchmark`, `PinLoadingUiTest`, `PinActivityRecreationTest`; bổ sung `PinResultLifecycleTest`, `PinStoreInstrumentedTest`, `ProtectionNavigationTest`.

## 6. Tests thực thi

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

Build debug, 45 unit tests và lint: PASS; lint 0 errors, 90 warnings, 1 hint. Có một lần lint LogTagMismatch ở logging helper; đã dùng hằng TAG và chạy lại PASS. Có lỗi compile ban đầu trong test helper dùng lateinit local reference; đã sửa và build lại.

Instrumentation baseline subset: 6 tests PASS. Suite mở rộng đầu tiên: 36 tests đã chạy, 34 PASS và 2 failures: benchmark first STOP trace timeout; wrapper đếm commits trong test trả về Editor thật thay vì wrapper khi gọi chuỗi. Wrapper đã sửa để giữ chaining và test chạy lại PASS. Diagnostic benchmark xác nhận một số lần touch Check chưa khởi chạy verify dù input đã đủ 4 số, busy=false, không có lỗi; session không thay đổi. Benchmark được đồng bộ route RESUMED/verify-mode và dùng semantic Check action để không phụ thuộc pointer khi IME/layout đang chuyển động. Không đổi production authorization hoặc bỏ lifecycle guard để che timeout.

Kết quả cuối: **37/37 instrumentation tests PASS** trong một suite đầy đủ (282.292 giây), gồm benchmark 21 samples và stress 25 luồng PIN → STOP liên tiếp. Sau đó thêm test Activity recreation thật giữ nguyên ViewModel, kết quả trả muộn không tạo completion: **1/1 PASS** (5.341 giây). Tổng **38 test cases PASS** qua hai lệnh trên cùng production code. Không tuyên bố suite bị failure trước đó là PASS; raw diagnostic/failure logs vẫn được giữ trong artifacts ignored.

Test system focus thật trên emulator đã PASS: nguồn media thứ hai giữ focus và phát nhạc, alarm pause, không auto-request khi media đang active, sau release media request focus lại và resume cùng player. QUIET → native isPlaying đo **1331.844 ms** trong một lần chạy. Test callback gain → native isPlaying với 10 lần loss/gain đo median/p95 **3.605 / 64.406 ms** ở lần chạy suite mở rộng. Đây là mốc framework/player, không đo âm thanh vật lý/DAC hoặc thời điểm focus thật sự rảnh.

Tests bao phủ granted/delayed/denied/transient/duck/permanent/gain, repeated focus changes, quiet/backoff/budget, native player reuse/tone replacement, injected onError, repeated triggers, STOP recovery pending và callback session cũ. Security/UI tests bao phủ PIN đúng/sai/lockout/change/compatibility, duplicate Check, spinner, composition restoration/background invalidation, actual Activity recreation với ViewModel được retain, proof expiry/reuse/stale session và PIN → STOP.

Raw artifacts (ignored): `stabilization-audio-pin-*.log`; Gradle HTML reports trong `app/build/reports`. Không clear logcat để thu metrics; chỉ lọc tag duration. Không dùng connectedAndroidTest/uninstall hoặc clear app data.

Lệnh instrumentation thực tế dùng `adb -s emulator-5580 shell am instrument -w -r com.example.antitheftdemo.test/androidx.test.runner.AndroidJUnitRunner`; test recreation chạy riêng bằng `-e class com.example.antitheftdemo.pincode.PinActivityRecreationTest`. APK dùng `install -r`. Build APK cuối: `app/build/outputs/apk/debug/app-debug.apk`. Fingerprint dữ liệu điện thoại chỉ thu trước lần cài profiling; vì điện thoại ngắt kết nối nên chưa thể xác nhận fingerprint sau cùng hoặc xác minh PIN thật bằng người dùng nhập. Không suy diễn việc tests namespace riêng PASS thành xác thực PIN thật đã PASS.

## 7. Platform limitations và checklist còn lại

Android 35+ chỉ cho request focus khi top app hoặc đang có FGS; OS có thể từ chối hoặc trì hoãn. Permanent loss không hứa hẹn GAIN; media quiet không chứng minh focus rảnh. Recovery có budget nên sau khi dùng hết vẫn SUSPENDED. `isPlaying` cùng focus/volume/DND checks là tín hiệu framework, không chứng minh người dùng thực sự nghe được qua mọi route/OEM policy. Không cam kết phát đè TikTok, YouTube hay cuộc gọi.

- [ ] Điện thoại API 36: cho phép APK instrumentation, đo lại trước/sau với cùng load và true cold/warm runs. Hiện **NOT TESTED** vì máy chặn cài test.
- [ ] TikTok/YouTube app thật trên điện thoại: alarm đã phát → video audio → thoát/dừng video → recovery, ít nhất 10 lần. **NOT TESTED** trong đợt này.
- [ ] Khóa màn hình/background, FGS audio eligibility, incoming call/VoIP/Bluetooth, DND/volume 0, audio route changes.
- [ ] Permanent loss lặp đến hết budget: vẫn báo SUSPENDED, không spam focus và không disarm; không hứa recovery vô hạn.
- [ ] PIN đúng → STOP vẫn alarm/effects; bấm STOP khi recovery pending; đợi quá mọi backoff xác nhận không player/focus/flash/vibration/wake lock/notification phục hồi.
- [ ] Đo Check → STOP trên PIN thật do người dùng nhập thủ công, không gửi PIN qua adb/log; thêm samples đủ để báo median/p95 và xác định true cold-start.
- [ ] Soát lại performance trên ít nhất một máy chậm API 24–25 và một máy API 35+; không suy diễn emulator thành điện thoại thật.

Để thu profiling thủ công sau khi cài bản debug: bật `adb shell setprop log.tag.AntiTheftPinTiming DEBUG`, đọc riêng tag `AntiTheftPinTiming`, thực hiện Check do người dùng nhập PIN trên màn hình, rồi trả tag về `INFO`. Audio có tag opt-in `AntiTheftAudioTiming`. Chỉ chia sẻ duration/step names, không bật log payload xác thực.
