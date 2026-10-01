"""Real AVD UI checks: cancel a system session, or download/upgrade the trusted previous APK to the current published release.

The isolated AVD fixture holds encrypted preferences in memory only and restores them.
No credential is decoded, no uninstall, no shell installation for the upgrade itself.
"""
import hashlib,json,re,subprocess,time,sys,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'artifacts/checkpoint-ui'
CURRENT=json.loads((ROOT/'artifacts/published/current.json').read_text(encoding='utf-8-sig'))
PREVIOUS=json.loads((ROOT/'artifacts/published/previous.json').read_text(encoding='utf-8-sig'))
CURRENT_CODE=CURRENT['versionCode'];PREVIOUS_CODE=PREVIOUS['versionCode']
PREFIX=sys.argv[2] if len(sys.argv)>2 else 'battle02'
assert re.fullmatch(r'[a-z0-9-]+',PREFIX), 'Safe output prefix required'
def adb(*args,timeout=180):
    r=subprocess.run(['adb','-s','emulator-5554',*args],capture_output=True,timeout=timeout)
    if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace')[:400])
    return r.stdout
def prefs(name):
    raw=adb('shell','run-as','org.fengshen.dev','cat',f'shared_prefs/{name}.xml')
    return ET.fromstring(raw)
def snapshot():
    r=prefs('opening-local-save');return json.loads(next(x.text for x in r if x.attrib.get('name')=='saveJson'))
def installed():
    t=adb('shell','dumpsys','package','org.fengshen.dev').decode();return int(re.search(r'versionCode=(\d+)',t)[1])
def click(resource,text=None):
    adb('shell','uiautomator','dump','/sdcard/update-check.xml')
    root=ET.fromstring(adb('shell','cat','/sdcard/update-check.xml'))
    for n in root.iter('node'):
        if n.attrib.get('resource-id')==resource and (text is None or text.lower() in n.attrib.get('text','').lower()):
            a=list(map(int,re.findall(r'\d+',n.attrib['bounds'])))
            adb('shell','input','tap',str((a[0]+a[2])//2),str((a[1]+a[3])//2));return
    raise RuntimeError('Expected system/app button absent: '+resource)
def screenshot(name):
    (OUT/name).write_bytes(adb('exec-out','screencap','-p'))
def instrument(method,report):
    result=adb('shell','am','instrument','-w','-e','class',f'org.fengshen.dev.UpdateFlowTest#{method}',
               'org.fengshen.dev.test/android.test.InstrumentationTestRunner')
    (ROOT/'reports'/report).write_bytes(result)
    assert b'OK (1 test)' in result, 'Instrumentation did not pass; inspect '+report
def isolated_check():
    assert b'ranchu' in adb('shell','getprop','ro.hardware'), 'AVD only'
    OUT.mkdir(parents=True,exist_ok=True);mode=sys.argv[1]
    if mode=='cancel':
        assert installed()==CURRENT_CODE
        adb('shell','appops','set','org.fengshen.dev','REQUEST_INSTALL_PACKAGES','allow')
        before=snapshot()
        instrument('testPrepareRealSystemCancellationProbe',f'{PREFIX}-updater-system-confirmation.txt')
        screenshot(f'{PREFIX}-updater-system-confirmation.png')
        click('android:id/button2');time.sleep(.5);screenshot(f'{PREFIX}-updater-cancel-feedback.png');time.sleep(3)
        assert installed()==CURRENT_CODE
        state={x.attrib['name']:x.text or x.attrib.get('value') for x in prefs('apk-update-state')}
        assert 'session' not in state and 'target' not in state, 'Cancelled session persisted'
        assert before==snapshot(), 'Cancellation changed the game save'
        report={'status':'PASS','mode':mode,'device':'emulator-5554','version':CURRENT_CODE,'sessionCleared':True,'saveUnchanged':True,'fixture':'same-byte reinstall deliberately cancelled; future target metadata is test=true, not a published release'}
    elif mode=='upgrade':
        old=ROOT/'artifacts/published/previous.apk'
        assert hashlib.sha256(old.read_bytes()).hexdigest()==PREVIOUS['sha256']
        adb('shell','am','force-stop','org.fengshen.dev')
        assert b'Success' in adb('install','-r','-d',str(old)) # setup only; upgrade below uses the App
        assert installed()==PREVIOUS_CODE
        before=snapshot()
        adb('shell','appops','set','org.fengshen.dev','REQUEST_INSTALL_PACKAGES','allow')
        existing=subprocess.run(['adb','-s','emulator-5554','shell','pidof','screenrecord'],capture_output=True)
        assert not existing.stdout.strip(), 'Another screenrecord already owns the AVD'
        video=subprocess.Popen(['adb','-s','emulator-5554','shell','screenrecord','--time-limit','180',f'/sdcard/{PREFIX}-updater-real-upgrade.mp4'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        time.sleep(1)
        video_pid=adb('shell','pidof','screenrecord').decode().strip()
        assert video_pid.isdecimal(), 'Recording must have one owned AVD process'
        ready_path='/sdcard/Android/data/org.fengshen.dev/files/installer-download-probe.json'
        system_path='/sdcard/Android/data/org.fengshen.dev/files/installer-system-probe.json'
        adb('shell','rm','-f',ready_path) # only our previous QA readiness marker
        adb('shell','rm','-f',system_path)
        driver_log=(ROOT/f'reports/{PREFIX}-updater-real-download.txt').open('wb')
        driver=subprocess.Popen(['adb','-s','emulator-5554','shell','am','instrument','-w','-e','class',
            'org.fengshen.dev.UpdateFlowTest#testOpenPublicUpdateOnPreviousVersion',
            'org.fengshen.dev.test/android.test.InstrumentationTestRunner'],stdout=driver_log,stderr=subprocess.STDOUT)
        ready=False
        for _ in range(110):
            probe=subprocess.run(['adb','-s','emulator-5554','shell','cat',ready_path],capture_output=True)
            if probe.returncode==0 and b'"downloadDialogVisible":true' in probe.stdout:
                ready=True;break
            if driver.poll() is not None:break
            time.sleep(1)
        assert ready, 'Live App did not offer installation; inspect the driver log'
        screenshot(f'{PREFIX}-updater-download-verified.png')
        actual=adb('shell','run-as','org.fengshen.dev','sha256sum',f'cache/apk-update/verified-{CURRENT_CODE}.apk').decode().split()[0]
        expected=CURRENT['sha256']
        assert actual==expected, 'App download does not match the actual release'
        bounds=json.loads(probe.stdout)['buttonBounds']
        adb('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
        system_ready=False
        for _ in range(35):
            probe=subprocess.run(['adb','-s','emulator-5554','shell','cat',system_path],capture_output=True)
            if probe.returncode==0 and b'"systemConfirmationVisible":true' in probe.stdout:
                system_ready=True;break
            time.sleep(1)
        assert system_ready, 'Real system install confirmation absent'
        screenshot(f'{PREFIX}-updater-upgrade-system.png')
        bounds=json.loads(probe.stdout)['buttonBounds']
        adb('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
        for _ in range(90):
            if installed()==CURRENT_CODE:break
            time.sleep(1)
        assert installed()==CURRENT_CODE, 'System did not complete the App-initiated upgrade'
        driver.wait(timeout=20);driver_log.close() # package replacement legitimately kills instrumentation
        adb('shell','am','start','-W','-n','org.fengshen.dev/.MainActivity');time.sleep(10)
        screenshot(f'{PREFIX}-updater-upgraded-map.png')
        after=snapshot()
        assert {k:v for k,v in before.items() if k!='contentVersion'}=={k:v for k,v in after.items() if k!='contentVersion'}, 'App-initiated update changed gameplay fields in the saved game'
        adb('shell','kill','-2',video_pid);video.wait(timeout=20)
        adb('pull',f'/sdcard/{PREFIX}-updater-real-upgrade.mp4',str(OUT/f'{PREFIX}-updater-real-upgrade.mp4'))
        report={'status':'PASS','mode':mode,'device':'emulator-5554','from':PREVIOUS_CODE,'to':CURRENT_CODE,'appDownloadedSHA256':actual,'saveUnchanged':True,'oldContentVersion':before['contentVersion'],'newContentVersion':after['contentVersion'],'upgradeMethod':'public App download + Android user confirmation; no adb install for the upgrade','driverTermination':'expected instrumentation termination by package replacement; acceptance checks are installed version, verified download and preserved save'}
    else:raise ValueError('cancel or upgrade')
    (ROOT/'reports'/f'{PREFIX}-updater-{mode}.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report,indent=2))

def main():
    assert b'ranchu' in adb('shell','getprop','ro.hardware'), 'AVD only'
    names=['opening-local-save','operation-a-ui','cloud-session']
    backups={name:adb('shell','run-as','org.fengshen.dev','cat',f'shared_prefs/{name}.xml') for name in names}
    adb('shell','am','force-stop','org.fengshen.dev')
    try:
        # An explicitly controlled compatibility fixture, not normal-route proof.
        # Contains real item/equipment IDs; encrypted user cloud session is held in
        # memory and removed while this fixture is used, then restored in finally.
        game=ET.fromstring(backups['opening-local-save'])
        node=next(n for n in game if n.attrib.get('name')=='saveJson');state=json.loads(node.text)
        with zipfile.ZipFile(ROOT/'artifacts/published'/('previous.apk' if sys.argv[1]=='upgrade' else 'current.apk')) as z:
            content=json.loads(z.read('assets/development/manifest.json'))['version']
        state['contentVersion']=content;state['inventory']['rom.item.0']=1;state['flags']['rom.npc.114.2']=True
        node.text=json.dumps(state,separators=(',',':'))
        for n in game:
            if n.attrib.get('name')=='contentVersion':n.text=content
        for name,raw in [('opening-local-save',ET.tostring(game,encoding='utf-8')),('cloud-session',b'<map/>')]:
            subprocess.run(['adb','-s','emulator-5554','shell','run-as','org.fengshen.dev','sh','-c',f"'cat > shared_prefs/{name}.xml'"],input=raw,check=True,capture_output=True)
        isolated_check()
        report=ROOT/'reports'/f'{PREFIX}-updater-{sys.argv[1]}.json'
        value=json.loads(report.read_text());value['isolatedFixture']='Prior-compatible save with real knife ID, existing equipment slots and completed gift flag; no cloud login'
        value['originalPreferencesRestored']=True
        report.write_text(json.dumps(value,indent=2),encoding='utf-8')
    finally:
        adb('shell','am','force-stop','org.fengshen.dev')
        for name,raw in backups.items():
            subprocess.run(['adb','-s','emulator-5554','shell','run-as','org.fengshen.dev','sh','-c',f"'cat > shared_prefs/{name}.xml'"],input=raw,check=True,capture_output=True)
if __name__=='__main__':main()
