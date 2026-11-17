import copy
import itertools
import json
import math
import os
from pathlib import Path
import unittest

from parkour_solver.movement.contact_combinations import contact_combinations, merge_contacts
from parkour_solver.movement.contact_profiles import contact_profile, generate_contact_profiles
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.movement.ladder.schedules import trace_ladder_schedule
from parkour_solver.scene.route import HALF_WIDTH, prepare_route, schedule
from parkour_solver.scene.geometry import BODY_HEIGHT
from parkour_solver.numerics import prepare, validate
from parkour_solver.numerics import solve
from parkour_solver.physics.vertical import CLIMB_CARRY, REST_CARRY, f32


def event(axis, tick=0, direction=1, box=None, role='contact'):
    box = box or ([1, -2, -2, 2, 8, 2] if axis == 'X' else [-2, -2, 1, 2, 8, 2]
                  if axis == 'Z' else [-2, 3, -2, 2, 4, 2])
    a = 'XYZ'.index(axis)
    width = BODY_HEIGHT if axis == 'Y' else HALF_WIDTH
    plane = box[a] - width if direction > 0 else box[a + 3] + (0 if axis == 'Y' else width)
    return dict(leg=0, offset=tick, axis=axis, direction=direction, plane=plane, box=box, role=role)


class CombinationTests(unittest.TestCase):
    def test_selected_hit_can_touch_its_avoidance_face_without_erasing_other_clearance(self):
        from parkour_solver.constraints.avoidance import compile_passages, face_clearance
        h = event('X',1)
        r = prepare_route({'version':1,'initial':{'position':[0,1,0],'velocity':[0,REST_CARRY,0]},
            'blocks':[{'bounds':[-1,1,-1,1],'top':1},{'bounds':[-1,1,-1,1],'top':1}],
            'airtimes':[4],'sharedMovement':True,'contactEvents':[h],
            'passages':[{'leg':0,'solidIndex':0,'faces':[{'axis':'X','direction':-1,'plane':h['plane']}],'gates':[]}]})
        p = schedule(r,[0])
        walls = [w for w in p['walls'] if w['name']=='blockage:0']
        self.assertEqual(next(w['rhs'] for w in walls if w['t1']==2),h['plane'])
        self.assertTrue(all(w['rhs'] < h['plane'] for w in walls if w['t1']!=2))
        self.assertEqual(face_clearance(r,0,2,dict(axis='X',direction=-1,plane=h['plane']+.1),1e-6),1e-6)

    def test_every_same_tick_axis_subset_and_two_tick_pair_is_generated(self):
        pool = [event(a, t) for t in (0, 1) for a in 'XYZ']
        found = list(contact_combinations(pool, max_events=6, limit=10000))
        actual = {tuple((e['offset'], e['axis']) for e in c) for c in found}
        expected = {tuple((e['offset'], e['axis']) for e in c)
                    for size in range(1, 7) for c in itertools.combinations(pool, size)}
        self.assertEqual(actual, expected)
        self.assertEqual(len(actual), 63)

    def test_free_gap_is_independent_but_full_ladder_sequence_is_combined(self):
        pool = [event('X', 0), event('Y', 3), event('Z', 8)]
        self.assertEqual(len(list(contact_combinations(pool, limit=100))), 3)
        combined = list(contact_combinations(pool, limit=100, ladder_windows=[{'leg': 0, 'start': 0, 'end': 8}]))
        self.assertEqual(len(combined), 7)

    def test_different_blocks_and_normal_signs_do_not_filter_combination_types(self):
        for xs, zs in itertools.product((-1, 1), repeat=2):
            x, z = event('X', direction=xs), event('Z', direction=zs)
            x['blockId'], z['blockId'] = 'a', 'b'
            self.assertEqual(len(list(contact_combinations([x, z], limit=100))), 3)

    def test_conflicting_plane_rejected_and_compatible_duplicate_merged(self):
        c = {'state': 1, 'axis': 0, 'plane': .7, 'direction': 1}
        self.assertEqual(merge_contacts([c, c]), [c])
        for changed in (dict(c, plane=.8), dict(c, direction=-1)):
            with self.assertRaises(ValueError):
                merge_contacts([c, changed])
        self.assertEqual(len(list(contact_combinations([event('X'), event('X', direction=-1)], limit=100))), 2)

    def test_events_and_before_after_avoidance_follow_run_insertion(self):
        contacts = [event('X'), event('Z', 1), event('Y', 1)]
        for e in contacts:
            e['avoidBefore'] = [{'mode': 'Z', 't1': 0, 'cmp': 'GE', 'rhs': -.5}]
            e['avoidAfter'] = [{'mode': 'X', 't1': 2, 'cmp': 'LE', 'rhs': 2}]
        r = prepare_route({'version': 1, 'initial': {'position': [0, 1, 0], 'velocity': [0, REST_CARRY, 0]},
            'blocks': [{'bounds': [-1, 1, -1, 1], 'top': 1}, {'bounds': [0, 2, 0, 2], 'top': 1}],
            'airtimes': [4], 'sharedMovement': True, 'contactEvents': contacts})
        a, b = schedule(r, [0]), schedule(r, [3])
        self.assertEqual([c['state'] + 3 for c in a['sceneContacts']], [c['state'] for c in b['sceneContacts']])
        self.assertEqual([c['state'] + 3 for c in a['verticalContacts']], [c['state'] for c in b['verticalContacts']])
        x = [w['t1'] for w in a['walls'] if w['name'].startswith('contact:')]
        y = [w['t1'] for w in b['walls'] if w['name'].startswith('contact:')]
        self.assertEqual([t + 3 for t in x], y)
        self.assertEqual(schedule(r, [3], 'run')['sceneContacts'], [])
        self.assertFalse(any('reverse' in w['name'] for w in a['walls']))

    def test_contact_on_landing_input_and_actual_step_have_explicit_representation(self):
        contacts = [event('X', 3), event('Y', 3, -1, [-2, 0, -2, 2, 1, 2], 'landing')]
        r = prepare_route({'version': 1, 'initial': {'position': [0, 1, 0], 'velocity': [0, REST_CARRY, 0]},
            'blocks': [{'bounds': [-1, 1, -1, 1], 'top': 1}, {'bounds': [0, 2, 0, 2], 'top': 1}],
            'airtimes': [4], 'sharedMovement': True, 'contactEvents': contacts})
        self.assertEqual({c['state'] for c in schedule(r, [0])['sceneContacts']}, {4})
        changed = copy.deepcopy(r)
        changed['contactEvents'][0]['plane'] += 1
        with self.assertRaises(ValueError):
            prepare_route(changed)


class MixedVerticalTests(unittest.TestCase):
    def test_same_tick_ceiling_climb_refresh_and_no_climb_give_different_carry(self):
        initial = {'position': 1.98, 'velocity': CLIMB_CARRY}
        ceiling = [{'state': 1, 'plane': 2, 'direction': 1}]
        active = trace_ladder_schedule(initial, [True] * 4, [False] * 3, [True, False, False], contacts=ceiling)
        stopped = trace_ladder_schedule(initial, [True] * 4, [False] * 3, [False] * 3, contacts=ceiling)
        self.assertEqual(active['position'][1], stopped['position'][1])
        self.assertEqual(active['velocity'][1], CLIMB_CARRY)
        self.assertEqual(stopped['velocity'][1], REST_CARRY)
        self.assertGreater(active['position'][2], stopped['position'][2])

    def test_repeated_ceiling_climb_sequence_and_final_omission(self):
        initial = {'position': 2., 'velocity': CLIMB_CARRY}
        ceilings = [{'state': state, 'plane': 2, 'direction': 1} for state in range(1, 6)]
        active = trace_ladder_schedule(initial, [True] * 7, [False] * 6, [True] * 5 + [False], contacts=ceilings)
        stopped = trace_ladder_schedule(initial, [True] * 7, [False] * 6, [True] * 4 + [False] * 2, contacts=ceilings)
        self.assertEqual(active['position'][:6], [2.] * 6)
        self.assertEqual(active['velocity'][5], CLIMB_CARRY)
        self.assertEqual(stopped['velocity'][5], REST_CARRY)
        self.assertLess(stopped['position'][6], 2.)

    def test_no_ladder_refresh_on_exit_despite_same_horizontal_hit(self):
        for ladder in ([True, False, False], [False, True, True]):
            trace = trace_ladder_schedule({'position': 2, 'velocity': CLIMB_CARRY}, ladder, [False] * 2,
                [True, False], contacts=[{'state': 1, 'plane': 2.05, 'direction': 1}])
            self.assertEqual(trace['velocity'][1], CLIMB_CARRY if ladder[1] else REST_CARRY)

    def test_profile_regenerates_airtime_for_wall_and_ceiling_combinations(self):
        h = event('X', box=[1, 0, -4, 2, 6, 4])
        c = event('Y', box=[-2, 3, -2, 3, 4, 3])
        profile = contact_profile(1, 1, [h, c], max_inputs=30)
        self.assertEqual(profile['airtime'], 3)
        self.assertEqual(profile['velocity'][1], REST_CARRY)
        self.assertEqual({e['axis'] for e in profile['contactEvents']}, {'X', 'Y'})
        generated = generate_contact_profiles(1, 1, [h, c, event('Z', 1)], max_inputs=30, limit=100)
        self.assertTrue(any({e['axis'] for e in p['contactEvents']} == {'X', 'Y', 'Z'} for p in generated['profiles']))


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER'), 'Set PKC_RUN_TICKS_COMPILER')
class CombinationSceneTests(unittest.TestCase):
    def setUp(self):
        self.compiler = JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text()))
        self.addCleanup(self.compiler.close)

    def test_adjacent_ladder_parts_use_exact_finite_face_alternatives(self):
        from parkour_solver.scene.ladders import ladder_regions, climb_walls, schedule_constraints, entry_channels
        boxes = [[1,0,0,2,4,1], [1,0,1,2,4,2]]
        cells = [[0,y,z] for y in range(3) for z in range(2)]
        col = ladder_regions(cells)[0]
        wall = next(w for w in climb_walls(col, boxes) if w['axis'] == 'X' and w['direction'] == 1)
        s = {'entryState': 0, 'exitState': 2, 'hit': [True], 'ladderTicks': [True,True],
             'trace': {'position': [1,1]}}
        rows, _, contacts = schedule_constraints(s, col, wall, entry_channels(col, wall)[0], None)
        self.assertEqual(len(contacts[0]['boxes']), 2)
        scene = {'version':1,'profile':'minecraft-1.8.9','bounds':[-8,-5,-8,8,12,8], 'solids': boxes, 'ladders': cells}
        positions = [[.6,1,1.5], [1-HALF_WIDTH,1,1.5]]
        checked = self.compiler.check_scene(scene, positions, [[.15,0,0]], [False,False], 5, contacts=contacts)
        self.assertTrue(checked['feasible'], checked)
        contacts[0]['boxes'] = boxes[:1]
        self.assertFalse(self.compiler.check_scene(scene, positions, [[.15,0,0]], [False,False], 5, contacts=contacts)['feasible'])

    def test_all_same_tick_axis_combinations_and_signs_against_shared_scene(self):
        for vertical, xhit, zhit, xs, zs in itertools.product(('none', 'ceiling', 'floor'), (False, True),
                                                           (False, True), (-1, 1), (-1, 1)):
            with self.subTest(vertical=vertical, x=xhit, z=zhit, xs=xs, zs=zs):
                p = [.45 * xs, 2., .45 * zs]
                displacement = [.2 * xs, .12 if vertical != 'floor' else -.12, .2 * zs]
                next_p = [p[a] + displacement[a] for a in range(3)]
                solids, contacts = [], []
                for axis, hit, sign in [('X', xhit, xs), ('Z', zhit, zs)]:
                    if hit:
                        box = [.85, -2, -3, 1.85, 8, 3] if axis == 'X' else [-3, -2, .85, 3, 8, 1.85]
                        a = 'XYZ'.index(axis)
                        if sign < 0:
                            box[a], box[a + 3] = -box[a + 3], -box[a]
                        e = event(axis, direction=sign, box=box)
                        next_p[a] = e['plane']
                        solids.append(box)
                        contacts.append({'state': 1, 'axis': a, 'direction': sign, 'plane': e['plane'], 'box': box})
                if vertical != 'none':
                    box = [-3, 2.05 + BODY_HEIGHT, -3, 3, 6, 3] if vertical == 'ceiling' else [-3, 0, -3, 3, 1.95, 3]
                    e = event('Y', direction=1 if vertical == 'ceiling' else -1, box=box)
                    next_p[1] = e['plane']
                    solids.append(box)
                    contacts.append({'state': 1, 'axis': 1, 'direction': e['direction'], 'plane': e['plane'], 'box': box})
                scene = {'version': 1, 'profile': 'minecraft-1.8.9', 'bounds': [-8, -5, -8, 8, 12, 8], 'solids': solids, 'stepHeight': 0}
                checked = self.compiler.check_scene(scene, [p, next_p], [displacement], [False, vertical == 'floor'], 5, contacts=contacts)
                self.assertTrue(checked['feasible'], checked)
                for index in range(len(contacts)):
                    missing = contacts[:index] + contacts[index + 1:]
                    if contacts[index]['axis'] != 1 or vertical == 'ceiling':
                        self.assertFalse(self.compiler.check_scene(scene, [p, next_p], [displacement],
                            [False, vertical == 'floor'], 5, contacts=missing)['feasible'])

    def test_selected_step_is_checked_as_step_not_a_fictitious_downward_clip(self):
        floor, stair = [-2, 0, -2, 1, 1, 2], [1, 0, -2, 3, 1.5, 2]
        scene = {'version': 1, 'profile': 'minecraft-1.8.9', 'bounds': [-8, -5, -8, 8, 12, 8], 'solids': [floor, stair]}
        selected = {'state': 1, 'axis': 1, 'direction': -1, 'plane': 1.5, 'box': stair, 'step': True}
        checked = self.compiler.check_scene(scene, [[.5, 1, 0], [1., 1.5, 0]], [[.5, REST_CARRY, 0]], [True, True], 5, contacts=[selected])
        self.assertTrue(checked['feasible'], checked)
        selected['plane'] = 1.4
        self.assertFalse(self.compiler.check_scene(scene, [[.5, 1, 0], [1., 1.5, 0]], [[.5, REST_CARRY, 0]],
                                                  [True, True], 5, contacts=[selected])['feasible'])


    def test_step_can_retain_x_z_contacts_from_the_selected_step_path(self):
        floor, stair = [-2, 0, -2, 1, 1, 3], [1, 0, -2, 3, 1.5, 3]
        xwall, zwall = [1.5, 0, -3, 2, 6, 3], [-3, 0, 1.5, 3, 6, 2]
        scene = {'version': 1, 'profile': 'minecraft-1.8.9', 'bounds': [-8, -5, -8, 8, 12, 8],
                 'solids': [floor, stair, xwall, zwall]}
        selected = [{'state': 1, 'axis': 1, 'direction': -1, 'plane': 1.5, 'box': stair, 'step': True},
                    {'state': 1, 'axis': 0, 'direction': 1, 'plane': 1.5 - HALF_WIDTH, 'box': xwall},
                    {'state': 1, 'axis': 2, 'direction': 1, 'plane': 1.5 - HALF_WIDTH, 'box': zwall}]
        checked = self.compiler.check_scene(scene, [[.5, 1, .5], [1.5 - HALF_WIDTH, 1.5, 1.5 - HALF_WIDTH]],
                                           [[.8, REST_CARRY, .8]], [True, True], 5, contacts=selected)
        self.assertTrue(checked['feasible'], checked)
        for axis in (0, 2):
            missing = [e for e in selected if e['axis'] != axis]
            self.assertFalse(self.compiler.check_scene(scene, [[.5, 1, .5], [1.5 - HALF_WIDTH, 1.5, 1.5 - HALF_WIDTH]],
                [[.8, REST_CARRY, .8]], [True, True], 5, contacts=missing)['feasible'])


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set PKC_RUN_TICKS_COMPILER and PKC_IPOPT_PYTHON')
class StepCombinationIntegrationTests(unittest.TestCase):
    def test_ipopt_step_and_two_wall_contacts_share_one_movement(self):
        from parkour_solver.scene.normalize import prepare_scene
        from parkour_solver.search.support_profiles import compile_block_path
        from parkour_solver.constraints.contacts import floor_checks
        from parkour_solver.search.repair import solve_contact_branches
        floor, stair = [-2,0,-2,1,1,3], [1,0,-2,3,1.5,3]
        xwall, zwall = [1.5,0,-3,2,6,3], [-3,0,1.5,3,6,2]
        r = {'version':1,'sceneModel':'collision_boxes','geometryBounds':[-8,-5,-8,8,12,8],
             'initial':{'position':[.5,1,.5],'velocity':[.8,REST_CARRY,.8]},
             'startBlock':'floor','goalBlock':'stair','blocks':[{'id':name,'boxes':[box]}
                for name,box in [('floor',floor),('stair',stair),('x',xwall),('z',zwall)]],
             'maxJumps':1,'maxRunTicksPerJump':0,'maxTotalRunTicks':0,'maxContactCombinations':0}
        selected = [event('Y',direction=-1,box=stair,role='step'),event('X',box=xwall),event('Z',box=zwall)]
        p = contact_profile(1,1.5,selected,max_inputs=4,jump_at_start=False)
        self.assertEqual(p['airtime'],1)
        scene = prepare_scene(r)
        compiled = compile_block_path(scene,['floor','stair'],[{'source':'floor','target':'stair',
            'profileKind':'step','profile':p,'entryFace':None,'exitFace':None}])
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            result = solve_contact_branches(compiled['route'],compiled['profiles'],floor_checks(scene,['floor','stair'],compiled['profiles']),
                compiler,seconds=10,candidate_seconds=2,retries=0,geometry=scene['collisionScene'],
                motion_bound={'storedSpeedNorm':2,'movementAfterSupportCheck':2},python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(result['status'],'feasible',result)
        self.assertEqual(result['best']['plan']['ticks'],1)
        self.assertTrue(result['best']['geometryCheck']['feasible'])
        self.assertEqual(result['best']['candidate']['velocity'][1],[0.,0.])
        self.assertEqual(result['best']['candidate']['y'][1],1.5)

    def test_ipopt_ceiling_x_z_ladder_refresh_then_ceiling_stop_and_land(self):
        from parkour_solver.scene.normalize import prepare_scene
        from parkour_solver.search.support_profiles import compile_block_path
        from parkour_solver.constraints.contacts import floor_checks
        from parkour_solver.search.repair import solve_contact_branches
        floor, goal = [-2,0,-2,.5,1,.85], [.5,0,-2,.85,1,.85]
        xwall, zwall, ceiling = [.85,0,-2,2,5,2], [-2,0,.85,2,5,2], [-2,2.85,-2,2,4,2]
        request = {'version':1,'sceneModel':'collision_boxes','geometryBounds':[-8,-5,-8,8,12,8],
            'initial':{'position':[.5,1,.5],'velocity':[.2,REST_CARRY,.2]}, 'startBlock':'floor','goalBlock':'goal',
            'blocks':[{'id':name,'boxes':[box]} for name,box in [('floor',floor),('goal',goal),('x',xwall),('z',zwall),('c',ceiling)]],
            'climbableCells':[[0,1,0]],'maxJumps':1,'maxRunTicksPerJump':0,'maxTotalRunTicks':0,'maxContactCombinations':0}
        selected = [event('X',box=xwall),event('Z',box=zwall),event('Y',box=ceiling),event('Y',1,box=ceiling)]
        profile = contact_profile(1,1,selected,max_inputs=8,ladder=[True]*9)
        self.assertEqual(profile['airtime'],3)
        scene = prepare_scene(request)
        compiled = compile_block_path(scene,['floor','goal'],[{'source':'floor','target':'goal','profileKind':'contacts',
            'profile':profile,'entryFace':None,'exitFace':None}])
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            result = solve_contact_branches(compiled['route'],compiled['profiles'],floor_checks(scene,['floor','goal'],compiled['profiles']),
                compiler,seconds=12,candidate_seconds=2,retries=1,geometry=scene['collisionScene'],
                motion_bound={'storedSpeedNorm':2,'movementAfterSupportCheck':2},python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(result['status'],'feasible',result)
        b = result['best']
        self.assertTrue(b['geometryCheck']['feasible'])
        self.assertEqual(b['candidate']['vy'][1],CLIMB_CARRY)
        self.assertEqual(b['candidate']['vy'][2],REST_CARRY)
        self.assertTrue(b['candidate']['onGround'][3])
        self.assertTrue(all(b['plan']['ladderTicks']))
