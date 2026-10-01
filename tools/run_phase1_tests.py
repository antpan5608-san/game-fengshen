"""Run the existing unittest suite and persist its actual result; no ROM downloads."""
import sys
import unittest
from forensics.common import ROOT,save

def test_ids(suite):
    for item in suite:
        if isinstance(item,unittest.TestSuite):yield from test_ids(item)
        else:yield item.id()

def main():
    suite=unittest.defaultTestLoader.discover(str(ROOT/'tests'),pattern='test_*.py')
    ids=list(test_ids(suite))
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    report={'command':'./phase1.ps1 test','status':'PASS' if result.wasSuccessful() else 'FAIL',
        'testsRun':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'skipped':len(result.skipped),
        'previousPhase1TestsDiscovered':sum(not x.startswith(('test_vertical_slice.','test_development_package.')) for x in ids),
        'v1TestsDiscovered':sum(x.startswith('test_vertical_slice.') for x in ids),
        'developmentTestsDiscovered':sum(x.startswith('test_development_package.') for x in ids),
        'romDownloadsDuringTests':False,'testIds':ids,
        'failureDetails':[{'id':t.id(),'traceback':message} for t,message in result.failures+result.errors],
        'skippedDetails':[{'id':t.id(),'reason':reason} for t,reason in result.skipped]}
    save(ROOT/'reports/phase1-tests.json',report)
    return 0 if result.wasSuccessful() else 1

if __name__=='__main__':sys.exit(main())
