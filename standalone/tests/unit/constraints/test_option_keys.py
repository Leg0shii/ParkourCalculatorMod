"""Exact model identities and continuation of the replacement builder."""
import copy
import itertools
import json
import unittest

from parkour_solver.constraints.variants import ConstraintVariants
from parkour_solver.reachability.intervals import reachable_envelopes


def source(n=4, state=None):
    model = dict(ticks=n, position=[-1., 0.], velocity=[0., 0.], magnitude=[1.]*n,
                 friction=[.9]*n, walls=[])
    return ConstraintVariants(dict(supportClearance=0.), dict(position=[1.]*(n+1)),
        dict(events=[dict(jump=0, runTicks=0)]), dict(solids=[[0., 0., -.2, 1., 3., .2]]),
        reachable_envelopes(model, [1.]*(n+1)), dict(through=n, solidIndices=[0]), state=state)


class OptionKeyTests(unittest.TestCase):
    def test_signed_zero_and_nonpreferred_exact_timings_remain_distinct(self):
        owner = source()
        row = dict(mode='Z', offset=1, cmp='GE', rhs=-0.)
        self.assertNotEqual(owner.effective_key([row]), owner.effective_key([dict(row, rhs=0.)]))
        options = list(owner)
        self.assertGreater(len(options), 1)
        self.assertEqual(len(options), len({owner.effective_key(o['rows']) for o in options}))

    def test_interrupted_analysis_keeps_selected_unprocessed_child(self):
        owner = source()
        actual = owner.analyze
        calls = 0
        def interrupted(rows):
            nonlocal calls
            calls += 1
            if calls == 2:
                raise TimeoutError('analysis interrupted')
            return actual(rows)
        owner.analyze = interrupted
        with self.assertRaises(TimeoutError):
            next(owner)
        self.assertIn('selected', owner.state['pending'][-1])
        saved = json.loads(json.dumps(owner.state, allow_nan=False))
        self.assertEqual(list(source(state=saved)), list(source()))

    def test_every_budget_boundary_restores_identical_unfinished_alternatives(self):
        expected = list(source())
        for allowance in (1, 5, 18, 40, 90, 180):
            with self.subTest(allowance=allowance):
                owner = source()
                calls = 0
                def check():
                    nonlocal calls
                    calls += 1
                    if calls > allowance:
                        raise TimeoutError('global budget')
                owner.check = check
                actual = []
                try:
                    actual.extend(owner)
                except TimeoutError:
                    pass
                restored = source(state=json.loads(json.dumps(owner.state, allow_nan=False)))
                self.assertEqual(actual+list(restored), expected)

    def test_old_raw_face_cursors_are_explicitly_rejected(self):
        saved = copy.deepcopy(source().state)
        saved['version'] = 2
        with self.assertRaisesRegex(ValueError, 'Incompatible constraint-generation'):
            source(state=saved)


if __name__ == '__main__':
    unittest.main()

class GlobalBudgetTests(unittest.TestCase):
    def test_more_than_128_alternatives_run_and_resume_under_only_the_global_call_budget(self):
        from unittest.mock import patch
        from parkour_solver.search.repair import solve_contact_branches
        from parkour_solver.search.work import SearchWork
        from tests.support.repair_resume import route
        generated = source(8)
        requests = []
        def invoke(variants, count, state=None):
            work = SearchWork(count)
            def solve(request, *args, **kwargs):
                work.before_solve({})
                requests.append(json.dumps(request['constraints'], sort_keys=True))
                return dict(status='unknown', best=None, termination='done', attempts=[dict(diagnostics=[dict(
                    status='unknown', termination='worker_finished', workerStatus='Infeasible_Problem_Detected')])])
            with patch('parkour_solver.search.repair.find_route', side_effect=solve):
                return solve_contact_branches(route(), [], [], object(), seconds=30, candidate_seconds=.1,
                    retries=0, initial_variants=variants, work=work, resume_state=state,
                    oracle_rejection=True, retry_unresolved=False)
        first = invoke(generated, 140)
        self.assertEqual(len(requests), 140)
        self.assertEqual(first['termination'], 'numerical_call_limit')
        state = json.loads(json.dumps(first['repairState'], allow_nan=False))
        resumed = source(8, state=state['constraintGeneration'])
        second = invoke(resumed, 5, state)
        self.assertEqual(len(requests), 145)
        self.assertEqual(len(set(requests)), 145)
        self.assertEqual(second['termination'], 'numerical_call_limit')
        self.assertEqual(len(second['repairState']['completed']), 145)
