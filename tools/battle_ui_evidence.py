"""Validate isolated same-candidate battle UI artifacts, never claim real-phone play."""
import hashlib
import io
import json
import math
import re
from pathlib import Path
if __package__:
    from . import room28_evidence
else:
    import room28_evidence

UI_SCOPE = 'WORLD-C61-UI-PERSONAL'
UI_SCOPES = (UI_SCOPE, room28_evidence.SCOPE)
UI_GATES = ['battleUiGestureSafety', 'battleUiHerbAtomic', 'battleUiVictoryOnce',
            'battleUiBindingCommands', 'battleUiAutomatic08',
            'battleUiFont1', 'battleUiFont13', 'battleUiFont2']
UI_PROOF_KEY = 'battleUiEvidenceSha256'
FONTS = ('1.0', '1.3', '2.0')
PARTY = ('nezha', 'xiaolongnv', 'yangjian', 'jiangziya')
METHODS = ('testControlledMobileBattleTouchAndSnapshots', 'testControlledMobileBattleHerbAndSave',
           'testControlledNanhaiVictoryFlagAndResumeOnce',
           'testControlledBindingItemSelectionCancelAndSingleActorCommand',
           'testControlledWholly08PartyAdvancesWithoutTouchCommand')
PHONE_METHODS = ('testMobileBattlePhoneSizeAndLargeFont', 'testControlledBattlePartyPhoneSizeAndLargeFont')
GEOMETRY_EVIDENCE = 'ACTUAL_WINDOW_INSETS_V1'


def acceptance(require_insets=False):
    value=dict(kind='CONTROLLED_EMULATOR_NOT_REAL_PHONE_OR_FULL_STORY',screen=[2640,1216],
               window=[2640,1080],safe=[2640,936],density=3,fonts=[1.0,1.3,2.0],
               nativeCasesPerFont=8,requiredScreenshotsPerFont=29,requiredInstrumentLogs=log_names(),proofKey=UI_PROOF_KEY)
    if require_insets:
        value.pop('window');value.pop('safe')
        value.update(windowHeights=[1080,1216],safe='MEASURED_ANDROID_INSETS',geometryEvidence=GEOMETRY_EVIDENCE)
    return value


def screenshot_names(font):
    names = [f'mobile-party-phone-{font}-{count}-{enemies}-native.png'
             for count in range(1, 5) for enemies in (6, 1)]
    names += [f'mobile-party-phone-{font}-hero-{hero}.png' for hero in PARTY]
    names += [f'touch-ux-world-jiang-controlled-four-actor-{kind}-phone-{font}.png' for kind in
              ('battle-ready', 'live-action', 'item-targets-selection-only', 'item-selected-medicine',
               'victory', 'reward-details', 'reward-details-scrolled')]
    names += [f'touch-ux-world-jiang-controlled-default-enemy-info-no-action-phone-{font}.png']
    names += [f'mobile-phone-{kind}-{font}.png' for kind in
              ('hud', 'boss', 'growth', 'growth-scrolled', 'info', 'info-scrolled',
               'medicine', 'medicine-detail', 'medicine-detail-scrolled')]
    return names


def log_names():
    return ([method + '.txt' for method in METHODS]
            + [f'{method}-font-{font}.txt' for font in FONTS for method in PHONE_METHODS])


def bounded_bytes(path, limit):
    if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= limit:
        raise ValueError('Missing, symlinked or oversized battle UI evidence: ' + path.name)
    return path.read_bytes()


def json_evidence(path):
    data = bounded_bytes(path, 256 * 1024)
    value = json.loads(data.decode('utf-8'))
    if not isinstance(value, dict):
        raise ValueError('Battle UI metrics must be an object')
    return value, data


def box(value, max_height=936):
    if not isinstance(value, dict):
        raise ValueError('Missing actual battle UI box')
    coordinates = [value.get(k) for k in ('x', 'y', 'width', 'height')]
    if any(isinstance(n, bool) or not isinstance(n, (int, float)) or not math.isfinite(n) for n in coordinates):
        raise ValueError('Invalid actual battle UI coordinates')
    x, y, w, h = coordinates
    if x < 0 or y < 0 or w <= 0 or h <= 0 or x + w > 2640.01 or y + h > max_height+.01:
        raise ValueError('Battle UI box exceeds the measured inset-safe phone window')
    return x, y, w, h


def overlap(a, b):
    return a[0] < b[0] + b[2] and a[0] + a[2] > b[0] and a[1] < b[1] + b[3] and a[1] + a[3] > b[1]


def validate_metrics(value, font, native, require_insets=False):
    kind = 'CONTROLLED_NATIVE_LAYOUT_EMULATOR_NOT_REAL_PHONE' if native else 'CONTROLLED_LAYOUT_EMULATOR_NOT_REAL_PHONE'
    if (value.get('kind') != kind or value.get('screenWidth') != 2640 or value.get('screenHeight') != 1216
            or value.get('windowWidth') != 2640
            or value.get('density') != 3 or isinstance(value.get('fontScale'), bool)
            or value.get('fontScale') != float(font)):
        raise ValueError('Battle UI proof has different actual screen/window/font metrics')
    measured=value.get('geometryEvidence')==GEOMETRY_EVIDENCE
    if value.get('geometryEvidence') is not None and not measured:
        raise ValueError('Unknown geometry evidence protocol')
    if require_insets and not measured:
        raise ValueError('Current visual candidate requires actual Android inset measurements')
    height=value.get('windowHeight')
    safe=(0,0,2640,936)
    if measured:
        if isinstance(height,bool) or not isinstance(height,int) or height not in (1080,1216):
            raise ValueError('Unsupported actual Surface height')
        insets=value.get('safeInsets')
        if not isinstance(insets,dict) or set(insets)!=set(('left','top','right','bottom')):
            raise ValueError('Missing measured Android insets')
        left,top,right,bottom=(insets[k] for k in ('left','top','right','bottom'))
        if any(isinstance(n,bool) or not isinstance(n,int) or not 0<=n<=limit for n,limit in
               ((left,880),(right,880),(top,height//3),(bottom,height//3))):
            raise ValueError('Invalid measured Android insets')
        safe=box(value.get('safeArea'),height)
        if safe!=(left,top,2640-left-right,height-top-bottom) or safe[2]<560*3 or safe[3]<280*3:
            raise ValueError('Safe area differs from actual Surface/insets')
    elif height!=1080:
        raise ValueError('Legacy proof must retain its original Surface measurements')
    if not native:
        if value.get('minTouchDp') != 48 or value.get('medicineTextRowsVisible', 0) < 3:
            raise ValueError('Original medicine layout checks are missing')
        return
    cases = value.get('cases')
    if not isinstance(cases, list) or len(cases) != 8 or value.get('fourActorInputAndRewardChecks') != 'PASS':
        raise ValueError('All native party layouts and real four-actor input/reward checks are required')
    for index, case in enumerate(cases):
        count = index // 2 + 1
        enemies, group = ([35] * 6, 8) if index % 2 == 0 else ([137], 156)
        if case.get('party') != list(PARTY[:count]) or case.get('enemies') != enemies or case.get('groupId') != group:
            raise ValueError('Native UI case target was omitted or substituted')
        frame = box(case.get('frame'),height if measured else 936)
        if frame[0]<safe[0] or frame[1]<safe[1] or frame[0]+frame[2]>safe[0]+safe[2]+.01 or frame[1]+frame[3]>safe[1]+safe[3]+.01:
            raise ValueError('Native battle frame escapes measured inset-safe area')
        cards = case.get('cards')
        if not isinstance(cards, list) or len(cards) != count:
            raise ValueError('Native party cards are missing')
        boxes = [box(c,height if measured else 936) for c in cards] + [box(case.get(k),height if measured else 936) for k in ('enemyField', 'allyField')]
        for b in boxes:
            if b[0] < frame[0] or b[1] < frame[1] or b[0]+b[2] > frame[0]+frame[2]+.01 or b[1]+b[3] > frame[1]+frame[3]+.01:
                raise ValueError('Native party/enemy region escapes its frame')
        if any(c[2] < 144 or c[3] < 144 for c in boxes[:count]):
            raise ValueError('Native party targets are smaller than 48dp')
        if any(overlap(a, b) for i, a in enumerate(boxes) for b in boxes[i+1:]):
            raise ValueError('Native party/enemy regions overlap')
        if count >= 3 and len(enemies) == 6 and case.get('compactInstanceNumbers') is not True:
            raise ValueError('Crowded native enemy instance IDs are not independently visible')


def proof_digests(evidence, logs, require_insets=False):
    """Bind all expected raw screenshots, measured JSON and actual passing logs."""
    from PIL import Image
    evidence, logs = Path(evidence), Path(logs)
    files = {}
    for name in log_names():
        data = bounded_bytes(logs / name, 512 * 1024)
        text = data.decode('utf-8')
        if not re.search(r'^OK \(1 test\)\s*$', text, re.M) or re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|Error in ', text):
            raise ValueError('Actual battle UI instrumentation did not pass: ' + name)
        files['town02-runtime/' + name] = hashlib.sha256(data).hexdigest()
    for font in FONTS:
        for prefix, native in (('mobile-phone-', False), ('mobile-party-phone-', True)):
            name = prefix + font + '.json'
            value, data = json_evidence(evidence / name)
            validate_metrics(value, font, native,require_insets)
            files['checkpoint-ui/' + name] = hashlib.sha256(data).hexdigest()
        for name in screenshot_names(font):
            path = evidence / name
            data = bounded_bytes(path, 16 * 1024 * 1024)
            try:
                with Image.open(io.BytesIO(data)) as png:
                    if png.format != 'PNG' or png.size != (2640, 1216):
                        raise ValueError('Actual battle UI screenshot dimensions differ: ' + name)
                    png.load()
            except OSError as error:
                raise ValueError('Unreadable battle UI screenshot: ' + name) from error
            files['checkpoint-ui/' + name] = hashlib.sha256(data).hexdigest()
    payload = json.dumps(dict(kind='CONTROLLED_BATTLE_UI_EMULATOR_NOT_PHONE_OR_FULL_STORY', files=files),
                         sort_keys=True, separators=(',', ':')).encode('utf-8')
    return {UI_PROOF_KEY: hashlib.sha256(payload).hexdigest()}
