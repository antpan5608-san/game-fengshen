"""Release gate fixtures only; no synthetic fixture is App acceptance evidence."""
import copy
import unittest
from tests.test_room28_personal_scope import Room28PersonalScopeTest
from tools import runtime_handoff as handoff, field_magic_evidence as magic


class FieldMagicPersonalScopeTest(unittest.TestCase):
    def setUp(self):
        self.fixture = Room28PersonalScopeTest()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.scope = copy.deepcopy(self.fixture.scope)
        self.scope['fieldMagicAcceptance'] = magic.ACCEPTANCE
        self.scope['personalTest']['gates'] += magic.GATES
        self.fixture.write_scope(self.scope)
        self.candidate = dict(self.fixture.candidate, versionCode=89)
        self.proposed = dict(self.candidate,
            **{k: 'PASS' for k in handoff.personal_gates(self.scope)},
            **{k: '1'*64 for k in (*handoff.C61_PROOF_KEYS, handoff.battle_ui.UI_PROOF_KEY,
                                   *handoff.room28.PROOF_KEYS, *magic.PROOF_KEYS)})

    def test_new_candidate_keeps_all_historical_gates_and_requires_new_proofs(self):
        self.assertEqual(31, len(handoff.personal_gates(handoff.active_scope(self.candidate))))
        receipt = handoff.finish_personal(self.proposed)
        handoff.review_personal(receipt)
        for key in (*magic.GATES, *magic.PROOF_KEYS):
            broken = dict(self.proposed)
            broken.pop(key)
            with self.assertRaises(ValueError, msg=key):
                handoff.finish_personal(broken)

    def test_current_spell_release_cannot_drop_acceptance_even_with_a_rehashed_scope(self):
        broken = copy.deepcopy(self.scope)
        broken.pop('fieldMagicAcceptance')
        broken['personalTest']['gates'] = [g for g in broken['personalTest']['gates'] if g not in magic.GATES]
        self.fixture.write_scope(broken)
        with self.assertRaisesRegex(ValueError, 'field magic'):
            handoff.active_scope(self.candidate)

    def test_old_scope_remains_valid_for_its_actual_historical_version(self):
        self.fixture.write_scope(self.fixture.scope)
        self.assertEqual(29, len(handoff.personal_gates(handoff.active_scope(self.fixture.candidate))))

    def test_missing_raw_app_evidence_never_returns_a_proof_digest(self):
        with self.assertRaises(ValueError):
            magic.proof_digests(self.fixture.folder)
