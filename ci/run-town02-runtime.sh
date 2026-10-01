#!/usr/bin/env bash
# One isolated AVD for the existing build workflow; no publication credentials.
set -euo pipefail
sdk="${ANDROID_HOME:?Existing runner SDK is required}"
export ANDROID_SDK_ROOT="$sdk"
export PATH="$sdk/platform-tools:$PATH"
mkdir -p artifacts/town02-runtime
sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"
(set +o pipefail; yes | "$sdkmanager" --licenses) > artifacts/town02-runtime/sdk-licenses.txt 2>&1
"$sdkmanager" 'emulator' 'platform-tools' 'build-tools;35.0.0' 'system-images;android-30;default;x86_64' > artifacts/town02-runtime/sdk-setup.txt 2>&1
printf 'no\n' | "$sdk/cmdline-tools/latest/bin/avdmanager" create avd --name fengshen-town02-ci --package 'system-images;android-30;default;x86_64' --device pixel_5
python - <<'PYAVD'
import os
from pathlib import Path
p=Path(os.environ.get('ANDROID_AVD_HOME',str(Path.home()/'.android/avd')))/'fengshen-town02-ci.avd/config.ini'
s=p.read_text().splitlines();values={'hw.lcd.width':'960','hw.lcd.height':'540','hw.lcd.density':'160'}
s=[line for line in s if line.split('=',1)[0].strip() not in values]
p.write_text('\n'.join(s+[k+'='+v for k,v in values.items()])+'\n')
PYAVD
accel=off
# Only the ephemeral runner's current UID may use its existing KVM device.
if [[ -c /dev/kvm && ! -w /dev/kvm ]] && command -v setfacl >/dev/null; then sudo setfacl -m "u:$(id -un):rw" /dev/kvm; fi
if [[ -r /dev/kvm && -w /dev/kvm ]]; then accel=auto; fi
"$sdk/emulator/emulator" -avd fengshen-town02-ci -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -accel "$accel" -memory 3072 -cores 2 > artifacts/town02-runtime/emulator.txt 2>&1 &
emulator_pid=$!
trap 'kill "$emulator_pid" 2>/dev/null || true' EXIT
ready=false
for attempt in $(seq 1 120); do
    if ! kill -0 "$emulator_pid" 2>/dev/null; then echo 'Android emulator exited; App validation unavailable'; exit 1; fi
    boot=$(timeout 10 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
    if [[ "$boot" == 1 ]] && timeout 10 adb shell service check package | grep -q found; then ready=true; break; fi
    sleep 5
done
[[ "$ready" == true ]] || { echo 'Android boot/service budget exhausted; App validation NOT_RUN'; exit 1; }
adb shell wm size 960x540
adb shell wm density 160
sleep 10
base=(artifacts/runtime-base/*-release.apk)
candidate=(artifacts/ci/*-release.apk)
testapk=(artifacts/runtime-test/*.apk)
[[ ${#base[@]} == 1 && ${#candidate[@]} == 1 && ${#testapk[@]} == 1 ]]
python tools/ci_apk.py verify --base-only --apk "${base[0]}" --output artifacts/town02-runtime/base.json
python tools/ci_apk.py verify --apk "${candidate[0]}" --output artifacts/town02-runtime/candidate.json
python - "${testapk[0]}" <<'PY'
import re,sys
from tools import ci_apk as ci
cert=ci.command([ci.tool('apksigner'),'verify','--print-certs',sys.argv[1]])
assert re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)',cert)==[ci.CONFIG['signerSha256']], 'Test APK must have the existing signature'
PY
run_test(){
    timeout 1200 adb shell am instrument -w -e keepFixtureForRestart true -e class "org.fengshen.dev.TouchTest#$1" org.fengshen.dev.test/android.test.InstrumentationTestRunner > "artifacts/town02-runtime/$1.txt" 2>&1
    grep -q 'OK (1 test)' "artifacts/town02-runtime/$1.txt"
}
adb install -r "${base[0]}"
adb install -r "${testapk[0]}"
run_test testExportCurrentSaveForUpgrade
adb shell am force-stop org.fengshen.dev
adb install -r "${candidate[0]}" # Same signature, actual covering install; never uninstall/clear.
run_test testUpgradeKeepsPreviousSave
run_test testControlledHerbBoundariesAndSaveCompatibility
python tools/record_app_audio.py town02-ci testNormalHerbSupplyLoop --silent
# Existing recorder checks external force-stop/restart and restores original preferences.
python - <<'PY'
import json,os
from pathlib import Path
from tools import ci_apk as ci
r=json.loads(Path('artifacts/town02-runtime/candidate.json').read_text())
r.update(sourceCommit=os.environ['GITHUB_SHA'],buildRunID=os.environ['GITHUB_RUN_ID'],runtime='PASS',upgrade='PASS',normalHerbSupply='PASS',controlledBoundaries='PASS',audio='NOT_RUN',onePlus13T='NOT_RUN')
Path('artifacts/town02-runtime/runtime-receipt.json').write_text(json.dumps(r,indent=2)+'\n')
print(json.dumps(r))
PY
