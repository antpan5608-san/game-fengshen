"""Pinned original CPU evidence and input protection; never App acceptance."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'tools/rom-extractor/probe-original-magic.py'
DATA = ROOT / 'game-data/provenance/expected/original-battle-magic'
ROM = Path(os.environ.get('FENGSHEN_PROBE_ROM', ROOT / 'private-inputs/town02/target.nes'))
EFFECT = ROOT / '.local-ai/native-evidence/magic-battle-row0-effect-trace-01/private-derived/battle-magic-controlled/004059-entry-A026-ram.bin'


class OriginalBattleMagicEvidenceTest(unittest.TestCase):
    def test_public_tables_bind_original_report_and_current_script(self):
        report = json.loads((DATA / 'original-battle-magic-report.json').read_text(encoding='utf-8'))
        self.assertEqual(hashlib.sha256(SCRIPT.read_bytes()).hexdigest(), report['scriptSha256'])
        self.assertEqual(10884, sum(row['cases'] for row in report['results']))
        self.assertEqual(6, len(report['results']))
        for row in report['results']:
            data = (DATA / (row['name'] + '.tsv')).read_bytes()
            self.assertEqual(row['sha256'], hashlib.sha256(data).hexdigest())
            self.assertEqual(row['cases'], len(data.splitlines()) - 1)
        historical = ROOT / 'game-data/provenance/expected/original-magic'
        old = json.loads((historical / 'original-magic-report.json').read_text(encoding='utf-8'))
        self.assertEqual(3700, sum(row['cases'] for row in old['results']))
        for row in old['results']:
            self.assertEqual(row['sha256'], hashlib.sha256((historical / (row['name'] + '.tsv')).read_bytes()).hexdigest())

    def invoke(self, rom, output, *args):
        return subprocess.run([sys.executable, str(SCRIPT), '--rom', str(rom), '--output', str(output), *map(str, args)],
                              cwd=ROOT, capture_output=True, text=True, timeout=30)

    @unittest.skipUnless(ROM.exists(), 'Actual pinned private ROM required; no synthetic source substitution')
    def test_wrong_battle_ram_rejected_before_creating_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); ram = root / 'wrong.bin'; ram.write_bytes(bytes(2048))
            result = self.invoke(ROM, root / 'output', '--battle-effect-ram', ram)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('Exact independently captured private battle effect RAM required', result.stderr)
            self.assertFalse((root / 'output').exists())

    @unittest.skipUnless(ROM.exists() and EFFECT.exists(), 'Actual ROM and captured effect RAM required')
    def test_partial_battle_inputs_rejected_before_any_tables(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'output'
            result = self.invoke(ROM, output, '--battle-effect-ram', EFFECT)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('All four original battle fixtures required', result.stderr)
            self.assertFalse(output.exists())

    @unittest.skipUnless(ROM.exists(), 'Actual pinned private ROM required')
    def test_existing_battle_evidence_preserved_even_without_battle_options(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory); sentinel = output / 'battle-row0-heal.tsv'
            sentinel.write_bytes(b'EXISTING_ORIGINAL_EVIDENCE')
            result = self.invoke(ROM, output)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('Fresh output directory required', result.stderr)
            self.assertEqual(b'EXISTING_ORIGINAL_EVIDENCE', sentinel.read_bytes())
            self.assertEqual([sentinel], list(output.iterdir()))

    @unittest.skipUnless(ROM.exists(), 'Actual pinned private ROM required')
    def test_wrong_rom_rejected_before_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); data = bytearray(ROM.read_bytes()); data[-1] ^= 1
            bad = root / 'wrong.nes'; bad.write_bytes(data)
            result = self.invoke(bad, root / 'output')
            self.assertNotEqual(0, result.returncode)
            self.assertIn('Unsupported ROM fingerprint', result.stderr)
            self.assertFalse((root / 'output').exists())


if __name__ == '__main__':
    unittest.main()
