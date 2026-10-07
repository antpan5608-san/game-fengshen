"""One original room, using the existing scene/house/tile recipe interfaces.

Private native captures are not CI inputs. Their reviewed hashes and the executed
CPU tables bind the original evidence; Android acceptance is a separate gate.
"""
import hashlib
import io
import json
from PIL import Image
from forensics.fengshen246 import (
    SHA256, extract_map, extract_npcs, extract_text, extract_default_map_palette,
    decode_tokens, glyph_pixels,
)

PATH = 'game-data/provenance/town-room28-resources.json'
ORIGINAL = 'game-data/provenance/town-room28-original.json'
REVISION = 'town0-house2-room28-original-normal-entry-return-talk-hidden-medicine'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def validate(reader, root, checked_span, graphic):
    p = json.loads((root / PATH).read_text(encoding='utf-8'))
    original_bytes = (root / ORIGINAL).read_bytes()
    original = json.loads(original_bytes)
    if p['romSha256'] != SHA256 or p['scopeRevision'] != REVISION or \
            p['originalEvidence'] != dict(path=ORIGINAL, sha256=digest(original_bytes)) or \
            original['romSha256'] != SHA256 or original['scopeRevision'] != REVISION:
        raise ValueError('Room28 original evidence binding differs')
    if [room['mapId'] for room in p['rooms']] != [28]:
        raise ValueError('Room28 cannot admit an unevidenced house')
    native = original['normalOriginal']
    entry = dict(callerMapId=0, houseIndex=2, collisionClass=28,
                 trigger=[12, 23], toMapId=28, spawn=[6, 10])
    if native['kind'] != 'NORMAL_NEW_GAME_CONTROLLER_ONLY_NO_RAM_WRITES_NO_SAVESTATE_LOAD' or \
            native['entry'] != entry or native['return'] != dict(
                fromMapId=28, trigger=[6, 10], toMapId=0, spawn=[12, 23],
                settledFramesAfterMapChange=140) or native['hiddenPickup'] != dict(
                npcIndex=1, category=0, itemId=0, flagMask=1,
                quantityBeforeFirstRepeatReturn=[0, 1, 1, 1],
                flagBeforeFirstRepeatReturn=[0, 1, 1, 1]):
        raise ValueError('Room28 normal entry/return/pickup evidence differs')
    if native['probePath'] != 'tools/rom-extractor/probe-town-room28.lua' or \
            digest((root / native['probePath']).read_bytes()) != native['probeSha256']:
        raise ValueError('Room28 executed native controller source differs')
    tables = {'entry': 2, 'terrain': 64, 'talk': 256, 'hidden': 28}
    if len(original['cpu']) != len(tables):
        raise ValueError('Room28 CPU boundaries missing')
    for name, count in tables.items():
        path = f'game-data/provenance/expected/town-room28/town-room28-{name}-original.tsv'
        table = next((v for v in original['cpu'] if v['path'] == path), None)
        if table is None or table['caseCount'] != count or table['failures'] != 0 or \
                table['probePath'] != 'tools/rom-extractor/probe-town-room28.py':
            raise ValueError('Room28 CPU scope differs')
        data = (root / path).read_bytes()
        if digest(data) != table['sha256'] or len(data.splitlines()) != count + 1 or \
                digest((root / table['probePath']).read_bytes()) != table['probeSha256']:
            raise ValueError('Room28 executed CPU source/table differs')
    if p['sources'] != original['sources']:
        raise ValueError('Room28 source spans differ')
    for span in p['sources']:
        checked_span(reader, span)
    if reader.read(0, 0xd289) != bytes([28]) or \
            reader.read(8, reader.word(8, 0xdc69 + 56), 5) != bytes([6, 10, 254, 12, 23]):
        raise ValueError('Room28 original house/caller return differs')
    binding = dict(**entry, tableSource=reader.span(0, 0xd289, 1, 'Caller0 house2 target'))
    if p['bindings'] != [binding]:
        raise ValueError('Room28 house binding differs')
    room = p['rooms'][0]
    m = extract_map(reader, 28)
    if room['map'] != dict(mapId=28, gridSha256=m['gridSha256'], tilesetId=2,
            palette=extract_default_map_palette(reader, 28)['palette'], walkableClasses=[0, 2, 5]):
        raise ValueError('Room28 geometry/default palette differs')
    font = room['font']
    if font != original['dialogue']['font'] or font['chr2kBanks'] != [2, 3] or \
            font['activePpuMatch'] is not True:
        raise ValueError('Room28 active original font differs')
    raw = b''.join(checked_span(reader, span) for span in font['sources'])
    if len(raw) != 4096 or [s['offset'] for s in font['sources']] != [528400, 530448]:
        raise ValueError('Room28 font banks differ')
    charset = {int(k): v for k, v in font['charset'].items()}
    if {g['code'] for g in font['glyphs']} != set(charset):
        raise ValueError('Room28 font glyph scope incomplete')
    for g in font['glyphs']:
        if g['character'] != charset[g['code']] or digest(bytes(
                v for row in glyph_pixels(raw, 0, g['code']) for v in row)) != g['pixelsSha256']:
            raise ValueError('Room28 glyph pixels differ')
    text = extract_text(reader, 38, 12)
    expected_dialogue = dict(id=text['id'], text=decode_tokens(bytes.fromhex(text['rawHex']), charset)['text'],
        source=dict(confidence='PROVISIONAL_ROM_GLYPH_TRANSCRIPTION', originalVerified=False,
                    record=text['range'], pointerEvidence=text['pointerEvidence'], evidence=PATH))
    if room['dialogues'] != [expected_dialogue]:
        raise ValueError('Room28 hint cannot invent an outcome')
    records = extract_npcs(reader, 28)['records']
    if len(records) != 2 or len(room['npcs']) != 2 or set(room['graphics']) != {
            'npc-room28-161.png', 'npc-room28-198.png'}:
        raise ValueError('Room28 actor scope differs')
    for index, (npc, rec) in enumerate(zip(room['npcs'], records)):
        sprite = f"npc-room28-{rec['entityByte']}.png"
        if (npc['id'], npc['mapId'], npc['cell'], npc['spriteId'], npc['sprite'],
                npc['source']['record'], npc['firstEffects'], npc['repeatDialogue'], npc['houseResourceEvidence']) != (
                f'rom.npc.28.{index}', 28, [(rec[k] - 120) // 16 for k in ('xCandidate', 'yCandidate')],
                rec['entityByte'], sprite, rec['range'], [], None, PATH):
            raise ValueError('Room28 actor identity or effects differ')
        if index == 0:
            if npc['firstDialogue'] != 'rom.dialogue.38.12' or npc.get('readOnlyDialogue') is not True or \
                    any(key in npc for key in ('treasure', 'originalTalk', 'hiddenInvestigation')):
                raise ValueError('Room28 plain hint cannot grant or set an event')
        elif npc['firstDialogue'] != '' or npc.get('hiddenInvestigation') is not True or \
                npc.get('openedSprite') != sprite or npc.get('treasure') != dict(
                    itemId='rom.medicine.0', flagId='rom.map.28.flag.1', amount=1, categoryGrant=0, evidence=PATH):
            raise ValueError('Room28 original hidden medicine differs')
        recipe = room['graphics'][sprite]
        pointer = rec['animationProgramPointer']
        frame_address = reader.word(0, pointer + 1)
        if recipe['frameRecordSource'] != rec['range'] or recipe['animationSource'] != reader.span(
                0, pointer, 3, 'Actual room28 same-entity initial animation transport') or \
                recipe['frameSource'] != reader.span(0, frame_address, 5,
                    'Original room28 frame bytes; high attributes retained without guessed decoding' if index == 0
                    else 'Original room28 hidden blank frame') or \
                recipe['captureKind'] != 'NORMAL_ORIGINAL_NEW_GAME_ONE_OAM_POSE' or \
                recipe['normalPlayEvidence'] is not True or recipe['opaquePixelMatch'] is not True or \
                recipe['actorEntityId'] != rec['entityByte'] or recipe['width'] != 16 or recipe['height'] != 16:
            raise ValueError('Room28 original observed pose binding differs')
        # The high frame attribute bits are not guessed into a universal rule.
        # Bind this observed four-tile pose to its exact original frame bytes.
        frame = checked_span(reader, recipe['frameSource'])
        if [t['xy'] for t in recipe['tiles']] != [[0, 0], [8, 0], [0, 8], [8, 8]]:
            raise ValueError('Room28 four-tile original pose differs')
        for tile, code in zip(recipe['tiles'], frame[1:]):
            observed = original['normalOriginal']['captures']
            if len(checked_span(reader, tile)) != 16 or tile['attribute'] != (66 if index == 0 else 0) or \
                    tile['flipX'] is not (index == 0) or tile['flipY'] is not False:
                raise ValueError('Room28 actual original OAM flags differ')
            bank = 336 + code // 64
            if checked_span(reader, tile) != reader.data[524304 + bank * 1024 + code % 64 * 16:
                    524304 + bank * 1024 + code % 64 * 16 + 16]:
                raise ValueError('Room28 actual original OAM tile differs')
            if recipe['captureSha256'] not in [v['sha256'] for v in observed if v['path'].endswith('001592-room28-stable-entry.png')]:
                raise ValueError('Room28 pose belongs to another capture')
        image = Image.open(io.BytesIO(graphic(reader, recipe))).convert('RGBA')
        opaque = sum(pixel[3] != 0 for pixel in image.getdata())
        if opaque != recipe['opaquePixelCount'] or opaque != (162 if index == 0 else 0):
            raise ValueError('Room28 hidden object cannot acquire a fake icon')
    return room, p
