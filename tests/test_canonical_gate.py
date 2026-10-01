import copy
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from forensics.comparison import empty_baseline,publish_canonical
from test_validation import SOURCES,valid_fixture

class CanonicalGateTests(unittest.TestCase):
    def setUp(self):self.comparison={'nonOriginalContent':{'records':[]}}
    def test_synthetic_profile_never_publishes(self):
        with patch('forensics.comparison.save') as write,self.assertRaises(ValueError):publish_canonical(valid_fixture(),SOURCES,self.comparison)
        write.assert_not_called()
    def test_unverified_rows_never_publish(self):
        d=valid_fixture();d.update(profile='verified-baseline',status='READY_FOR_REVIEW')
        with patch('forensics.comparison.save') as write,self.assertRaises(ValueError):publish_canonical(d,SOURCES,self.comparison)
        write.assert_not_called()
    def test_modern_content_only_excluded_at_publish(self):
        d=empty_baseline();d['entities']['items']=[{'id':'item.vip','name':'VIP','sourceRefs':['source.test.fixture']}]
        with patch('forensics.comparison.save') as write:
            publish_canonical(d,SOURCES,self.comparison)
            self.assertEqual(write.call_args_list[0].args[1]['entities']['items'],[])
            self.assertEqual(write.call_args_list[1].args[1]['rejected'][0]['reason'],'NON_ORIGINAL_REFERENCE_CONTENT')
    def test_exclusion_leaving_dangling_reference_blocks_publish(self):
        d=valid_fixture();d.update(profile='verified-baseline',status='READY_FOR_REVIEW');d['entities']['items'][0]['name']='VIP'
        with patch('forensics.comparison.save') as write,self.assertRaises(ValueError):publish_canonical(d,SOURCES,self.comparison)
        write.assert_not_called()
    def test_empty_cannot_claim_ready(self):
        d=empty_baseline();d['status']='READY_FOR_REVIEW'
        with patch('forensics.comparison.save') as write,self.assertRaises(ValueError):publish_canonical(d,SOURCES,self.comparison)
        write.assert_not_called()

if __name__=='__main__':unittest.main()
