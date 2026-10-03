"""Exercise actual Git checkout with CRLF defaults; retain strict byte hashes."""
import hashlib,json,subprocess,tempfile,unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
class EvidenceCheckoutTests(unittest.TestCase):
    def test_cpu_tables_keep_their_reviewed_bytes_under_autocrlf_checkout(self):
        proofs=['world-hell-hall-batch-terrain.json','world-hell-chest-grants.json','world-hell-field67-step.json','world-field67-item12.json','world-seventh-side-terrain.json','world-rebirth-terrain.json','world-rebirth-script.json','world-continent-bridges.json','world-continent-actor-barriers.json','world-forest101-terrain.json','world-forest101-direction.json']
        tables={}
        for name in proofs:
            p=json.loads((ROOT/'game-data/provenance'/name).read_text(encoding='utf-8'))
            for definition in [p]+([p['encounterGate']]if 'encounterGate'in p else []):
                raw=(ROOT/definition['cpuExpectedPath']).read_bytes()
                self.assertEqual(definition['cpuExpectedSha256'],hashlib.sha256(raw).hexdigest())
                tables[definition['cpuExpectedPath']]=raw
        # The scoped checkpoint recipes use raw JSON-file checksums as well as
        # CPU-table checksums; exercise their real Windows-style checkout too.
        for proof_name in ('world-village3-content.json','world-clinic-content.json','world-continent-bridge-content.json','world-continent-barrier-content.json','world-forest101-content.json','world-forest101-direction-content.json'):
            p=json.loads((ROOT/'game-data/provenance'/proof_name).read_text(encoding='utf-8'))
            pin_path=p['baseExport']['pinPath'];raw=(ROOT/pin_path).read_bytes()
            self.assertEqual(p['baseExport']['pinSha256'],hashlib.sha256(raw).hexdigest());tables[pin_path]=raw
            pin=json.loads(raw);proof_path=pin['iteration']['provenance'];raw=(ROOT/proof_path).read_bytes()
            self.assertEqual(p['baseExport']['provenanceSha256'],hashlib.sha256(raw).hexdigest());tables[proof_path]=raw
        medical=json.loads((ROOT/'game-data/provenance/world-clinic-rules.json').read_text(encoding='utf-8'))
        for prefix in ('revival','care'):
            table_path=medical[prefix+'ExpectedPath'];raw=(ROOT/table_path).read_bytes()
            self.assertEqual(medical[prefix+'ExpectedSha256'],hashlib.sha256(raw).hexdigest());tables[table_path]=raw
        with tempfile.TemporaryDirectory(prefix='fengshen-evidence-checkout-')as td:
            target=Path(td)
            def git(*args):
                return subprocess.run(['git','-C',td,*args],check=True,capture_output=True).stdout
            git('init','--quiet');git('config','core.autocrlf','true')
            (target/'.gitattributes').write_bytes((ROOT/'.gitattributes').read_bytes())
            for name,raw in tables.items():
                p=target/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
            git('add','.');git('-c','user.name=Isolated test','-c','user.email=test@example.invalid','commit','--quiet','-m','Isolated evidence checkout')
            for name in tables:(target/name).unlink()
            git('checkout','--','.')
            for name,raw in tables.items():
                self.assertEqual(raw,(target/name).read_bytes(),name)
                self.assertEqual(raw,git('show','HEAD:'+name),name)

if __name__=='__main__':unittest.main()
