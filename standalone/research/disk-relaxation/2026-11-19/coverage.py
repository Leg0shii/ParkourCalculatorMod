"""Acceptance step 4: relaxed-dispatch outcomes across the automatic-search test modules.
Usage (from standalone/): coverage.py <output.json>"""
import collections
import json
import sys
import os
import unittest
sys.path.insert(0, os.getcwd())
from unittest.mock import patch

from parkour_solver.numerics.disk import RelaxedDispatch

modules = ['tests.integration.test_integration', 'tests.unit.search.test_semantic_search',
           'tests.unit.search.test_event_search', 'tests.unit.search.test_anchor_search']
original = RelaxedDispatch.solve
counts = {m: collections.Counter() for m in modules}
current = {'module': None}


def recorded(self, problem, **kwargs):
    result = original(self, problem, **kwargs)
    c = counts[current['module']]
    c['requests'] += 1
    c['backend:' + result.get('backend', '?')] += 1
    if result.get('fallback'):
        c['fallback:' + result['fallback']] += 1
    confirmation = result.get('confirmation')
    if confirmation is not None:
        c['confirmations'] += 1
        c['confirmationIpoptCalls'] += not confirmation['cached']
        c['materialGaps'] += confirmation['material']
    return result


outcome = {}
with patch.object(RelaxedDispatch, 'solve', recorded):
    for module in modules:
        current['module'] = module
        suite = unittest.defaultTestLoader.loadTestsFromName(module)
        result = unittest.TextTestRunner(verbosity=0).run(suite)
        outcome[module] = dict(counts[module], tests=result.testsRun, failures=len(result.failures),
                               errors=len(result.errors), skipped=len(result.skipped))
json.dump(outcome, open(sys.argv[1], 'x'), indent=1)
print(json.dumps(outcome, indent=1))
