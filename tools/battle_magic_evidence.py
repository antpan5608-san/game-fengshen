"""Actual battle magic touch/phase/full cold proof, distinct from native rule provenance."""
import copy
import hashlib
import json
from pathlib import Path

try:
    from .room28_evidence import read
except ImportError:
    from room28_evidence import read

GATES = ['battleMagicFourRoleTouchPhasesAndFonts', 'battleMagicFullSaveExternalCold']
PROOF_KEYS = ('battleMagicRecordingSha256', 'battleMagicColdBoundarySha256', 'battleMagicUiEvidenceSha256')
KIND = 'CONTROLLED_FOUR_ROLE_BATTLE_MAGIC_NOT_NORMAL_JOIN_OR_PHONE'
STAGES = [('heal-effect', 44, 58, 2, 'HEAL'), ('heal-debit', 41, 58, 2, 'TEXT'),
          ('cure-effect', 41, 58, 0, 'STATUS'), ('cure-debit', 38, 58, 0, 'TEXT')]
ACCEPTANCE = dict(kind=KIND, spells=['rom.magic.battle.1.0', 'rom.magic.battle.1.1'],
    caster='xiaolongnv', casterLevel=12, target='nezha', cost=3,
    beforeHP=5, maximumHP=200, beforeStatus=2, afterHP=58, afterStatus=0,
    beforeMP=44, afterMP=38, fonts=[1.0, 1.3, 2.0], proofKeys=list(PROOF_KEYS))


def validate_digests(receipt):
    for key in PROOF_KEYS:
        value = receipt.get(key)
        if not isinstance(value, str) or len(value) != 64 or any(c not in '0123456789abcdef' for c in value):
            raise ValueError('Missing actual battle magic proof: ' + key)


def complete_save(value):
    fields = {'saveSchemaVersion', 'contentVersion', 'mapId', 'x', 'y', 'direction',
              'characters', 'terrainMode', 'inventory', 'flags', 'money', 'encounterSteps'}
    actor_fields = {'id', 'level', 'experience', 'hp', 'maxHp', 'mp', 'maxMp',
                    'strength', 'stamina', 'agility', 'spirit', 'equipment'}
    if (not isinstance(value, dict) or not fields.issubset(value)
            or value.get('saveSchemaVersion') != 1 or value.get('contentVersion') != 'opening-segment-001-c62'
            or not isinstance(value.get('characters'), list)
            or [c.get('id') for c in value['characters']] != ['nezha', 'xiaolongnv', 'yangjian', 'jiangziya']
            or any(not actor_fields.issubset(c) for c in value['characters'])):
        raise ValueError('Battle magic requires a complete four-role save')
    return value['characters']


def proof_digests(directory):
    from PIL import Image
    directory = Path(directory)
    recording, raw_recording = read(directory / 'world-battle-magic-recording.json')
    cold, raw_cold = read(directory / 'world-battle-magic-cold-boundary.json')
    expected, raw_expected = read(directory / 'touch-ux-battle-magic-expected-save.json')
    complete_save(expected)
    if (recording.get('source') != 'Actual Android App screenrecord; SILENT, no sound validation'
            or recording.get('kind') != 'CONTROLLED_BATTLE_MAGIC_SMOKE'
            or recording.get('controlledAssertions') != 'PASS' or recording.get('normalAssertions') != 'NOT_APPLICABLE'
            or recording.get('forceStopRestartEqual') is not True or recording.get('continuedExploration') is not False
            or recording.get('originalPreferencesRestored') is not True):
        raise ValueError('Battle magic requires actual controlled App recordings')
    if (cold.get('kind') != 'ACTUAL_APP_EXTERNAL_COLD_BOUNDARY' or cold.get('equal') is not True
            or cold.get('differentTopLevelFields') != [] or cold.get('before') != expected or cold.get('after') != expected):
        raise ValueError('Battle magic complete external cold save differs')
    hashes = {'touch-ux-battle-magic-expected-save.json': hashlib.sha256(raw_expected).hexdigest()}
    pictures = [('touch-ux-battle-magic-cold-full-state.png', None)]
    for font in ('1_0', '1_3', '2_0'):
        name = 'touch-ux-battle-magic-font-' + font + '.json'
        report, raw = read(directory / name)
        flags = ('selectionCancelMultiPointerUnchanged', 'staleAndRepeatUnchanged',
                 'partyLabelsAndMpHeaderFit', 'fullSavePersisted')
        if (report.get('kind') != KIND or report.get('font') != float(font.replace('_', '.'))
                or any(report.get(k) is not True for k in flags)):
            raise ValueError('Battle magic touch/font assertions missing')
        before, after_magic, after = report.get('before'), report.get('afterMagic'), report.get('after')
        actors = complete_save(before)
        complete_save(after_magic)
        complete_save(after)
        if (actors[0]['hp'] != 5 or actors[0]['maxHp'] != 200 or actors[0].get('statusMask', 0) != 2
                or actors[1]['level'] != 12 or actors[1]['mp'] != 44):
            raise ValueError('Battle magic source differs from original fixture')
        wanted = copy.deepcopy(before)
        wanted['characters'][0]['hp'] = 58
        wanted['characters'][0].pop('statusMask', None)  # Save codec omits zero status.
        wanted['characters'][1]['mp'] = 38
        if after_magic != wanted:
            raise ValueError('Battle magic changed unrelated pre-victory state')
        if after['characters'][1]['mp'] != 38 or after['characters'][0].get('statusMask', 0) != 0:
            raise ValueError('Battle magic MP/status missing in committed save')
        if font == '1_0' and after != expected:
            raise ValueError('Battle magic recorder describes a different committed save')
        phases = report.get('phases', [])
        if len(phases) != len(STAGES):
            raise ValueError('Battle magic phase snapshots missing')
        for phase, (stage, mp, hp, status, kind) in zip(phases, STAGES):
            if (phase.get('stage') != stage or phase.get('actorID') != 'xiaolongnv' or phase.get('targetID') != 'nezha'
                    or phase.get('actionKind') != kind or phase.get('casterMP') != mp
                    or phase.get('targetHP') != hp or phase.get('targetStatus') != status):
                raise ValueError('Battle magic effect/debit phase differs')
            hp_state = {c['id']: c['hp'] for c in actors}
            mp_state = {c['id']: c['mp'] for c in actors}
            status_state = {c['id']: c.get('statusMask', 0) for c in actors}
            hp_state['nezha'] = hp
            mp_state['xiaolongnv'] = mp
            status_state['nezha'] = status
            if phase.get('partyHP') != hp_state or phase.get('partyMP') != mp_state or phase.get('partyStatus') != status_state:
                raise ValueError('Battle magic complete party phase differs')
        hashes[name] = hashlib.sha256(raw).hexdigest()
        for stage in ('selection', 'antidote-selection', 'saved', *(x[0] for x in STAGES)):
            pictures.append(('touch-ux-battle-magic-' + stage + '-font-' + font + '.png',
                             (report.get('screenWidth'), report.get('screenHeight'))))
    for name, size in pictures:
        path = directory / name
        if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= 10 * 1024 * 1024:
            raise ValueError('Missing actual battle magic screenshot: ' + name)
        with Image.open(path) as picture:
            if (size is not None and picture.size != size or min(picture.size) < 80
                    or picture.convert('RGB').getbbox() is None):
                raise ValueError('Wrong or blank battle magic screenshot: ' + name)
        hashes[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    segments = recording.get('segments', [])
    normal = [s for s in segments if Path(s.get('file', '')).name.startswith('world-battle-magic-normal-')]
    restart = [s for s in segments if Path(s.get('file', '')).name == 'world-battle-magic-cold-restart.mp4']
    if (not normal or len(restart) != 1 or normal[-1].get('savedWorldAfter') != expected
            or restart[0].get('savedWorldBefore') != expected):
        raise ValueError('Battle magic recordings/cold boundary mismatch')
    for segment in segments:
        path = directory / Path(segment['file']).name
        if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= 1024 * 1024 * 1024:
            raise ValueError('Missing original battle magic video')
        if hashlib.sha256(path.read_bytes()).hexdigest() != segment.get('sha256'):
            raise ValueError('Original battle magic video bytes changed')
    return dict(battleMagicRecordingSha256=hashlib.sha256(raw_recording).hexdigest(),
                battleMagicColdBoundarySha256=hashlib.sha256(raw_cold).hexdigest(),
                battleMagicUiEvidenceSha256=hashlib.sha256(json.dumps(hashes, sort_keys=True,
                    separators=(',', ':')).encode('utf-8')).hexdigest())
