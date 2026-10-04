"""Bounded transport of normal App checkpoints between the existing CI jobs.

No game-state generation or edits: copy exact App-written JSON bytes, validate
the recorder's cold-start boundary, and bind every stage to one reviewed APK.
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
from pathlib import Path

STAGES = ('base', 'world', 'continuation')
CHECKPOINTS = {'base': ('north-palace',), 'world': ('ferry', 'yang-join')}
WORLD_KEYS = (
    'worldCave85Normal worldCave85OnceAndColdRestart worldEastPalaceNormal '
    'worldEastPartyAndColdRestart worldHellVillageNormal worldHellVillageColdRestart '
    'worldFirstHallNormal worldFirstHallColdRestart worldSecondHallNormal '
    'worldSecondHallColdRestart worldHallBatchNormal worldHallBatchColdRestart '
    'worldFinalHallsNormal worldRebirthDialogueAndColdRestart '
    'worldContinentBridgeAndZone16Normal worldContinentBridgeColdRestart '
    'worldVillageThreeServicesAndColdRestart worldMedicalNormalEntryAndColdRestart '
    'worldMedicalRevivalNormal worldMedicalPoisonNormal worldMedicalConfusionNormal '
    'worldForest101Normal worldForest101ColdRestart worldTree107Normal worldTree107ColdRestart '
    'worldRoom171Normal worldRoom171GiftColdRestart worldYangJoinNormal '
    'worldYangThreePartyAndColdRestart worldVillageFourServicesTalkNormal '
    'worldVillageFourColdRestart worldFixedFerryIslandNormal worldFixedFerryColdRestartAndReverse'
).split()
CONTINUATION_KEYS = (
    'worldIslandOriginalLayersAndFourVillainsNormal worldIslandOnceChestsAndColdRestart '
    'worldVillageFiveServicesTalkNormal worldVillageFiveColdRestart '
    'worldCave87FlowerNormal worldCave87DepartureAndColdRestart '
    'worldVillageSixServicesTalkNormal worldVillageSixColdRestart '
    'worldNightEightGiftAndCaveNormal worldNightEightColdRestart '
    'worldQueenRouteAndBindingNormal worldQueenHuangOnceAndColdRestart'
).split()
BINDING_KEYS = ('sourceCommit', 'buildRunID', 'sha256', 'contentHash',
                'contentVersion', 'versionCode', 'versionName', 'signerSha256')
MAX_JSON_BYTES = 8 * 1024 * 1024


def read_json(path):
    if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= MAX_JSON_BYTES:
        raise ValueError('Missing, symlinked or oversized checkpoint JSON: ' + path.name)
    value = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(value, dict):
        raise ValueError('Checkpoint JSON must be an object')
    return value


def digest(path):
    read_json(path)  # Files in this transport are strictly bounded JSON only.
    return hashlib.sha256(path.read_bytes()).hexdigest()


def binding(candidate):
    result = {key: candidate[key] for key in BINDING_KEYS}
    result['buildRunID'] = str(result['buildRunID'])
    return result


def current_candidate(path):
    value = read_json(path)
    value.update(sourceCommit=os.environ['GITHUB_SHA'], buildRunID=os.environ['GITHUB_RUN_ID'])
    binding(value)
    return value


def finish_stage(stage, proposed, previous=None):
    """Keep only gates actually reached by this stage; never pre-approve later flows."""
    if stage == 'all':
        return proposed
    if stage not in STAGES:
        raise ValueError('Unknown runtime stage')
    index = STAGES.index(stage)
    if index:
        if previous is None or binding(previous) != binding(proposed):
            raise ValueError('Previous stage is not the exact same candidate')
        if previous.get('runtime') != 'PARTIAL' or previous.get('completedStages') != list(STAGES[:index]):
            raise ValueError('Missing or incomplete preceding runtime stages')
    own_keys = set(WORLD_KEYS if stage == 'world' else CONTINUATION_KEYS if index == 2 else proposed)
    if stage == 'base':
        own_keys -= set(WORLD_KEYS + CONTINUATION_KEYS)
    result = dict(previous or {})
    for key in own_keys:
        if key not in proposed:
            raise ValueError('Missing actual stage gate: ' + key)
        result[key] = proposed[key]
    result.update(binding(proposed), completedStages=list(STAGES[:index + 1]),
                  runtime='PASS' if stage == 'continuation' else 'PARTIAL')
    return result


def validate_checkpoint(root, label, candidate=None):
    save = root / f'world-{label}-expected-save.json'
    index = root / f'world-{label}-normal-index.json'
    recording = root / f'world-{label}-recording.json'
    expected, flow, video = map(read_json, (save, index, recording))
    if candidate is not None and expected.get('contentVersion') != candidate['contentVersion']:
        raise ValueError('Normal save belongs to different candidate content')
    if flow.get('kind') not in ('CONTINUATION_FROM_VERIFIED_SAVE', 'CONTINUATION_FROM_VERIFIED_NORMAL_NANHAI_SAVE'):
        raise ValueError('Checkpoint is not a normal App continuation')
    if flow.get('stateChangesAtLoad') is not False or not flow.get('events'):
        raise ValueError('Normal checkpoint has missing or modified source state')
    if flow['events'][-1].get('snapshot') != expected:
        raise ValueError('Checkpoint differs from the final normal App event')
    if (video.get('normalAssertions') != 'PASS' or video.get('forceStopRestartEqual') is not True
            or video.get('continuedExploration') is not True):
        raise ValueError('Actual normal flow and external cold restart must pass')
    cold = [s for s in video.get('segments', []) if s.get('phase') == 'EXTERNAL_FORCE_STOP_ACTUAL_COLD_RESTART_AND_CONTINUE']
    if len(cold) != 1 or cold[0].get('savedWorldBefore') != expected:
        raise ValueError('Recorder cold-start boundary differs from the normal checkpoint')
    return (save, index, recording)


def pack(stage, evidence, receipt_path, output, candidate):
    if stage not in CHECKPOINTS or output.exists():
        raise ValueError('Invalid stage or existing handoff destination')
    receipt = read_json(receipt_path)
    if binding(receipt) != binding(candidate) or receipt.get('runtime') != 'PARTIAL':
        raise ValueError('Handoff receipt is not the same partially verified candidate')
    if receipt.get('completedStages') != list(STAGES[:STAGES.index(stage) + 1]):
        raise ValueError('Incomplete runtime checkpoint chain')
    # Validate everything before writing or copying a single byte.
    files = [receipt_path]
    for label in CHECKPOINTS[stage]:
        sources = validate_checkpoint(evidence, label, candidate)
        files.extend(sources)
    manifest = {p.name: digest(p) for p in files}
    output.mkdir(parents=True)
    for path in files:
        shutil.copyfile(path, output / path.name)
    (output / 'handoff.json').write_text(json.dumps(dict(schema=1, stage=stage,
        candidate=binding(candidate), completedStages=receipt['completedStages'],
        files=manifest, kind='EXACT_NORMAL_APP_CHECKPOINT_SAME_CANDIDATE'), indent=2) + '\n')


def verify(input_dir, stage, candidate):
    if input_dir.is_symlink() or not input_dir.is_dir():
        raise ValueError('Checkpoint transport must be a real isolated directory')
    parent = STAGES[STAGES.index(stage) - 1] if stage in STAGES[1:] else None
    if parent not in CHECKPOINTS:
        raise ValueError('This stage does not accept a checkpoint import')
    handoff = read_json(input_dir / 'handoff.json')
    expected = {'runtime-receipt.json'} | {f'world-{label}-{suffix}.json'
        for label in CHECKPOINTS[parent] for suffix in ('expected-save', 'normal-index', 'recording')}
    if (handoff.get('schema') != 1 or handoff.get('stage') != parent
            or handoff.get('candidate') != binding(candidate)
            or handoff.get('kind') != 'EXACT_NORMAL_APP_CHECKPOINT_SAME_CANDIDATE'
            or set(handoff.get('files', {})) != expected):
        raise ValueError('Handoff provenance or strict file allowlist mismatch')
    if {p.name for p in input_dir.iterdir()} != expected | {'handoff.json'}:
        raise ValueError('Unexpected checkpoint transport files')
    for name, sha in handoff['files'].items():
        if digest(input_dir / name) != sha:
            raise ValueError('Checkpoint bytes changed: ' + name)
    receipt = read_json(input_dir / 'runtime-receipt.json')
    if (binding(receipt) != binding(candidate) or receipt.get('runtime') != 'PARTIAL'
            or receipt.get('completedStages') != list(STAGES[:STAGES.index(stage)])
            or handoff.get('completedStages') != receipt.get('completedStages')):
        raise ValueError('Runtime stage receipt mismatch')
    for label in CHECKPOINTS[parent]:
        validate_checkpoint(input_dir, label, candidate)
    return receipt


def import_to_isolated_avd(input_dir, stage, candidate, output_receipt):
    receipt = verify(input_dir, stage, candidate)
    # Never access an attached phone, production preferences, or a cloud save.
    adb = ['adb', '-s', 'emulator-5554']
    hardware = subprocess.check_output(adb + ['shell', 'getprop', 'ro.hardware'], timeout=10).strip()
    if hardware != b'ranchu':
        raise ValueError('Normal checkpoint import is isolated AOSP AVD only')
    # The original recorder already uses root on this debuggable AOSP image;
    # scoped-storage access must not be assumed from an unprivileged shell.
    subprocess.run(adb + ['root'], check=True, capture_output=True, timeout=10)
    subprocess.run(adb + ['wait-for-device'], check=True, capture_output=True, timeout=10)
    base = '/sdcard/Android/data/org.fengshen.dev/files/'
    subprocess.run(adb + ['shell', 'mkdir', '-p', base], check=True, capture_output=True, timeout=10)
    parent = STAGES[STAGES.index(stage) - 1]
    for label in CHECKPOINTS[parent]:
        name = f'world-{label}-expected-save.json'
        subprocess.run(adb + ['push', str(input_dir / name), base + name], check=True, capture_output=True, timeout=10)
        actual = subprocess.check_output(adb + ['shell', 'sha256sum', base + name], timeout=10).decode().split()[0]
        if actual != digest(input_dir / name):
            raise ValueError('Isolated checkpoint copy changed bytes')
    output_receipt.parent.mkdir(parents=True, exist_ok=True)
    output_receipt.write_text(json.dumps(receipt, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('pack', 'import'))
    parser.add_argument('--stage', required=True, choices=STAGES)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, default=Path('artifacts/checkpoint-ui'))
    parser.add_argument('--receipt', type=Path, default=Path('artifacts/town02-runtime/runtime-receipt.json'))
    args = parser.parse_args()
    candidate = current_candidate(args.candidate)
    if args.mode == 'import':
        import_to_isolated_avd(args.directory, args.stage, candidate, args.receipt)
    else:
        # Original pull_evidence prefixes App world JSON with touch-ux-.
        # Normalize names in a temporary transport source, preserving exact bytes.
        import tempfile
        with tempfile.TemporaryDirectory(prefix='fengshen-normal-handoff-') as td:
            source = Path(td)
            for label in CHECKPOINTS[args.stage]:
                for suffix in ('expected-save', 'normal-index', 'recording'):
                    name = f'world-{label}-{suffix}.json'
                    original = args.evidence / (name if suffix == 'recording' else 'touch-ux-' + name)
                    read_json(original)
                    shutil.copyfile(original, source / name)
            pack(args.stage, source, args.receipt, args.directory, candidate)
    print('Same-candidate normal checkpoint ' + args.mode + ' validated; no production state accessed')


if __name__ == '__main__':
    main()
