"""Exercise actual Git checkout with CRLF defaults; retain strict byte hashes."""
import hashlib,json,subprocess,tempfile,unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
class EvidenceCheckoutTests(unittest.TestCase):
    def test_cpu_tables_keep_their_reviewed_bytes_under_autocrlf_checkout(self):
        proofs=['world-hell-hall-batch-terrain.json','world-hell-chest-grants.json','world-hell-field67-step.json','world-field67-item12.json','world-seventh-side-terrain.json','world-rebirth-terrain.json','world-rebirth-script.json','world-continent-bridges.json','world-continent-actor-barriers.json','world-forest101-terrain.json','world-forest101-direction.json','world-tree-contact.json','world-tree107-talk.json','world-tree107-chests.json']
        tables={}
        for name in proofs:
            p=json.loads((ROOT/'game-data/provenance'/name).read_text(encoding='utf-8'))
            for definition in [p]+([p['encounterGate']]if 'encounterGate'in p else []):
                raw=(ROOT/definition['cpuExpectedPath']).read_bytes()
                self.assertEqual(definition['cpuExpectedSha256'],hashlib.sha256(raw).hexdigest())
                tables[definition['cpuExpectedPath']]=raw
        # The scoped checkpoint recipes use raw JSON-file checksums as well as
        # CPU-table checksums; exercise their real Windows-style checkout too.
        for proof_name in ('world-village3-content.json','world-clinic-content.json','world-continent-bridge-content.json','world-continent-barrier-content.json','world-forest101-content.json','world-forest101-direction-content.json','world-tree107-content.json','world-room171-content.json','world-yang-join-content.json','world-village4-content.json','world-ferry-content.json'):
            p=json.loads((ROOT/'game-data/provenance'/proof_name).read_text(encoding='utf-8'))
            pin_path=p['baseExport']['pinPath'];raw=(ROOT/pin_path).read_bytes()
            self.assertEqual(p['baseExport']['pinSha256'],hashlib.sha256(raw).hexdigest());tables[pin_path]=raw
            pin=json.loads(raw);proof_path=pin['iteration']['provenance'];raw=(ROOT/proof_path).read_bytes()
            self.assertEqual(p['baseExport']['provenanceSha256'],hashlib.sha256(raw).hexdigest());tables[proof_path]=raw
        for name in ('world-room171-resources.json','world-teacher171-talk.json','world-yang-join.json'):
            p=json.loads((ROOT/'game-data/provenance'/name).read_text(encoding='utf-8'))
            groups=p['cpuExpected'] if isinstance(p['cpuExpected'],list) else [p['cpuExpected']]
            if 'discipleCpuExpected' in p:groups=groups+[p['discipleCpuExpected']]
            if 'multiplierExpected' in p:groups=groups+[p['multiplierExpected']]
            for group in groups:
                raw=(ROOT/group['path']).read_bytes()
                self.assertEqual(group['sha256'],hashlib.sha256(raw).hexdigest())
                tables[group['path']]=raw
        village=json.loads((ROOT/'game-data/provenance/world-village4-resources.json').read_text(encoding='utf-8'))
        ferry=json.loads((ROOT/'game-data/provenance/world-ferry-original.json').read_text(encoding='utf-8'))
        for path,sha in [(ferry['expected']['path'],ferry['expected']['sha256']),(ferry['expected']['probe'],ferry['expected']['probeSha256'])]:
            raw=(ROOT/path).read_bytes();self.assertEqual(sha,hashlib.sha256(raw).hexdigest());tables[path]=raw
        for kind in ('bridge','talk'):
            definition=village[kind];raw=(ROOT/definition['cpuExpectedPath']).read_bytes()
            self.assertEqual(definition['cpuExpectedSha256'],hashlib.sha256(raw).hexdigest())
            tables[definition['cpuExpectedPath']]=raw
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
