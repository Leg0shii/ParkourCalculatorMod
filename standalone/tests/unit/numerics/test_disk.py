import copy
import gzip
import json
import math
import os
import pathlib
import unittest
from types import SimpleNamespace
from unittest import mock

from parkour_solver.numerics import disk, prepare, solve, validate
from parkour_solver.numerics.model import fingerprint
from parkour_solver.physics.angles import wrap
from parkour_solver.validation.numerical import validate_controls
from tests.support.numerical_model import problem

FIXTURES = pathlib.Path(__file__).resolve().parents[2]/'fixtures'/'numerics'
RUNTIME = os.environ.get('PKC_IPOPT_PYTHON')


def read(name):
    with gzip.open(FIXTURES/name, 'rt') as stream:
        return json.load(stream)


def full_controls(problem, yaw):
    return [[problem['magnitude'][t]*math.cos(problem['base'][t]+math.radians(yaw[t])),
             problem['magnitude'][t]*math.sin(problem['base'][t]+math.radians(yaw[t]))] for t in range(problem['ticks'])]


def relaxed(problem):
    content = disk.spec(problem, strict=True)
    result = disk.solve(content, seconds=disk.CAP)
    return result, validate_controls(problem, result['controls']) if result['controls'] else None


def request(ticks, magnitude, **fields):
    value = problem(ticks)
    value['magnitude'] = [magnitude]*ticks
    value.update(fields)
    return prepare(value)


class ValidatorParityTests(unittest.TestCase):
    def assert_parity(self, problem, yaw):
        expected = validate(problem, yaw)
        actual = validate_controls(problem, full_controls(problem, yaw))
        for key in set(expected) | set(actual):
            if key not in ('yaw', 'controls'):
                self.assertEqual(actual[key], expected[key], key)
        self.assertLessEqual(max(abs(wrap(a-b)) for a, b in zip(actual['yaw'], expected['yaw'])), 1e-9)

    def test_full_magnitude_controls_replay_exactly_like_their_facings(self):
        checked = 0
        for name in ('real11-200.json.gz', 'real11-400.json.gz', 'real11-200-ladders.json.gz', 'real11-400-ladders.json.gz'):
            p = prepare(read(name))
            self.assert_parity(p, p['yaw'])
            checked += 1
        p = prepare(read('real11-400.json.gz'))
        for yaw in read('real11-400-warm-yaws.json.gz').values():
            self.assert_parity(p, yaw)
            checked += 1
        for row in read('disk-real11.json.gz')['rows']:
            if row['stockYaw'] is not None:
                p = prepare(row['request'])
                self.assert_parity(p, row['stockYaw'])
                self.assertTrue(validate_controls(p, full_controls(p, row['stockYaw']))['feasible'])
                checked += 1
        self.assertEqual(checked, 20)

    def test_the_same_direction_with_less_acceleration_is_a_different_trajectory(self):
        p = request(2, .1)
        full = full_controls(p, [10., 20.])
        half = [[.5*a for a in c] for c in full]
        self.assertEqual(validate_controls(p, full)['yaw'], validate_controls(p, half)['yaw'])
        self.assertNotEqual(validate_controls(p, full)['positions'], validate_controls(p, half)['positions'])

    def test_acceleration_outside_the_disk_is_infeasible(self):
        p = request(2, .1)
        controls = [[.1+1e-6, 0.], [0., .05]]
        checked = validate_controls(p, controls)
        self.assertFalse(checked['feasible'])
        self.assertEqual(checked['worstConstraint'], 'acceleration_disk@0')
        self.assertTrue(validate_controls(p, [[.1, 0.], [0., .05]])['feasible'])
        with self.assertRaises(ValueError):
            validate_controls(p, [[.1, 0.]])


class ArchivedReal11Tests(unittest.TestCase):
    def test_archived_requests_keep_their_verdicts_and_bound_the_stock_optimum(self):
        verdicts = []
        for row in read('disk-real11.json.gz')['rows']:
            p = prepare(row['request'])
            result, candidate = relaxed(p)
            verdicts.append(result['outcome'])
            if row['stockObjective'] is None:
                self.assertEqual(result['outcome'], 'infeasible', row['problemHash'])
                self.assertIn(result['status'], disk.INFEASIBLE)
                continue
            self.assertTrue(candidate['feasible'], row['problemHash'])
            sense = 1 if p['objective']['sense'] == 'MAX' else -1
            self.assertGreaterEqual(sense*candidate['objective'], sense*row['stockObjective']-1e-6, row['problemHash'])
        self.assertEqual((verdicts.count('feasible'), verdicts.count('infeasible')), (14, 4))

    def test_requests_undecided_under_their_objective_are_excluded_by_the_zero_cost_retry(self):
        for row in read('disk-undecided.json.gz')['rows']:
            content = disk.spec(prepare(row['request']), strict=True)
            self.assertEqual(disk._solve(content, disk.CAP)['outcome'], 'undecided', row['capturedId'])
            result = disk.solve(content, seconds=disk.CAP)
            self.assertEqual((result['outcome'], result['retried']), ('infeasible', True), row['capturedId'])
            self.assertIn(result['status'], disk.INFEASIBLE)


@unittest.skipUnless(RUNTIME, 'Set PKC_IPOPT_PYTHON to compare the relaxation with IPOPT')
class IpoptOracleTests(unittest.TestCase):
    def compare(self, p):
        result, candidate = relaxed(p)
        self.assertEqual(result['outcome'], 'feasible')
        self.assertTrue(candidate['feasible'])
        # Seeded with the disk directions, as a production confirmation is.
        full = solve(dict(p, yaw=candidate['yaw']), seconds=5, stop_on_feasible=False, python_executable=RUNTIME)
        self.assertEqual(full['status'], 'feasible')
        sense = 1 if p['objective']['sense'] == 'MAX' else -1
        self.assertGreaterEqual(sense*candidate['objective'], sense*full['candidate']['objective']-1e-7)
        self.assertAlmostEqual(result['objective'], candidate['objective'], delta=1e-9)
        if disk.deficits(p, result['controls'])[0] <= disk.ALARM_FRACTION:
            self.assertAlmostEqual(candidate['objective'], full['candidate']['objective'], delta=1e-6)
        return candidate

    def test_free_single_tick_optimum_is_full_strength(self):
        candidate = self.compare(request(1, .1))
        self.assertAlmostEqual(candidate['objective'], .2, delta=1e-7)

    def test_zero_acceleration(self):
        self.compare(request(3, 0.))

    def test_contact_reset_with_approach_and_reach(self):
        self.compare(request(3, .2, contacts=[dict(state=2, axis=0, plane=.35, direction=1)]))

    def test_equality_and_relative_rows(self):
        self.compare(request(3, .2, walls=[dict(mode='Z', t1=2, cmp='EQ', rhs=.5)]))
        self.compare(request(3, .2, walls=[dict(mode='X', t1=2, t2=1, op='MINUS', cmp='LE', rhs=.15)]))

    def test_minimized_and_custom_yaw_objectives(self):
        self.compare(request(3, .2, objective=dict(axis='Z', sense='MIN', tick=3)))
        self.compare(request(3, .2, objective=dict(axis='X', sense='MAX', tick=3, customYaw=30., type='POSITION')))
        self.compare(request(3, .2, objective=dict(axis='X', sense='MAX', tick=2, customYaw=30., type='MOTION')))


class StartAndStatusTests(unittest.TestCase):
    def test_translated_start_has_the_same_relative_answer(self):
        base, _ = relaxed(request(2, .1))
        moved, _ = relaxed(request(2, .1, position=[100., -50.]))
        self.assertAlmostEqual(moved['objective']-100., base['objective'], delta=1e-9)

    def test_interval_start_is_feasible_only_through_its_interval(self):
        model = dict(ticks=1, position=[0., 0.], velocity=[0., 0.], magnitude=[.1], friction=[.5], threshold=0.,
                     walls=[dict(mode='X', t1=1, cmp='GE', rhs=1.)], contacts=[])
        self.assertEqual(disk.solve(disk.spec(model, strict=True), seconds=disk.CAP)['outcome'], 'infeasible')
        model['initialIntervals'] = [[.85, -.1], [.95, .1], [0., 0.], [0., 0.]]
        self.assertEqual(disk.solve(disk.spec(model, strict=True), seconds=disk.CAP)['outcome'], 'feasible')

    def test_unreachable_landing_box_is_primal_infeasible(self):
        result, candidate = relaxed(request(2, .1, walls=[dict(mode='X', t1=2, cmp='GE', rhs=5.)]))
        self.assertEqual((result['outcome'], candidate), ('infeasible', None))
        self.assertIn(result['status'], disk.INFEASIBLE)

    def test_undecided_is_solved_again_and_decided_results_are_cached(self):
        content = disk.spec(request(4, .13, walls=[dict(mode='Z', t1=3, cmp='LE', rhs=.3)]), strict=True)
        with mock.patch.object(disk, 'MAX_ITER', 1):
            first = disk.solve(content, seconds=disk.CAP)
            second = disk.solve(content, seconds=disk.CAP)
        self.assertEqual((first['outcome'], first['cached'], second['cached']), ('undecided', False, False))
        decided = disk.solve(content, seconds=disk.CAP)
        self.assertEqual((decided['outcome'], decided['cached']), ('feasible', False))
        self.assertTrue(disk.solve(copy.deepcopy(content), seconds=disk.CAP)['cached'])

    def test_unsupported_requests_have_no_spec(self):
        self.assertIsNone(disk.spec(request(2, .1, threshold=.005), strict=True))
        self.assertIsNone(disk.spec(request(2, .1, walls=[dict(mode='F', t1=0, cmp='EQ', rhs=90.)]), strict=True))
        screened = disk.spec(request(2, .1, walls=[dict(mode='F', t1=0, cmp='EQ', rhs=90.)]), strict=False)
        self.assertEqual(screened['rows'], [])


class DispatchTests(unittest.TestCase):
    def dispatch(self, problem, *, promoted=False, stop_on_feasible=False):
        calls, incumbents = [], []
        def full(request, **kwargs):
            calls.append(kwargs['stop_on_feasible'])
            return dict(status='unknown', feasible=False, candidate=None, termination='worker_finished',
                        workerLaunched=True, workerStatus='Solve_Succeeded')
        with mock.patch('parkour_solver.numerics.solver.solve', side_effect=full):
            result = disk.RelaxedDispatch(promoted=promoted, gate=SimpleNamespace(cache={})).solve(
                problem, seconds=1., stop_on_feasible=stop_on_feasible, on_incumbent=incumbents.append)
        return result, calls, incumbents

    def test_a_relaxed_candidate_is_published_once_without_a_worker(self):
        p = request(3, .2, walls=[dict(mode='Z', t1=2, cmp='LE', rhs=.1)])
        result, calls, incumbents = self.dispatch(p)
        self.assertEqual((result['status'], result['backend'], result['termination'], result['workerLaunched']),
                         ('feasible', 'clarabel', 'disk_finished', False))
        self.assertEqual(calls, [])
        candidate = result['candidate']
        self.assertEqual(candidate['model'], 'relaxed_disk')
        self.assertEqual(candidate['positions'], validate_controls(p, candidate['controls'])['positions'])
        self.assertEqual(incumbents, [candidate])
        self.assertEqual(result['problemHash'], fingerprint(p))

    def test_relaxed_infeasible_requests_are_unknown_without_ipopt(self):
        result, calls, _ = self.dispatch(request(2, .1, walls=[dict(mode='X', t1=2, cmp='GE', rhs=5.)]))
        self.assertEqual((result['status'], result['termination'], result['candidate'], calls),
                         ('unknown', 'disk_finished', None, []))
        self.assertIn(result['workerStatus'], disk.INFEASIBLE)

    def test_ipopt_decides_unsupported_promoted_undecided_and_rejected_requests(self):
        result, calls, _ = self.dispatch(request(2, .1, threshold=.005))
        self.assertEqual((result['backend'], result['fallback'], calls), ('ipopt', 'unsupported', [False]))
        result, calls, _ = self.dispatch(request(2, .1), promoted=True)
        self.assertEqual((result['backend'], result['fallback'], calls), ('ipopt', 'promoted', [False]))
        result, calls, _ = self.dispatch(request(2, .1), promoted=True, stop_on_feasible=True)
        self.assertEqual((result['backend'], calls), ('clarabel', []))
        with mock.patch.object(disk, 'MAX_ITER', 1):
            result, calls, _ = self.dispatch(request(4, .17, walls=[dict(mode='X', t1=3, cmp='LE', rhs=.6)]),
                                             stop_on_feasible=True)
        self.assertEqual((result['backend'], result['fallback'], calls), ('ipopt', 'relaxed_undecided', [True]))
        outside = dict(status='Solved', outcome='feasible', controls=[[.2, 0.], [0., 0.]], objective=0., iterations=1,
                       cached=False)
        with mock.patch.object(disk, 'solve', return_value=outside):
            result, calls, _ = self.dispatch(request(2, .1))
        self.assertEqual((result['backend'], result['fallback'], calls), ('ipopt', 'rejected_relaxed_point', [False]))


def analytic(name):
    """Detector controls with known answers (acceleration-detector study, c9deef4)."""
    base = dict(version=1, position=[0., 0.], velocity=[0., 0.], friction=[.91]*2, base=[0.]*2, threshold=0.,
                contacts=[], stopOnFeasible=False, seconds=5., objective=dict(axis='X', tick=2, sense='MAX'))
    if name == 'true-gap':
        # Z is pinned and X capped at 0: the disk optimum stands still, full strength must move backwards.
        return prepare(dict(base, ticks=1, magnitude=[1.], friction=[.91], base=[0.], yaw=[180.],
            walls=[dict(mode='Z', t1=1, cmp='EQ', rhs=0.), dict(mode='X', t1=1, cmp='LE', rhs=0.)],
            objective=dict(axis='X', tick=1, sense='MAX')))
    if name == 'false-alarm':
        # The first tick's X is pinned; any Z acceleration scores the same.
        return prepare(dict(base, ticks=2, magnitude=[1., 1.], yaw=[90., 0.],
                            walls=[dict(mode='X', t1=1, cmp='EQ', rhs=0.)]))
    return prepare(dict(base, ticks=2, magnitude=[1., 1.], yaw=[0., 0.], walls=[]))


@unittest.skipUnless(RUNTIME, 'Set PKC_IPOPT_PYTHON to confirm alarms with IPOPT')
class ConfirmationTests(unittest.TestCase):
    def dispatch(self, problem, *, gate=None, stop_on_feasible=False):
        import parkour_solver.numerics.solver as full
        calls, incumbents = [], []
        original = full.solve
        def counted(request, **kwargs):
            calls.append(kwargs['stop_on_feasible'])
            return original(request, **dict(kwargs, python_executable=RUNTIME))
        dispatch = disk.RelaxedDispatch(promoted=False, gate=gate or SimpleNamespace(cache={}))
        with mock.patch.object(full, 'solve', side_effect=counted):
            result = dispatch.solve(problem, seconds=5., stop_on_feasible=stop_on_feasible,
                                    on_incumbent=incumbents.append)
        return dispatch, result, calls, incumbents

    def test_a_genuine_gap_is_confirmed_and_promotes_the_lineage(self):
        dispatch, result, calls, incumbents = self.dispatch(analytic('true-gap'))
        confirmation = result['confirmation']
        self.assertEqual((result['backend'], result['status'], calls), ('ipopt', 'feasible', [False]))
        self.assertNotIn('model', result['candidate'])
        self.assertEqual(incumbents, [result['candidate']])
        self.assertEqual(confirmation['ipoptObjective'], result['candidate']['objective'])
        self.assertEqual(confirmation['gap'], confirmation['diskObjective']-result['candidate']['objective'])
        self.assertGreater(confirmation['gap'], disk.MATERIAL_GAP)
        self.assertTrue(confirmation['material'])
        self.assertTrue(dispatch.promoted)
        self.assertEqual([p['gap'] for p in dispatch.promotions], [confirmation['gap']])

    def test_a_false_alarm_is_confirmed_without_promotion(self):
        dispatch, result, calls, _ = self.dispatch(analytic('false-alarm'))
        self.assertGreater(result['disk']['peakRelativeDeficit'], disk.ALARM_FRACTION)
        self.assertEqual((result['backend'], result['status'], calls), ('ipopt', 'feasible', [False]))
        self.assertLessEqual(result['confirmation']['gap'], disk.MATERIAL_GAP)
        self.assertFalse(dispatch.promoted)

    def test_full_strength_optima_and_feasibility_requests_never_call_ipopt(self):
        _, result, calls, _ = self.dispatch(analytic('no-alarm'))
        self.assertEqual((result['backend'], calls), ('clarabel', []))
        dispatch, result, calls, _ = self.dispatch(analytic('true-gap'), stop_on_feasible=True)
        self.assertEqual((result['backend'], result['candidate']['model'], calls), ('clarabel', 'relaxed_disk', []))
        self.assertGreater(result['disk']['peakRelativeDeficit'], disk.ALARM_FRACTION)
        self.assertFalse(dispatch.promoted)

    def test_a_minimized_gap_keeps_its_sign(self):
        p = analytic('true-gap')
        p['walls'][1].update(cmp='GE')
        p['objective']['sense'] = 'MIN'
        p['yaw'] = [0.]
        _, result, _, _ = self.dispatch(p)
        confirmation = result['confirmation']
        self.assertEqual(confirmation['gap'], result['candidate']['objective']-confirmation['diskObjective'])
        self.assertTrue(confirmation['material'])

    def test_a_repeated_alarm_is_confirmed_once_per_run(self):
        gate = SimpleNamespace(cache={})
        _, first, calls, _ = self.dispatch(analytic('false-alarm'), gate=gate)
        _, second, more, incumbents = self.dispatch(analytic('false-alarm'), gate=gate)
        self.assertEqual((calls, more), ([False], []))
        self.assertEqual((first['confirmation']['cached'], second['confirmation']['cached']), (False, True))
        self.assertFalse(second['workerLaunched'])
        self.assertEqual(second['candidate'], first['candidate'])
        self.assertEqual(incumbents, [second['candidate']])


class RejectedConfirmationTests(unittest.TestCase):
    def test_an_ipopt_rejection_governs_the_alarmed_request(self):
        rejected = dict(status='unknown', feasible=False, candidate=None, termination='worker_finished',
                        workerLaunched=True, workerStatus='Infeasible_Problem_Detected')
        with mock.patch('parkour_solver.numerics.solver.solve', return_value=rejected):
            dispatch = disk.RelaxedDispatch(promoted=False, gate=SimpleNamespace(cache={}))
            result = dispatch.solve(analytic('true-gap'), seconds=1., stop_on_feasible=False)
        self.assertEqual((result['status'], result['termination'], result['workerStatus'], result['candidate']),
                         ('unknown', 'worker_finished', 'Infeasible_Problem_Detected', None))
        self.assertEqual((result['confirmation']['gap'], dispatch.promoted), (None, False))


if __name__ == '__main__':
    unittest.main()
