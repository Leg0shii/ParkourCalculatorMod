"""Geometry, joint propagation, and independent path coverage controls."""
import copy
import itertools
import json
import math
import unittest

from parkour_solver.constraints.encounters import landing_segments
from parkour_solver.movement.contact_combinations import expanded_face
from parkour_solver.constraints.variants import ConstraintVariants
from tests.support.obstacle_faces import faces
from parkour_solver.reachability.intervals import reachable_envelopes
from parkour_solver.reachability.screen import condition_envelopes
from parkour_solver.scene.normalize import prepare_scene
from tests.support.checkpoint_guidance import flat_scene


def source(n=3, *, y=None, position=(-1., 0.), walls=(), box=(0., 0., -.2, 1., 3., .2), boxes=None,
           profile=None, magnitude=1., friction=.9, state=None):
    y = y or [1.]*(n+1)
    boxes = boxes or [box]
    model = dict(ticks=n, position=list(position), velocity=[0., 0.], magnitude=[magnitude]*n,
                 friction=[friction]*n, walls=list(walls), threshold=0.)
    profile = dict(position=y, **(profile or {}))
    return ConstraintVariants(dict(supportClearance=0.), profile,
        dict(events=[dict(jump=0, runTicks=0)]), dict(solids=[list(b) for b in boxes]), reachable_envelopes(model, y),
        dict(through=n, solidIndices=list(range(len(boxes)))), state=state)


def clear(boxes, y, positions):
    for t in range(len(positions)-1):
        a = [positions[t][0], y[t], positions[t][1]]
        for axis in (1, 0, 2):
            b = list(a)
            b[axis] = y[t+1] if axis == 1 else positions[t+1][axis//2]
            for box in boxes:
                if not any(all(f['direction']*(p['XYZ'.index(f['axis'])]-f['plane']) >= -1e-9 for p in (a, b))
                           for f in faces(box)):
                    return False
            a = b
    return True


def satisfies(rows, positions):
    return all((positions[r.get('t1', r.get('offset'))][0 if r['mode'] == 'X' else 1]-r['rhs'])*
               (1 if r['cmp'] == 'GE' else -1) >= -1e-8 for r in rows)


class EncounterTests(unittest.TestCase):
    def test_known_entry_comes_from_finite_support_not_post_contact_travel(self):
        box = [0., 0., 0., 1., 20., 1.]
        support = [0., 0., -1., 1., 1., 0.]
        profile = dict(contactEvents=[dict(axis='Y', direction=-1, offset=0, role='landing',
            boxes=[support], regions=[expanded_face(support, 1, 1, 1e-6)])])
        owner = source(4, position=(.5, -2.), box=box, profile=profile, magnitude=2.)
        item = owner.items[-1]
        owner.prepare_item(item)
        self.assertEqual(item['begin'], 1)
        self.assertEqual([(item['sides'][i]['axis'], item['sides'][i]['direction'])
                          for i in item['entryFaces']], [('Z', -1)])
        options = []
        cursor = {}
        while (option := owner.option(item, cursor)) is not None:
            options.append(option)
        self.assertTrue(options)
        for option in options:
            if 'passage' in option:
                self.assertEqual(option['passage']['faces'][0]['key'], [['Z', -1, faces(box)[4]['plane']]])
                self.assertTrue(all(row['t1'] >= item['begin'] for row in option['rows']))
                self.assertTrue(all(f['axis'] != 'Y' for f in option['passage']['faces']))

    def test_conditioned_clear_bridge_does_not_remove_an_opposite_exit_face(self):
        # Z- -> X- -> Z+; A already guarantees the X- portion. Only the Z+
        # requirement remains to be chosen for this box. It must not be rejected
        # as an impossible same-axis switch from its known Z- source geometry.
        walls = [dict(mode='X', t1=t, cmp='LE', rhs=-.4) for t in (1, 2)]
        owner = source(3, position=(.5, -1.), box=(0., 0., 0., 1., 3., 1.),
                       magnitude=3., friction=0., walls=walls)
        positions = [[.5, -1.], [-2.5, -1.], [-2.5, 2.], [.5, 2.]]
        variants = list(owner)
        self.assertTrue(any(satisfies(v['rows'], positions) for v in variants))

    def test_ambiguous_support_entry_preserves_both_axes(self):
        support = [-2., 0., -2., 0., 1., 0.]
        owner = source(3, profile=dict(contactEvents=[dict(axis='Y', direction=-1, offset=0,
            role='landing', boxes=[support], regions=[expanded_face(support, 1, 1, 1e-6)])]), magnitude=2.)
        item = owner.items[-1]
        owner.prepare_item(item)
        self.assertTrue({'X', 'Z'} <= {item['sides'][i]['axis'] for i in item['entryFaces']})

    def test_meaningful_vertical_windows_can_exceed_old_switch_counts(self):
        owner = source(6, y=[1., 5., 1., 5., 1., 5., 1.])
        options = list(itertools.islice(owner, 30))
        self.assertTrue(options)
        self.assertTrue(any(p['blockageCounts']['total'] > 3 and p['blockageCounts']['vertical'] > 2
                            for v in options for p in v['selectedPassages']))

    def test_whole_over_or_under_clearance_has_no_horizontal_rows(self):
        for y in ([5.]*4, [-3.]*4):
            owner = source(y=y)
            options = list(owner)
            self.assertEqual(len(options), 1)
            self.assertEqual(options[0]['rows'], [])
            self.assertEqual(options[0]['selectedPassages'], [])

    def test_head_hit_is_not_a_landing_segment_but_selected_floor_is(self):
        profile = dict(grounded=[False]*8, contactEvents=[
            dict(axis='Y', direction=1, offset=1, role='ceiling'),
            dict(axis='Y', direction=-1, offset=4, role='landing')])
        self.assertEqual(landing_segments(profile, 7), [(0, 5), (5, 7)])
        self.assertEqual(landing_segments(profile, 3), [(0, 3)])

    def test_named_probe_is_retained_as_unknown_not_cleared_by_endpoint_y(self):
        owner = source(2, profile=dict(caseEvents=[dict(input=0, case='grind')]), y=[1.]*3)
        model = dict(owner.envelopes['model'], vertical=dict(contacts=[dict(state=1, case='grind')]))
        owner.envelopes = reachable_envelopes(model, [1.]*3)
        item = owner.items[0]
        owner.prepare_item(item)
        self.assertTrue(item['uncertain'])
        self.assertTrue(all(p['at']//3 != 0 for p in item['phases']))
        self.assertEqual(owner.envelopes['phases'][0]['X']['Y'], [-math.inf, math.inf])

    def test_downstream_faces_use_actual_conditioned_bounds_and_restore_in_sibling(self):
        owner = source(2)
        base = copy.deepcopy(owner.envelopes)
        rows = [dict(mode='Z', offset=1, cmp='GE', rhs=.8)]
        a = owner.analyze(rows)
        b = owner.analyze([dict(rows[0], cmp='LE', rhs=-.8)])
        prepared = []
        for analysis in (a, b):
            owner._active_envelopes = analysis['envelopes']
            item = copy.deepcopy(owner.items[0])
            owner.prepare_item(item)
            prepared.append(item)
        self.assertGreater(a['envelopes']['states'][1]['Z'][0], .79)
        self.assertLess(b['envelopes']['states'][1]['Z'][1], -.79)
        self.assertEqual(owner.envelopes, base)
        # At state1 Z is already outside this box; its second input Y/X phases
        # are removed before any timing choices for this encounter are expanded.
        self.assertNotIn([1, 'Y'], prepared[0]['possiblePhases'])
        self.assertNotIn([1, 'X'], prepared[0]['possiblePhases'])
        self.assertEqual(owner.state['metrics'].get('optionNodes', 0), 0)
        self.assertNotEqual(a['envelopes'], b['envelopes'])

    def test_three_way_conflict_backtracks_despite_all_pairwise_checks_passing(self):
        owner = source(3, friction=0., position=(0., 0.))
        rows = [dict(leg=0, offset=t, mode=axis, cmp='GE', rhs=value)
                for t, axis, value in [(1, 'X', .8), (2, 'Z', 1.6), (3, 'X', 2.15)]]
        for pair in itertools.combinations(rows, 2):
            self.assertFalse(owner.analyze(list(pair))['proof']['ruledOut'])
        self.assertTrue(owner.analyze(rows)['proof']['ruledOut'])
        owner.items[:] = [dict(rows=[[r]]) for r in rows]
        self.assertEqual(list(owner), [])
        self.assertTrue(owner.state['screenExclusions'])

    def test_region_item_comes_first_and_is_screened_jointly_with_later_items(self):
        rows = [dict(leg=0, offset=t, mode=axis, cmp='GE', rhs=value, name='contact:region')
                for t, axis, value in [(1, 'X', .8), (2, 'Z', 1.6), (3, 'X', 2.15)]]
        near = dict(rows[0], cmp='LE')
        region = dict(offset=1, surface='surface:test', owner='c', options=[[rows[0]], [near]])
        owner = source(3, friction=0., position=(0., 0.), profile=dict(regionChoices=[region]))
        item = next(i for i in owner.items if i.get('role') == 'contact_region')
        self.assertLess(owner.items.index(item), min(k for k, i in enumerate(owner.items) if 'clusterId' in i))
        self.assertFalse(owner.analyze([near] + rows[1:])['proof']['ruledOut'])
        owner.items[:] = [item] + [dict(rows=[[r]]) for r in rows[1:]]
        variant, = list(owner)
        self.assertIn((1, 'X', 'LE', .8), {(r['offset'], r['mode'], r['cmp'], r['rhs']) for r in variant['rows']})
        self.assertTrue(owner.state['screenExclusions'])

    def test_walk_paused_inside_a_region_item_resumes_without_repeats(self):
        options = [[dict(leg=0, offset=2, mode='X', cmp='LE', rhs=v, name='contact:region')] for v in (-.5, .5, 1.5)]
        profile = dict(regionChoices=[dict(offset=2, surface='surface:test', owner='c', options=options)])
        expected = list(source(3, profile=profile))
        self.assertTrue(expected)
        for stop in range(1, 60):
            owner, calls = source(3, profile=profile), [0]
            def check():
                calls[0] += 1
                if calls[0] == stop:
                    raise TimeoutError('paused')
            owner.check = check
            actual = []
            try:
                actual.extend(owner)
            except TimeoutError:
                pass
            restored = source(3, profile=profile, state=json.loads(json.dumps(owner.state, allow_nan=False)))
            self.assertEqual(actual + list(restored), expected, stop)

    def test_region_item_beyond_the_scope_is_left_for_a_later_leg(self):
        region = dict(offset=4, surface='surface:test', owner='c', options=[[], []])
        self.assertFalse(any(i.get('role') == 'contact_region'
                             for i in source(3, profile=dict(regionChoices=[region])).items))

    def test_rejected_faces_options_and_screened_alternatives_are_logged_with_their_counts(self):
        owner = source(3)
        list(owner)
        state, metrics = owner.state, owner.state['metrics']
        self.assertEqual(len(state['encounterLog']), metrics['preparedEncounters'])
        self.assertEqual(sum(len(e['excludedFaces']) for e in state['encounterLog']), metrics['excludedFaces'])
        self.assertEqual(sum(len(e['coveredPhases']) for e in state['encounterLog']), metrics['excludedPhases'])
        reasons = [d['reason'] for d in state['droppedOptions']]
        self.assertEqual((reasons.count('duplicate'), reasons.count('dominated')),
                         (metrics['duplicateOptions'], metrics['dominatedOptions']))
        screened = [e for e in state['screenExclusions'] if 'item' in e]
        self.assertTrue(screened)
        for entry in screened:
            self.assertEqual(owner.items[entry['item']]['clusterId'], 0)
            self.assertTrue(entry['optionRows'])

    def test_legacy_local_cap_fields_are_removed_from_normalized_policy(self):
        request = dict(flat_scene(), maxContactBranches=1, maxPassageCandidates=1,
                       maxBlockagesPerObstacle=0, maxHorizontalBlockagesPerObstacle=0,
                       maxVerticalBlockagesPerObstacle=0)
        normalized = prepare_scene(request)
        for key in set(request)-set(flat_scene()):
            self.assertNotIn(key, normalized)

    def test_small_exact_paths_are_covered_including_vertical_and_reflected_geometry(self):
        # Independent Y/X/Z axis sweeps, rather than the old generator's strings.
        # Two inputs cannot complete and re-enter a box encounter in these controls.
        for shift, sign, y in [(0., 1, [1., 1., 1.]), (13., -1, [1., 1., 1.]),
                               (0., 1, [1., 4., 1.]), (0., 1, [-2., 1., -2.])]:
            box = [shift, 0., -.2, shift+1., 3., .2]
            initial = [shift-1. if sign == 1 else shift+2., 0.]
            owner = source(2, y=y, box=box, position=initial, magnitude=2., friction=0.)
            variants = list(owner)
            self.assertTrue(variants)
            self.assertGreater(self.assert_paths_covered(variants, [box], y, initial), 0)

    def assert_paths_covered(self, variants, boxes, y, initial):
        covered = 0
        for angles in itertools.product(range(0, 360, 45), repeat=2):
            positions = [initial]
            for angle in angles:
                positions.append([positions[-1][0]+2*math.cos(math.radians(angle)),
                                  positions[-1][1]+2*math.sin(math.radians(angle))])
            if clear(boxes, y, positions):
                covered += 1
                self.assertTrue(any(satisfies(v['rows'], positions) for v in variants), (boxes, y, positions))
        return covered

    def test_flush_boxes_are_one_cluster_whose_paths_are_covered(self):
        # A tall box with a lower one flush on its east side: one obstacle whose
        # sides change when the player rises above the lower box.
        boxes = [(0., 0., -.2, 1., 3., .2), (1., 0., -.6, 2., 1.5, .2)]
        for y in ([1.]*3, [1., 2.5, 1.], [1., 2.5, 2.5], [2.5, 1., 2.5]):
            for initial in ([-1., 0.], [2.5, -1.5]):
                owner = source(2, y=y, boxes=boxes, position=initial, magnitude=2., friction=0.)
                self.assertEqual(sum('clusterId' in i for i in owner.items), 1)
                variants = list(owner)
                self.assertGreater(self.assert_paths_covered(variants, boxes, y, initial), 0)
                for v in variants:
                    for p in v['selectedPassages']:
                        self.assertEqual((p['members'], p['blockId']), ([0, 1], 'solid:0+solid:1'))

    def test_staying_west_while_rising_over_the_lower_box_is_one_side(self):
        # South, then west of the low box, above it (west of the tall one only),
        # and back down: the nested west sides are one side, not a revisit.
        boxes = [(0., 0., 0., 1., 3., 1.), (-1., 0., 0., 0., 1.5, 1.)]
        y = [1., 1., 1., 2.5, 1.]
        positions = [[-.5, -1.], [-2., -1.], [-2., .5], [-2., .5], [-2., .5]]
        self.assertTrue(clear(boxes, y, positions))
        owner = source(4, y=y, boxes=boxes, position=positions[0], magnitude=2., friction=0.)
        variants = list(owner)
        self.assertTrue(any(satisfies(v['rows'], positions) for v in variants))

    def test_variants_differing_only_in_switch_ticks_share_a_family(self):
        variants = list(source(4, magnitude=1., friction=0.))
        sides = {}
        for v in variants:
            path = [[f['key'] for f in p['faces']] for p in v['selectedPassages']]
            self.assertEqual(sides.setdefault(v['family'], path), path)
            self.assertEqual(len(v['switches']), sum(len(p) - 1 for p in path))
        self.assertEqual(len({json.dumps(p) for p in sides.values()}), len(sides))
        self.assertTrue(any(len({json.dumps(v['switches']) for v in variants if v['family'] == f}) > 1 for f in sides))

    def test_each_landing_rectangle_is_its_own_family(self):
        owner = source(4, magnitude=1., friction=0.)
        lo, hi = owner.envelopes['states'][4]['X']
        middle = (lo + hi) / 2
        rectangles = [[dict(leg=0, offset=4, mode='X', cmp='LE', rhs=middle)], [dict(leg=0, offset=4, mode='X', cmp='GE', rhs=middle)]]
        owner.items.insert(0, dict(rows=rectangles, role='contact_region', surface='surface:test', owner='goal'))
        variants = list(owner)
        def implies(rows, r):
            return any(x['offset'] == 4 and x['mode'] == 'X' and x['cmp'] == r['cmp'] and
                       (x['rhs'] <= r['rhs'] if r['cmp'] == 'LE' else x['rhs'] >= r['rhs']) for x in rows)
        chosen = [next(i for i, (r,) in enumerate(rectangles) if implies(v['rows'], r)) for v in variants]
        self.assertEqual(set(chosen), {0, 1})
        for a, b in itertools.combinations(range(len(variants)), 2):
            if chosen[a] != chosen[b]:
                self.assertNotEqual(variants[a]['family'], variants[b]['family'])



class BlockTableEncounterTests(unittest.TestCase):
    def assert_dropped_clusters_are_clear(self, scene, roots):
        from tests.support.block_table import compiler, nodes
        from parkour_solver.constraints.encounters import prepare_encounter
        from parkour_solver.reachability.service import InteractionGate
        from parkour_solver.search.engine import segment_solids
        dropped = 0
        with compiler() as java:
            gate = InteractionGate(scene, java, seconds=10)
            for node in nodes(scene, roots, gate):
                prepared = gate.prepare(node) if node['profile']['airtime'] else None
                if prepared is None:
                    continue
                route, profile, plan = prepared['route'], prepared['profiles'][0], prepared['plan']
                envelopes = gate.analyze_model(route, plan, prepared['vertical'])['envelopes']
                scope = dict(through=plan['ticks'], solidIndices=list(range(len(scene['collisionScene']['solids']))))
                reachable = segment_solids(scene, node, profile, scope['through'], gate)
                full = ConstraintVariants(route, profile, plan, scene['collisionScene'], envelopes, scope)
                kept = ConstraintVariants(route, profile, plan, scene['collisionScene'], envelopes, scope,
                                          segment_solids=reachable)
                removed = [item for item in full.items if 'clusterId' in item and item['begin'] in reachable
                           and set(item['members']).isdisjoint(reachable[item['begin']])]
                self.assertEqual(len(full.items)-len(kept.items), len(removed))
                for item in removed:
                    prepare_encounter(full, item, envelopes)
                    self.assertEqual((item['possiblePhases'], item['uncertain']), ([], []),
                                     (item['members'], item['begin'], item['end']))
                dropped += len(removed)
        return dropped

    def test_real11_clusters_the_table_drops_have_no_possible_phase(self):
        from tests.support.block_table import real11_inputs
        self.assertGreater(sum(self.assert_dropped_clusters_are_clear(scene, roots) for scene, roots in real11_inputs()), 0)

    def test_random_scene_clusters_the_table_drops_have_no_possible_phase(self):
        import random
        from tests.support.block_table import random_box_request
        from parkour_solver.movement.program import empty_program
        rng = random.Random(20261118)
        dropped = 0
        for index in range(20):
            with self.subTest(scene=index):
                scene = prepare_scene(random_box_request(rng))
                dropped += self.assert_dropped_clusters_are_clear(scene, [empty_program(scene['initial'], threshold=0.)])
        self.assertGreater(dropped, 0)


if __name__ == '__main__':
    unittest.main()
