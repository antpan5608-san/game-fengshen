"""AVD-only upgrade check using v16's normal gameplay save, never an uninstall.

Reads only opening-local-save (no account/token preferences). APK paths are the
already verified local publication artifacts, not a second release mechanism.
"""
import hashlib
import json
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERIAL = "emulator-5554"


def adb(*args):
    r = subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True, timeout=180)
    if r.returncode:
        raise RuntimeError("AVD check failed: " + r.stderr.decode(errors="replace")[:300])
    return r.stdout


def save():
    raw = adb("shell", "run-as", "org.fengshen.dev", "cat",
              "shared_prefs/opening-local-save.xml")
    root = ET.fromstring(raw)
    text = next(x.text for x in root if x.attrib.get("name") == "saveJson")
    return raw, json.loads(text)


def main():
    devices = adb("get-state").decode().strip()
    assert devices == "device"
    assert b"ranchu" in adb("shell", "getprop", "ro.hardware"), "AVD only"
    adb("shell", "am", "force-stop", "org.fengshen.dev")
    old = ROOT / "artifacts/published/previous.apk"
    new = ROOT / "artifacts/published/current.apk"
    assert hashlib.sha256(old.read_bytes()).hexdigest() == "f50d1122af442b4c6216d7f3a37e19d517a05f704ecd4f9b33d5937827f96a92"
    assert hashlib.sha256(new.read_bytes()).hexdigest() == "15562dd117d2a703dbb09144943a3ccb8fe737fbe85fd68803bf702aa787da79"
    assert b"Success" in adb("install", "-r", "-d", str(old))
    result = adb("shell", "am", "instrument", "-w", "-e", "class",
                 "org.fengshen.dev.TouchTest#testNormalOpeningRouteGiftAndMap16Encounter",
                 "org.fengshen.dev.test/android.test.InstrumentationTestRunner")
    (ROOT / "reports/audio-log01-upgrade-old-gameplay.txt").write_bytes(result)
    assert b"OK (1 test)" in result, "v16 normal gameplay did not pass"
    before_raw, before = save()
    assert before["contentVersion"] == "opening-segment-001-c7"
    adb("shell", "am", "force-stop", "org.fengshen.dev")
    assert b"Success" in adb("install", "-r", str(new))
    installed_raw, installed = save()
    assert before_raw == installed_raw, "installer altered preferences"
    adb("shell", "am", "start", "-W", "-n", "org.fengshen.dev/.MainActivity")
    time.sleep(12)
    adb("shell", "am", "force-stop", "org.fengshen.dev")
    after_raw, after = save()
    assert after.pop("contentVersion") == "opening-segment-001-c8"
    expected = dict(before)
    expected.pop("contentVersion")
    assert expected == after, "restored gameplay state changed"
    report = {
        "status": "PASS", "device": SERIAL, "realDevice": False,
        "route": "v16 normal joystick/NPC/encounter/save -> install -r v17 -> start -> stop",
        "preferencesUnchangedByInstaller": before_raw == installed_raw,
        "gameplayStateUnchangedAfterMigration": expected == after,
        "beforeContent": "opening-segment-001-c7", "afterContent": "opening-segment-001-c8",
        "beforePreferencesSHA256": hashlib.sha256(before_raw).hexdigest(),
        "afterPreferencesSHA256": hashlib.sha256(after_raw).hexdigest(),
        "compared": list(expected), "uninstallOrClearUserPhoneData": False,
    }
    (ROOT / "reports/audio-log01-upgrade.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    adb("shell", "am", "start", "-W", "-n", "org.fengshen.dev/.MainActivity")


if __name__ == "__main__":
    main()
