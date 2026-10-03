"""Original CPU output integrity and ROM spans; not normal Android acceptance."""
import hashlib,json,sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import export_development as ex

class Field67EvidenceTests(unittest.TestCase):
    def test_unmodified_original_step_and_audio_request_spans_and_expected_outputs(self):
        p=json.loads((ex.ROOT/'game-data/provenance/world-hell-field67-step.json').read_text(encoding='utf-8'))
        r=ex.iteration_reader()
        self.assertEqual(hashlib.sha256(r.data).hexdigest(),p['romSha256'])
        for source in p['sources']:
            raw=r.read(source['module'],source['cpuAddress'],source['length'])
            self.assertEqual(hashlib.sha256(raw).hexdigest(),source['sha256'])
            self.assertEqual(raw,r.data[source['offset']:source['offset']+source['length']])
        raw=(ex.ROOT/p['cpuExpectedPath']).read_bytes()
        self.assertEqual(hashlib.sha256(raw).hexdigest(),p['cpuExpectedSha256'])
        rows=[list(map(int,line.split('\t')))for line in raw.decode().splitlines()]
        self.assertEqual(3840,len(rows));self.assertEqual(3840,len({tuple(v[:6])for v in rows}))
        for mid,pending,active,status,hp,count,after,state,active_after,failure,failure2,audio in rows:
            poison_hp=max(0,hp-1)if status&2 else hp
            poison_state=32 if hp>0 and status&2 and poison_hp==0 else status
            protected=mid==67 and (pending or active)
            expected=max(0,poison_hp-10)if mid==67 and not protected and not poison_state&64 else poison_hp
            expected_state=32 if poison_hp>0 and expected==0 else poison_state
            self.assertEqual((expected,expected_state),(after,state))
            self.assertEqual(int(bool(state&96)),failure)
            self.assertEqual(failure,failure2)
            self.assertEqual(1 if mid==67 and pending else active,active_after)
        self.assertEqual({23,60,66,67,68},{v[0]for v in rows})
        self.assertTrue(p['rules']['sourceMapBeforeExitDispatch'])
        self.assertTrue(p['remainingUnknown'])

if __name__=='__main__':unittest.main()
