#!/usr/bin/env bash
# One isolated AVD for the existing build workflow; no publication credentials.
set -euo pipefail
sdk="${ANDROID_HOME:?Existing runner SDK is required}"
export ANDROID_SDK_ROOT="$sdk"
export PATH="$sdk/platform-tools:$PATH"
mkdir -p artifacts/town02-runtime
export ANDROID_AVD_HOME="${RUNNER_TEMP:-$PWD/artifacts/town02-runtime}/fengshen-town02-avd"
mkdir -p "$ANDROID_AVD_HOME"
sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"
(set +o pipefail; yes | "$sdkmanager" --licenses) > artifacts/town02-runtime/sdk-licenses.txt 2>&1
"$sdkmanager" 'emulator' 'platform-tools' 'build-tools;35.0.0' 'system-images;android-30;default;x86_64' > artifacts/town02-runtime/sdk-setup.txt 2>&1
printf 'no\n' | "$sdk/cmdline-tools/latest/bin/avdmanager" create avd --name fengshen-town02-ci --package 'system-images;android-30;default;x86_64' --device pixel_5 --path "$ANDROID_AVD_HOME/fengshen-town02-ci.avd"
python - <<'PYAVD'
import os
from pathlib import Path
p=Path(os.environ.get('ANDROID_AVD_HOME',str(Path.home()/'.android/avd')))/'fengshen-town02-ci.avd/config.ini'
s=p.read_text().splitlines();values={'hw.lcd.width':'2640','hw.lcd.height':'1216','hw.lcd.density':'480'}
s=[line for line in s if line.split('=',1)[0].strip() not in values]
p.write_text('\n'.join(s+[k+'='+v for k,v in values.items()])+'\n')
PYAVD
accel=off
# Only the ephemeral runner's current UID may use its existing KVM device.
if [[ -c /dev/kvm && ! -w /dev/kvm ]] && command -v setfacl >/dev/null; then sudo setfacl -m "u:$(id -un):rw" /dev/kvm; fi
if [[ -r /dev/kvm && -w /dev/kvm ]]; then accel=auto; fi
"$sdk/emulator/emulator" -avd fengshen-town02-ci -skin 2640x1216 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -accel "$accel" -memory 3072 -cores 2 > artifacts/town02-runtime/emulator.txt 2>&1 &
emulator_pid=$!
pull_evidence(){
python - <<'PYEVIDENCE'
import subprocess,re
from pathlib import Path
base='/sdcard/Android/data/org.fengshen.dev/files/'
for name in subprocess.check_output(['adb','shell','ls',base],text=True,timeout=10).splitlines():
    if re.fullmatch(r'(nanhai-[A-Za-z0-9._-]+|touch-ux-[A-Za-z0-9._-]+|town01-(?:touch-ux-|shop|bought|herb)[A-Za-z0-9._-]*)\.(png|json)',name):
        Path('artifacts/checkpoint-ui').mkdir(parents=True,exist_ok=True)
        target=Path('artifacts/checkpoint-ui')/(name if name.startswith(('touch-ux-','nanhai-')) else 'touch-ux-'+name)
        subprocess.run(['adb','pull',base+name,str(target)],check=True,timeout=10)
PYEVIDENCE
}
finish_runtime(){
    set +e
    pull_evidence
    kill "$emulator_pid" 2>/dev/null || true
}
trap finish_runtime EXIT
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
    if ! timeout 1200 adb shell am instrument -w -e keepFixtureForRestart true -e class "org.fengshen.dev.TouchTest#$1" org.fengshen.dev.test/android.test.InstrumentationTestRunner > "artifacts/town02-runtime/$1.txt" 2>&1; then
        cat "artifacts/town02-runtime/$1.txt"; exit 1
    fi
    cat "artifacts/town02-runtime/$1.txt"
    if ! grep -q 'OK (1 test)' "artifacts/town02-runtime/$1.txt"; then
        adb logcat -d -s AndroidRuntime | tail -n 60
        exit 1
    fi
}
adb install -r "${base[0]}"
adb install -r "${testapk[0]}"
# The reviewed base already has TOUCH-UX; preserve it rather than rerun its obsolete cursor baseline.
run_test testExportCurrentSaveForUpgrade
adb shell am force-stop org.fengshen.dev
adb install -r "${candidate[0]}" # Same signature, actual covering install; never uninstall/clear.
run_test testUpgradeKeepsPreviousSave
run_test testTouchUxSelectionScrollAndAtomicEquipment
run_test testTouchUxTradeGesturesAndResultEquivalence
run_test testControlledHerbBoundariesAndSaveCompatibility
run_test testControlledNanhaiVictoryFlagAndResumeOnce
run_test testNormalTownShopsBuySellAndReturn
run_test testOpeningKnifeEquipCyclePersistsWithoutDuplication
run_test testInput01RealMapWallSlidesAndMenuCancellation
run_test testHeldJoystickMenuOpenReleaseDoesNotResumeMovement
timeout 600 adb shell am instrument -w -e class org.fengshen.dev.ContentTest org.fengshen.dev.test/android.test.InstrumentationTestRunner > artifacts/town02-runtime/testContent.txt 2>&1
grep -Eq 'OK \([0-9]+ tests\)' artifacts/town02-runtime/testContent.txt
python tools/record_app_audio.py touch-ux-after testNormalTouchUxSupplyAndEquipment --silent
python tools/record_app_audio.py nanhai-ci testNormalNanhaiRouteBossAndVictory --silent --cold-test testNanhaiColdStartMatchesNormalSave --budget-seconds 3600
# Actual phone-sized windows and scaled text; only this isolated AVD is changed.
adb shell wm size 2640x1216
adb shell wm density 480
adb shell wm size > artifacts/town02-runtime/phone-display.txt
adb shell wm density >> artifacts/town02-runtime/phone-display.txt
for font in 1.0 1.3 2.0; do
    adb shell settings put system font_scale "$font"
    sleep 3
    run_test testTouchUxPhoneSizeAndLargeFont
done
adb shell settings put system font_scale 1.0
adb shell wm size 960x540
adb shell wm density 160
# Existing recorder checks external force-stop/restart and restores original preferences.
python - <<'PY'
import json,os
from pathlib import Path
from tools import ci_apk as ci
r=json.loads(Path('artifacts/town02-runtime/candidate.json').read_text())
r.update(sourceCommit=os.environ['GITHUB_SHA'],buildRunID=os.environ['GITHUB_RUN_ID'],runtime='PASS',upgrade='PASS',normalHerbSupply='PASS',controlledBoundaries='PASS',shopEquipmentInputRegression='PASS',touchUx='PASS',phoneSizedLayout='PASS',baselineComparison='PRESERVED_NOT_RERUN',nanhaiNormalRoute='PASS',nanhaiBossVictory='PASS',nanhaiOnceAndColdRestart='PASS',audio='NOT_RUN',onePlus13T='NOT_RUN')
Path('artifacts/town02-runtime/runtime-receipt.json').write_text(json.dumps(r,indent=2)+'\n')
print(json.dumps(r))
PY
