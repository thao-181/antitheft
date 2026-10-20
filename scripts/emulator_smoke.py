"""ADB smoke checks for a development emulator. Does not clear app data.

Run only on an emulator with no existing user PIN. --resume continues a run that
this script has already configured. Test fixture digits are never printed.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--adb", default=str(Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk/platform-tools/adb.exe"))
parser.add_argument("--serial", default="emulator-5554")
parser.add_argument("--resume", action="store_true")
parser.add_argument("--cleanup", action="store_true")
parser.add_argument("--extended-only", action="store_true")
args = parser.parse_args()
PACKAGE = "com.example.antitheftdemo"
# Explicit test fixture, created only by this script, never an app default.
TEST_PIN = "2468"
base = [args.adb, "-s", args.serial]

def adb(*arguments, check=True):
    result = subprocess.run([*base, *arguments], capture_output=True, text=True, timeout=30)
    if check and result.returncode:
        raise RuntimeError("ADB operation failed: " + result.stderr.strip())
    return result

def shell(*arguments, check=True):
    return adb("shell", *arguments, check=check)

def ui():
    shell("uiautomator", "dump", "/sdcard/antitheft-smoke.xml")
    return list(ET.fromstring(shell("cat", "/sdcard/antitheft-smoke.xml").stdout).iter("node"))

def has(label, nodes=None):
    return any(n.get("text") == label or n.get("content-desc") == label for n in (nodes if nodes is not None else ui()))

def wait(label, timeout=25):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        nodes = ui()
        if has(label, nodes):
            return nodes
        time.sleep(.3)
    raise AssertionError("Expected UI state: " + label)

def tap_node(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
    shell("input", "tap", str((x1+x2)//2), str((y1+y2)//2))

def tap(label):
    nodes = wait(label)
    tap_node(next(n for n in nodes if n.get("text") == label or n.get("content-desc") == label))

def enter_pin(value):
    nodes = ui()
    field = next(n for n in nodes if n.get("class") == "android.widget.EditText")
    tap_node(field)
    shell("input", "text", value)

def check_pin(value):
    enter_pin(value)
    tap("Check")

def service_present():
    return ".detection.service.MonitoringService" in shell("dumpsys", "activity", "services", PACKAGE).stdout

def await_service_stopped():
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        if not service_present(): return
        time.sleep(.3)
    raise AssertionError("Monitoring service did not stop")

def stop_with_pin():
    tap("Deactivate")
    wait("Enter your 4 digit PIN Code")
    check_pin(TEST_PIN)
    wait("Stop protection")
    if not service_present(): raise AssertionError("PIN prematurely stopped monitoring")
    tap("Stop protection")
    wait("Anti Theft Alarm")
    await_service_stopped()

def home():
    if has("Anti Theft Alarm"): return
    shell("input", "keyevent", "4")
    wait("Anti Theft Alarm")

def activate(label):
    tap(label)
    nodes = ui()
    tap("Activate protection")
    wait("Tap to deactivate")
    if not service_present(): raise AssertionError("UI secured without monitoring service")

if not args.serial.startswith("emulator-"):
    raise SystemExit("This smoke script is restricted to emulators.")
exists = shell("run-as", PACKAGE, "test", "-f", "shared_prefs/pin_security.xml", check=False).returncode == 0
marker = Path("app/build/validation/smoke-pin-created.txt")
if exists and not (args.resume and marker.exists()):
    raise SystemExit("Existing PIN detected. Refusing to modify or guess an existing user's PIN.")
if service_present():
    if not (args.resume and marker.exists()):
        raise SystemExit("An active monitoring service already exists; refusing to interrupt it.")
    shell("am", "start", "-n", PACKAGE + "/.MainActivity")
    nodes = ui()
    shortcut = next((n for n in nodes if n.get("text", "").startswith("Protection active") or n.get("text", "").startswith("Alarm triggered - open protection")), None)
    if shortcut is not None: tap_node(shortcut)
    if has("Enter your 4 digit PIN Code"):
        check_pin(TEST_PIN)
        wait("Stop protection")
        if not service_present(): raise AssertionError("PIN prematurely stopped monitoring")
        tap("Stop protection")
        wait("Anti Theft Alarm")
        await_service_stopped()
    else: stop_with_pin()
    print("PASS recovered the script's previous session using its PIN", flush=True)
if args.cleanup:
    raise SystemExit(0)
shell("am", "force-stop", PACKAGE)
shell("am", "start", "-n", PACKAGE + "/.MainActivity")
if not exists:
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        nodes = ui()
        if any(has(label, nodes) for label in ["Language", "Next", "Permission", "Create new PIN"]): break
        time.sleep(.3)
    if has("Language", nodes):
        tap("English")
        tap("Save Language")
        wait("Next")
    for _ in range(3):
        nodes = ui()
        if not has("Next", nodes): break
        tap("Next")
        time.sleep(.5)
    nodes = ui()
    if has("Permission", nodes):
        if not has("Notifications allowed. Monitoring can show its status.", nodes):
            switch = next(n for n in nodes if n.get("checkable") == "true")
            tap_node(switch)
            deadline = time.monotonic() + 15
            while time.monotonic() < deadline:
                nodes = ui()
                allow = next((n for n in nodes if n.get("resource-id", "").endswith("permission_allow_button") or n.get("text") == "Allow"), None)
                if allow is not None:
                    tap_node(allow); break
                time.sleep(.3)
            wait("Notifications allowed. Monitoring can show its status.")
        tap("Continue")
    wait("Create new PIN")
    enter_pin(TEST_PIN)
    tap("Create")
    wait("Confirm new PIN")
    enter_pin("2469")
    tap("Submit")
    wait("PINs do not match. Please confirm again.")
    enter_pin(TEST_PIN)
    marker.parent.mkdir(parents=True, exist_ok=True)
    marker.write_text("Only a non-secret marker; this emulator was configured by emulator_smoke.py.")
    tap("Submit")
    wait("Anti Theft Alarm")
    print("PASS onboarding, real notification permission, PIN confirmation mismatch and success", flush=True)
else:
    wait("Anti Theft Alarm")

shell("am", "force-stop", PACKAGE)
shell("am", "start", "-n", PACKAGE + "/.MainActivity")
wait("Anti Theft Alarm")
print("PASS relaunch skips completed setup and retains PIN", flush=True)

original_wifi = "enabled" in shell("cmd", "wifi", "status").stdout.lower().split("wifi is ")[-1].splitlines()[0]
original_sensors = {}
if args.extended_only:
    for sensor in ("proximity", "acceleration"):
        output = adb("emu", "sensor", "get", sensor).stdout
        original_sensors[sensor] = next(line.split("=", 1)[1].strip() for line in output.splitlines() if "=" in line)
try:
    if not args.extended_only:
        shell("svc", "wifi", "enable")
        time.sleep(2)
        activate("Wi-Fi Detection")
        tap("Deactivate")
        check_pin("2469")
        wait("Incorrect PIN")
        if not service_present(): raise AssertionError("Wrong PIN stopped monitoring")
        shell("input", "keyevent", "4")
        wait("Tap to deactivate")
        print("PASS incorrect PIN and cancellation preserve secured monitoring", flush=True)
        shell("input", "keyevent", "3")
        time.sleep(1)
        if not service_present(): raise AssertionError("Monitoring lost in background")
        shell("svc", "wifi", "disable")
        time.sleep(2)
        shell("am", "start", "-n", PACKAGE + "/.MainActivity")
        wait("Alarm playing - enter PIN to stop")
        stop_with_pin()
        print("PASS background Wi-Fi adapter alarm and PIN-authenticated service/audio cleanup", flush=True)
        # A consumed proof cannot disable a later session.
        shell("svc", "wifi", "enable")
        tap("Wi-Fi Detection")
        tap("Activate protection")
        wait("Tap to deactivate")
        tap("Deactivate")
        wait("Enter your 4 digit PIN Code")
        check_pin("2469")
        wait("Incorrect PIN")
        check_pin(TEST_PIN)
        wait("Stop protection")
        if not service_present(): raise AssertionError("PIN prematurely stopped monitoring")
        tap("Stop protection")
        wait("Anti Theft Alarm")
        await_service_stopped()
        print("PASS a new monitoring session requires PIN again", flush=True)
        home()
        shell("dumpsys", "battery", "set", "ac", "1")
        shell("dumpsys", "battery", "set", "level", "75")
        activate("Charger Detection")
        shell("dumpsys", "battery", "unplug")
        wait("Alarm playing - enter PIN to stop")
        stop_with_pin()
        print("PASS charger connected baseline, disconnect alarm and PIN stop", flush=True)
        home()
        shell("dumpsys", "battery", "set", "ac", "1")
        shell("dumpsys", "battery", "set", "level", "99")
        activate("Battery Full Detection")
        shell("dumpsys", "battery", "set", "level", "100")
        wait("Alarm playing - enter PIN to stop")
        stop_with_pin()
        print("PASS charging battery 99-to-100 alarm and PIN stop", flush=True)
        home()
    else:
        adb("emu", "sensor", "set", "proximity", "10")
        activate("Proximity Detection")
        if has("Alarm playing - enter PIN to stop"): raise AssertionError("Proximity triggered on initial far value")
        adb("emu", "sensor", "set", "proximity", "0")
        time.sleep(.8)
        adb("emu", "sensor", "set", "proximity", "10")
        wait("Alarm playing - enter PIN to stop")
        stop_with_pin()
        print("PASS proximity far baseline, near-to-far sensor alarm and PIN stop", flush=True)
        home()
        adb("emu", "sensor", "set", "acceleration", "0:0:9.81")
        activate("Motion Detection")
        time.sleep(2.2)
        if has("Alarm playing - enter PIN to stop"): raise AssertionError("Motion triggered while stationary")
        adb("emu", "sensor", "set", "acceleration", "15:0:9.81")
        wait("Alarm playing - enter PIN to stop")
        adb("emu", "sensor", "set", "acceleration", "0:0:9.81")
        stop_with_pin()
        print("PASS motion stationary baseline, sustained acceleration alarm and PIN stop", flush=True)
        home()
        tap("Handsfree Detection")
        tap("Activate protection")
        wait("Connect wired or Bluetooth headphones before activating this mode.")
        if service_present(): raise AssertionError("Headphone failure leaked a monitoring service")
        if has("Tap to deactivate"): raise AssertionError("Headphone failure left SECURED UI")
        print("PASS handsfree refuses to arm without a connected audio endpoint", flush=True)
        home()
        shell("svc", "wifi", "enable")
        time.sleep(2)
        activate("Wi-Fi Detection")
        shell("input", "keyevent", "3")
        shell("cmd", "statusbar", "expand-notifications")
        tap("Protection active")
        wait("Enter your 4 digit PIN Code")
        check_pin(TEST_PIN)
        wait("Stop protection")
        if not service_present(): raise AssertionError("PIN prematurely stopped monitoring")
        tap("Stop protection")
        wait("Anti Theft Alarm")
        await_service_stopped()
        print("PASS notification opens PIN, then authorized STOP requires a tap before Home", flush=True)
        home()
        activate("Wi-Fi Detection")
        home()
        tap("Charger Detection")
        tap("Activate protection")
        wait("Deactivate the current mode with PIN first.")
        if not service_present(): raise AssertionError("Selecting another mode lost the armed service")
        home()
        tap("Wi-Fi Detection")
        stop_with_pin()
        print("PASS a second mode cannot silently replace the armed mode", flush=True)
        home()
        activate("Wi-Fi Detection")
        shell("am", "force-stop", PACKAGE)
        shell("am", "start", "-n", PACKAGE + "/.MainActivity")
        wait("Previous monitoring was interrupted. Activate protection again.")
        if service_present(): raise AssertionError("App unexpectedly rearmed after force-stop")
        print("PASS process termination is reported and monitoring does not restart automatically", flush=True)

finally:
    for sensor, value in original_sensors.items():
        adb("emu", "sensor", "set", sensor, value, check=False)
    shell("dumpsys", "battery", "reset", check=False)
    shell("svc", "wifi", "enable" if original_wifi else "disable", check=False)
