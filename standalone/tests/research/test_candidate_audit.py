from tests.support.paths import AUTONOMY_DATA, STANDALONE_ROOT
import json
from pathlib import Path
import runpy
import unittest


ROOT=AUTONOMY_DATA/'experiments/orchestrator-fixes'
AUDIT=runpy.run_path(str(STANDALONE_ROOT/'tools/audit_candidates.py'))


class CandidateAuditTests(unittest.TestCase):
    def test_saved_cold_calls_reconcile_without_counting_resume_snapshots_twice(self):
        reports=[AUDIT['read'](ROOT/'evidence'/(s+'.json.gz')) for s in AUDIT['SOURCES']]
        ledger=AUDIT['extract'](reports)
        ledger['counts']=AUDIT['metrics'](ledger)
        self.assertEqual(ledger['counts']['mainBranches'],227)
        self.assertEqual(ledger['counts']['mainWorkerCalls'],172)
        self.assertEqual(ledger['counts']['spatialWorkerCalls'],36)
        self.assertEqual(ledger['counts']['totalWorkerCalls'],208)
        self.assertEqual(ledger['counts']['missingContactRecords'],2)
        self.assertEqual(ledger['counts']['mainWorkerStatuses'],{'feasible':172})
        calls=AUDIT['worker_ledger'](ledger)
        self.assertEqual(len(calls),208)
        self.assertEqual(len({r['call'] for r in calls}),208)
        text=AUDIT['render'](ledger)
        for branch in ledger['branches']:
            self.assertIn('| '+branch['label']+' |',text)
        self.assertEqual(sum(b['corridorAudit'] is not None for b in ledger['branches']),62)

    def test_missing_validation_record_keeps_every_possible_alignment(self):
        branches=[dict(branch=json.dumps([[],[],[]]),status='feasible',problemHashes=[str(i)]) for i in range(3)]
        attempts=[dict(status='feasible')]*3
        contacts=[dict(constraintCount=0,geometryCheck=dict(feasible=True))]*2
        align=AUDIT['align_contacts'](branches,attempts,contacts)
        self.assertEqual(align,[{None,0},{None,0,1},{None,1}])

    def test_missing_call_records_are_not_reported_as_ipopt_failures(self):
        contact=dict(status='unknown',termination='search_exhausted')
        self.assertEqual(AUDIT['contact_outcome'](contact),'search_exhausted')
        self.assertEqual(AUDIT['contact_outcome'](None),'validation_record_missing')


if __name__=='__main__':
    unittest.main()
