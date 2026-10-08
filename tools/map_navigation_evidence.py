"""Bind actual controlled navigation touches to the original signed-candidate receipt.

This verifies a bounded opening-room smoke, never normal story, phone or sound.
"""
import hashlib
import json
import math
import re
from pathlib import Path

METHOD = 'testControlledNavigationHiddenGroundAndObjectCancel'
MODEL = 'CURRENT_MAP_NAVIGATION_TOUCH_V1'
KIND = 'CONTROLLED_FRESH_OPENING_NOT_NORMAL_WORLD_COMPLETION'
FONTS = ('1.0', '1.3', '2.0')
GATES = ['mapNavigationGroundObjectAndFonts']
PROOF_KEYS = ('mapNavigationEvidenceSha256',)
ACCEPTANCE = dict(kind=KIND, model=MODEL, mapId=114, screen=[2640, 1216],
                  fonts=[1.0, 1.3, 2.0], method=METHOD, proofKey=PROOF_KEYS[0],
                  coverage='HIDDEN_STICK_GESTURE_PRIORITY_GROUND_NATURAL_STEPS_OBJECT_ARRIVAL_CANCEL')
BOOLEAN_CHECKS = ('defaultHiddenJoystick', 'hiddenStickAreaMapGesture',
                  'arrivalSelectionOnly', 'cancelNoCommit', 'noAutoResume', 'dragNoTarget',
                  'cancelGestureNoTarget', 'multipleFingersNoTarget', 'hudNoMapTarget',
                  'menuNoMapTarget', 'menuAndCancelMinimum48dp')


def read_bytes(path, limit):
    path = Path(path)
    if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= limit:
        raise ValueError('Missing or unsafe navigation evidence: ' + path.name)
    return path.read_bytes()


def validate_digests(receipt):
    value = receipt.get(PROOF_KEYS[0])
    if not isinstance(value, str) or re.fullmatch('[0-9a-f]{64}', value) is None:
        raise ValueError('Missing actual navigation proof digest')


def proof_digests(directory, logs, candidate):
    # Scope queries must not depend on image decoding. Raw review requires Pillow.
    from PIL import Image
    directory, logs = Path(directory), Path(logs)
    version = candidate.get('versionCode')
    if type(version) is not int or version < 102:
        raise ValueError('Navigation proof requires the new signed candidate version')
    binding = {k: candidate[k] for k in ('sourceCommit', 'buildRunID', 'sha256',
               'contentHash', 'contentVersion', 'versionCode', 'versionName', 'signerSha256')}
    binding['buildRunID'] = str(binding['buildRunID'])
    files = {}
    for font in FONTS:
        name = f'touch-ux-world01-navigation-touch-{font}.json'
        data = read_bytes(directory / name, 64 * 1024)
        value = json.loads(data.decode('utf-8'))
        if not isinstance(value, dict):
            raise ValueError('Navigation evidence must be an object')
        scale = value.get('fontScale')
        if (value.get('model') != MODEL or value.get('scope') != KIND
                or type(value.get('versionCode')) is not int or value['versionCode'] != version
                or value.get('contentVersion') != candidate['contentVersion']
                or type(value.get('mapId')) is not int or value['mapId'] != 114
                or type(scale) not in (int, float) or not math.isfinite(scale)
                or abs(scale - float(font)) > .001
                or any(value.get(k) is not True for k in BOOLEAN_CHECKS)):
            raise ValueError('Navigation model/version/font/scope or actual assertions differ')
        for expected, actual, lower, upper in (
                ('groundExpectedSteps', 'groundActualSteps', 6, 9),
                ('objectExpectedSteps', 'objectActualSteps', 2, 196608)):
            if (type(value.get(expected)) is not int or type(value.get(actual)) is not int
                    or not lower <= value[expected] <= upper or value[actual] != value[expected]):
                raise ValueError('Navigation natural steps differ from the original planner')
        if (not isinstance(value.get('objectId'), str)
                or re.fullmatch('[A-Za-z0-9._-]{1,128}', value['objectId']) is None
                or value.get('arrivalFace') not in ('UP', 'LEFT', 'DOWN', 'RIGHT')):
            raise ValueError('Missing stable navigation object or original arrival facing')
        if (type(value.get('frameWidth')) is not int or type(value.get('frameHeight')) is not int
                or [value['frameWidth'], value['frameHeight']] != ACCEPTANCE['screen']
                or type(value.get('cyanPixels')) is not int or not 20 < value['cyanPixels'] <= 2640 * 1216):
            raise ValueError('Navigation frame geometry or cyan pixel observation missing')
        files[name] = hashlib.sha256(data).hexdigest()
        log_name = f'{METHOD}-font-{font}.txt'
        log_data = read_bytes(logs / log_name, 1024 * 1024)
        text = log_data.decode('utf-8')
        if ('OK (1 test)' not in text or any(marker in text for marker in
                ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'INSTRUMENTATION_ABORTED', 'Process crashed'))):
            raise ValueError('Actual navigation instrument method did not pass')
        files[log_name] = hashlib.sha256(log_data).hexdigest()
        for stage in ('ground', 'object-options'):
            image_name = f'touch-ux-world01-navigation-{stage}-{font}.png'
            image_path = directory / image_name
            image_data = read_bytes(image_path, 16 * 1024 * 1024)
            with Image.open(image_path) as image:
                if image.format != 'PNG' or list(image.size) != ACCEPTANCE['screen']:
                    raise ValueError('Wrong actual navigation PNG geometry')
                image.load()
                rgb = image.convert('RGB')
                colors = rgb.getcolors(65536)
                if colors is not None and len(colors) == 1:
                    raise ValueError('Blank navigation screenshot')
                if stage == 'ground':
                    cyan = (sum(count for count, color in colors if color == (41, 223, 255))
                            if colors is not None else sum(pixel == (41, 223, 255) for pixel in rgb.getdata()))
                    if cyan != value['cyanPixels']:
                        raise ValueError('Actual cyan PNG differs from navigation report')
            files[image_name] = hashlib.sha256(image_data).hexdigest()
    payload = dict(candidate=binding, files=files)
    return {PROOF_KEYS[0]: hashlib.sha256(json.dumps(payload, sort_keys=True,
                separators=(',', ':')).encode()).hexdigest()}
