"""Explicit emulator-only UI checks for Stage A-C. Never clears application data or changes the stored PIN.
Uses a separate validation application ID; preserves the normal application PIN and data.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--serial", default="emulator-5554")
parser.add_argument("--adb", default=str(Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk/platform-tools/adb.exe"))
parser.add_argument("--package", default="com.example.antitheftdemo.validation")
args = parser.parse_args()
PACKAGE = args.package
TEST_PIN = "2468"  # Test-only fixture. Never printed or written by this script.
if not args.serial.startswith("emulator-") or PACKAGE != "com.example.antitheftdemo.validation":
    raise SystemExit("This script only runs against the isolated validation app on an emulator.")
base = [args.adb, "-s", args.serial]

def adb(*arguments, check=True):
    result = subprocess.run([*base, *arguments], text=True, capture_output=True, timeout=30)
    if check and result.returncode: raise RuntimeError("ADB operation failed")
    return result.stdout

def shell(*arguments, **kwargs): return adb("shell", *arguments, **kwargs)
def ui():
    for _ in range(5):
        shell("uiautomator", "dump", "/sdcard/settings-flow-smoke.xml", check=False)
        xml = shell("cat", "/sdcard/settings-flow-smoke.xml", check=False).strip()
        if xml.startswith("<?xml"):
            return list(ET.fromstring(xml).iter("node"))
        time.sleep(.4)
    raise RuntimeError("Emulator UI is not ready for accessibility inspection")
def has(label, nodes=None):
    return any(n.get("text") == label or n.get("content-desc") == label for n in (ui() if nodes is None else nodes))
def wait(label, timeout=25):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        nodes = ui()
        if has(label, nodes): return nodes
        time.sleep(.25)
    raise AssertionError("Expected UI state: " + label)
def tap_node(node):
    x1,y1,x2,y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
    shell("input", "tap", str((x1+x2)//2), str((y1+y2)//2))
def tap(label):
    nodes = wait(label)
    tap_node(next(n for n in nodes if n.get("text") == label or n.get("content-desc") == label))
def pin(value):
    tap_node(next(n for n in ui() if n.get("class") == "android.widget.EditText"))
    shell("input", "text", value)
    tap("Check")
def service(): return ".detection.service.MonitoringService" in shell("dumpsys", "activity", "services", PACKAGE)
def assert_armed():
    if not service(): raise AssertionError("Monitoring was stopped without verified deactivation")
def assert_stopped():
    deadline=time.monotonic()+10
    while service() and time.monotonic()<deadline: time.sleep(.25)
    if service(): raise AssertionError("Service was not released after PIN")
def home():
    if not has("Anti Theft Alarm"): shell("input", "keyevent", "4")
    wait("Anti Theft Alarm")
def activate(title):
    tap(title)
    if has("Reset protection"): tap("Reset protection")
    tap("Activate protection")
    wait("Tap to deactivate")
    assert_armed()
def prefs(name):
    result = shell("run-as", PACKAGE, "cat", "shared_prefs/"+name+".xml", check=False)
    if not result.strip().startswith("<?xml"): return {}
    result_map={}
    for node in ET.fromstring(result):
        result_map[node.get("name")] = node.text if node.tag == "string" else node.get("value")
    return result_map
def ensure_switch(label, value):
    nodes=ui()
    node=next(n for n in nodes if n.get("checkable") == "true" and
              any(child.get("content-desc") == label for child in n.iter()))
    if (node.get("checked") == "true") != value: tap_node(node)
def report(message):
    print("PASS " + message, flush=True)
    target=Path("app/build/validation/settings-flow-smoke.log")
    target.parent.mkdir(parents=True,exist_ok=True)
    with target.open("a",encoding="utf-8") as out: out.write("PASS " + message + "\n")

if service(): raise SystemExit("A service is already armed; refusing to interrupt it.")
old_wifi = "enabled" in shell("cmd", "wifi", "status").lower().split("wifi is ")[-1].splitlines()[0]
old_proximity=next(line.split("=",1)[1].strip() for line in adb("emu","sensor","get","proximity").splitlines() if "=" in line)
old_volume=None
shell("am","start","-n",PACKAGE+"/com.example.antitheftdemo.MainActivity")
time.sleep(3.5)
if has("Language"):
    tap("English")
    tap("Save Language")
    for _ in range(3): tap("Next"); time.sleep(.5)
    nodes=wait("Permission")
    if not has("Notifications allowed. Monitoring can show its status.",nodes):
        tap_node(next(n for n in nodes if n.get("checkable") == "true"))
        deadline=time.monotonic()+15
        while time.monotonic()<deadline:
            nodes=ui()
            allow=next((n for n in nodes if n.get("resource-id","").endswith("permission_allow_button")),None)
            if allow is not None: tap_node(allow); break
        wait("Notifications allowed. Monitoring can show its status.")
    tap("Continue")
    wait("Create new PIN")
    tap_node(next(n for n in ui() if n.get("class") == "android.widget.EditText"))
    shell("input","text",TEST_PIN)
    tap("Create")
    wait("Confirm new PIN")
    tap_node(next(n for n in ui() if n.get("class") == "android.widget.EditText"))
    shell("input","text",TEST_PIN)
    tap("Submit")
wait("Anti Theft Alarm")
try:
    shell("svc","wifi","enable")
    time.sleep(1)
    activate("Wi-Fi Detection")
    tap("Deactivate")
    wait("Verify PIN to stop protection")
    if has("Enter your 4 digit PIN Code"): raise AssertionError("Deactivate skipped confirmation")
    assert_armed()
    shell("input","keyevent","4")
    wait("Tap to deactivate")
    assert_armed()
    tap("Deactivate")
    tap("Verify PIN to stop protection")
    pin("2469")
    wait("Incorrect PIN")
    assert_armed()
    shell("input","keyevent","4")
    wait("Verify PIN to stop protection")
    assert_armed()
    tap("Verify PIN to stop protection")
    pin(TEST_PIN)
    wait("Anti Theft Alarm")
    assert_stopped()
    tap("Wi-Fi Detection")
    wait("Reset protection")
    if service(): raise AssertionError("Returning Home automatically rearmed detection")
    home()
    report("STOP confirmation, Back, incorrect/cancelled PIN, verified cleanup then Home without rearming")

    tap("Settings")
    for label in ["PIN Code","Alarm Tone","Language","Flash Light","Vibrate"]: wait(label)
    for label in ["Other","Rate","Share","Feedback","Privacy Policy"]:
        if has(label): raise AssertionError("Removed Settings section is visible")
    original_language=prefs("setup").get("language","English")
    tap("Language")
    alternate="French" if original_language!="French" else "English"
    tap(alternate)
    tap("Save Language")
    wait("Settings")
    if prefs("setup").get("language") != alternate: raise AssertionError("Language selection was not saved")
    tap("Language")
    tap(original_language)
    tap("Save Language")
    wait("Settings")
    tap("PIN Code")
    wait("Enter your 4 digit PIN Code")
    pin(TEST_PIN)
    wait("Create new PIN")
    shell("input","keyevent","4")
    wait("Settings")
    report("Settings five rows, no Other section, Language save/return, old PIN verification before creation")

    tap("Alarm Tone")
    tap("Alarm tone 2")
    tap("Ok")
    wait("Settings")
    tap("Alarm Tone")
    tap("Alarm tone 4")
    tap("Cancel")
    wait("Settings")
    if prefs("alarm_settings").get("tone") != "2": raise AssertionError("Cancel changed saved tone")
    ensure_switch("Vibrate",False)
    flash_supported="feature:android.hardware.camera.flash" in shell("pm","list","features")
    if flash_supported: ensure_switch("Flash Light",True)
    else: print("LIMIT emulator has no flash; torch hardware check remains pending",flush=True)
    time.sleep(.5)
    saved=prefs("alarm_settings")
    if saved.get("vibrate") != "false": raise AssertionError("Vibrate setting was not saved")
    if flash_supported and saved.get("flash") != "true": raise AssertionError("Flash setting was not saved")
    shell("am","force-stop",PACKAGE)
    shell("am","start","-n",PACKAGE+"/com.example.antitheftdemo.MainActivity")
    wait("Anti Theft Alarm")
    tap("Settings")
    wait("Customize Alarm Tone")
    if prefs("alarm_settings") != saved: raise AssertionError("Settings did not survive app restart")
    report("Alarm tone 2, Cancel rollback, Vibrate OFF and supported Flash preference persist across restart")
    home()

    adb("emu","sensor","set","proximity","10")
    activate("Proximity Detection")
    home()
    tap("Settings")
    tap("Alarm Tone")
    wait("Preview is unavailable while protection is armed.")
    tap("Alarm tone 2")
    tap("Ok")
    assert_armed()
    home()
    tap("Proximity Detection")
    adb("emu","sensor","set","proximity","0")
    time.sleep(.8)
    adb("emu","sensor","set","proximity","10")
    wait("Alarm playing - enter PIN to stop")
    shell("input","keyevent","223")
    time.sleep(2)
    assert_armed()
    shell("input","keyevent","224")
    shell("input","keyevent","82")
    shell("am","start","-n",PACKAGE+"/com.example.antitheftdemo.MainActivity")
    wait("Alarm playing - enter PIN to stop")
    report("Selected tone alarm survives Settings and screen lock; preview cannot overlap an armed session")

    volume_status=shell("cmd","media_session","volume","--stream","4","--get")
    match=re.search(r"(?:Current volume is|volume is) (\d+)",volume_status)
    minimum=re.search(r"range \[(\d+)\.\.",volume_status)
    if match and (minimum is None or int(minimum.group(1)) == 0):
        old_volume=int(match.group(1))
        shell("cmd","media_session","volume","--stream","4","--set","0")
        wait("Alarm audio paused or muted by Android - enter PIN to stop")
        assert_armed()
        shell("cmd","media_session","volume","--stream","4","--set",str(max(1,old_volume)))
        wait("Alarm playing - enter PIN to stop")
        report("Volume zero is reported as suspended while monitoring stays armed; user volume restoration resumes audio")
    elif minimum and int(minimum.group(1)) > 0:
        print("LIMIT emulator alarm stream has a nonzero minimum; zero-volume policy is covered by unit tests",flush=True)
    else: print("LIMIT emulator shell cannot query alarm volume",flush=True)
    tap("Deactivate")
    wait("Verify PIN to stop protection")
    wait("Alarm playing - enter PIN to stop")
    assert_armed()
    tap("Verify PIN to stop protection")
    pin(TEST_PIN)
    wait("Anti Theft Alarm")
    assert_stopped()
    report("Alarm continues through red confirmation; PIN stops service and effects then returns directly Home")
    tap("Settings")
    tap("PIN Code")
    pin(TEST_PIN)
    wait("Create new PIN")
    shell("input","keyevent","4")
    wait("Settings")
    report("Original PIN was preserved after Settings and alarm tests")
    home()
finally:
    adb("emu","sensor","set","proximity",old_proximity,check=False)
    shell("svc","wifi","enable" if old_wifi else "disable",check=False)
    if old_volume is not None: shell("cmd","media_session","volume","--stream","4","--set",str(old_volume),check=False)
