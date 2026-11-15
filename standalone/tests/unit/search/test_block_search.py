import copy
import random
import threading
import unittest

from parkour_solver.search.support_profiles import build_transition_graph, compile_block_path, find_blocks, prepare_scene, search_block_paths
from parkour_solver.scene.route import HALF_WIDTH, schedule
from parkour_solver.numerics import prepare, validate


def scene():
    return {'version': 1, 'sceneModel': 'coplanar_support_planes',
            'initial': {'position': [.5, 1, .5], 'velocity': [0, 0, 0]},
            'startBlock': 'start', 'goalBlock': 'goal',
            'blocks': [{'id': 'start', 'bounds': [0, 1, 0, 1], 'top': 1},
                       {'id': 'b', 'bounds': [3, 4, 0, 1], 'top': 1},
                       {'id': 'c', 'bounds': [0, 1, 3, 4], 'top': 1},
                       {'id': 'goal', 'bounds': [7, 8, 0, 1], 'top': 1}],
            'maxJumps': 4, 'maxTotalRunTicks': 2}


class Coefficients:
    def compile(self, route, plan, seconds):
        return {'magnitude': [.3 if j else .1 if g else .02 for g, j in zip(plan['grounded'], plan['jump'])],
                'friction': [.5 if g else .9 for g in plan['grounded']], 'base': [0] * len(plan['jump'])}

    def derive_footprints(self, route, seconds):
        return [[b['bounds'][0] - HALF_WIDTH, b['bounds'][1] + HALF_WIDTH,
                 b['bounds'][2] - HALF_WIDTH, b['bounds'][3] + HALF_WIDTH] for b in route['blocks']]


def accepted(cost=0, ticks=24, objective=0):
    return {'status': 'feasible', 'best': {'cost': cost, 'plan': {'ticks': ticks},
                                        'candidate': {'yaw': [0] * ticks, 'objective': objective}}}


def graph(adjacency, distances):
    return {'adjacency': adjacency, 'distanceToGoal': distances, 'rejectedEdges': []}


class BlockSceneTests(unittest.TestCase):
    def test_unordered_supports_and_fixed_inputs(self):
        request = scene()
        request['blocks'].reverse()
        original = copy.deepcopy(request)
        result = prepare_scene(request)
        self.assertTrue(result['inputs']['force45'])
        self.assertTrue(result['inputs']['sprint'])
        self.assertEqual(request, original)
        for field, value in [('force45', False), ('sprint', False)]:
            request['inputs'] = {field: value}
            with self.assertRaises(ValueError):
                prepare_scene(request)

    def test_unimplemented_geometry_and_duplicate_ids_are_rejected(self):
        for change in ('height', 'boxes', 'airtimes', 'duplicate'):
            with self.subTest(change=change):
                request = scene()
                if change == 'height':
                    request['blocks'][1]['top'] = 1.5
                elif change == 'boxes':
                    request['blocks'][1]['boxes'] = [[3, 4, 0, 1], [5, 6, 0, 1]]
                elif change == 'airtimes':
                    request['airtimes'] = [12]
                else:
                    request['blocks'][1]['id'] = 'start'
                with self.assertRaises(ValueError):
                    prepare_scene(request)

    def test_repeated_visits_compile_distinct_events_without_losing_support_identity(self):
        request = prepare_scene(scene())
        result = compile_block_path(request, ['start', 'b', 'start', 'b', 'goal'])
        self.assertEqual(result['status'], 'derived')
        self.assertEqual(result['route']['airtimes'], [12] * 4)
        self.assertEqual([b['id'] for b in result['route']['blocks']],
                         ['start@0', 'b@1', 'start@2', 'b@3', 'goal@4'])
        self.assertEqual(result['route']['blocks'][1]['bounds'], result['route']['blocks'][3]['bounds'])
        self.assertNotIn('airtimes', request)


class TransitionGraphTests(unittest.TestCase):
    def test_runoff_has_a_smaller_candidate_radius_than_the_jump_to_the_same_height(self):
        request = scene()
        request['sceneModel'] = 'support_planes'
        request['blocks'] = [request['blocks'][0], {'id': 'goal', 'bounds': [5, 6, 0, 1], 'top': .5}]
        topology = build_transition_graph(request, Coefficients())
        options = [topology['transitions'][e] for e in topology['edgeOptions']['start']['goal']]
        self.assertTrue(options)
        self.assertTrue(all(e['profileKind'] == 'ordinary' for e in options))
        self.assertLess(topology['flightBounds']['run_off:4']['radius'], topology['flightBounds']['jump:13']['radius'])
        self.assertTrue(any(r.get('profileKind') == 'run_off' for r in topology['rejectedEdges']))

    def test_equal_duration_profiles_keep_distinct_jump_and_no_jump_coefficients(self):
        request = scene()
        request['sceneModel'] = 'support_planes'
        request['blocks'] = [request['blocks'][0], {'id': 'goal', 'bounds': [1, 2, 0, 1], 'top': .875},
                             {'id': 'step', 'bounds': [1, 2, 0, 1], 'top': 1.5}]
        request = prepare_scene(request)
        topology = build_transition_graph(request, Coefficients())
        fall = topology['flightBounds']['run_off:2']
        jump = topology['flightBounds']['jump:2']
        self.assertLess(fall['magnitude'][0], jump['magnitude'][0])
        self.assertLess(fall['radius'], jump['radius'])
        option = next(topology['transitions'][e] for e in topology['edgeOptions']['start']['goal']
                      if topology['transitions'][e]['profileKind'] == 'run_off')
        compiled = compile_block_path(request, ['start', 'goal'], transitions=[option])
        self.assertEqual(compiled['route']['kinds'], ['walk_off'])
        self.assertEqual(compiled['route']['contactEntries'], [None])
        plan = schedule(compiled['route'], [1])
        self.assertFalse(any(plan['jump']))
        self.assertIn((1, 0), plan['exitChecks'])

    def test_stair_and_ordinary_profiles_remain_separate_options_for_the_same_blocks(self):
        request = scene()
        request['sceneModel'] = 'support_planes'
        request['blocks'] = [request['blocks'][0], {'id': 'goal', 'bounds': [1, 2, 0, 1], 'top': 1.5}]
        request = prepare_scene(request)
        topology = build_transition_graph(request, Coefficients())
        edges = [topology['transitions'][e] for e in topology['edgeOptions']['start']['goal']]
        self.assertEqual(sorted(e['profile']['airtime'] for e in edges), [2, 2, 2, 2, 10])
        stair = next(e for e in edges if e['profileKind'] == 'stair')
        compiled = compile_block_path(request, ['start', 'goal'], transitions=[stair])
        plan = schedule(compiled['route'], [2])
        self.assertEqual(plan['grounded'], [True, True, True, False])
        self.assertEqual(plan['jump'], [False, False, True, False])
        entries = [row for row in plan['walls'] if row['name'] == 'contact_enter:0']
        self.assertEqual({row['t1'] for row in entries}, {3})
        self.assertEqual({row['mode'] for row in entries}, {'X', 'Z'})
        self.assertIn((3, 1, 'landing'), plan['supportChecks'])

    def test_reach_bound_removes_remote_support_without_merging_blocks(self):
        request = scene()
        request['blocks'].append({'id': 'remote', 'bounds': [100, 101, 0, 1], 'top': 1})
        result = build_transition_graph(request, Coefficients())
        self.assertNotIn('remote', result['distanceToGoal'])
        self.assertIn('start', result['adjacency']['start'])
        self.assertIn('b', result['adjacency']['start'])
        self.assertNotIn('goal', result['adjacency']['start'])
        self.assertGreater(result['bound']['movementAfterSupportCheck'], result['bound']['storedSpeedNorm'])

    def test_high_initial_momentum_expands_the_graph_bound(self):
        request = scene()
        rest = build_transition_graph(request, Coefficients())
        request['initial']['velocity'][0] = 2
        moving = build_transition_graph(request, Coefficients())
        self.assertNotIn('goal', rest['adjacency']['start'])
        self.assertIn('goal', moving['adjacency']['start'])

    def test_graph_compilation_errors_and_timeouts_have_different_verdicts(self):
        class FailedCompiler:
            def compile(self, *args):
                raise RuntimeError('model unavailable')

        class SlowCompiler:
            def compile(self, *args):
                raise TimeoutError('budget')

        self.assertEqual(find_blocks(scene(), FailedCompiler())['status'], 'error')
        timed_out = find_blocks(scene(), SlowCompiler())
        self.assertEqual(timed_out['status'], 'unknown')
        self.assertEqual(timed_out['termination'], 'graph_timeout')

    def test_free_facing_witnesses_with_inertia_survive_the_optimistic_edge_test(self):
        rng = random.Random(4128)
        compiler = Coefficients()
        for _ in range(100):
            request = scene()
            request['initial']['velocity'] = [rng.uniform(-.6, .6), 0, rng.uniform(-.6, .6)]
            coefficients = compiler.compile(request, {'grounded': [True] + [False] * 11,
                                                      'jump': [True] + [False] * 11}, 1)
            problem = prepare(dict(coefficients, version=1, ticks=12, position=[.5, .5],
                                   velocity=[request['initial']['velocity'][0], request['initial']['velocity'][2]],
                                   yaw=[rng.uniform(-180, 180) for _ in range(12)], threshold=.005,
                                   objective={'axis': 'X', 'sense': 'MAX', 'tick': 12}))
            witness = validate(problem, problem['yaw'])
            x, z = witness['positions'][11]
            request['blocks'][-1]['bounds'] = [x - .1, x + .1, z - .1, z + .1]
            result = build_transition_graph(request, compiler)
            self.assertIn('goal', result['adjacency']['start'])


class BlockPathSearchTests(unittest.TestCase):
    def test_same_block_route_keeps_different_contact_profiles(self):
        topology = graph({'start': ['goal'], 'goal': []}, {'start': 1, 'goal': 0})
        topology.update(edgeOptions={'start': {'goal': ['bad', 'good', 'ordinary']}},
                        flightInputsToGoal={'start': 2, 'goal': 0},
                        transitions={'bad': {'profile': {'airtime': 2}}, 'good': {'profile': {'airtime': 2}},
                                     'ordinary': {'profile': {'airtime': 10}}})
        calls = []

        def evaluate(path, remaining, transition_ids, max_ticks=None):
            calls.append(tuple(transition_ids))
            return accepted(ticks=2) if transition_ids == ('good',) else {'status': 'unknown'}

        result = search_block_paths(scene(), topology, evaluate)
        self.assertEqual(calls, [('bad',), ('good',)])
        self.assertEqual(result['best']['transitionIds'], ['good'])

    def test_different_arrivals_at_the_same_block_are_not_deduplicated(self):
        topology = graph({'start': ['b', 'c'], 'b': ['goal'], 'c': ['b'], 'goal': []},
                         {'goal': 0, 'b': 1, 'c': 2, 'start': 2})
        calls = []

        def evaluate(path, remaining):
            calls.append(path)
            return {'status': 'unknown'} if path == ['start', 'b', 'goal'] else accepted()

        result = search_block_paths(scene(), topology, evaluate)
        self.assertEqual(result['best']['blockIds'], ['start', 'c', 'b', 'goal'])
        self.assertIn(['start', 'b'], calls)
        self.assertIn(['start', 'c', 'b'], calls)
        self.assertEqual(len(result['unresolvedPrefixes']), 1)

    def test_unknown_prefix_is_extended_and_full_prefix_can_recover(self):
        topology = graph({'start': ['b'], 'b': ['goal'], 'goal': []}, {'start': 2, 'b': 1, 'goal': 0})
        result = search_block_paths(scene(), topology,
                                    lambda path, _: accepted() if path[-1] == 'goal' else {'status': 'unknown'})
        self.assertEqual(result['status'], 'feasible')
        self.assertEqual(result['best']['blockIds'], ['start', 'b', 'goal'])

    def test_same_block_jump_is_available_for_momentum(self):
        topology = graph({'start': ['start', 'goal'], 'goal': []}, {'start': 1, 'goal': 0})
        result = search_block_paths(scene(), topology,
                                    lambda path, _: accepted() if path == ['start', 'start', 'goal']
                                    else {'status': 'unknown'})
        self.assertEqual(result['best']['blockIds'], ['start', 'start', 'goal'])

    def test_graph_disconnection_and_hop_budget_do_not_call_the_solver(self):
        topology = graph({'start': ['b', 'c'], 'b': ['goal'], 'c': ['c'], 'goal': []},
                         {'start': 2, 'b': 1, 'goal': 0})
        request = scene()
        request['maxJumps'] = 1
        result = search_block_paths(request, topology, lambda *_: self.fail('inaccessible path evaluated'))
        self.assertEqual(result['status'], 'unknown')
        self.assertEqual(result['pruning']['horizonPrefixes'], 1)

    def test_candidate_and_pool_limits_are_reported_as_limits(self):
        topology = graph({'start': ['b', 'c'], 'b': ['goal'], 'c': ['goal'], 'goal': []},
                         {'start': 2, 'b': 1, 'c': 1, 'goal': 0})
        request = scene()
        request.update(maxPrefixes=1, maxQueuedPrefixes=1)
        result = search_block_paths(request, topology, lambda *_: {'status': 'unknown'})
        self.assertEqual(result['status'], 'unknown')
        self.assertEqual(result['termination'], 'candidate_limit')
        self.assertGreater(result['pruning']['heuristicPoolDrops'], 0)
        self.assertFalse(result['minimumTicksProven'])

    def test_error_is_not_a_route_miss_and_cancellation_keeps_an_incumbent(self):
        topology = graph({'start': ['goal', 'b'], 'b': ['goal'], 'goal': []},
                         {'start': 1, 'b': 1, 'goal': 0})
        result = search_block_paths(scene(), topology, lambda *_: {'status': 'error', 'error': 'worker'})
        self.assertEqual(result['status'], 'error')
        self.assertEqual(len(result['attempts']), 1)
        cancel = threading.Event()

        def evaluate(path, remaining):
            cancel.set()
            return accepted(1)

        result = search_block_paths(scene(), topology, evaluate, improve=True, cancel_event=cancel)
        self.assertEqual(result['status'], 'feasible')
        self.assertEqual(result['termination'], 'cancelled')
        self.assertEqual(result['best']['cost'], 1)
        self.assertFalse(result['minimumTicksProven'])

    def test_fewer_total_ticks_beat_a_zero_run_route(self):
        topology = graph({'start': ['goal', 'b'], 'b': ['goal'], 'goal': []},
                         {'start': 1, 'b': 1, 'goal': 0})
        topology.update(profiles={'start': {'goal': {'airtime': 12}, 'b': {'airtime': 2}},
                                  'b': {'goal': {'airtime': 2}}},
                        flightInputsToGoal={'start': 4, 'b': 2, 'goal': 0})
        calls = []

        def evaluate(path, remaining):
            calls.append(path)
            return accepted(cost=2, ticks=6, objective=-100) if path == ['start', 'b', 'goal'] else accepted(ticks=12, objective=100)

        result = search_block_paths(scene(), topology, evaluate)
        self.assertEqual(result['best']['blockIds'], ['start', 'b', 'goal'])
        self.assertEqual(result['best']['plan']['ticks'], 6)
        self.assertNotIn(['start', 'goal'], calls)
        self.assertGreater(result['pruning']['tickBoundPrefixes'], 0)

    def test_equal_total_ticks_compare_objective_even_when_run_counts_differ(self):
        topology = graph({'start': ['b', 'c'], 'b': ['goal'], 'c': ['goal'], 'goal': []},
                         {'start': 2, 'b': 1, 'c': 1, 'goal': 0})
        topology.update(profiles={'start': {'b': {'airtime': 2}, 'c': {'airtime': 3}},
                                  'b': {'goal': {'airtime': 2}}, 'c': {'goal': {'airtime': 3}}},
                        flightInputsToGoal={'start': 4, 'b': 2, 'c': 3, 'goal': 0})
        calls = []

        def evaluate(path, remaining):
            calls.append(path)
            if path == ['start', 'b', 'goal']:
                return accepted(cost=2, ticks=6, objective=10)
            return accepted(cost=0, ticks=6, objective=1)

        request = scene()
        result = search_block_paths(request, topology, evaluate)
        self.assertIn(['start', 'c', 'goal'], calls)
        self.assertEqual(result['best']['blockIds'], ['start', 'b', 'goal'])
        request['objective'] = {'axis': 'Z', 'sense': 'MIN'}
        result = search_block_paths(request, topology, evaluate)
        self.assertEqual(result['best']['blockIds'], ['start', 'c', 'goal'])

    def test_unknown_shorter_route_prevents_a_minimum_tick_claim(self):
        topology = graph({'start': ['goal', 'b'], 'b': ['goal'], 'goal': []},
                         {'start': 1, 'b': 1, 'goal': 0})
        topology.update(profiles={'start': {'goal': {'airtime': 4}, 'b': {'airtime': 3}},
                                  'b': {'goal': {'airtime': 3}}},
                        flightInputsToGoal={'start': 4, 'b': 3, 'goal': 0})
        result = search_block_paths(scene(), topology,
                                    lambda path, _: {'status': 'unknown'} if path == ['start', 'goal'] else accepted(ticks=6))
        self.assertEqual(result['best']['plan']['ticks'], 6)
        self.assertEqual(result['ticksLowerBound'], 4)
        self.assertFalse(result['minimumTicksProven'])
        self.assertFalse(result['objectiveOptimalityProven'])


if __name__ == '__main__':
    unittest.main()
