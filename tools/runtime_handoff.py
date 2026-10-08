"""Bounded transport of normal App checkpoints between the existing CI jobs.

No game-state generation or edits: copy exact App-written JSON bytes, validate
the recorder's cold-start boundary, and bind every stage to one reviewed APK.
"""
import argparse
import base64
import hashlib
import json
import os
import shutil
import subprocess
from pathlib import Path

if __package__:
    from . import battle_visual_evidence as visual
    from . import map_navigation_evidence as navigation
    from . import battle_ui_evidence as battle_ui, room28_evidence as room28, field_magic_evidence as field_magic, battle_magic_evidence as battle_magic
else:
    import battle_visual_evidence as visual
    import map_navigation_evidence as navigation
    import battle_ui_evidence as battle_ui
    import room28_evidence as room28
    import field_magic_evidence as field_magic
    import battle_magic_evidence as battle_magic

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
MAX_SAVE_BYTES = 64 * 1024
IMPORT_NAMES = {f'world-{label}-expected-save.json' for label in
                ('north-palace', 'hell-village2', 'hall-batch', 'ferry', 'yang-join', 'runtime-storage-probe')}
SCOPE_PATH = Path(__file__).resolve().parents[1] / 'ci/runtime-scope.json'
R1_BASE_KEYS = ('upgrade normalHerbSupply controlledBoundaries shopEquipmentInputRegression '
    'touchUx phoneSizedLayout nanhaiNormalRoute nanhaiBossVictory nanhaiOnceAndColdRestart '
    'mobileGrowth mobileEnemyInformation mobileDirectTouch mobileActionSnapshots battleHerb '
    'worldCurrentServices worldSeaNorth worldStatusAndAntidote worldSaveProtection worldWestPalace '
    'worldSharedVillageServices worldTerrainRestore worldNorthPalace worldPearlUseAndColdRestart '
    'worldWholly08Controller worldMedicalControlledCommands').split()
R1_WORLD_KEYS = WORLD_KEYS[:6]
R1_CONTINUATION_KEYS = ['playableR1MedicalNormal', 'playableR1MedicalColdRestart']
R1_STAGE_GATES = dict(base=R1_BASE_KEYS, world=R1_WORLD_KEYS, continuation=R1_CONTINUATION_KEYS)
R2_WORLD_KEYS = R1_WORLD_KEYS + R1_CONTINUATION_KEYS + [
    'worldFirstHallNormal','worldFirstHallColdRestart','worldSecondHallNormal',
    'worldSecondHallColdRestart','worldHallBatchNormal','worldHallBatchColdRestart']
R2_CONTINUATION_KEYS = ['worldFinalHallsNormal','worldRebirthDialogueAndColdRestart']
R2_STAGE_GATES = dict(base=R1_BASE_KEYS,world=R2_WORLD_KEYS,continuation=R2_CONTINUATION_KEYS)
R2_MAP_IDS = [0, 1, 2, 3, 4, 5, 6, 16, 17, 18, 19, 20, 22, 23, 25, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 74, 76, 77, 78, 79, 85, 86, 87, 95, 96, 97, 98, 99, 100, 101, 107, 108, 109, 110, 114, 115, 116, 117, 139, 141, 158, 159, 163, 164, 171]
PERSONAL_GATES = ['upgrade', 'contentLoad', 'touchTransactions', 'partySupplyAndInn',
                  'medicalDoors', 'herbAndBattle', 'saveProtection', 'preMigrationBackup', 'externalColdRestart']
C60_MAP_IDS = [0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 16, 17, 18, 19, 20, 22, 23, 25, 37, 41, 42,
    60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 74, 76, 77, 78, 79, 85, 86, 87, 89, 95,
    96, 97, 98, 99, 100, 101, 107, 108, 109, 110, 114, 115, 116, 117, 136, 139, 141, 145,
    146, 147, 148, 158, 159, 163, 164, 171, 172]
C60_PERSONAL_GATES = ['controlledReplayVersionMarker', 'well8ControlledCodec', 'saveHistoryRollback',
                      'saveHistoryExternalColdRestart', 'saveHistoryCorruptionRetention']
C61_MAP_IDS = sorted(C60_MAP_IDS + [7, 121, 142])
C62_MAP_IDS = sorted(C61_MAP_IDS + [28])
C61_SCOPES = ('WORLD-C61-PERSONAL', *battle_ui.UI_SCOPES)
C60_SCOPES = ('WORLD-C60-PERSONAL', *C61_SCOPES)
C61_PERSONAL_GATES = ['jiangInvitationCodec', 'jiangControlledTouchJoin',
                      'jiangExternalColdRestart', 'jiangFourPartyBattle']
C61_PROOF_KEYS = ('jiangRecordingSha256', 'jiangColdBoundarySha256')
C61_CONTENT_TESTS = [
    'testC61FrozenDependenciesAndMedicalPartySave',
    'testControlledVillage2GirlEquipmentAndLegacyLootInventory',
    'testScopedHellZonesRetainEveryGroupAndSupportedStatusBehavior',
    'testControlledReusableWorldItemRestoresRemovedObjectState',
    'testControlledSceneMechanismSessionSerialization',
    'testOpeningCombatPackageExecutesEveryRomGroup', 'testBundledAndDirectoryUseSameLoader',
    'testTamperedContentRejected', 'testPreviousContentWithoutCharacterNameStillLoads',
    'testScopedEastPartyAndContinuationLoadAndSerialize', 'testOriginalOpeningExitAndCollision',
    'testWorld01NormalRouteAndRepeatedRoundTrips',
    'testWorld01SpecialEntranceDoesNotOpenOtherUnknownTerrain',
    'testDevelopmentInitialCharacterFields', 'testCloudSessionTokenEncryptedAndCleared',
    'testPathTraversalRejected', 'testTownTradeFailuresAndEquipmentCycle',
    'testControlledWell8LocationItemPendingCodecAndNoDuplicateCompletion',
    'testJiamengOriginalMapsActorsAndBattleDefinitionsFixture',
    'testJiamengSavedActorsDialogueAndManualReturnFixture',
    'testControlledJiangInvitationCodecAndDepartureBoundaries']

C62_CONTENT_TESTS = ['testC62Room28DependenciesAndInteriorSaveCodec' if n ==
    'testC61FrozenDependenciesAndMedicalPartySave' else n for n in C61_CONTENT_TESTS]


def personal_gates(scope):
    return (PERSONAL_GATES
            + (C60_PERSONAL_GATES if scope['id'] in C60_SCOPES else [])
            + (C61_PERSONAL_GATES if scope['id'] in C61_SCOPES else [])
            + (battle_ui.UI_GATES if scope['id'] in battle_ui.UI_SCOPES else [])
            + (room28.GATES if scope['id'] == room28.SCOPE else [])
            + (field_magic.GATES if scope.get('fieldMagicAcceptance') == field_magic.ACCEPTANCE else [])
            + (battle_magic.GATES if scope.get('battleMagicAcceptance') == battle_magic.ACCEPTANCE else [])
            + (visual.GATES if scope.get('battleVisualAcceptance') == visual.ACCEPTANCE else [])
            + (navigation.GATES if scope.get('mapNavigationAcceptance') == navigation.ACCEPTANCE else []))


def validate_jiang_digests(receipt):
    for key in C61_PROOF_KEYS:
        value = receipt.get(key)
        if not isinstance(value, str) or len(value) != 64 or any(c not in '0123456789abcdef' for c in value):
            raise ValueError('Missing actual Jiang proof digest: ' + key)


def validate_battle_ui_digest(receipt):
    value = receipt.get(battle_ui.UI_PROOF_KEY)
    if not isinstance(value, str) or len(value) != 64 or any(c not in '0123456789abcdef' for c in value):
        raise ValueError('Missing actual three-font battle UI proof digest')


def jiang_proof_digests(directory, content_version='opening-segment-001-c61'):
    """Verify the original controlled App recorder and its complete cold boundary."""
    if content_version not in ('opening-segment-001-c61','opening-segment-001-c62'):
        raise ValueError('Jiang proof cannot admit an unknown content version')
    recording_path = directory / 'world-jiang-recording.json'
    boundary_path = directory / 'world-jiang-cold-boundary.json'
    proof, boundary = read_json(recording_path), read_json(boundary_path)
    if (proof.get('kind') != 'CONTROLLED_JIANG_INVITATION_SMOKE'
            or proof.get('controlledAssertions') != 'PASS'
            or proof.get('normalAssertions') != 'NOT_APPLICABLE'
            or proof.get('forceStopRestartEqual') is not True
            or proof.get('continuedExploration') is not False
            or proof.get('originalPreferencesRestored') is not True):
        raise ValueError('Jiang requires actual controlled App/cold smoke, not normal-route claims')
    if (boundary.get('kind') != 'ACTUAL_APP_EXTERNAL_COLD_BOUNDARY'
            or boundary.get('equal') is not True or boundary.get('differentTopLevelFields') != []
            or not isinstance(boundary.get('before'), dict)
            or boundary['before'] != boundary.get('after')):
        raise ValueError('Jiang cold restart must preserve the entire actual saved state')
    before = boundary['before']
    if (before.get('contentVersion') != content_version or before.get('mapId') != 7
            or [c.get('id') for c in before.get('characters', [])] != ['nezha','xiaolongnv','yangjian','jiangziya']
            or before.get('flags', {}).get('rom.event.7.21.dialogue.pending') is not True):
        raise ValueError('Jiang cold proof is not the declared four-party pending endpoint')
    names = ['world-jiang-normal-00.mp4', 'world-jiang-cold-restart.mp4']
    segments = proof.get('segments', [])
    if len(segments) != 2 or proof.get('videos') != ['artifacts/checkpoint-ui/' + n for n in names]:
        raise ValueError('Jiang requires both original App video segments')
    for segment, name in zip(segments, names):
        path = directory / name
        if (segment.get('file') != 'artifacts/checkpoint-ui/' + name or path.is_symlink()
                or not path.is_file() or path.stat().st_size == 0
                or hashlib.sha256(path.read_bytes()).hexdigest() != segment.get('sha256')):
            raise ValueError('Jiang original App video missing or changed')
    if (segments[0].get('savedWorldAfter') != before or segments[1].get('savedWorldBefore') != before):
        raise ValueError('Jiang recorder and external cold proof describe different saves')
    return dict(jiangRecordingSha256=digest(recording_path), jiangColdBoundarySha256=digest(boundary_path))


def personal_quality(scope=None):
    scope = active_scope() if scope is None else scope
    return bool(scope and scope.get('quality') == 'PERSONAL_TEST')


def finish_personal(proposed):
    scope = active_scope(proposed)
    if not personal_quality(scope):
        raise ValueError('Personal delivery requires the exact authorized personal scope')
    gates = personal_gates(scope)
    if any(proposed.get(key) != 'PASS' for key in gates):
        raise ValueError('Personal delivery minimum smoke/upgrade/backup gate did not pass')
    result = dict(binding(proposed), **{key: proposed[key] for key in gates})
    if scope['id'] in C61_SCOPES:
        validate_jiang_digests(proposed)
        result.update({key: proposed[key] for key in C61_PROOF_KEYS})
    if scope['id'] in battle_ui.UI_SCOPES:
        validate_battle_ui_digest(proposed)
        result[battle_ui.UI_PROOF_KEY] = proposed[battle_ui.UI_PROOF_KEY]
    if scope['id'] == room28.SCOPE:
        room28.validate_digests(proposed)
        result.update({key: proposed[key] for key in room28.PROOF_KEYS})
    if scope.get('fieldMagicAcceptance') == field_magic.ACCEPTANCE:
        field_magic.validate_digests(proposed)
        result.update({key: proposed[key] for key in field_magic.PROOF_KEYS})
    if scope.get('battleMagicAcceptance') == battle_magic.ACCEPTANCE:
        battle_magic.validate_digests(proposed)
        result.update({key: proposed[key] for key in battle_magic.PROOF_KEYS})
    if scope.get('battleVisualAcceptance') == visual.ACCEPTANCE:
        visual.validate_digests(proposed)
        result.update({key:proposed[key]for key in visual.PROOF_KEYS})
    if scope.get('mapNavigationAcceptance') == navigation.ACCEPTANCE:
        navigation.validate_digests(proposed)
        result.update({key: proposed[key] for key in navigation.PROOF_KEYS})
    result.update(quality='PERSONAL_TEST', manual_acceptance='PENDING', runtime='SMOKE_PASS',
        completedStages=['personal-smoke'], runtimeScope=scope['id'], runtimeScopeSha256=digest(SCOPE_PATH),
        longTests='DEFERRED_TO_MANUAL', stableAcceptance='NOT_RUN', audio='NOT_RUN', onePlus13T='NOT_RUN',
        smokeStart='CONTROLLED_REPLAY_OF_VERIFIED_NORMAL_SAVE',
        fixtureSourceSaveSha256='84ab15c0acc25e5ec5dfcb5345a1978ce634cdfc27ec0e47b26c728dece31a01')
    return result


def review_personal(receipt):
    scope = active_scope(receipt)
    if not personal_quality(scope) or receipt.get('quality') != 'PERSONAL_TEST':
        raise ValueError('Personal receipt must match the authorized source quality')
    if receipt.get('manual_acceptance') != 'PENDING' or receipt.get('runtime') != 'SMOKE_PASS':
        raise ValueError('Personal smoke cannot claim stable or manual acceptance')
    if receipt.get('runtimeScopeSha256') != digest(SCOPE_PATH) or receipt.get('runtimeScope') != scope['id']:
        raise ValueError('Personal receipt scope/hash mismatch')
    if receipt.get('completedStages') != ['personal-smoke'] or any(receipt.get(k) != 'PASS' for k in personal_gates(scope)):
        raise ValueError('Actual personal minimum gates are required')
    if scope['id'] in C61_SCOPES:
        validate_jiang_digests(receipt)
    if scope['id'] in battle_ui.UI_SCOPES:
        validate_battle_ui_digest(receipt)
        if receipt.get('audio') != 'NOT_RUN' or receipt.get('onePlus13T') != 'NOT_RUN':
            raise ValueError('Emulator UI proof cannot claim phone or audio acceptance')
    if scope['id'] == room28.SCOPE:
        room28.validate_digests(receipt)
    if scope.get('fieldMagicAcceptance') == field_magic.ACCEPTANCE:
        field_magic.validate_digests(receipt)
    if scope.get('battleMagicAcceptance') == battle_magic.ACCEPTANCE:
        battle_magic.validate_digests(receipt)
    if scope.get('battleVisualAcceptance') == visual.ACCEPTANCE:
        visual.validate_digests(receipt)
    if scope.get('mapNavigationAcceptance') == navigation.ACCEPTANCE:
        navigation.validate_digests(receipt)
    if receipt.get('longTests') != 'DEFERRED_TO_MANUAL' or receipt.get('stableAcceptance') != 'NOT_RUN':
        raise ValueError('Unexecuted long/stable acceptance must remain explicit')
    if any(receipt.get(k) == 'PASS' for k in R1_BASE_KEYS + WORLD_KEYS + CONTINUATION_KEYS + R1_CONTINUATION_KEYS if k != 'upgrade'):
        raise ValueError('Personal smoke cannot masquerade as normal full-route acceptance')
    return receipt


def active_scope(candidate=None):
    """A hash-bound dependency/test manifest, never a skip-validation flag."""
    if not SCOPE_PATH.exists():
        return None
    scope = read_json(SCOPE_PATH)
    pin = read_json(SCOPE_PATH.parent / 'content-source.json')
    reference = pin.get('runtimeScope', {})
    if scope.get('id') == 'PLAYABLE-R1':
        expected_maps=[0,1,2,16,17,18,19,20,22,23,25,85,95,96,97,98,114,139]
        expected_endpoint=dict(mapId=2,party=['nezha','xiaolongnv'],bossFlag='rom.map.95.flag.128')
        expected_gates=R1_STAGE_GATES
        expected_points={'base':['north-palace'],'world':['hell-village2']}
    elif scope.get('id') == 'WORLD-HELL-R2':
        expected_maps=R2_MAP_IDS
        expected_endpoint=dict(mapId=16,cell=[238,160],party=['nezha','xiaolongnv'],sceneFlag='rom.map.86.flag.128')
        expected_gates=R2_STAGE_GATES
        expected_points={'base':['north-palace'],'world':['hall-batch']}
        if scope.get('quality') != 'STABLE':
            raise ValueError('Hell/rebirth milestone requires actual stable normal stages')
    elif scope.get('id') in C60_SCOPES:
        c61 = scope['id'] in C61_SCOPES
        c62 = scope['id'] == room28.SCOPE
        expected_maps = C62_MAP_IDS if c62 else C61_MAP_IDS if c61 else C60_MAP_IDS
        # End of the existing controlled short smoke, not a new normal-story claim.
        expected_endpoint = dict(mapId=2, party=['nezha', 'xiaolongnv'], bossFlag='rom.map.95.flag.128')
        expected_gates = R1_STAGE_GATES
        expected_points = {'base': ['north-palace'], 'world': ['hell-village2']}
        if (scope.get('quality') != 'PERSONAL_TEST'
                or scope.get('contentVersion') != ('opening-segment-001-c62' if c62 else 'opening-segment-001-c61' if c61 else 'opening-segment-001-c60')
                or scope.get('manifestSha256') != ('625a314a010f6f41f7cb27af373c750c87399d1dc8b59fba9ea13b1ce2eb8bef' if c62 else '37f0f7bb1080f6fe59f3853928c7e5006c2974d6f3ca5698713b2a37f5747557' if c61 else '8c56f689610cff897c58d5efdac2370f32e0934cd172a3b0b173f0eb6c1b7bdb')
                or scope.get('acceptanceScope') != 'CONTROLLED_FIXTURES_AND_SHORT_APP_SMOKE_NOT_FULL_WORLD_OR_STABLE'):
            raise ValueError('Personal scope cannot claim stable/full-world acceptance or a different target')
        if c61 and (scope.get('controlledEndpoint') != dict(mapId=7,
                party=['nezha','xiaolongnv','yangjian','jiangziya'], sceneFlag='rom.map.7.flag.128')
                or scope.get('contentTests') != (C62_CONTENT_TESTS if c62 else C61_CONTENT_TESTS)):
            raise ValueError('c61 controlled endpoint or mandatory content tests changed')
        if c62 and scope.get('normalRoom28Acceptance') != room28.ACCEPTANCE:
            raise ValueError('Room28 normal/cold acceptance cannot omit its exact original endpoint or proofs')
        if c62 and (scope.get('fieldMagicAcceptance') is not None or
                candidate is not None and int(candidate.get('versionCode',0))>=89) and scope.get('fieldMagicAcceptance') != field_magic.ACCEPTANCE:
            raise ValueError('Current field magic touch/fonts/full cold proofs cannot be omitted')
        if c62 and (scope.get('battleMagicAcceptance') is not None or
                candidate is not None and int(candidate.get('versionCode',0))>=90) and scope.get('battleMagicAcceptance') != battle_magic.ACCEPTANCE:
            raise ValueError('Current battle magic touch/phases/fonts/full cold proofs cannot be omitted')
        if c62 and (scope.get('battleVisualAcceptance') is not None or candidate is not None and int(candidate.get('versionCode',0))>=91) and scope.get('battleVisualAcceptance')!=visual.ACCEPTANCE:
            raise ValueError('Current original visual/normal/cold proofs cannot be omitted')
        if c62 and (scope.get('mapNavigationAcceptance') is not None or
                candidate is not None and int(candidate.get('versionCode', 0)) >= 102) and scope.get('mapNavigationAcceptance') != navigation.ACCEPTANCE:
            raise ValueError('Current map navigation touch/fonts proofs cannot be omitted')
        if scope['id'] in battle_ui.UI_SCOPES and scope.get('battleUiAcceptance') != battle_ui.acceptance(
                require_insets=scope.get('battleVisualAcceptance') == visual.ACCEPTANCE,
                require_feedback=scope.get('battleVisualAcceptance') == visual.ACCEPTANCE):
            raise ValueError('Exact battle UI acceptance cannot omit fonts, native cases, screenshots or logs')
    else:
        raise ValueError('Unknown authorized frozen milestone')
    if (reference != dict(path='ci/runtime-scope.json',sha256=digest(SCOPE_PATH))
            or scope.get('requiredJobs') != ['runtime','runtime-world','runtime-continuation']
            or scope.get('completedStages') != list(STAGES)
            or scope.get('contentVersion') != pin['contentVersion']
            or scope.get('manifestSha256') != pin['manifestSha256']
            or scope.get('mapIds') != expected_maps
            or scope.get('endpoint') != expected_endpoint
            or scope.get('stageGates') != expected_gates
            or scope.get('checkpoints') != expected_points):
        raise ValueError('Frozen milestone differs from its dependency, endpoint or content pin')
    if candidate is not None and (candidate['contentHash'] != scope['manifestSha256']
            or candidate['contentVersion'] != scope['contentVersion']):
        raise ValueError('Runtime scope does not describe this exact candidate content')
    if scope.get('quality', 'STABLE') not in ('STABLE', 'PERSONAL_TEST'):
        raise ValueError('Unknown delivery quality')
    if scope.get('quality') == 'PERSONAL_TEST' and scope.get('personalTest') != dict(
            requiredJobs=['runtime'], completedStages=['personal-smoke'], gates=personal_gates(scope),
            manual_acceptance='PENDING'):
        raise ValueError('Personal minimum gates cannot be silently weakened')
    return scope


def checkpoints(candidate=None):
    scope = active_scope(candidate)
    return scope['checkpoints'] if scope else CHECKPOINTS


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
    scope = active_scope(proposed)
    if stage == 'all' and scope is not None:
        raise ValueError('Frozen milestone requires all three actual same-candidate stages')
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
        if scope and (previous.get('runtimeScope') != scope['id'] or
                previous.get('runtimeScopeSha256') != digest(SCOPE_PATH)):
            raise ValueError('Previous stage has a different frozen runtime scope')
    own_keys = set(WORLD_KEYS if stage == 'world' else CONTINUATION_KEYS if index == 2 else proposed)
    if stage == 'base':
        own_keys -= set(WORLD_KEYS + CONTINUATION_KEYS)
    if scope:
        own_keys = set(scope['stageGates'][stage])
    result = dict(previous or {})
    for key in own_keys:
        if key not in proposed:
            raise ValueError('Missing actual stage gate: ' + key)
        result[key] = proposed[key]
        if scope and proposed[key] != 'PASS':
            raise ValueError('Frozen milestone gate did not actually pass: ' + key)
    result.update(binding(proposed), completedStages=list(STAGES[:index + 1]),
                  runtime='PASS' if stage == 'continuation' else 'PARTIAL')
    if scope:
        result.update(runtimeScope=scope['id'], runtimeScopeSha256=digest(SCOPE_PATH),
            deferredFullWorldGates=scope['deferredFullWorldGates'], audio='NOT_RUN', onePlus13T='NOT_RUN')
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
    points = checkpoints(candidate)
    if stage not in points or output.exists():
        raise ValueError('Invalid stage or existing handoff destination')
    receipt = read_json(receipt_path)
    if binding(receipt) != binding(candidate) or receipt.get('runtime') != 'PARTIAL':
        raise ValueError('Handoff receipt is not the same partially verified candidate')
    if receipt.get('completedStages') != list(STAGES[:STAGES.index(stage) + 1]):
        raise ValueError('Incomplete runtime checkpoint chain')
    # Validate everything before writing or copying a single byte.
    files = [receipt_path]
    for label in points[stage]:
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
    points = checkpoints(candidate)
    if parent not in points:
        raise ValueError('This stage does not accept a checkpoint import')
    handoff = read_json(input_dir / 'handoff.json')
    expected = {'runtime-receipt.json'} | {f'world-{label}-{suffix}.json'
        for label in points[parent] for suffix in ('expected-save', 'normal-index', 'recording')}
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
    scope = active_scope(candidate)
    if scope and (receipt.get('runtimeScope') != scope['id'] or
            receipt.get('runtimeScopeSha256') != digest(SCOPE_PATH)):
        raise ValueError('Checkpoint receipt belongs to another frozen runtime scope')
    for label in points[parent]:
        validate_checkpoint(input_dir, label, candidate)
    return receipt


def isolated_adb():
    # Never access an attached phone, production preferences, or a cloud save.
    adb = ['adb', '-s', 'emulator-5554']
    hardware = subprocess.check_output(adb + ['shell', 'getprop', 'ro.hardware'], timeout=10).strip()
    if hardware != b'ranchu':
        raise ValueError('Normal checkpoint import is isolated AOSP AVD only')
    # The original recorder already uses root on this debuggable AOSP image;
    # scoped-storage access must not be assumed from an unprivileged shell.
    subprocess.run(adb + ['root'], check=True, capture_output=True, timeout=10)
    subprocess.run(adb + ['wait-for-device'], check=True, capture_output=True, timeout=10)
    return adb


def write_checkpoint_as_app(adb, name, data, candidate):
    """Test-only App UID writes/reads exact bytes; never imports a GameState."""
    if name not in IMPORT_NAMES or not 0 < len(data) <= MAX_SAVE_BYTES:
        raise ValueError('Checkpoint transport name or size is not permitted')
    sha = hashlib.sha256(data).hexdigest()
    command = adb + ['shell', 'am', 'instrument', '-w',
        '-e', 'class', 'org.fengshen.dev.TouchTest#testImportVerifiedCheckpointBytes',
        '-e', 'checkpointName', name,
        '-e', 'checkpointBytesBase64', base64.b64encode(data).decode('ascii'),
        '-e', 'checkpointSha256', sha,
        '-e', 'checkpointVersionCode', str(candidate['versionCode']),
        '-e', 'checkpointContentVersion', candidate['contentVersion'],
        'org.fengshen.dev.test/android.test.InstrumentationTestRunner']
    # Do not include the command/payload in a CalledProcessError or public log.
    try:
        result = subprocess.run(command, check=False, capture_output=True, timeout=120)
    except subprocess.TimeoutExpired:
        raise ValueError('App-owned checkpoint write/read timed out: ' + name) from None
    if result.returncode or b'OK (1 test)' not in result.stdout:
        # The test asserts only filename/version/hash/validity, never save JSON.
        print(result.stdout.decode('utf-8', errors='replace')[-8000:])
        raise ValueError('App-owned checkpoint write/read failed: ' + name)
    base = '/sdcard/Android/data/org.fengshen.dev/files/'
    actual = subprocess.check_output(adb + ['shell', 'sha256sum', base + name], timeout=10).decode().split()[0]
    if actual != sha:
        raise ValueError('Isolated checkpoint copy changed bytes')


def import_to_isolated_avd(input_dir, stage, candidate, output_receipt):
    receipt = verify(input_dir, stage, candidate)
    adb = isolated_adb()
    parent = STAGES[STAGES.index(stage) - 1]
    for label in checkpoints(candidate)[parent]:
        name = f'world-{label}-expected-save.json'
        write_checkpoint_as_app(adb, name, (input_dir / name).read_bytes(), candidate)
    output_receipt.parent.mkdir(parents=True, exist_ok=True)
    output_receipt.write_text(json.dumps(receipt, indent=2) + '\n')


def probe_app_storage(candidate):
    # Historical verified normal snapshot, CONTROLLED storage-only probe.
    # No launch, restoreSnapshot, preferences update, HP grant or normal-flow claim.
    fixture = Path(__file__).resolve().parents[1] / 'android/app/src/androidTest/assets/north-repeat-before-optional-herb.json'
    data = json.dumps(read_json(fixture)['snapshot'], ensure_ascii=False).encode('utf-8')
    write_checkpoint_as_app(isolated_adb(), 'world-runtime-storage-probe-expected-save.json', data, candidate)
    print('CONTROLLED native App-owned storage probe PASS; no game state imported')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('pack', 'import', 'scope', 'review', 'probe'))
    parser.add_argument('--stage', choices=STAGES)
    parser.add_argument('--candidate', type=Path)
    parser.add_argument('--directory', type=Path)
    parser.add_argument('--field', choices=('id', 'contentTests', 'quality'))
    parser.add_argument('--evidence', type=Path, default=Path('artifacts/checkpoint-ui'))
    parser.add_argument('--receipt', type=Path, default=Path('artifacts/town02-runtime/runtime-receipt.json'))
    args = parser.parse_args()
    if args.mode == 'scope':
        scope = active_scope()
        if args.field == 'quality':
            print(scope.get('quality','STABLE') if scope else 'STABLE')
            return
        print((scope['id'] if scope else 'WORLD-FULL-01') if args.field == 'id' else
            ','.join('org.fengshen.dev.ContentTest#' + n for n in scope['contentTests']) if scope else
            'org.fengshen.dev.ContentTest')
        return
    if args.mode == 'review':
        scope = active_scope()
        receipt = read_json(args.receipt)
        if personal_quality(scope):
            review_personal(receipt)
            if scope['id'] in battle_ui.UI_SCOPES:
                proofs = battle_ui.proof_digests(args.evidence, args.receipt.parent,
                    require_insets=scope.get('battleVisualAcceptance') == visual.ACCEPTANCE,
                    require_feedback=scope.get('battleVisualAcceptance') == visual.ACCEPTANCE)
                if any(receipt.get(key) != value for key, value in proofs.items()):
                    raise ValueError('Raw battle UI artifacts differ from this reviewed candidate proof')
            if scope['id'] == room28.SCOPE:
                proofs = {**room28.proof_digests(args.evidence),
                    **jiang_proof_digests(args.evidence,scope['contentVersion'])}
                if any(receipt.get(key) != value for key,value in proofs.items()):
                    raise ValueError('Raw room28/Jiang artifacts differ from this reviewed candidate proof')
            if scope.get('fieldMagicAcceptance') == field_magic.ACCEPTANCE:
                proofs = field_magic.proof_digests(args.evidence)
                if any(receipt.get(key) != value for key,value in proofs.items()):
                    raise ValueError('Raw field magic artifacts differ from this reviewed candidate proof')
            if scope.get('battleMagicAcceptance') == battle_magic.ACCEPTANCE:
                proofs = battle_magic.proof_digests(args.evidence)
                if any(receipt.get(key) != value for key,value in proofs.items()):
                    raise ValueError('Raw battle magic artifacts differ from this reviewed candidate proof')
            if scope.get('battleVisualAcceptance') == visual.ACCEPTANCE:
                proofs=visual.proof_digests(args.evidence,args.receipt.parent)
                if any(receipt.get(key)!=value for key,value in proofs.items()):
                    raise ValueError('Raw visual normal/cold artifacts differ from this reviewed candidate proof')
            if scope.get('mapNavigationAcceptance') == navigation.ACCEPTANCE:
                proofs = navigation.proof_digests(args.evidence, args.receipt.parent, receipt)
                if any(receipt.get(key) != value for key, value in proofs.items()):
                    raise ValueError('Raw navigation artifacts differ from this reviewed candidate proof')
            print('PERSONAL_TEST minimum checks verified; manual acceptance PENDING; stable acceptance NOT_RUN')
            return
        if not scope or receipt.get('runtimeScope') != scope['id'] or receipt.get('runtimeScopeSha256') != digest(SCOPE_PATH):
            raise ValueError('Missing exact frozen milestone scope receipt')
        active_scope(receipt)
        if receipt.get('runtime') != 'PASS' or receipt.get('completedStages') != list(STAGES):
            raise ValueError('All three milestone runtime stages must actually pass')
        for keys in scope['stageGates'].values():
            for key in keys:
                if receipt.get(key) != 'PASS':
                    raise ValueError('Milestone actual runtime gate missing: ' + key)
        if any(receipt.get(key) == 'PASS' for key in scope['deferredFullWorldGates']):
            raise ValueError('Stage-excluded world content must not be reported as verified')
        print('Exact frozen milestone same-candidate runtime scope verified')
        return
    if args.mode == 'probe':
        if not args.candidate:
            parser.error('Storage probe requires the verified candidate')
        probe_app_storage(current_candidate(args.candidate))
        return
    if not args.stage or not args.candidate or not args.directory:
        parser.error('Checkpoint pack/import requires stage, candidate and directory')
    candidate = current_candidate(args.candidate)
    if args.mode == 'import':
        import_to_isolated_avd(args.directory, args.stage, candidate, args.receipt)
    else:
        # Original pull_evidence prefixes App world JSON with touch-ux-.
        # Normalize names in a temporary transport source, preserving exact bytes.
        import tempfile
        with tempfile.TemporaryDirectory(prefix='fengshen-normal-handoff-') as td:
            source = Path(td)
            for label in checkpoints(candidate)[args.stage]:
                for suffix in ('expected-save', 'normal-index', 'recording'):
                    name = f'world-{label}-{suffix}.json'
                    original = args.evidence / (name if suffix == 'recording' else 'touch-ux-' + name)
                    read_json(original)
                    shutil.copyfile(original, source / name)
            pack(args.stage, source, args.receipt, args.directory, candidate)
    print('Same-candidate normal checkpoint ' + args.mode + ' validated; no production state accessed')


if __name__ == '__main__':
    main()
