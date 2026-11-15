from tests.support.ordered_route import route
import copy
import threading
import unittest

from parkour_solver.scene.route import prepare_route, schedule
from parkour_solver.search.ordered_route import search_allocations


def outcome(status):
    return {'status': status, 'candidate': {'yaw': [], 'positions': [[.5, .5]]}}


class SearchTests(unittest.TestCase):
    def test_equal_run_allocations_remain_available_for_objective_optimization(self):
        def evaluate(allocation, stage, parent, remaining):
            if allocation == [0, 0] and stage == 'jump':
                return outcome('unknown')
            value = 10 if allocation == [1, 0] else 1
            return {'status': 'feasible', 'candidate': {'yaw': [], 'positions': [], 'objective': value},
                    'plan': {'ticks': 24 + sum(allocation)}}

        result = search_allocations(route(), evaluate, improve=True, rank_equal_cost=True)
        self.assertEqual(result['best']['cost'], 1)
        self.assertEqual(result['best']['allocation'], [1, 0])
        self.assertEqual(result['best']['candidate']['objective'], 10)

    def test_increments_the_first_failing_jump_and_backtracks_at_support_limit(self):
        calls = []

        def evaluate(allocation, stage, parent, remaining):
            calls.append((tuple(allocation), stage))
            if stage == 'run':
                return outcome('blocked' if allocation == [0, 2] else 'feasible')
            if allocation in ([0], [1], [1, 0]):
                return outcome('feasible')
            return outcome('unknown')

        result = search_allocations(route(), evaluate)
        self.assertEqual(result['best']['allocation'], [1, 0])
        self.assertEqual(calls, [((0,), 'jump'), ((0, 0), 'jump'), ((0, 1), 'run'),
                                 ((0, 1), 'jump'), ((0, 2), 'run'), ((1,), 'run'),
                                 ((1,), 'jump'), ((1, 0), 'jump')])
        self.assertEqual(result['backtracks'], 1)
        self.assertFalse(result['minimumProven'])
        self.assertTrue(result['unresolved'])

    def test_unknown_run_backtracks_without_claiming_physical_infeasibility(self):
        def evaluate(allocation, stage, parent, remaining):
            if allocation == [0]:
                return outcome('unknown')
            return outcome('unknown' if stage == 'run' else 'feasible')

        result = search_allocations(route(1), evaluate)
        self.assertEqual(result['status'], 'unknown')
        self.assertEqual([a['allocation'] for a in result['attempts']], [[0], [1]])
        self.assertEqual(len(result['unresolved']), 2)
        self.assertFalse(result['minimumProven'])

    def test_improve_can_trade_a_prior_tick_for_fewer_later_ticks(self):
        r = route()
        r['runBounds'][1][1] = 3

        def evaluate(allocation, stage, parent, remaining):
            if stage == 'run' or len(allocation) == 1:
                return outcome('feasible')
            return outcome('feasible' if allocation in ([0, 3], [1, 0]) else 'unknown')

        first = search_allocations(r, evaluate)
        better = search_allocations(r, evaluate, improve=True)
        self.assertEqual(first['best']['allocation'], [0, 3])
        self.assertEqual(better['best']['allocation'], [1, 0])

    def test_zero_run_witness_attains_nonnegative_lower_bound(self):
        result = search_allocations(route(), lambda *args: outcome('feasible'))
        self.assertEqual(result['best']['allocation'], [0, 0])
        self.assertTrue(result['minimumProven'])

    def test_limits_and_cancel_do_not_leave_a_partial_route(self):
        r = route()
        r['maxTotalRunTicks'] = 0
        result = search_allocations(r, lambda *args: outcome('unknown'))
        self.assertIsNone(result['best'])
        self.assertEqual(len(result['attempts']), 1)
        cancel = threading.Event()
        cancel.set()
        result = search_allocations(r, lambda *args: self.fail('cancelled search evaluated'), cancel_event=cancel)
        self.assertEqual(result['status'], 'cancelled')

    def test_error_stops_instead_of_becoming_an_impossible_schedule(self):
        result = search_allocations(route(), lambda *args: {'status': 'error', 'error': 'missing runtime'})
        self.assertEqual(result['status'], 'error')
        self.assertEqual(result['unresolved'], [])


class ScheduleTests(unittest.TestCase):
    def test_immediate_continuation_and_landing_collision_phase(self):
        r = prepare_route(route())
        plan = schedule(r, [0, 0])
        self.assertEqual([e['jump'] for e in plan['events']], [0, 12])
        self.assertEqual([e['landing'] for e in plan['events']], [12, 24])
        self.assertIn((11, 1, 'landing'), plan['supportChecks'])
        self.assertNotIn((12, 1, 'run'), plan['supportChecks'])
        self.assertEqual([i for i, g in enumerate(plan['grounded']) if g], [0, 12])
        self.assertFalse(any(w['mode'] == 'F' for w in plan['walls']))

    def test_run_ticks_shift_all_later_events_and_require_ground_support(self):
        r = prepare_route(route())
        plan = schedule(r, [1, 2])
        self.assertEqual([e['jump'] for e in plan['events']], [1, 15])
        self.assertEqual([e['landing'] for e in plan['events']], [13, 27])
        self.assertIn((13, 1, 'run'), plan['supportChecks'])
        self.assertIn((14, 1, 'run'), plan['supportChecks'])
        probe = schedule(r, [1, 2], 'run')
        self.assertEqual(probe['ticks'], 15)
        self.assertEqual([i for i, jump in enumerate(probe['jump']) if jump], [1])

    def test_candidates_have_independent_arrays_and_source_is_unchanged(self):
        source = route()
        original = copy.deepcopy(source)
        r = prepare_route(source)
        first, second = schedule(r, [0]), schedule(r, [1])
        first['grounded'][0] = False
        first['walls'][0]['rhs'] = 999
        self.assertTrue(second['grounded'][0])
        self.assertNotEqual(second['walls'][0]['rhs'], 999)
        self.assertEqual(source, original)

    def test_initial_overlap_and_invalid_airtimes_are_rejected(self):
        r = route()
        r['initial']['position'][0] = 3
        with self.assertRaises(ValueError):
            prepare_route(r)
        r = route()
        r['airtimes'] = [12]
        with self.assertRaises(ValueError):
            prepare_route(r)

    def test_unsupported_physics_is_not_silently_ignored(self):
        for field in ('ladderTicks', 'ladderPattern', 'contacts', 'vertical'):
            r = route()
            r[field] = []
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'retiming adapter'):
                prepare_route(r)


if __name__ == '__main__':
    unittest.main()
