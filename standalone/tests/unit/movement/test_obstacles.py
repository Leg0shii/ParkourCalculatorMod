from tests.support.paths import AUTONOMY_DATA, RESEARCH_ROOT
import copy
import json
from pathlib import Path
import unittest

from parkour_solver.search.support_profiles import build_transition_graph, compile_block_path, prepare_scene
from parkour_solver.movement.profiles import normal_jump_height
from parkour_solver.constraints.avoidance import DEFAULT_CAPS, blockage_counts, blockage_usage, compile_passages, faces, gate_timeline, outside, passage_options, passage_y_valid, within_caps
from parkour_solver.scene.geometry import BODY_HEIGHT, expanded_box
from parkour_solver.scene.route import HALF_WIDTH, prepare_route, schedule
from parkour_solver.search.ranking import passage_seed
from tests.unit.search.test_block_search import Coefficients
from parkour_solver.numerics import prepare


FIXTURES = AUTONOMY_DATA / 'fixtures'


def scene():
    return json.loads((FIXTURES / 'obstacle-wide-neo.json').read_text())


def passage_case():
    request = prepare_scene(scene())
    compiled = compile_block_path(request, ['start', 'goal'])
    route, profile = compiled['route'], compiled['profiles'][0]
    plan = schedule(route, [0])
    candidate = {'positions': [[.5 + 3 * t / 12, .5] for t in range(13)]}
    bound = {'storedSpeedNorm': .5, 'movementAfterSupportCheck': .8,
             'groundMagnitude': .13, 'groundFriction': .546}
    return request, route, profile, plan, candidate, bound


class ObstacleGeometryTests(unittest.TestCase):
    def test_collision_boxes_derive_supports_and_survive_normalization_without_mutation(self):
        request = scene()
        original = copy.deepcopy(request)
        result = prepare_scene(request)
        self.assertEqual(request, original)
        self.assertEqual(result, prepare_scene(result))
        self.assertEqual(result['collisionScene']['solids'][1], request['blocks'][1]['boxes'][0])
        self.assertEqual(result['blocks'][1]['top'], 4)
        self.assertEqual(result['blocks'][1]['solidIndex'], 1)

    def test_multipart_supports_remain_separate_with_their_actual_gap(self):
        request = scene()
        request['blocks'][1]['boxes'].append([5, 0, .25, 6, 4, .75])
        result = prepare_scene(request)
        parts = [b for b in result['blocks'] if b['sourceBlock'] == 'wall']
        self.assertEqual([b['id'] for b in parts], ['wall#0', 'wall#1'])
        self.assertLess(parts[0]['bounds'][1], parts[1]['bounds'][0])
        self.assertEqual(len(result['collisionScene']['solids']), 4)

    def test_geometry_requires_vertical_bounds_coverage_and_consistent_supports(self):
        for change in ('missing_bottom', 'outside', 'bad_support', 'unsupported_medium', 'incoming_hit'):
            with self.subTest(change=change):
                request = scene()
                if change == 'missing_bottom':
                    request['blocks'][1]['boxes'] = [[1, 2, 3, 4]]
                elif change == 'outside':
                    request['geometryBounds'][3] = 1
                elif change == 'incoming_hit':
                    request['initial']['previousHorizontalCollision'] = True
                else:
                    request = prepare_scene(request)
                    if change == 'bad_support':
                        request['blocks'][1]['top'] = 1
                    else:
                        request['collisionScene']['fluids'] = [[0, 0, 0]]
                with self.assertRaises(ValueError):
                    prepare_scene(request)

    def test_jump_height_split_removes_high_landing_edges_but_preserves_the_solid(self):
        request = prepare_scene(scene())
        topology = build_transition_graph(request, Coefficients())
        self.assertNotIn('wall', topology['adjacency']['start'])
        self.assertIn('goal', topology['adjacency']['start'])
        self.assertIn({'source': 'start', 'target': 'wall', 'reason': 'profile_not_generated'}, topology['rejectedEdges'])
        self.assertEqual(len(request['collisionScene']['solids']), 3)
        self.assertAlmostEqual(normal_jump_height(), 1.2491870787446813)

    def test_body_clearance_uses_the_head_and_the_actual_bottom(self):
        box = expanded_box([1, 0, 1, 2, 4, 2])
        self.assertEqual(box, [1 - HALF_WIDTH, -BODY_HEIGHT, 1 - HALF_WIDTH,
                               2 + HALF_WIDTH, 4, 2 + HALF_WIDTH])
        under = {'axis': 'Y', 'direction': -1, 'plane': box[1]}
        self.assertFalse(outside(under, [0, -1, 0]))
        self.assertTrue(outside(under, [0, -2, 0]))


class BlockageTests(unittest.TestCase):
    def test_default_caps_and_tightening_are_per_physical_block(self):
        result = prepare_scene(scene())
        self.assertEqual(result['maxBlockagesPerObstacle'], 3)
        self.assertEqual(result['maxHorizontalBlockagesPerObstacle'], 2)
        self.assertEqual(result['maxVerticalBlockagesPerObstacle'], 2)
        for name, maximum in [('maxBlockagesPerObstacle', 3), ('maxHorizontalBlockagesPerObstacle', 2),
                              ('maxVerticalBlockagesPerObstacle', 2)]:
            request = scene()
            request[name] = maximum + 1
            with self.assertRaises(ValueError):
                prepare_scene(request)

    def test_horizontal_vertical_and_combined_limits_all_apply(self):
        def chain(axes):
            return [{'axis': a, 'direction': 1, 'plane': 1} for a in axes]

        def passage(axes, block='A', solid=1):
            return {'leg': 0, 'blockId': block, 'solidIndex': solid, 'faces': chain(axes),
                    'gates': list(range(len(axes) - 1))}

        self.assertEqual(blockage_counts(chain('XZYX')), {'horizontal': 1, 'vertical': 2, 'total': 3})
        self.assertTrue(within_caps([passage('XZYX')], DEFAULT_CAPS))
        self.assertTrue(within_caps([passage('XZX'), passage('ZXZ', 'B', 2)], DEFAULT_CAPS))
        self.assertFalse(within_caps([passage('XZXZ')], DEFAULT_CAPS))
        self.assertFalse(within_caps([passage('XYXY')], DEFAULT_CAPS))
        self.assertFalse(within_caps([passage('XZX'), passage('XYX', solid=2)], DEFAULT_CAPS))

    def test_multipart_blocks_share_the_cap_across_separate_passages_and_legs(self):
        chain = [{'axis': axis, 'direction': 1, 'plane': 1} for axis in 'XZX']
        first = {'leg': 0, 'blockId': 'wall', 'solidIndex': 1, 'faces': chain, 'gates': [2, 5]}
        second = {'leg': 1, 'blockId': 'wall', 'solidIndex': 2, 'faces': chain[:2], 'gates': [3]}
        self.assertEqual(blockage_usage([first, second])[('block', 'wall')], {'horizontal': 3, 'vertical': 0, 'total': 3})
        self.assertFalse(within_caps([first, second], DEFAULT_CAPS))
        second['blockId'] = 'another_wall'
        self.assertTrue(within_caps([first, second], DEFAULT_CAPS))

    def test_generator_keeps_mixed_three_blockages_and_respects_all_three_caps(self):
        _, route, profile, plan, witness, bound = passage_case()
        result = passage_options([1.7, 0, .25, 2, 1.7, .75], 1, 0, profile, route, plan, witness, bound,
                                 max_candidates=1024)
        options = [item['passage'] for item in result['options']]
        self.assertTrue(any(p['blockageCounts']['total'] == 3 for p in options))
        self.assertTrue(all(within_caps([p], DEFAULT_CAPS) for p in options))
        self.assertGreater(result['budgetPruned'], 0)

    def test_interleaved_blocks_keep_independent_intervals_on_one_retimed_timeline(self):
        _, route, profile, _, _, _ = passage_case()
        route['runBounds'][0] = [0, 2]
        route['maxTotalRunTicks'] = 2
        route['passages'] = [
            {'leg': 0, 'blockId': 'A', 'solidIndex': 1,
             'faces': [{'axis': 'X', 'direction': -1, 'plane': 1.4},
                       {'axis': 'Z', 'direction': -1, 'plane': -.05},
                       {'axis': 'X', 'direction': 1, 'plane': 2.3}], 'gates': [4, 9]},
            {'leg': 0, 'blockId': 'B', 'solidIndex': 2,
             'faces': [{'axis': 'X', 'direction': -1, 'plane': 1.84},
                       {'axis': 'Y', 'direction': -1, 'plane': 2.05}], 'gates': [7]}]
        route = prepare_route(route)
        for runs in (0, 2):
            plan = schedule(route, [runs])
            timeline = gate_timeline(route['passages'], plan['events'])
            self.assertEqual([g['blockId'] for g in timeline], ['A', 'B', 'A'])
            self.assertEqual([g['firstState'] for g in timeline], [runs + 4, runs + 7, runs + 9])
            self.assertEqual([g['secondState'] for g in timeline], [runs + 4, runs + 8, runs + 10])
            self.assertEqual([g['kind'] for g in timeline], ['horizontal', 'vertical', 'horizontal'])
            rows = [r for r in plan['walls'] if r['t1'] == runs + 7 and r['name'].startswith('blockage:')]
            self.assertEqual({r['name'] for r in rows}, {'blockage:1', 'blockage:2'})
            self.assertTrue(all(passage_y_valid(p, plan['events'][0], profile) for p in route['passages']))

    def test_refining_one_part_reserves_the_other_parts_budget_without_resetting_the_block(self):
        _, route, profile, plan, witness, bound = passage_case()
        route['passages'] = [{'leg': 0, 'blockId': 'wall', 'solidIndex': 2,
                              'faces': [{'axis': axis, 'direction': 1, 'plane': 1} for axis in 'XZX'],
                              'gates': [2, 5]}]
        result = passage_options([2.2, 3.85, -2, 2.8, 5, 2], 1, 0, profile, route, plan, witness, bound,
                                 max_candidates=512, block_id='wall')
        self.assertTrue(result['options'])
        self.assertTrue(all(item['passage']['blockageCounts']['horizontal'] == 0 for item in result['options']))
        self.assertTrue(all(within_caps(route['passages'] + [item['passage']], DEFAULT_CAPS) for item in result['options']))

    def test_later_passage_seed_keeps_the_earlier_prefix_guess_and_adds_no_facing_pins(self):
        _, route, _, _, _, _ = passage_case()
        route['blocks'].insert(0, {'id': 'earlier', 'bounds': [-3, -2, 0, 1], 'top': 1})
        route['initial']['position'] = [-2.5, 1, .5]
        route['airtimes'] = [12, 12]
        route['runBounds'] = [[0, 0], [0, 0]]
        route.pop('kinds')
        route.pop('landingPhases')
        route.pop('contactEntries')
        route.pop('walkOffExits')
        route['passages'] = [{'leg': 1, 'solidIndex': 1,
                              'faces': [{'axis': 'X', 'direction': -1, 'plane': 1.4},
                                        {'axis': 'Z', 'direction': 1, 'plane': 1.05}], 'gates': [5]}]
        route = prepare_route(route)
        plan = schedule(route, [0, 0])
        coefficients = Coefficients().compile(route, plan, 1)
        problem = prepare(dict(coefficients, version=1, ticks=24, position=[-2.5, .5], velocity=[0, 0],
                               yaw=[0] * 24, walls=plan['walls'], objective={'axis': 'X', 'sense': 'MAX', 'tick': 24}))
        seed = passage_seed(problem, route, plan)
        self.assertEqual(seed[:12], problem['yaw'][:12])
        self.assertTrue(any(yaw != 0 for yaw in seed[12:]))
        self.assertFalse(any(w['mode'] == 'F' for w in plan['walls']))

    def test_all_six_orders_match_the_minecraft_corner_probe(self):
        evidence = json.loads((RESEARCH_ROOT / 'blockage-order/results.json').read_text())
        edge_faces = faces([0, 0, 0, 1, 1, 1])
        for case in evidence['cases']:
            with self.subTest(case=case):
                first, second = case['type'].split('->')
                chain = [next(f for f in edge_faces if f['axis'] == axis and f['direction'] == sign)
                         for axis, sign in [(first, case['firstSign']), (second, case['secondSign'])]]
                passage = {'leg': 0, 'solidIndex': 0, 'faces': chain, 'gates': [0]}
                event = {'runTicks': 0, 'jump': 0, 'landing': 1}
                rows, failures = compile_passages({'passages': [passage], 'supportClearance': 0}, [event])
                positions = [case['start'], case['end']]
                horizontal_ok = all((positions[r['t1']][0 if r['mode'] == 'X' else 2] >= r['rhs']
                                     if r['cmp'] == 'GE' else positions[r['t1']][0 if r['mode'] == 'X' else 2] <= r['rhs'])
                                    for r in rows)
                profile = {'airtime': 1, 'position': [p[1] for p in positions]}
                self.assertEqual(not failures and horizontal_ok and passage_y_valid(passage, event, profile),
                                 case['directClippedAxis'] == 'none')

    def test_wide_neo_generates_both_sides_and_height_prunes_every_vertical_face(self):
        request, route, profile, plan, witness, bound = passage_case()
        result = passage_options(request['collisionScene']['solids'][1], 1, 0, profile, route, plan, witness, bound,
                                 max_candidates=256)
        self.assertEqual(result['verticalFacesPruned'], 2)
        wide = [o['passage'] for o in result['options'] if o['passage']['kind'] == 'wide_neo']
        self.assertTrue(wide)
        self.assertEqual({p['faces'][1]['direction'] for p in wide}, {-1, 1})
        self.assertTrue(all(f['axis'] != 'Y' for p in wide for f in p['faces']))
        self.assertTrue(all(p['blockageCount'] == 2 for p in wide))
        self.assertGreater(result['reachPruned'], 0)

    def test_reachable_top_and_suspended_bottom_keep_vertical_and_horizontal_candidates(self):
        request, route, profile, plan, witness, bound = passage_case()
        for box, direction in [([1.7, 0, .25, 2, 1.7, .75], 1),
                               ([1.7, 3, .25, 2, 4, .75], -1)]:
            result = passage_options(box, 1, 0, profile, route, plan, witness, bound, max_candidates=512)
            options = [o['passage'] for o in result['options']]
            self.assertTrue(any(any(f['axis'] == 'Y' and f['direction'] == direction for f in p['faces']) for p in options))
            self.assertTrue(any(p['kind'] in ('cross_neo', 'wide_neo') for p in options))

    def test_gate_intervals_retime_with_runs_and_do_not_make_one_face_a_permanent_wall(self):
        _, route, profile, _, _, _ = passage_case()
        route['runBounds'][0] = [0, 3]
        route['maxTotalRunTicks'] = 3
        route['passages'] = [{'leg': 0, 'solidIndex': 1,
                              'faces': [{'axis': 'X', 'direction': -1, 'plane': 1.4},
                                        {'axis': 'Z', 'direction': 1, 'plane': 1.05},
                                        {'axis': 'X', 'direction': 1, 'plane': 2.3}],
                              'gates': [3, 8], 'kind': 'wide_neo'}]
        route = prepare_route(route)
        for runs in (0, 2):
            plan = schedule(route, [runs])
            rows = [r for r in plan['walls'] if r['name'] == 'blockage:1']
            xlo = [r['t1'] for r in rows if r['mode'] == 'X' and r['cmp'] == 'LE']
            xhi = [r['t1'] for r in rows if r['mode'] == 'X' and r['cmp'] == 'GE']
            zhi = [r['t1'] for r in rows if r['mode'] == 'Z']
            self.assertEqual(xlo, list(range(runs + 4)))
            self.assertEqual(zhi, list(range(runs + 3, runs + 9)))
            self.assertEqual(xhi, list(range(runs + 9, runs + 13)))
            self.assertEqual(plan['passageFailures'], [])
            self.assertTrue(passage_y_valid(route['passages'][0], plan['events'][0], profile))

    def test_y_gate_stops_constraining_x_after_the_body_clears_the_bottom(self):
        _, route, profile, plan, _, _ = passage_case()
        passage = {'leg': 0, 'solidIndex': 1,
                   'faces': [{'axis': 'X', 'direction': -1, 'plane': 1.4},
                             {'axis': 'Y', 'direction': -1, 'plane': 1.5}], 'gates': [9]}
        route['passages'] = [passage]
        rows, failures = compile_passages(route, plan['events'])
        self.assertEqual([r['t1'] for r in rows], list(range(10)))
        self.assertEqual(failures, [])
        self.assertTrue(passage_y_valid(passage, plan['events'][0], profile))
        passage['gates'] = [6]
        self.assertFalse(passage_y_valid(passage, plan['events'][0], profile))

    def test_caps_are_visible_and_never_silently_expand_the_blockage_family(self):
        request, route, profile, plan, witness, bound = passage_case()
        result = passage_options(request['collisionScene']['solids'][1], 1, 0, profile, route, plan, witness, bound,
                                 max_blockages=1, max_candidates=1)
        self.assertTrue(all(p['passage']['blockageCount'] <= 1 for p in result['options']))
        self.assertLessEqual(len(result['options']), 1)


if __name__ == '__main__':
    unittest.main()
