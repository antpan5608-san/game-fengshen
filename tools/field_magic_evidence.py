"""Actual signed-App field magic evidence; isolated fixture, never a normal-join claim."""
import copy
import hashlib
import json
from pathlib import Path

try:
    from .room28_evidence import read
except ImportError:
    from room28_evidence import read

GATES = ['fieldMagicTouchCancelCommitAndFonts', 'fieldMagicFullSaveExternalCold']
PROOF_KEYS = ('fieldMagicRecordingSha256', 'fieldMagicColdBoundarySha256', 'fieldMagicUiEvidenceSha256')
ACCEPTANCE = dict(kind='CONTROLLED_FOUR_ROLE_REAL_TOUCH_NOT_NORMAL_JOIN_OR_PHONE',
    spellId='rom.magic.field.1.0', cost=3, caster='xiaolongnv', casterLevel=12,
    target='nezha', beforeHP=5, maximumHP=200, afterHP=58, beforeMP=44, afterMP=41,
    fonts=[1.0, 1.3, 2.0], proofKeys=list(PROOF_KEYS))


def validate_digests(receipt):
    for key in PROOF_KEYS:
        value = receipt.get(key)
        if not isinstance(value, str) or len(value) != 64 or any(c not in '0123456789abcdef' for c in value):
            raise ValueError('Missing actual field magic proof: ' + key)


def proof_digests(directory):
    from PIL import Image  # Source/scope queries remain independent of image packages.
    directory = Path(directory)
    recording, recording_bytes = read(directory / 'world-field-magic-recording.json')
    cold, cold_bytes = read(directory / 'world-field-magic-cold-boundary.json')
    expected, expected_bytes = read(directory / 'touch-ux-world-field-magic-expected-save.json')
    if (recording.get('source') != 'Actual Android App screenrecord; SILENT, no sound validation'
            or recording.get('kind') != 'CONTROLLED_FIELD_MAGIC_SMOKE'
            or recording.get('controlledAssertions') != 'PASS'
            or recording.get('normalAssertions') != 'NOT_APPLICABLE'
            or recording.get('forceStopRestartEqual') is not True
            or recording.get('continuedExploration') is not False
            or recording.get('originalPreferencesRestored') is not True):
        raise ValueError('Field magic requires actual controlled App evidence and restored preferences')
    if (cold.get('kind') != 'ACTUAL_APP_EXTERNAL_COLD_BOUNDARY' or cold.get('equal') is not True
            or cold.get('differentTopLevelFields') != [] or cold.get('before') != expected
            or cold.get('after') != expected):
        raise ValueError('Field magic full save differs at actual external cold boundary')
    hashes = {'touch-ux-world-field-magic-expected-save.json': hashlib.sha256(expected_bytes).hexdigest()}
    images = []
    for font in ('1_0', '1_3', '2_0'):
        name = 'touch-ux-world-field-magic-font-' + font + '.json'
        data, raw = read(directory / name)
        if (data.get('kind') != ACCEPTANCE['kind'] or data.get('font') != float(font.replace('_', '.'))
                or data.get('selectionAndCancelUnchanged') is not True
                or data.get('protectedSaveFailureRollbackUnchanged') is not True
                or data.get('repeatConfirmationUnchanged') is not True):
            raise ValueError('Field magic actual font/touch assertion missing')
        before, after = data.get('before'), data.get('after')
        if not isinstance(before, dict) or not isinstance(after, dict):
            raise ValueError('Field magic requires complete before/after snapshots')
        required = {'saveSchemaVersion', 'contentVersion', 'mapId', 'x', 'y', 'direction',
                    'characters', 'terrainMode', 'inventory', 'flags', 'money', 'encounterSteps'}
        if (not required.issubset(before) or before.get('saveSchemaVersion') != 1
                or before.get('contentVersion') != 'opening-segment-001-c62'):
            raise ValueError('Field magic complete snapshot schema/content marker missing')
        wanted = copy.deepcopy(before)
        characters = wanted.get('characters', [])
        if [c.get('id') for c in characters] != ['nezha', 'xiaolongnv', 'yangjian', 'jiangziya']:
            raise ValueError('Field magic four-role fixture is missing')
        actor_fields = {'id', 'level', 'experience', 'hp', 'maxHp', 'mp', 'maxMp',
                        'strength', 'stamina', 'agility', 'spirit', 'equipment'}
        if any(not actor_fields.issubset(c) for c in characters):
            raise ValueError('Field magic complete actor snapshots missing')
        if (characters[0]['hp'] != 5 or characters[0]['maxHp'] != 200
                or characters[1]['level'] != 12 or characters[1]['mp'] != 44):
            raise ValueError('Field magic fixture differs from independent original observation')
        characters[0]['hp'] = 58
        characters[1]['mp'] = 41
        if after != wanted or font == '1_0' and after != expected:
            raise ValueError('Field magic changed unrelated snapshot fields or wrong HP/MP')
        hashes[name] = hashlib.sha256(raw).hexdigest()
        images.append(('touch-ux-world-field-magic-committed-font-' + font + '.png',
                       (data.get('screenWidth'), data.get('screenHeight'))))
    images += [('touch-ux-world-field-magic-' + stage + '.png', None) for stage in
               ('nezha-unavailable', 'selection', 'target', 'save-failure-rollback', 'cold-full-state')]
    for name, size in images:
        path = directory / name
        if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= 10 * 1024 * 1024:
            raise ValueError('Missing actual field magic screenshot: ' + name)
        with Image.open(path) as picture:
            if (size is not None and picture.size != size or min(picture.size) < 80
                    or picture.convert('RGB').getbbox() is None):
                raise ValueError('Wrong or blank field magic screenshot: ' + name)
        hashes[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    names = {'world-field-magic-normal-00.mp4', 'world-field-magic-cold-restart.mp4'}
    segments = recording.get('segments', [])
    if not names.issubset({Path(s.get('file', '')).name for s in segments}):
        raise ValueError('Field magic original App recordings missing')
    normal = [s for s in segments if Path(s.get('file', '')).name.startswith('world-field-magic-normal-')]
    restart = [s for s in segments if Path(s.get('file', '')).name == 'world-field-magic-cold-restart.mp4']
    if (not normal or len(restart) != 1 or normal[-1].get('savedWorldAfter') != expected
            or restart[0].get('savedWorldBefore') != expected):
        raise ValueError('Field magic recorder and full cold boundary describe different saves')
    for segment in segments:
        path = directory / Path(segment['file']).name
        if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= 1024 * 1024 * 1024:
            raise ValueError('Missing original field magic video')
        if hashlib.sha256(path.read_bytes()).hexdigest() != segment.get('sha256'):
            raise ValueError('Original field magic video bytes changed')
    return dict(fieldMagicRecordingSha256=hashlib.sha256(recording_bytes).hexdigest(),
                fieldMagicColdBoundarySha256=hashlib.sha256(cold_bytes).hexdigest(),
                fieldMagicUiEvidenceSha256=hashlib.sha256(json.dumps(hashes, sort_keys=True,
                    separators=(',', ':')).encode('utf-8')).hexdigest())
