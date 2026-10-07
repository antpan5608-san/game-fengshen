"""Verify same-candidate normal room28/cold artifacts; no player state synthesis."""
import hashlib
import json
from pathlib import Path

SCOPE = 'WORLD-C62-ROOM-PERSONAL'
GATES = ['room28NormalEntryHintAndReturn', 'room28NormalHiddenMedicineOnce',
         'room28ExternalColdAndReentry']
PROOF_KEYS = ('room28RecordingSha256', 'room28ColdBoundarySha256', 'room28UiEvidenceSha256')
SCREENSHOTS = ('normal-original-door-entry', 'normal-read-only-hint', 'normal-hidden-herb-once',
               'normal-return-original-door', 'normal-saved-interior', 'cold-full-state',
               'cold-reentry-no-repeat-grant')
ACCEPTANCE = dict(kind='NORMAL_NEW_GAME_TOUCH_WITH_EXTERNAL_COLD_NOT_FULL_STORY_OR_PHONE',
    contentVersion='opening-segment-001-c62', mapId=28, savedCell=[6, 7],
    callerMapId=0, returnCell=[12, 23], originalMedicineId=0, savedMedicineQuantity=1,
    screen=[960, 540], requiredScreenshots=7, proofKeys=list(PROOF_KEYS))


def read(path):
    path = Path(path)
    if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= 1024 * 1024:
        raise ValueError('Missing or unsafe room28 JSON: ' + path.name)
    data = path.read_bytes()
    value = json.loads(data.decode('utf-8'))
    if not isinstance(value, dict):
        raise ValueError('Room28 evidence must be an object')
    return value, data


def validate_digests(receipt):
    for key in PROOF_KEYS:
        value = receipt.get(key)
        if not isinstance(value, str) or len(value) != 64 or any(c not in '0123456789abcdef' for c in value):
            raise ValueError('Missing room28 same-candidate proof: ' + key)


def proof_digests(directory):
    # Scope/source queries run before the original publisher installs Pillow.
    # Actual image verification still requires it and must fail if unavailable.
    from PIL import Image
    directory = Path(directory)
    recording, recording_bytes = read(directory / 'world-room28-recording.json')
    boundary, boundary_bytes = read(directory / 'world-room28-cold-boundary.json')
    index, index_bytes = read(directory / 'touch-ux-world-room28-normal-index.json')
    expected, expected_bytes = read(directory / 'touch-ux-world-room28-expected-save.json')
    if (recording.get('source') != 'Actual Android App screenrecord; SILENT, no sound validation'
            or recording.get('normalAssertions') != 'PASS'
            or recording.get('forceStopRestartEqual') is not True
            or recording.get('continuedExploration') is not True
            or recording.get('originalPreferencesRestored') is not True
            or 'controlledAssertions' in recording):
        raise ValueError('Room28 requires a real normal new-game flow and restored original preferences')
    if (boundary.get('kind') != 'ACTUAL_APP_EXTERNAL_COLD_BOUNDARY'
            or boundary.get('equal') is not True or boundary.get('differentTopLevelFields') != []
            or boundary.get('before') != expected or boundary.get('after') != expected
            or index.get('kind') != 'NORMAL_NEW_GAME_GAMEVIEW_TOUCH_INPUTS'
            or index.get('stateGrants') is not False or index.get('restoredFixture') is not False
            or index.get('expectedSave') != expected):
        raise ValueError('Room28 entire normal saved state and actual cold boundary differ')
    if (any(type(expected.get(k)) is not int for k in ('mapId', 'x', 'y'))
            or type(expected.get('inventory', {}).get('rom.medicine.0')) is not int
            or any(type(expected.get('interiorContext', {}).get(k)) is not int
                for k in ('callerMapId', 'returnX', 'returnY'))
            or expected.get('contentVersion') != 'opening-segment-001-c62'
            or expected.get('mapId') != 28 or expected.get('x') != 104 or expected.get('y') != 120
            or expected.get('interiorContext') != dict(callerMapId=0, returnX=12, returnY=23)
            or expected.get('flags', {}).get('rom.map.28.flag.1') is not True
            or expected.get('inventory', {}).get('rom.medicine.0') != 1
            or [c.get('id') for c in expected.get('characters', [])] != ['nezha']):
        raise ValueError('Room28 recording is not the exact normal room/caller/herb endpoint')
    names = ('world-room28-normal-00.mp4', 'world-room28-cold-restart.mp4')
    segments = recording.get('segments', [])
    if len(segments) != 2 or recording.get('videos') != ['artifacts/checkpoint-ui/' + n for n in names]:
        raise ValueError('Room28 requires both complete original App segments')
    for segment, name in zip(segments, names):
        path = directory / name
        if (segment.get('file') != 'artifacts/checkpoint-ui/' + name or path.is_symlink()
                or not path.is_file() or path.stat().st_size == 0
                or hashlib.sha256(path.read_bytes()).hexdigest() != segment.get('sha256')):
            raise ValueError('Room28 original App video missing or changed')
    if segments[0].get('savedWorldAfter') != expected or segments[1].get('savedWorldBefore') != expected:
        raise ValueError('Room28 recorder and full cold boundary describe different saves')
    # Bind raw screenshots and App-written normal state/index, not only digest shapes.
    files = {name: hashlib.sha256(data).hexdigest() for name, data in (
        ('touch-ux-world-room28-normal-index.json', index_bytes),
        ('touch-ux-world-room28-expected-save.json', expected_bytes))}
    for stage in SCREENSHOTS:
        name = 'touch-ux-world-room28-' + stage + '.png'
        path = directory / name
        if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= 8 * 1024 * 1024:
            raise ValueError('Missing room28 actual screenshot: ' + name)
        with Image.open(path) as image:
            image.load()
            if image.format != 'PNG' or image.size != (960, 540) or len(image.convert('RGB').getcolors(256) or []) == 1:
                raise ValueError('Room28 actual screenshot has wrong geometry or blank pixels')
        files[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    return dict(room28RecordingSha256=hashlib.sha256(recording_bytes).hexdigest(),
                room28ColdBoundarySha256=hashlib.sha256(boundary_bytes).hexdigest(),
                room28UiEvidenceSha256=hashlib.sha256(json.dumps(files, sort_keys=True,
                    separators=(',', ':')).encode()).hexdigest())
