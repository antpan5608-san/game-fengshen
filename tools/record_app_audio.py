"""Record the actual emulator App display + Windows output loopback, no microphone.
The existing normal-controller Android test supplies input; no ROM footage is substituted.
"""
import subprocess,time,wave,json,threading,sys,xml.etree.ElementTree as ET,io,hashlib
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'artifacts/checkpoint-ui'

class SavedBoundaryUnavailable(RuntimeError):
    pass

def validate_recording_budget(prefix,budget):
    # Only the scoped first-hall preparation and multi-hall continuation has a larger normal
    # budget. The old single-flow limits and all failure/restore checks stay.
    maximum=9000 if prefix=='world-first-hall' else 7200 if prefix=='world-hall-batch' else 3600
    if not 60<=budget<=maximum:
        raise ValueError('Isolated recording budget outside the scoped bound')

def read_saved_boundary(read_pref,pause=time.sleep,attempts=20):
    """Read the actual persisted boundary across atomic preference-file replacement.

    Never return an older cached save or turn a missing boundary into a PASS.
    Error text contains the failure type, not the player's XML/JSON payload.
    """
    last=None
    for attempt in range(attempts):
        try:
            raw=read_pref('opening-local-save')
            if not raw:raise ValueError('MissingPreferenceBytes')
            xml=ET.fromstring(raw)
            state=json.loads(next(n.text for n in xml if n.attrib.get('name')=='saveJson'))
            if not isinstance(state,dict):raise ValueError('SaveMustBeObject')
            return state
        except (ET.ParseError,StopIteration,TypeError,ValueError) as error:
            last=type(error).__name__
            if attempt+1<attempts:pause(.1)
    raise SavedBoundaryUnavailable(f'Actual persisted save unavailable after {attempts} reads ({last})')

def recording_result(videos,segments,controlled=False):
    result={'source':'Actual Android App screenrecord; SILENT, no sound validation','videos':videos,
        'segments':segments,'normalAssertions':'NOT_APPLICABLE' if controlled else 'PASS',
        'forceStopRestartEqual':True,'continuedExploration':not controlled,'originalPreferencesRestored':True}
    if controlled:result.update(kind='CONTROLLED_SAVE_HISTORY_SMOKE',controlledAssertions='PASS')
    return result

def record_silent():
    """Portable branch of the existing recorder; video only, no audio claim."""
    cold_method='testHerbColdStartMatchesNormalSave';budget=1200
    if '--cold-test' in sys.argv:
        i=sys.argv.index('--cold-test');cold_method=sys.argv[i+1];del sys.argv[i:i+2]
    if '--budget-seconds' in sys.argv:
        i=sys.argv.index('--budget-seconds');budget=int(sys.argv[i+1]);del sys.argv[i:i+2]
    assert cold_method.isidentifier()
    comparison='--comparison' in sys.argv
    if comparison:sys.argv.remove('--comparison')
    prefix=sys.argv[1] if len(sys.argv)>1 else 'town02'
    validate_recording_budget(prefix,budget)
    method=sys.argv[2] if len(sys.argv)>2 else 'testNormalHerbSupplyLoop'
    controlled='--controlled-save-history' in sys.argv
    if controlled:
        sys.argv.remove('--controlled-save-history')
        assert not comparison and prefix=='world-save-history'
        assert method=='testControlledSaveHistoryManualRollbackAndActivityRestart'
        assert cold_method=='testSaveHistoryExternalColdStartMatchesRestoredSnapshot'
    def adb(*args,**kwargs):
        return subprocess.run(['adb','-s','emulator-5554',*args],check=True,capture_output=True,timeout=90,**kwargs).stdout
    assert b'ranchu' in adb('shell','getprop','ro.hardware'), 'Isolated emulator only'
    prior=subprocess.run(['adb','-s','emulator-5554','shell','pidof','screenrecord'],capture_output=True,timeout=30)
    assert not prior.stdout.strip(),'Another recorder owns the emulator'
    probe=subprocess.run(['adb','-s','emulator-5554','shell','run-as','org.fengshen.dev','id'],capture_output=True,timeout=30)
    root_mode=probe.returncode!=0
    if root_mode:
        adb('root');time.sleep(2);adb('wait-for-device') # AOSP debuggable emulator; no real device access.
    base='/data/data/org.fengshen.dev/shared_prefs/' if root_mode else 'shared_prefs/'
    def read_pref(name):
        cmd=['shell']+([] if root_mode else ['run-as','org.fengshen.dev'])+['cat',base+name+'.xml']
        r=subprocess.run(['adb','-s','emulator-5554',*cmd],capture_output=True,timeout=60)
        return r.stdout if r.returncode==0 else None
    def saved():
        return read_saved_boundary(read_pref)
    names=['opening-local-save','operation-a-ui','cloud-session']
    backups={name:read_pref(name) for name in names}
    OUT.mkdir(parents=True,exist_ok=True)
    test_log=OUT/f'{prefix}-normal-test.txt'
    videos=[];segments=[]
    test=None;video=None
    try:
        with test_log.open('w') as log:
            test=subprocess.Popen(['adb','-s','emulator-5554','shell','am','instrument','-w','-e','keepFixtureForRestart','true','-e','class',f'org.fengshen.dev.TouchTest#{method}','org.fengshen.dev.test/android.test.InstrumentationTestRunner'],stdout=log,stderr=subprocess.STDOUT)
            started=time.monotonic()
            while test.poll() is None:
                if time.monotonic()-started>budget:raise TimeoutError('Normal App route exceeded isolated runtime budget')
                try: boundary_before=saved()
                except SavedBoundaryUnavailable:
                    if videos:raise
                    boundary_before=None # First new-game launch can precede its first automatic save.
                remote=f'/sdcard/{prefix}-normal-{len(videos):02d}.mp4'
                uptime_ms=int(float(adb('shell','cat','/proc/uptime').decode().split()[0])*1000)
                segment_started=time.monotonic()
                video=subprocess.Popen(['adb','-s','emulator-5554','shell','screenrecord','--bit-rate','1000000','--time-limit','180',remote],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
                while video.poll() is None and test.poll() is None:
                    if time.monotonic()-started>budget:raise TimeoutError('Normal App recording budget exhausted')
                    time.sleep(.5)
                if video.poll() is None:
                    pid=adb('shell','pidof','screenrecord').decode().strip()
                    if pid.isdecimal():adb('shell','kill','-2',pid)
                video.wait(timeout=60)
                local=OUT/f'{prefix}-normal-{len(videos):02d}.mp4'
                adb('pull',remote,str(local));videos.append(str(local.relative_to(ROOT)))
                segments.append({'file':videos[-1],'sha256':hashlib.sha256(local.read_bytes()).hexdigest(),
                    'startedAndroidUptimeMs':uptime_ms,'durationSeconds':round(time.monotonic()-segment_started,3),
                    'savedWorldBefore':boundary_before,'savedWorldAfter':saved(),
                    'boundaryScope':('Isolated controlled save-history fixture; no normal route claim' if controlled else
                        'Pre-launch isolated baseline; normal new-game begins at its indexed marker' if len(videos)==1 else 'Continuing the same normal controller flow'),
                    'saveLimit':'Read-only persisted world checkpoints; during battle live action HP is visible in raw frames',
                    'limit':'Capture start approximate; actual frames in retained MP4'})
        observed=test_log.read_text(encoding='utf-8',errors='replace')
        if 'OK (1 test)' not in observed:
            # Isolated App instrumentation only; no server credentials or raw production logs.
            print('NORMAL_APP_ASSERTION_FAILURE: '+str(test_log.relative_to(ROOT)))
            print(observed[-12000:])
            raise AssertionError('Normal route assertions did not pass')
        if comparison:
            result={'source':'Actual Android App screenrecord; SILENT','kind':'CONTROLLED_UI_COMPARISON','videos':videos,'comparisonAssertions':'PASS','audio':'NOT_RUN'}
            (OUT/f'{prefix}-recording.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result));return
        before=saved()
        cold_remote=f'/sdcard/{prefix}-cold-restart.mp4'
        cold_started=time.monotonic()
        cold_uptime=int(float(adb('shell','cat','/proc/uptime').decode().split()[0])*1000)
        video=subprocess.Popen(['adb','-s','emulator-5554','shell','screenrecord','--bit-rate','1000000','--time-limit','180',cold_remote],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
        time.sleep(.5)
        adb('shell','am','force-stop','org.fengshen.dev')
        adb('shell','am','start','-W','-n','org.fengshen.dev/.MainActivity');time.sleep(8)
        assert saved()==before,'Cold restart changed saved state'
        cold_log=OUT/f'{prefix}-cold-start-test.txt'
        with cold_log.open('w') as log:
            subprocess.run(['adb','-s','emulator-5554','shell','am','instrument','-w','-e','keepFixtureForRestart','true','-e','class',f'org.fengshen.dev.TouchTest#{cold_method}','org.fengshen.dev.test/android.test.InstrumentationTestRunner'],stdout=log,stderr=subprocess.STDOUT,timeout=300,check=True)
        assert 'OK (1 test)' in cold_log.read_text(),'Actual cold GameView did not restore the normal result'
        adb('shell','am','start','-W','-n','org.fengshen.dev/.MainActivity');time.sleep(8)
        (OUT/f'{prefix}-force-stop-restored.png').write_bytes(adb('exec-out','screencap','-p'))
        if video.poll() is None:
            pid=adb('shell','pidof','screenrecord').decode().strip()
            if pid.isdecimal():adb('shell','kill','-2',pid)
        video.wait(timeout=60)
        cold_local=OUT/f'{prefix}-cold-restart.mp4';adb('pull',cold_remote,str(cold_local))
        cold_name=str(cold_local.relative_to(ROOT));videos.append(cold_name)
        segments.append({'file':cold_name,'sha256':hashlib.sha256(cold_local.read_bytes()).hexdigest(),
            'phase':'EXTERNAL_FORCE_STOP_CONTROLLED_SAVE_RESTORE_COLD_RESTART' if controlled else 'EXTERNAL_FORCE_STOP_ACTUAL_COLD_RESTART_AND_CONTINUE',
            'startedAndroidUptimeMs':cold_uptime,'durationSeconds':round(time.monotonic()-cold_started,3),
            'savedWorldBefore':before,'savedWorldAfter':saved(),
            'boundaryScope':'Same controlled rollback save before and after external cold restart' if controlled else 'Same normal save before external force-stop and after actual restart/continued movement',
            'limit':'Capture start approximate; actual frames in retained MP4'})
        result=recording_result(videos,segments,controlled)
        (OUT/f'{prefix}-recording.json').write_text(json.dumps(result,indent=2)+'\n')
        print(json.dumps(result))
    except Exception:
        # Preserve the instrument outcome even when recording or save-boundary
        # collection failed first; incomplete tests remain NOT_RUN, never PASS.
        if test_log.is_file():
            print('ISOLATED_APP_TEST_AT_RECORDING_FAILURE: '+str(test_log.relative_to(ROOT)))
            print(test_log.read_text(encoding='utf-8',errors='replace')[-12000:])
        raise
    finally:
        if video and video.poll() is None:
            pid=adb('shell','pidof','screenrecord').decode().strip()
            if pid.isdecimal():adb('shell','kill','-2',pid)
            video.wait(timeout=60)
        if test and test.poll() is None:test.terminate();test.wait(timeout=10)
        adb('shell','am','force-stop','org.fengshen.dev')
        for name,raw in backups.items():
            path=base+name+'.xml'
            start=['shell']+([] if root_mode else ['run-as','org.fengshen.dev'])
            if raw is None:adb(*start,'rm','-f',path)
            else:adb(*start,'sh','-c',f"'cat > {path}'",input=raw)

def main():
    if '--silent' in sys.argv:
        sys.argv.remove('--silent')
        return record_silent()
    import numpy as np
    import pyaudiowpatch as audio
    import imageio_ffmpeg
    prefix=sys.argv[1] if len(sys.argv)>1 else 'audio-log01'
    method=sys.argv[2] if len(sys.argv)>2 else 'testNormalOpeningRouteGiftAndMap16Encounter'
    def adb(*args,**kwargs):return subprocess.run(['adb','-s','emulator-5554',*args],check=True,capture_output=True,**kwargs).stdout
    assert b'ranchu' in adb('shell','getprop','ro.hardware'), 'Isolated recording requires the emulator'
    def saved():
        xml=ET.fromstring(adb('shell','run-as','org.fengshen.dev','cat','shared_prefs/opening-local-save.xml'))
        return json.loads(next(n.text for n in xml if n.attrib.get('name')=='saveJson'))
    names=['opening-local-save','operation-a-ui','cloud-session']
    backups={n:adb('shell','run-as','org.fengshen.dev','cat',f'shared_prefs/{n}.xml') for n in names}
    existing=subprocess.run(['adb','-s','emulator-5554','shell','pidof','screenrecord'],capture_output=True)
    assert not existing.stdout.strip(), 'Another recorder already owns the emulator'
    rotation=adb('shell','wm','user-rotation').decode().strip()
    fixed=adb('shell','wm','fixed-to-user-rotation').decode().strip()
    assert rotation=='free' or rotation in ['lock 0','lock 1','lock 2','lock 3']
    assert fixed in ['default','enabled','disabled']
    # Launcher can force portrait even while the App requests landscape. Lock
    # only this AVD capture window so screenrecord starts with landscape bounds.
    adb('shell','wm','user-rotation','lock','1');adb('shell','wm','fixed-to-user-rotation','enabled');time.sleep(1)
    capture_size=Image.open(io.BytesIO(adb('exec-out','screencap','-p'))).size
    assert capture_size[0]>capture_size[1], 'Capture must begin in landscape'
    apk=adb('shell','pm','path','org.fengshen.dev').decode().strip().split(':',1)[1]
    apk_sha=adb('shell','run-as','org.fengshen.dev','sha256sum',apk).decode().split()[0]
    OUT.mkdir(parents=True,exist_ok=True)
    api=audio.PyAudio();device=api.get_default_wasapi_loopback()
    rate=int(device['defaultSampleRate']);channels=device['maxInputChannels']
    stream=api.open(format=audio.paInt16,channels=channels,rate=rate,input=True,input_device_index=device['index'])
    started=time.monotonic();chunks=[];stop=threading.Event()
    def capture():
        while not stop.is_set():chunks.append(stream.read(1024,exception_on_overflow=False))
    thread=threading.Thread(target=capture);thread.start()
    video=subprocess.Popen(['adb','-s','emulator-5554','shell','screenrecord','--time-limit','180',f'/sdcard/{prefix}-normal.mp4'],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
    time.sleep(1)
    video_pid=adb('shell','pidof','screenrecord').decode().strip();assert video_pid.isdecimal()
    restart_ok=False;failure=None
    try:
        with (ROOT/f'reports/{prefix}-normal-recording-test.txt').open('w',encoding='utf-8') as f:
            test=subprocess.run(['adb','-s','emulator-5554','shell','am','instrument','-w','-e','keepFixtureForRestart','true','-e','class',f'org.fengshen.dev.TouchTest#{method}','org.fengshen.dev.test/android.test.InstrumentationTestRunner'],stdout=f,stderr=subprocess.STDOUT,timeout=400)
        assert 'OK (1 test)' in (ROOT/f'reports/{prefix}-normal-recording-test.txt').read_text(encoding='utf-8'), 'Normal-route assertions did not pass'
        before=saved()
        adb('shell','am','force-stop','org.fengshen.dev')
        adb('shell','am','start','-W','-n','org.fengshen.dev/.MainActivity');time.sleep(8)
        assert saved()==before, 'Force-stop/restart changed the completed result'
        restart_ok=True
        (OUT/f'{prefix}-force-stop-restored.png').write_bytes(adb('exec-out','screencap','-p'))
    except BaseException as error:failure=error
    finally:
        adb('shell','am','force-stop','org.fengshen.dev')
        for name,raw_xml in backups.items():
            adb('shell','run-as','org.fengshen.dev','sh','-c',f"'cat > shared_prefs/{name}.xml'",input=raw_xml)
        # screenrecord exits itself at Android's 180-second limit. It may have
        # already finished; this recording error must not strand loopback capture.
        subprocess.run(['adb','-s','emulator-5554','shell','kill','-2',video_pid],capture_output=True)
        time.sleep(1);stop.set();thread.join();stream.stop_stream();stream.close();api.terminate()
        adb('shell','wm','fixed-to-user-rotation',fixed);adb('shell','wm','user-rotation',*rotation.split())
    video.wait(timeout=25)
    if failure:raise failure
    raw=b''.join(chunks);wav=OUT/f'{prefix}-normal-output.wav'
    with wave.open(str(wav),'wb') as f:f.setnchannels(channels);f.setsampwidth(2);f.setframerate(rate);f.writeframes(raw)
    adb('pull',f'/sdcard/{prefix}-normal.mp4',str(OUT/f'{prefix}-normal-silent.mp4'))
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-v','error','-y','-i',str(OUT/f'{prefix}-normal-silent.mp4'),'-i',str(wav),'-c:v','copy','-c:a','aac','-shortest',str(OUT/f'{prefix}-normal-with-audio.mp4')],check=True)
    x=np.frombuffer(raw,'<i2').astype(float)
    result={'source':'Actual Android App + Windows WASAPI default output loopback, no mic',
            'emulator':'Fengshen_A_API35 Android 15 API35 x86_64',
            'audioSeconds':len(raw)/2/channels/rate,'elapsedSeconds':time.monotonic()-started,
            'rms':float(np.sqrt(np.mean(x*x))),'peak':float(abs(x).max()),
            'testShellExitCode':test.returncode,'forceStopRestartEqual':restart_ok,'originalPreferencesRestored':True,
            'installedApkSHA256':apk_sha,'captureSize':list(capture_size),'avdRotationRestored':True,
            'video':f'artifacts/checkpoint-ui/{prefix}-normal-with-audio.mp4',
            'syncLimit':'Independent host output and Android screenrecord starts; not sample-synchronized.'}
    if result['audioSeconds']>180:result['videoLimit']='Android screenrecord truncated at 180 seconds; this file does not prove later steps.'
    (ROOT/f'reports/{prefix}-recording.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result,indent=2))
if __name__=='__main__':main()
