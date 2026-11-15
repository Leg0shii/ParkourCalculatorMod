from tests.support.paths import AUTONOMY_DATA
import copy
import importlib.util
import json
import math
import os
from pathlib import Path
import unittest

from parkour_solver.scene.route import center_bounds, schedule
from research.route_guidance.graph import assignments, build_family, carry_vertices, decisions, edge_path, fixed_values
from parkour_solver.physics.vertical import REST_CARRY
from parkour_solver.numerics.model import prepare, validate


ROOT = AUTONOMY_DATA
NUMERICAL = all(importlib.util.find_spec(m) is not None for m in ('casadi', 'clarabel', 'numpy', 'scipy'))


class Coefficients:
    def derive_footprints(self, route, seconds):
        return [center_bounds(block) for block in route['blocks']]

    derive_standing_footprints = derive_footprints

    def compile(self, route, plan, seconds):
        return dict(magnitude=[.3 if j else .12 if g else .02 for g, j in zip(plan['grounded'], plan['jump'])],
                    friction=[.546 if g else .91 for g in plan['grounded']], base=[0.]*len(plan['jump']))


def ordinary(jumps=1, runs=0):
    return dict(version=1, sceneModel='open_air_supports',
        initial=dict(position=[.5, 1, .5], velocity=[0, REST_CARRY, 0], yaw=0),
        blocks=[dict(id=str(i), bounds=[3*i, 3*i+2, 0, 1], top=1) for i in range(jumps+1)],
        runBounds=[[0, runs] for _ in range(jumps)], maxTotalRunTicks=jumps*runs)


class GuidanceGraphTests(unittest.TestCase):
    def test_phase_flow_preserves_every_small_allocation_and_horizon(self):
        family = build_family(dict(route=ordinary(3, 2), ticks=39), Coefficients())
        selections = list(assignments(family))
        expected = {(a, b, c) for a in range(3) for b in range(3) for c in range(3) if a+b+c == 3}
        self.assertEqual({tuple(s['allocation']) for s in selections}, expected)
        self.assertEqual(len(selections), len(expected))
        for selection in selections:
            path = edge_path(family, selection['allocation'])
            self.assertEqual(len(path), 39)
            self.assertEqual(family['edges'][path[-1]]['target'], family['end'])
        self.assertLess(len(family['edges']), sum(len(edge_path(family, s['allocation'])) for s in selections))

    def test_supports_use_pre_landing_and_run_inputs_without_pinning_jump(self):
        family = build_family(dict(route=ordinary(1, 1), ticks=13), Coefficients())
        path = [family['edges'][i] for i in edge_path(family, [1])]
        self.assertEqual(len(path[0]['guards']), 4)
        self.assertEqual(path[1]['kind'], 'jump')
        self.assertEqual(path[1]['guards'], [])
        self.assertEqual(path[-1]['age'], 11)
        self.assertEqual({r['where'] for r in path[-1]['guards']}, {'in'})
        self.assertTrue(all(not e['guards'] for e in path[2:-1]))

    def test_consecutive_passage_uses_correct_same_or_successive_states(self):
        for axes, first_end, second_start in [(('X', 'Z'), 4, 4), (('Z', 'X'), 4, 5)]:
            avoid = dict(id='corner', leg=0, choices=[4], faces=[
                dict(axis=axes[0], direction=-1, plane=1), dict(axis=axes[1], direction=1, plane=0)])
            family = build_family(dict(route=ordinary(), ticks=12, avoidances=[avoid]), Coefficients())
            rows = {(e['age']+int(r['where'] == 'out'), r['axis'], r['cmp'])
                    for e in family['edges'] for r in e['guards'] if r['option'] is not None}
            a, b = [0 if axis == 'X' else 1 for axis in axes]
            self.assertEqual(max(t for t, axis, cmp in rows if axis == a and cmp == 'LE'), first_end)
            self.assertEqual(min(t for t, axis, cmp in rows if axis == b and cmp == 'GE'), second_start)

    def test_closed_inertia_hull_contains_both_threshold_branches(self):
        for lo, hi in [(-1, 1), (-.005, -.005), (.005, .005), (-.002, .002), (.1, .3), (-.3, -.1)]:
            vertices = carry_vertices(lo, hi, .005)
            self.assertTrue(vertices)
            for value in [lo, hi]:
                carry = value if abs(value) >= .005 else 0
                self.assertIn((value, carry), vertices)
        self.assertEqual(carry_vertices(-.002, .002, .005), [(-.002, 0.), (.002, 0.)])

    def test_decision_fixes_keep_parent_domains_and_reject_unknowns(self):
        family = build_family(dict(route=ordinary(2, 2), ticks=26), Coefficients())
        before = copy.deepcopy(family['bounds'])
        zeros, options = fixed_values(family, {'departure:0': 1})
        self.assertTrue(zeros)
        self.assertEqual(options, [])
        self.assertTrue(all(family['edges'][i]['tick'] != 1 for i in zeros))
        self.assertEqual(family['bounds'], before)
        self.assertEqual(decisions(family)['departure:1'], [14])
        with self.assertRaises(ValueError):
            fixed_values(family, {'departure:0': 99})

    def test_scope_and_horizon_errors_are_explicit(self):
        request = dict(route=ordinary(), ticks=12)
        bad = copy.deepcopy(request)
        bad['route']['constraints'] = [dict(leg=0, mode='X', cmp='LE', rhs=0)]
        with self.assertRaises(ValueError):
            build_family(bad, Coefficients())
        for key, value in [('ticks', 11), ('ticks', 13), ('ladder', True)]:
            bad = dict(request, **{key: value})
            with self.assertRaises(ValueError):
                build_family(bad, Coefficients())


@unittest.skipUnless(NUMERICAL, 'Install route_guidance/requirements.txt')
class GuidanceNumericalTests(unittest.TestCase):
    def test_dual_projection_and_box_residual_correction_cover_known_optimum(self):
        from research.route_guidance.conic import ConeProgram
        import numpy as np
        p = ConeProgram()
        mass, x = p.variable('mass', 1, 1), p.variable('x', -2, 3)
        p.cones.append([{mass: -1}, {x: -1}])
        a, b, q, _ = p.data({x: -1})
        rng = np.random.default_rng(724)
        for _ in range(30):
            upper, residual = p.upper_value(a, b, q, rng.normal(size=len(b)))
            self.assertGreaterEqual(upper, 1-1e-9)
            self.assertGreaterEqual(residual, 0)

    def test_fixed_history_disk_matches_analytic_affine_oracle(self):
        from research.route_guidance.conic import solve_conic
        request = dict(route=ordinary(), ticks=12)
        request['route']['blocks'][-1]['bounds'] = [-20, 20, -20, 20]
        family = build_family(request, Coefficients())
        family['threshold'] = 0.
        result = solve_conic(family)
        self.assertEqual(result['status'], 'solved')
        x, v = .5, 0.
        for edge in family['edges']:
            x += v+edge['magnitude']
            v = edge['friction']*(v+edge['magnitude'])
        self.assertAlmostEqual(result['value'], x, places=7)
        self.assertGreaterEqual(result['upperBound'], x-1e-9)
        self.assertLess(result['upperBound']-x, 1e-6)

    def test_real_witness_lifts_cover_thresholds_and_random_free_facings(self):
        from research.route_guidance.compact import audit_compact_witness
        from research.route_guidance.conic import build_conic
        from research.route_guidance.physical import assignments, lift_witness
        import numpy as np
        rng = np.random.default_rng(142)
        for velocity in [-.005, -.004999999, 0., .004999999, .005]:
            request = dict(route=ordinary(), ticks=12)
            request['route']['initial']['velocity'][0] = velocity
            request['route']['blocks'][-1]['bounds'] = [-20, 20, -20, 20]
            family = build_family(request, Coefficients())
            model = build_conic(family)
            selection = next(assignments(family))
            for sample in range(4):
                yaw = rng.uniform(-180, 180, 12).tolist()
                coefficients = [family['edges'][i] for i in edge_path(family, [0])]
                problem = prepare(dict(version=1, ticks=12, position=[.5, .5], velocity=[velocity, 0],
                    magnitude=[e['magnitude'] for e in coefficients], friction=[e['friction'] for e in coefficients],
                    base=[0.]*12, yaw=yaw, threshold=.005, objective=dict(axis='X', sense='MAX', tick=12)))
                candidate = validate(problem, yaw)
                audit = lift_witness(family, model, selection, candidate)
                self.assertLess(audit['violation'], 1e-8)
                self.assertEqual(audit['initialState'], [.5, .5, velocity, 0.])
                for arm in ('compact_circle', 'compact_disk'):
                    self.assertLess(audit_compact_witness(family, arm, selection, candidate)['violation'], 1e-8)

    def test_conditional_bounds_nest_and_compact_circle_is_in_disk(self):
        from research.route_guidance.conic import build_conic, solve_conic
        from research.route_guidance.compact import solve_compact
        family = build_family(dict(route=ordinary(2, 1), ticks=25), Coefficients())
        parent = solve_conic(family)
        self.assertEqual(parent['status'], 'solved')
        for choice in decisions(family)['departure:0']:
            child = solve_conic(family, {'departure:0': choice})
            if child['status'] == 'solved':
                self.assertLessEqual(child['value'], parent['upperBound']+1e-7)
        circle = solve_compact(family, 'compact_circle', seed=parent)
        disk = solve_compact(family, 'compact_disk', seed=parent)
        self.assertEqual(circle['status'], 'solved')
        self.assertEqual(disk['status'], 'solved')
        self.assertLessEqual(circle['value'], disk['value']+1e-6)
        self.assertAlmostEqual(circle['states'][0][0], .5, places=12)
        with self.assertRaises(ValueError):
            solve_conic(family, {'departure:0': 0}, built=build_conic(family))


@unittest.skipUnless(NUMERICAL and os.environ.get('PKC_RUN_TICKS_COMPILER'),
                     'Install guidance dependencies and set PKC_RUN_TICKS_COMPILER')
class GuidancePhysicalTests(unittest.TestCase):
    def setUp(self):
        from parkour_solver.bridge.compiler import JavaCompiler
        self.compiler = JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text()))
        self.addCleanup(self.compiler.close)

    def test_edge_coefficients_match_complete_authoritative_schedules(self):
        from research.route_guidance.physical import assignments
        for force45 in (False, True):
            for sprint in (False, True):
                raw = ordinary(2, 2)
                raw['inputs'] = dict(force45=force45, sprint=sprint, incomingSprint=False)
                family = build_family(dict(route=raw, ticks=26), self.compiler)
                for selection in assignments(family):
                    plan = schedule(family['route'], selection['allocation'])
                    compiled = self.compiler.compile(family['route'], plan, 3)
                    for t, index in enumerate(edge_path(family, selection['allocation'])):
                        for key in ('magnitude', 'friction', 'base'):
                            self.assertEqual(family['edges'][index][key], compiled[key][t], (key, t, selection))

    def test_guidance_produces_independently_checked_whole_initial_state_path(self):
        from research.route_guidance.conic import build_conic, solve_conic
        from research.route_guidance.guidance import guide_route
        from research.route_guidance.physical import lift_witness
        request = json.loads((ROOT/'experiments/route-guidance/fixtures/open.json').read_text())
        family = build_family(request, self.compiler)
        model = build_conic(family)
        root = solve_conic(family, built=model)
        result = guide_route(family, 'convex_disk', self.compiler, root=root, refine_seconds=2)
        self.assertEqual(result['status'], 'feasible')
        physical = result['refinement']
        self.assertTrue(physical['geometryCheck']['feasible'])
        self.assertEqual(physical['candidate']['positions'][0], [.5, .5])
        self.assertEqual(physical['candidate']['velocity'][0], [0., 0.])
        self.assertLess(lift_witness(family, model, physical['selection'], physical['candidate'])['violation'], 1e-7)
        self.assertGreaterEqual(root['upperBound']+1e-7, physical['value'])

    def test_retaining_two_timings_recovers_the_narrow_support_ranking_counterexample(self):
        from research.route_guidance.conic import build_conic, solve_conic
        from research.route_guidance.guidance import guide_route
        from research.route_guidance.physical import lift_witness
        request = json.loads((ROOT/'experiments/route-guidance/fixtures/narrow-fast-entry.json').read_text())
        family = build_family(request, self.compiler)
        root = solve_conic(family)
        greedy = guide_route(family, 'convex_disk', self.compiler, root=root, refine_seconds=3, beam_width=1)
        beam = guide_route(family, 'convex_disk', self.compiler, root=root, refine_seconds=3, beam_width=2)
        self.assertEqual(greedy['status'], 'feasible')
        self.assertEqual(beam['status'], 'feasible')
        self.assertEqual(greedy['fixed']['departure:0'], 1)
        self.assertEqual(beam['fixed']['departure:0'], 0)
        self.assertGreater(beam['refinement']['value']-greedy['refinement']['value'], .2)
        self.assertTrue(beam['refinement']['geometryCheck']['feasible'])
        self.assertGreaterEqual(root['upperBound'], beam['refinement']['value'])
        self.assertFalse(beam['objectiveOptimalityProven'])
        physical = greedy['refinement']
        pattern = [[0 if abs(v) < family['threshold'] else 1 if v > 0 else -1 for v in row]
                   for row in physical['candidate']['velocity'][:-1]]
        fixed = physical['selection']['fixed']
        model = build_conic(family, fixed, carry_pattern=pattern)
        restricted = solve_conic(family, fixed, built=model)
        self.assertTrue(restricted['carryPatternRestricted'])
        self.assertAlmostEqual(restricted['value'], physical['value'], places=6)
        self.assertLess(lift_witness(family, model, physical['selection'], physical['candidate'])['violation'], 1e-7)


if __name__ == '__main__':
    unittest.main()
