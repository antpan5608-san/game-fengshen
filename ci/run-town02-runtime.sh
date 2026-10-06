#!/usr/bin/env bash
# One isolated AVD per sequential stage of the existing workflow; no publication credentials.
set -euo pipefail
stage="${FENGSHEN_RUNTIME_STAGE:-all}"
case "$stage" in all|base|world|continuation|development-smoke) ;; *) echo "Unknown runtime stage" >&2; exit 1;; esac
export FENGSHEN_RUNTIME_STAGE="$stage"
if [[ "$stage" == development-smoke ]]; then
    scope_id=DEVELOPMENT_ONLY;quality=NOT_PUBLISHED
else
    scope_id=$(python tools/runtime_handoff.py scope --field id)
    quality=$(python tools/runtime_handoff.py scope --field quality)
fi
export FENGSHEN_RUNTIME_SCOPE="$scope_id"
if [[ ( "$scope_id" == PLAYABLE-R1 || "$scope_id" == WORLD-HELL-R2 ) && "$stage" == all ]]; then echo "Frozen R1 requires base/world/continuation jobs" >&2; exit 1; fi
sdk="${ANDROID_HOME:?Existing runner SDK is required}"
export ANDROID_SDK_ROOT="$sdk"
export PATH="$sdk/platform-tools:$PATH"
mkdir -p artifacts/town02-runtime
export ANDROID_AVD_HOME="${RUNNER_TEMP:-$PWD/artifacts/town02-runtime}/fengshen-town02-avd"
mkdir -p "$ANDROID_AVD_HOME"
sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"
if ! (set +o pipefail; yes | "$sdkmanager" --licenses) > artifacts/town02-runtime/sdk-licenses.txt 2>&1; then
    tail -n 80 artifacts/town02-runtime/sdk-licenses.txt
    echo 'Android SDK license preparation failed; App validation NOT_RUN' >&2
    exit 1
fi
if ! "$sdkmanager" 'emulator' 'platform-tools' 'build-tools;35.0.0' 'system-images;android-30;default;x86_64' > artifacts/town02-runtime/sdk-setup.txt 2>&1; then
    tail -n 80 artifacts/town02-runtime/sdk-setup.txt
    echo 'Android SDK package preparation failed; App validation NOT_RUN' >&2
    exit 1
fi
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
# Keep completed normal flow evidence even when a later independent flow fails.
# These are copies of already hashed isolated App clips, never production logs.
retain_world_clips(){
python - <<'PYWORLDCLIPS'
import json,shutil,hashlib
from pathlib import Path
for flow in ('world-r1-medical','world-west','world-village1','world-north-palace','world-cave85','world-east-palace','world-hell-village2','world-first-hall','world-second-hall','world-hall-batch','world-rebirth','world-village3','world-medical','world-continent-bridge','world-forest101','world-tree107','world-room171','world-yang-join','world-village4','world-ferry','world-island','world-village5','world-cave87','world-village6','world-night8','world-queen117'):
    recording_path=Path(f'artifacts/checkpoint-ui/{flow}-recording.json')
    index_path=Path(f'artifacts/checkpoint-ui/touch-ux-{flow}-normal-index.json')
    if not recording_path.exists() or not index_path.exists():continue # This flow has not completed.
    recording=json.loads(recording_path.read_text())
    index=json.loads(index_path.read_text())
    normal=[s for s in recording['segments'] if '-normal-' in s['file']]
    cold=next(s for s in recording['segments'] if s.get('phase')=='EXTERNAL_FORCE_STOP_ACTUAL_COLD_RESTART_AND_CONTINUE')
    out=Path('artifacts')/f'{flow}-review-clips';out.mkdir(parents=True,exist_ok=True)
    selected=[];total=0
    for segment in [normal[-1],cold]:
        src=Path(segment['file']);assert hashlib.sha256(src.read_bytes()).hexdigest()==segment['sha256']
        if total+src.stat().st_size>24*1024*1024:continue
        shutil.copyfile(src,out/src.name);total+=src.stat().st_size;selected.append(segment)
    (out/'clip-index.json').write_text(json.dumps({'segments':selected,'allSegmentIndex':recording,
        'normalFlow':index,'kind':'UNCHANGED_RAW_ANDROID_APP_CONTINUATION_CLIPS_SILENT',
        'limitBytes':24*1024*1024,'allFootageRetainedInOriginalRuntimeArtifact':True},indent=2))
PYWORLDCLIPS
}
pull_evidence(){
python - <<'PYEVIDENCE'
import subprocess,re
from pathlib import Path
base='/sdcard/Android/data/org.fengshen.dev/files/'
listed=subprocess.run(['adb','shell','ls',base],capture_output=True,text=True,timeout=10)
if listed.returncode:
    # Same AOSP-only diagnostic access already used by the existing recorder, never a real device.
    assert 'ranchu' in subprocess.check_output(['adb','shell','getprop','ro.hardware'],text=True,timeout=10)
    subprocess.run(['adb','root'],check=True,capture_output=True,timeout=10)
    subprocess.run(['adb','wait-for-device'],check=True,timeout=10)
    listed=subprocess.run(['adb','shell','ls',base],capture_output=True,text=True,timeout=10)
    if listed.returncode:
        print('No installed App evidence directory; preserve the primary runtime failure')
        raise SystemExit(0)
for name in listed.stdout.splitlines():
    if re.fullmatch(r'(world-[A-Za-z0-9._-]+|mobile-[A-Za-z0-9._-]+|nanhai-[A-Za-z0-9._-]+|touch-ux-[A-Za-z0-9._-]+|town01-(?:touch-ux-|shop|bought|herb|inn)[A-Za-z0-9._-]*|town01-normal-injury-attempts)\.(png|json)',name):
        Path('artifacts/checkpoint-ui').mkdir(parents=True,exist_ok=True)
        target=Path('artifacts/checkpoint-ui')/(name if name.startswith(('touch-ux-','nanhai-','mobile-')) else 'touch-ux-'+name)
        subprocess.run(['adb','pull',base+name,str(target)],check=True,timeout=10)
PYEVIDENCE
retain_world_clips
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
run_test(){
    local keep_fixture="${2:-true}"
    case "$keep_fixture" in true|false) ;; *) echo "Invalid isolated fixture retention mode" >&2; exit 1;; esac
    if ! timeout 1200 adb shell am instrument -w -e keepFixtureForRestart "$keep_fixture" -e class "org.fengshen.dev.TouchTest#$1" org.fengshen.dev.test/android.test.InstrumentationTestRunner > "artifacts/town02-runtime/$1.txt" 2>&1; then
        cat "artifacts/town02-runtime/$1.txt"; exit 1
    fi
    cat "artifacts/town02-runtime/$1.txt"
    if ! grep -q 'OK (1 test)' "artifacts/town02-runtime/$1.txt"; then
        adb logcat -d -s AndroidRuntime | tail -n 60
        exit 1
    fi
}
if [[ "$stage" == development-smoke ]]; then
    development_scope="${FENGSHEN_DEVELOPMENT_SMOKE_SCOPE:-full}"
    case "$development_scope" in full|rollback|jiang) ;; *) echo "Unknown DEBUG smoke scope" >&2; exit 1;; esac
    # DEBUG-only isolated AVD. No stable receipt or signed publication claim.
    adb install -r android/app/build/outputs/apk/debug/app-debug.apk
    adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if [[ "$development_scope" == jiang ]]; then
        # Inactive bounded c61 recipe: loader/codec plus actual controlled touch
        # and external force-stop. This never creates a release receipt.
        adb shell am instrument -w -e class org.fengshen.dev.ContentTest#testC61FrozenDependenciesAndMedicalPartySave,org.fengshen.dev.ContentTest#testControlledJiangInvitationCodecAndDepartureBoundaries org.fengshen.dev.test/android.test.InstrumentationTestRunner > artifacts/town02-runtime/development-content-loader.txt 2>&1
        cat artifacts/town02-runtime/development-content-loader.txt
        grep -q 'OK (2 tests)' artifacts/town02-runtime/development-content-loader.txt || exit 1
        if ! python tools/record_app_audio.py world-jiang testControlledJiangInvitationPendingAtColdBoundary --silent --controlled-jiang --cold-test testJiangExternalColdStartMatchesPendingAndCompletesOnce --budget-seconds 300; then
            adb logcat -d -b crash -s AndroidRuntime > artifacts/town02-runtime/development-jiang-crash.txt
            cat artifacts/town02-runtime/development-jiang-crash.txt
            exit 1
        fi
        pull_evidence
        exit 0
    fi
    # Expose the existing loader's exact failed invariant before waiting for UI.
    adb shell am instrument -w -e class org.fengshen.dev.ContentTest#testControlledWell8LocationItemPendingCodecAndNoDuplicateCompletion,org.fengshen.dev.ContentTest#testC60FrozenDependenciesAndMedicalPartySave,org.fengshen.dev.ContentTest#testJiamengSavedActorsDialogueAndManualReturnFixture org.fengshen.dev.test/android.test.InstrumentationTestRunner > artifacts/town02-runtime/development-content-loader.txt 2>&1
    cat artifacts/town02-runtime/development-content-loader.txt
    grep -Eq 'OK \([0-9]+ tests?\)' artifacts/town02-runtime/development-content-loader.txt || exit 1
    run_test testTouchUxSelectionScrollAndAtomicEquipment false
    run_test testControlledR1ReplayVersionMarkerBounds false
    run_test testControlledPlayableR1MedicalDoorReentryFromVerifiedSave false
    python tools/record_app_audio.py personal-r1-smoke testPersonalR1SmokeFromVerifiedEastSave --silent --cold-test testPersonalR1SmokeColdRestartMatchesVerifiedSave --budget-seconds 300
    if [[ "$development_scope" == full ]]; then
        run_test testControlledSaveHistoryRealForegroundFiveMinuteAutoSave false
        run_test testControlledSaveHistoryCorruptionAndRetentionProtectActiveAndMigration false
    fi
    if ! python tools/record_app_audio.py world-save-history testControlledSaveHistoryManualRollbackAndActivityRestart --silent --controlled-save-history --cold-test testSaveHistoryExternalColdStartMatchesRestoredSnapshot --budget-seconds 300; then
        adb logcat -d -b crash -s AndroidRuntime > artifacts/town02-runtime/development-rollback-crash.txt
        cat artifacts/town02-runtime/development-rollback-crash.txt
        exit 1
    fi
    pull_evidence
    exit 0
fi
base=(artifacts/runtime-base/*-release.apk)
candidate=(artifacts/ci/*-release.apk)
testapk=(artifacts/runtime-test/*.apk)
[[ ${#candidate[@]} == 1 && ${#testapk[@]} == 1 ]]
if [[ "$stage" == all || "$stage" == base ]]; then
[[ ${#base[@]} == 1 ]]
python - "${base[0]}" <<'PYBASE'
import json,sys,hashlib
from pathlib import Path
from tools import ci_apk as ci
apk=Path(sys.argv[1]);pin=ci.CONFIG['runtimeBaseline']
assert hashlib.sha256(apk.read_bytes()).hexdigest()==pin['apkSha256']
r=json.loads((apk.parent/'apk-receipt.json').read_text(encoding='utf-8-sig'))
assert (r['sourceCommit'],str(r['buildRunID']),r['versionCode'],r['contentHash'])==(pin['sourceCommit'],str(pin['buildRunId']),pin['versionCode'],pin['manifestSha256'])
assert ci.verify_apk(apk,release=True)['versionCode']==pin['versionCode']
ci.content(apk,pin)
Path('artifacts/town02-runtime/base.json').write_text(json.dumps(r,indent=2)+'\n')
print('Trusted actual latest covering-upgrade baseline validated')
PYBASE
fi
python tools/ci_apk.py verify --apk "${candidate[0]}" --output artifacts/town02-runtime/candidate.json
python - "${testapk[0]}" <<'PY'
import re,sys
from tools import ci_apk as ci
cert=ci.command([ci.tool('apksigner'),'verify','--print-certs',sys.argv[1]])
assert re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)',cert)==[ci.CONFIG['signerSha256']], 'Test APK must have the existing signature'
PY

if [[ "$stage" == all || "$stage" == base ]]; then
adb install -r "${base[0]}"
adb install -r "${testapk[0]}"
# The reviewed base already has TOUCH-UX; preserve it rather than rerun its obsolete cursor baseline.
run_test testExportCurrentSaveForUpgrade
adb shell am force-stop org.fengshen.dev
adb install -r "${candidate[0]}" # Same signature, actual covering install; never uninstall/clear.
else
# Fresh isolated AVD, exact same candidate; covering upgrade remains a mandatory base gate.
adb install -r "${candidate[0]}"
adb install -r "${testapk[0]}"
fi
# Validate the real bundled loader before a launch timeout can obscure the
# precise content assertion. This isolated suite restores the upgrade fixture.
content_tests=$(python tools/runtime_handoff.py scope --field contentTests)
timeout 600 adb shell am instrument -w -e class "$content_tests" org.fengshen.dev.test/android.test.InstrumentationTestRunner > artifacts/town02-runtime/testContent.txt 2>&1
cat artifacts/town02-runtime/testContent.txt
grep -Eq 'OK \([0-9]+ tests\)' artifacts/town02-runtime/testContent.txt
if [[ "$stage" == all || "$stage" == base ]]; then
run_test testUpgradeKeepsPreviousSave
python tools/runtime_handoff.py probe --candidate artifacts/town02-runtime/candidate.json
if [[ "$quality" == PERSONAL_TEST ]]; then
    # Explicit short personal tier. Full normal routes remain below, unexecuted here.
    run_test testContentMigrationKeepsFirstRecoverableBackup false
    run_test testTouchUxSelectionScrollAndAtomicEquipment false
    run_test testTouchUxTradeGesturesAndResultEquivalence false
    run_test testControlledHerbBoundariesAndSaveCompatibility false
    run_test testControlledMobileBattleHerbAndSave false
    run_test testUnrestorableSaveCannotBeOverwritten false
    if [[ "$scope_id" == WORLD-C60-PERSONAL || "$scope_id" == WORLD-C61-PERSONAL ]]; then run_test testControlledR1ReplayVersionMarkerBounds false; fi
    run_test testControlledPlayableR1MedicalDoorReentryFromVerifiedSave false
    if [[ "$scope_id" == WORLD-C60-PERSONAL || "$scope_id" == WORLD-C61-PERSONAL ]]; then
        # Run on this signed candidate; DEBUG observations cannot satisfy these gates.
        timeout 120 adb shell am instrument -w -e class org.fengshen.dev.ContentTest#testControlledWell8LocationItemPendingCodecAndNoDuplicateCompletion org.fengshen.dev.test/android.test.InstrumentationTestRunner > artifacts/town02-runtime/c60-well8-codec.txt 2>&1
        cat artifacts/town02-runtime/c60-well8-codec.txt
        grep -q 'OK (1 test)' artifacts/town02-runtime/c60-well8-codec.txt
        run_test testControlledSaveHistoryCorruptionAndRetentionProtectActiveAndMigration false
        python tools/record_app_audio.py world-save-history testControlledSaveHistoryManualRollbackAndActivityRestart --silent --controlled-save-history --cold-test testSaveHistoryExternalColdStartMatchesRestoredSnapshot --budget-seconds 300
    fi
    python tools/record_app_audio.py personal-r1-smoke testPersonalR1SmokeFromVerifiedEastSave --silent --cold-test testPersonalR1SmokeColdRestartMatchesVerifiedSave --budget-seconds 300
    if [[ "$scope_id" == WORLD-C61-PERSONAL ]]; then
        timeout 120 adb shell am instrument -w -e class org.fengshen.dev.ContentTest#testControlledJiangInvitationCodecAndDepartureBoundaries org.fengshen.dev.test/android.test.InstrumentationTestRunner > artifacts/town02-runtime/c61-jiang-codec.txt 2>&1
        cat artifacts/town02-runtime/c61-jiang-codec.txt
        grep -q 'OK (1 test)' artifacts/town02-runtime/c61-jiang-codec.txt
        if ! python tools/record_app_audio.py world-jiang testControlledJiangInvitationPendingAtColdBoundary --silent --controlled-jiang --cold-test testJiangExternalColdStartMatchesPendingAndCompletesOnce --budget-seconds 300; then
            adb logcat -d -b crash -s AndroidRuntime > artifacts/town02-runtime/c61-jiang-crash.txt
            cat artifacts/town02-runtime/c61-jiang-crash.txt
            exit 1
        fi
    fi
    pull_evidence
    python - <<'PYPERSONAL'
import json,os,hashlib
from pathlib import Path
from tools.runtime_handoff import finish_personal,PERSONAL_GATES,C60_PERSONAL_GATES,C61_PERSONAL_GATES,review_personal,active_scope,jiang_proof_digests
r=json.loads(Path('artifacts/town02-runtime/candidate.json').read_text())
r.update(sourceCommit=os.environ['GITHUB_SHA'],buildRunID=os.environ['GITHUB_RUN_ID'])
recording=Path('artifacts/checkpoint-ui/personal-r1-smoke-recording.json')
proof=json.loads(recording.read_text())
assert proof['normalAssertions']=='PASS' and proof['forceStopRestartEqual'] is True and proof['continuedExploration'] is True
for segment in proof['segments']:
    assert hashlib.sha256(Path(segment['file']).read_bytes()).hexdigest()==segment['sha256']
r.update({key:'PASS' for key in PERSONAL_GATES}) # Reached only after each mandatory actual command succeeds.
if active_scope(r)['id'] in ('WORLD-C60-PERSONAL','WORLD-C61-PERSONAL'):
    history_recording=Path('artifacts/checkpoint-ui/world-save-history-recording.json')
    history=json.loads(history_recording.read_text())
    assert history['kind']=='CONTROLLED_SAVE_HISTORY_SMOKE' and history['controlledAssertions']=='PASS'
    assert history['normalAssertions']=='NOT_APPLICABLE' and history['forceStopRestartEqual'] is True
    assert history['continuedExploration'] is False and history['originalPreferencesRestored'] is True
    for segment in history['segments']:
        assert hashlib.sha256(Path(segment['file']).read_bytes()).hexdigest()==segment['sha256']
    r.update({key:'PASS' for key in C60_PERSONAL_GATES})
    r['saveHistoryRecordingSha256']=hashlib.sha256(history_recording.read_bytes()).hexdigest()
if active_scope(r)['id']=='WORLD-C61-PERSONAL':
    r.update(jiang_proof_digests(Path('artifacts/checkpoint-ui')))
    r.update({key:'PASS' for key in C61_PERSONAL_GATES})
r=finish_personal(r);review_personal(r)
r['smokeRecordingSha256']=hashlib.sha256(recording.read_bytes()).hexdigest()
Path('artifacts/town02-runtime/runtime-receipt.json').write_text(json.dumps(r,indent=2)+'\n')
print(json.dumps(r))
PYPERSONAL
    exit 0
fi
if [[ "$scope_id" == PLAYABLE-R1 ]]; then run_test testControlledPlayableR1MedicalDoorReentryFromVerifiedSave false; fi
if [[ "$scope_id" == PLAYABLE-R1 ]]; then run_test testControlledR1VillageTwoPoisonSupplyAndInnFromVerifiedSave false; fi
run_test testTouchUxSelectionScrollAndAtomicEquipment
run_test testTouchUxTradeGesturesAndResultEquivalence
run_test testControlledHerbBoundariesAndSaveCompatibility
run_test testControlledNanhaiVictoryFlagAndResumeOnce
run_test testControlledNorthRepeatAfterOptionalMapSupply
if [[ "$scope_id" == PLAYABLE-R1 ]]; then run_test testControlledNorthTravelFromVerifiedPalaceSave; fi
run_test testControlledMobileBattleTouchAndSnapshots
run_test testControlledMobileBattleHerbAndSave
if [[ "$scope_id" == WORLD-FULL-01 ]]; then run_test testControlledBindingItemSelectionCancelAndSingleActorCommand; fi
run_test testUnrestorableSaveCannotBeOverwritten
run_test testWorldLegacyInteriorContextAndRestart
run_test testControlledWorldAntidoteAndFieldPoison
run_test testControlledInnTransactionsAndGestureSafety
run_test testControlledMedicalCommandsCancellationGestureAndSave
run_test testControlledWholly08PartyAdvancesWithoutTouchCommand
if [[ "$scope_id" == WORLD-FULL-01 ]]; then run_test testControlledFerryPauseSavedStageAndActivityRestart; fi
# Actual phone-sized windows and scaled text; only this isolated AVD is changed.
adb shell wm size 2640x1216
adb shell wm density 480
adb shell wm size > artifacts/town02-runtime/phone-display.txt
adb shell wm density >> artifacts/town02-runtime/phone-display.txt
for font in 1.0 1.3 2.0; do
    adb shell settings put system font_scale "$font"
    sleep 3
    run_test testTouchUxPhoneSizeAndLargeFont
    run_test testMobileBattlePhoneSizeAndLargeFont
done
adb shell settings put system font_scale 1.0
adb shell wm size 960x540
adb shell wm density 160

run_test testNormalTownShopsBuySellAndReturn
if [[ "$scope_id" == WORLD-FULL-01 ]]; then run_test testControlledRebirthCancelPendingRestartAndOnceOnlyCompletion; fi
run_test testOpeningKnifeEquipCyclePersistsWithoutDuplication
run_test testInput01RealMapWallSlidesAndMenuCancellation
run_test testHeldJoystickMenuOpenReleaseDoesNotResumeMovement
python tools/record_app_audio.py touch-ux-after testNormalTouchUxSupplyAndEquipment --silent
python tools/record_app_audio.py world-f0 testNormalWorldFullCurrentServices --silent
python tools/record_app_audio.py nanhai-ci testNormalNanhaiRouteBossAndVictory --silent --cold-test testNanhaiColdStartMatchesNormalSave --budget-seconds 3600
# Same candidate normal preparation already earns every level/coin. Reuse that
# legitimate checkpoint for optional northern regressions; no new player gate.
python tools/record_app_audio.py world-west testNormalWorldWestPalaceFromVerifiedNanhaiSave --silent --cold-test testWorldWestColdStartMatchesNormalSave --budget-seconds 3600
python tools/record_app_audio.py world-north-palace testNormalWorldNorthPalaceAndPearlFromVerifiedNanhaiSave --silent --cold-test testWorldNorthPalacePearlColdStartMatchesNormalSave --budget-seconds 3600
python tools/record_app_audio.py world-north testNormalWorldSeaNorthFromVerifiedNorthPalaceSave --silent --cold-test testWorldNorthColdStartMatchesNormalSave --budget-seconds 1800
python tools/record_app_audio.py world-village1 testNormalWorldVillageOneServicesFromVerifiedNorthPalaceSave --silent --cold-test testWorldVillageOneColdStartMatchesNormalSave --budget-seconds 1800
fi
if [[ "$stage" == world || "$stage" == continuation ]]; then
python tools/runtime_handoff.py import --stage "$stage" --candidate artifacts/town02-runtime/candidate.json --directory artifacts/runtime-handoff-input --receipt artifacts/town02-runtime/previous-stage.json
fi
if [[ "$stage" == all || "$stage" == world ]]; then
python tools/record_app_audio.py world-cave85 testNormalWorldCave85FromVerifiedNorthPalaceSave --silent --cold-test testWorldCave85ColdStartAndReentryMatchesNormalSave --budget-seconds 2400
python tools/record_app_audio.py world-east-palace testNormalWorldEastPalacePartyFromVerifiedCaveSave --silent --cold-test testWorldEastPartyColdStartMatchesNormalSave --budget-seconds 3600
python tools/record_app_audio.py world-hell-village2 testNormalWorldHellVillageServicesFromVerifiedEastPartySave --silent --cold-test testWorldHellVillageColdStartMatchesNormalSave --budget-seconds 1200
if [[ "$scope_id" == WORLD-HELL-R2 ]]; then
python tools/record_app_audio.py world-r1-medical testNormalPlayableR1MedicalFromVerifiedVillageSave --silent --cold-test testPlayableR1MedicalColdStartMatchesNormalSave --budget-seconds 1200
python tools/record_app_audio.py world-first-hall testNormalWorldFirstHallFromVerifiedMedicalSave --silent --cold-test testWorldFirstHallColdRestartAndRepeatNoReward --budget-seconds 18000
python tools/record_app_audio.py world-second-hall testNormalWorldSecondHallFromVerifiedFirstHallSave --silent --cold-test testWorldSecondHallColdRestartAndRepeatNoReward --budget-seconds 2400
python tools/record_app_audio.py world-hall-batch testNormalWorldHallBatchFromVerifiedSecondHallSave --silent --cold-test testWorldHallBatchColdRestartAndRepeatNoReward --budget-seconds 7200
fi
if [[ "$scope_id" == WORLD-FULL-01 ]]; then
python tools/record_app_audio.py world-first-hall testNormalWorldFirstHallFromVerifiedHellVillageSave --silent --cold-test testWorldFirstHallColdRestartAndRepeatNoReward --budget-seconds 18000
python tools/record_app_audio.py world-second-hall testNormalWorldSecondHallFromVerifiedFirstHallSave --silent --cold-test testWorldSecondHallColdRestartAndRepeatNoReward --budget-seconds 2400
python tools/record_app_audio.py world-hall-batch testNormalWorldHallBatchFromVerifiedSecondHallSave --silent --cold-test testWorldHallBatchColdRestartAndRepeatNoReward --budget-seconds 7200
python tools/record_app_audio.py world-rebirth testNormalWorldFinalHallsAndRebirthFromVerifiedHallBatchSave --silent --cold-test testWorldRebirthColdRestartAndContinueMatchesNormalSave --budget-seconds 3600
python tools/record_app_audio.py world-village3 testNormalWorldVillageThreeServicesFromVerifiedRebirthSave --silent --cold-test testWorldVillageThreeColdRestartAndRealReentry --budget-seconds 1800
python tools/record_app_audio.py world-medical testNormalMedicalServicesFromVerifiedVillageThreeSave --silent --cold-test testMedicalServicesColdRestartAndReentry --budget-seconds 1800
python tools/record_app_audio.py world-continent-bridge testNormalContinentBridgeAndZone16FromVerifiedMedicalSave --silent --cold-test testContinentBridgeColdRestartAndRealReturn --budget-seconds 1800
python tools/record_app_audio.py world-forest101 testNormalForest101FromVerifiedContinentBridgeSave --silent --cold-test testForest101ColdRestartAndRealContinentReturn --budget-seconds 2400
python tools/record_app_audio.py world-tree107 testNormalTree107FromVerifiedContinentBridgeSave --silent --cold-test testTree107ColdRestartAndRealReturn --budget-seconds 3600
python tools/record_app_audio.py world-room171 testNormalRoom171GiftFromVerifiedTreeSave --silent --cold-test testRoom171GiftColdRestartAndOriginalReturn --budget-seconds 2400
python tools/record_app_audio.py world-yang-join testNormalYangJoinAndThreePartyFromVerifiedRoomSave --silent --cold-test testYangJoinColdRestartAndOriginalTreeReturn --budget-seconds 3600
python tools/record_app_audio.py world-village4 testNormalVillageFourServicesAndTalkFromVerifiedYangSave --silent --cold-test testVillageFourColdRestartAndOriginalReturn --budget-seconds 2400
python tools/record_app_audio.py world-ferry testNormalFixedFerryAndIslandFromVerifiedVillageSave --silent --cold-test testFixedFerryIslandColdRestartAndOriginalReverse --budget-seconds 2400
fi
fi
if [[ "$stage" == all || "$stage" == continuation ]]; then
if [[ "$scope_id" == PLAYABLE-R1 ]]; then
python tools/record_app_audio.py world-r1-medical testNormalPlayableR1MedicalFromVerifiedVillageSave --silent --cold-test testPlayableR1MedicalColdStartMatchesNormalSave --budget-seconds 1200
elif [[ "$scope_id" == WORLD-HELL-R2 ]]; then
python tools/record_app_audio.py world-rebirth testNormalWorldFinalHallsAndRebirthFromVerifiedHallBatchSave --silent --cold-test testWorldRebirthColdRestartAndContinueMatchesNormalSave --budget-seconds 3600
else
python tools/record_app_audio.py world-island testNormalIslandLayersFourVillainsAndChestsFromVerifiedFerrySave --silent --cold-test testIslandVictoryColdRestartChestsAndRealReturn --budget-seconds 3600
python tools/record_app_audio.py world-village5 testNormalVillageFiveServicesAndTalkFromVerifiedYangSave --silent --cold-test testVillageFiveColdRestartAndOriginalReturn --budget-seconds 2400
python tools/record_app_audio.py world-cave87 testNormalCave87FlowerStoryFromVerifiedIslandSave --silent --cold-test testCave87DepartureColdRestartAndOriginalReturn --budget-seconds 2700
python tools/record_app_audio.py world-village6 testNormalVillageSixServicesAndTalkFromVerifiedFlowerSave --silent --cold-test testVillageSixColdRestartAndOriginalReturn --budget-seconds 2400
python tools/record_app_audio.py world-night8 testNormalNightEightGiftAndDarkCaveFromVerifiedVillageSixSave --silent --cold-test testNightEightColdRestartAndOriginalLightReset --budget-seconds 2400
python tools/record_app_audio.py world-queen117 testNormalQueenRouteBindingAndHuangFromVerifiedNightEightSave --silent --cold-test testQueenHuangColdRestartAndOriginalReturn --budget-seconds 3600
fi
fi
# Clinical sub-results are App-written evidence; pull before constructing receipt.
pull_evidence
# Existing recorder checks external force-stop/restart and restores original preferences.
python - <<'PY'
import json,os
from pathlib import Path
from tools import ci_apk as ci
r=json.loads(Path('artifacts/town02-runtime/candidate.json').read_text())
r.update(sourceCommit=os.environ['GITHUB_SHA'],buildRunID=os.environ['GITHUB_RUN_ID'],runtime='PASS',upgrade='PASS',normalHerbSupply='PASS',controlledBoundaries='PASS',shopEquipmentInputRegression='PASS',touchUx='PASS',phoneSizedLayout='PASS',baselineComparison='PRESERVED_NOT_RERUN',nanhaiNormalRoute='PASS',nanhaiBossVictory='PASS',nanhaiOnceAndColdRestart='PASS',mobileGrowth='PASS',mobileEnemyInformation='PASS',mobileDirectTouch='PASS',mobileActionSnapshots='PASS',battleHerb='PASS',worldCurrentServices='PASS',worldSeaNorth='PASS',worldStatusAndAntidote='PASS',worldSaveProtection='PASS',worldWestPalace='PASS',worldSharedVillageServices='PASS',worldTerrainRestore='PASS',worldNorthPalace='PASS',worldPearlUseAndColdRestart='PASS',worldCave85Normal='PASS',worldCave85OnceAndColdRestart='PASS',worldEastPalaceNormal='PASS',worldEastPartyAndColdRestart='PASS',worldHellVillageNormal='PASS',worldHellVillageColdRestart='PASS',worldWholly08Controller='PASS',worldFirstHallNormal='PASS',worldFirstHallColdRestart='PASS',worldSecondHallNormal='PASS',worldSecondHallColdRestart='PASS',worldHallBatchNormal='PASS',worldHallBatchColdRestart='PASS',audio='NOT_RUN',onePlus13T='NOT_RUN')
r.update(worldFinalHallsNormal='PASS',worldRebirthDialogueAndColdRestart='PASS')
r.update(worldContinentBridgeAndZone16Normal='PASS',worldContinentBridgeColdRestart='PASS',worldVillageThreeServicesAndColdRestart='PASS',worldMedicalControlledCommands='PASS',worldMedicalNormalEntryAndColdRestart='PASS')
r.update(worldForest101Normal='PASS',worldForest101ColdRestart='PASS')
r.update(worldTree107Normal='PASS',worldTree107ColdRestart='PASS')
r.update(worldRoom171Normal='PASS',worldRoom171GiftColdRestart='PASS')
r.update(worldYangJoinNormal='PASS',worldYangThreePartyAndColdRestart='PASS')
r.update(worldVillageFourServicesTalkNormal='PASS',worldVillageFourColdRestart='PASS')
r.update(worldFixedFerryIslandNormal='PASS',worldFixedFerryColdRestartAndReverse='PASS')
r.update(worldIslandOriginalLayersAndFourVillainsNormal='PASS',worldIslandOnceChestsAndColdRestart='PASS')
r.update(worldVillageFiveServicesTalkNormal='PASS',worldVillageFiveColdRestart='PASS')
r.update(worldCave87FlowerNormal='PASS',worldCave87DepartureAndColdRestart='PASS')
r.update(worldVillageSixServicesTalkNormal='PASS',worldVillageSixColdRestart='PASS')
r.update(worldNightEightGiftAndCaveNormal='PASS',worldNightEightColdRestart='PASS')
r.update(worldQueenRouteAndBindingNormal='PASS',worldQueenHuangOnceAndColdRestart='PASS')
stage=os.environ['FENGSHEN_RUNTIME_STAGE']
if stage in ('all','world') and os.environ.get('FENGSHEN_RUNTIME_SCOPE') == 'WORLD-FULL-01':
    medical=json.loads(Path('artifacts/checkpoint-ui/touch-ux-world-medical-normal-summary.json').read_text())
    for key in ('revivalNormal','poisonNormal','confusionNormal'):r['worldMedical'+key[0].upper()+key[1:]]=medical[key]
if (os.environ.get('FENGSHEN_RUNTIME_SCOPE'),os.environ['FENGSHEN_RUNTIME_STAGE']) in [('PLAYABLE-R1','continuation'),('WORLD-HELL-R2','world')]:
    r.update(playableR1MedicalNormal='PASS',playableR1MedicalColdRestart='PASS')
from tools.runtime_handoff import finish_stage
previous=json.loads(Path('artifacts/town02-runtime/previous-stage.json').read_text()) if stage in ('world','continuation') else None
r=finish_stage(stage,r,previous)
Path('artifacts/town02-runtime/runtime-receipt.json').write_text(json.dumps(r,indent=2)+'\n')
print(json.dumps(r))
PY
if [[ "$stage" == base || "$stage" == world ]]; then
python tools/runtime_handoff.py pack --stage "$stage" --candidate artifacts/town02-runtime/candidate.json --directory artifacts/runtime-handoff
fi

# Keep two small copies of original raw clips for direct review; full unedited footage stays in the original artifact.
# The state index lives in the App external directory until pulled; collect it before selecting clips.
pull_evidence
if [[ "$stage" == all || "$stage" == base ]]; then
python - <<'PYCLIPS'
import json,shutil,hashlib
from pathlib import Path
root=Path('.')
recording=json.loads(Path('artifacts/checkpoint-ui/nanhai-ci-recording.json').read_text())
index=json.loads(Path('artifacts/checkpoint-ui/nanhai-normal-index.json').read_text())
normal=[s for s in recording['segments'] if '-normal-' in s['file']]
entry_time=next(e['androidUptimeMs'] for e in index['events'] if e['name']=='sea-entry')
entry=next((s for s in normal if s['startedAndroidUptimeMs']<=entry_time<=s['startedAndroidUptimeMs']+s['durationSeconds']*1000),None)
if entry is None:raise ValueError('Entry capture not covered by retained raw segment')
cold=next(s for s in recording['segments'] if s.get('phase')=='EXTERNAL_FORCE_STOP_ACTUAL_COLD_RESTART_AND_CONTINUE')
for part,segments in [('entry',[entry]),('final',[normal[-1],cold])]:
    out=Path('artifacts/nanhai-review-clips')/part;out.mkdir(parents=True,exist_ok=True)
    for segment in segments:
        src=Path(segment['file']);assert hashlib.sha256(src.read_bytes()).hexdigest()==segment['sha256']
        shutil.copyfile(src,out/src.name)
    (out/'clip-index.json').write_text(json.dumps({'segments':segments,'normalFlow':index,'kind':'UNCHANGED_RAW_ANDROID_APP_CLIPS_SILENT'},indent=2))
recording=json.loads(Path('artifacts/checkpoint-ui/world-north-recording.json').read_text())
index=json.loads(Path('artifacts/checkpoint-ui/touch-ux-world-north-normal-index.json').read_text())
normal=[s for s in recording['segments'] if '-normal-' in s['file']]
cold=next(s for s in recording['segments'] if s.get('phase')=='EXTERNAL_FORCE_STOP_ACTUAL_COLD_RESTART_AND_CONTINUE')
out=Path('artifacts/world-review-clips');out.mkdir(parents=True,exist_ok=True)
selected=[];total=0
for segment in [normal[-1],cold]:
    src=Path(segment['file']);assert hashlib.sha256(src.read_bytes()).hexdigest()==segment['sha256']
    if total+src.stat().st_size>27*1024*1024:continue # Original full artifact still retains every segment.
    shutil.copyfile(src,out/src.name);total+=src.stat().st_size;selected.append(segment)
(out/'clip-index.json').write_text(json.dumps({'segments':selected,'allSegmentIndex':recording,
    'normalFlow':index,'kind':'UNCHANGED_RAW_ANDROID_APP_CONTINUATION_CLIPS_SILENT',
    'limitBytes':27*1024*1024,'allFootageRetainedInOriginalRuntimeArtifact':True},indent=2))
# Each new continuation has its own bounded review artifact; every original
# segment remains in the full runtime artifact, including segments not copied.

PYCLIPS
fi
