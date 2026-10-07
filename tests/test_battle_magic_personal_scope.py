"""Synthetic release gates do not prove App behavior or publication."""
import copy
import unittest
from tests.test_field_magic_personal_scope import FieldMagicPersonalScopeTest
from tools import runtime_handoff as h, battle_magic_evidence as magic


class BattleMagicPersonalScopeTest(unittest.TestCase):
    def setUp(self):
        self.fixture = FieldMagicPersonalScopeTest()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.scope = copy.deepcopy(self.fixture.scope)
        self.scope['battleMagicAcceptance'] = magic.ACCEPTANCE
        self.scope['personalTest']['gates'] += magic.GATES
        self.fixture.fixture.write_scope(self.scope)
        self.candidate = dict(self.fixture.candidate, versionCode=90)
        self.proposed = dict(self.fixture.proposed, versionCode=90,
            **{k: 'PASS' for k in magic.GATES}, **{k: '1'*64 for k in magic.PROOF_KEYS})

    def test_all_thirty_one_old_gates_and_actual_battle_proofs_required(self):
        self.assertEqual(33, len(h.personal_gates(h.active_scope(self.candidate))))
        h.review_personal(h.finish_personal(self.proposed))
        for key in (*magic.GATES, *magic.PROOF_KEYS):
            broken = dict(self.proposed)
            broken.pop(key)
            with self.assertRaises(ValueError, msg=key):
                h.finish_personal(broken)

    def test_current_release_cannot_rehash_away_battle_acceptance(self):
        broken = copy.deepcopy(self.scope)
        broken.pop('battleMagicAcceptance')
        broken['personalTest']['gates'] = [g for g in broken['personalTest']['gates'] if g not in magic.GATES]
        self.fixture.fixture.write_scope(broken)
        with self.assertRaisesRegex(ValueError, 'battle magic'):
            h.active_scope(self.candidate)

    def test_actual_historical_v89_retains_its_thirty_one_gates(self):
        self.fixture.fixture.write_scope(self.fixture.scope)
        self.assertEqual(31, len(h.personal_gates(h.active_scope(self.fixture.candidate))))

    def test_missing_raw_battle_artifacts_never_produce_a_proof(self):
        with self.assertRaises(ValueError):
            magic.proof_digests(self.fixture.fixture.folder)
