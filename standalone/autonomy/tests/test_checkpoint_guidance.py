import copy
import itertools
import json
import math
import os
from pathlib import Path
import time
import threading
import unittest

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.cell_routes.checkpoints import checkpoint, horizontal_segments, retain_prefix
from standalone.autonomy.cell_routes.checkpoint_search import ScopedWork, evaluate_program, _motion_bound
from standalone.autonomy.cell_routes.geometry import decompose
from standalone.autonomy.cell_routes.local import CorridorAdapter, SearchWork
from standalone.autonomy.cell_routes.routes import Corridor, audit_candidate, corridor_for_points, landmarks
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.movement_composition import append_choice, compile_program, empty_program, truncate_program
from standalone.autonomy.movement_generation import generate_choices
from standalone.autonomy.movement_search import iter_movement_programs
from standalone.autonomy.route import prepare_route, schedule
from standalone.autonomy.tests.test_cell_routes import side_scene
from standalone.autonomy.tests.test_search import route


ROOT = Path(__file__).parents[1]


def flat_scene():
    return prepare_scene(dict(version=1, sceneModel='collision_boxes', geometryBounds=[-10,-5,-10,20,12,10],
        initial=dict(position=[.5,1,.5], velocity=[.25,-.0784000015258789,0], yaw=-90),
        startBlock='start', goalBlock='goal', maxWallHits=0, maxContactCombinations=0,
        blocks=[dict(id='start', boxes=[[-2,0,-2,3,1,2]]), dict(id='goal', boxes=[[5,0,-1,6,1,1]])],
        movementSearch=dict(types=['normal'], maxInputs=30, maxPrefixes=96, maxQueuedPrefixes=128, maxChoicesPerType=16)))


def programs(scene, tick):
    return list(itertools.islice(iter_movement_programs(scene, checkpoint_tick=tick, threshold=0.,
                    deadline=time.monotonic()+3), 64))


class CheckpointTests(unittest.TestCase):
    def test_shared_checkpoint_has_horizontal_direction_and_no_y_objective(self):
        r = Corridor(0, [], [], [[0,1,0],[1,4,0],[1,8,2],[1,9,4],[1,12,4]], (), [], 5)
        segments = horizontal_segments(r)
        self.assertEqual(len(segments), 2)
        cp = checkpoint(r, [], dict(tick=8, segment=1))
        self.assertEqual(cp.direction, (0.,1.))
        self.assertAlmostEqual(cp.objective['customYaw'], 0)
        self.assertEqual(cp.tick, 8)
        self.assertFalse(cp.to_dict()['scoreUsesY'])
        self.assertEqual(checkpoint(r, [], dict(tick=8, segment=0)).direction, (1.,0.))

    def test_comparison_respects_ordered_gate_crossings_on_a_revisit(self):
        points = [[0,1,0],[3,1,0],[0,1,0],[-2,1,0]]
        gate = dict(id=0, axis=0, lateral=2, plane=1, lo=-1, hi=1)
        r = Corridor(0, [], [], points, (), [], 8)
        cp = checkpoint(r, [gate], dict(tick=3, segment=1))
        self.assertEqual(cp.required_signature, ((0,1,0),))
        wrong = dict(positions=[[0,0],[-.5,0],[-1,0],[-2,0]], y=[1]*4)
        right = dict(positions=[[0,0],[3,0],[0,0],[-2,0]], y=[1]*4)
        self.assertFalse(cp.stage(wrong, [gate])['accepted'])
        self.assertTrue(cp.stage(right, [gate])['accepted'])

    def test_checkpoint_rejects_a_state_past_the_next_turn(self):
        r = Corridor(0,[],[],[[0,1,0],[2,1,0],[2,1,2]],(),[],4)
        cp = checkpoint(r,[],dict(tick=2,segment=0))
        candidate = dict(positions=[[0,0],[2,0],[3,0]],y=[1,2,3])
        self.assertFalse(cp.stage(candidate,[])['accepted'])
        self.assertFalse(cp.stage(candidate,[])['beforeNextTurn'])

    def test_reverse_direction_uses_the_same_signed_horizontal_objective(self):
        r = Corridor(0,[],[],[[2,1,0],[-2,5,0]],(),[],4)
        cp = checkpoint(r,[],dict(tick=2))
        self.assertEqual(cp.direction, (-1.,0.))
        self.assertAlmostEqual(cp.objective['customYaw'], 90.)
        self.assertEqual(cp.objective['sense'], 'MAX')

    def test_cancellation_before_geometry_needs_no_compiler(self):
        from standalone.autonomy.cell_routes.checkpoint_search import find_checkpoint_route
        from standalone.autonomy.cell_routes.checkpoint_experiment import brief
        cancel = threading.Event()
        cancel.set()
        result = find_checkpoint_route(flat_scene(),None,checkpoints=[dict(tick=2)],cancel_event=cancel)
        self.assertEqual(result['status'], 'cancelled')
        self.assertEqual(brief(result)['calls'], 0)

    def test_partial_corridor_accepts_a_signature_prefix_and_full_audit_does_not(self):
        scene = side_scene()
        d, gates = decompose(scene), landmarks(scene)
        r = corridor_for_points(d, [[.5,1,.5],[1,1,-1],[4,1,-1],[5.5,1,.5]], gates)
        prefix = dict(positions=[[.5,.5],[1,-1]], y=[1,1])
        self.assertTrue(audit_candidate(r, prefix, gates, partial=True)['accepted'])
        self.assertFalse(audit_candidate(r, prefix, gates)['accepted'])

    def test_open_program_has_no_fabricated_terminal_goal_constraint(self):
        scene = flat_scene()
        p = next(p for p in programs(scene, 4) if p['profile']['airtime']==4 and p['profile']['jump'][0])
        compiled = compile_program(scene, p, require_complete=False)
        r = prepare_route(compiled['route'])
        plan = schedule(r, [0])
        self.assertFalse(r['terminalSupportRequired'])
        self.assertEqual(r['physicsThreshold'], 0.)
        self.assertNotIn((3,1,'landing'), plan['supportChecks'])
        self.assertFalse(any(w.get('name', '').startswith('landing:1:') for w in plan['walls']))
        self.assertTrue(all(w['mode']!='F' for w in plan['walls']))

    def test_different_method_lengths_are_cut_at_the_same_absolute_tick(self):
        scene = flat_scene()
        prefixes = [p for p in programs(scene, 4) if p['profile']['airtime']==4]
        self.assertGreater(len(prefixes), 1)
        self.assertEqual({p['profile']['airtime'] for p in prefixes}, {4})
        self.assertGreater(len({tuple(p['profile']['jump']) for p in prefixes}), 1)
        self.assertTrue(all(p['threshold']==0 for p in prefixes))
        self.assertGreater(len({p['profile']['position'][-1] for p in prefixes}), 1)

    def test_truncating_preserves_past_contact_and_drops_future_contact(self):
        scene = flat_scene()
        p = next(p for p in programs(scene, 6) if p['profile']['airtime']==6 and p['profile']['jump'][0])
        p['profile']['positionConstraints'].append(dict(mode='X',t1=5,cmp='GE',rhs=1))
        cut = truncate_program(p, 4)
        self.assertEqual(cut['profile']['position'], p['profile']['position'][:5])
        self.assertEqual(cut['profile']['jump'], p['profile']['jump'][:4])
        self.assertTrue(all(r['t1']<=4 for r in cut['profile']['positionConstraints']))
        self.assertEqual(cut['trace']['ticks'], 4)
        self.assertEqual(cut['profile']['generatedUnits'][-1]['end'], 4)
        self.assertEqual(cut['initial'], p['initial'])

    def test_retention_keeps_constraints_and_warm_yaw_without_pinning_states(self):
        p = empty_program(dict(position=[.5,1,.5], velocity=[.25,-.0784,0]))
        best = dict(plan=dict(walls=[dict(mode='X',t1=2,cmp='LE',rhs=3,name='blockage'),
                                    dict(mode='Z',t1=4,cmp='GE',rhs=1,name='checkpoint_probe:4')]),
                    candidate=dict(yaw=[1,2,3,4], positions=[[9,9]]*5, velocity=[[8,8]]*5))
        retained = retain_prefix(p, best)
        self.assertEqual(retained['profile']['positionConstraints'], best['plan']['walls'][:1])
        self.assertEqual(retained['guideSeedYaw'], [1,2,3,4])
        self.assertNotIn('positions', retained)
        self.assertNotIn('velocity', retained)
        self.assertEqual(retained['initial'], p['initial'])
        self.assertEqual(p['profile']['positionConstraints'], [])

    def test_scoped_call_limit_does_not_exhaust_other_candidates(self):
        parent = SearchWork(3)
        first = ScopedWork(parent, 1, time.monotonic()+2)
        first.before_solve({})
        with self.assertRaises(TimeoutError):
            first.before_solve({})
        self.assertIsNone(parent.termination)
        second = ScopedWork(parent, 1, time.monotonic()+2)
        second.before_solve({})
        self.assertEqual(parent.counts['solverCalls'], 2)

    def test_no_threshold_vertical_replay_carries_small_apex_velocity(self):
        from standalone.autonomy.open_movement import replay
        initial = dict(position=1, velocity=-.0784000015258789, onGround=True)
        ordinary = replay(initial, 7, jumping=True)
        continuous = replay(initial, 7, jumping=True, threshold=0.)
        self.assertGreater(max(continuous['position']), max(ordinary['position']))
        self.assertEqual(continuous['vertical']['threshold'], 0.)
        self.assertEqual(ordinary['vertical']['threshold'], .005)


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set compiler and IPOPT Python for numerical tests')
class CheckpointNumericalTests(unittest.TestCase):
    def setUp(self):
        self.compiler = JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text()))
        self.addCleanup(self.compiler.close)

    def test_repair_does_not_replace_a_supplied_seed_with_a_rejected_proposal(self):
        import gzip
        from standalone.autonomy.block_contacts import floor_checks, solve_contact_branches
        from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
        records = json.loads(gzip.decompress((ROOT/'experiments/failure-fixes/evidence/fix-warm.json.gz').read_bytes()))['runs']
        saved = next(r for r in records if r['name']=='turn-blockage-alternate_turn'
                     and r['direction']=='reverse' and r['seedKind']=='known')
        scene = prepare_scene(saved['request'])
        compiled = compile_program(scene, saved['program'], require_complete=False)
        compiled['route']['guideObjective'] = saved['calls'][0]['problem']['objective']
        d, gates = decompose(scene), landmarks(scene)
        corridor = corridor_for_points(d, saved['spatialPoints'], gates)
        adapter = CorridorAdapter(scene, corridor, gates, decomposition=d)
        work = SearchWork(4, deadline=time.monotonic()+3)
        result = solve_contact_branches(compiled['route'], compiled['profiles'],
            floor_checks(scene, [scene['startBlock'],scene['goalBlock']], compiled['profiles']), self.compiler,
            seconds=3, candidate_seconds=.7, retries=0, max_branches=scene['maxContactBranches'],
            improve=True, rank_equal_cost=True, optimize_goal=True, python_executable=os.environ['PKC_IPOPT_PYTHON'],
            geometry=scene['collisionScene'], motion_bound=_motion_bound(scene,self.compiler,2),
            solid_blocks=[b['sourceBlock'] for b in scene['blocks']], work=work,
            candidate_filter=lambda p,c,v:adapter.candidate(p,c,v,partial=True))
        self.assertIsNotNone(result['best'])
        self.assertGreaterEqual(result['best']['candidate']['objective'],saved['result']['best']['candidate']['objective']-1e-6)
        self.assertTrue(replay_witness(scene,result['best'],self.compiler)['passed'])

    def test_known_post_turn_seed_survives_polishing_and_replays(self):
        import gzip
        from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
        probe = json.loads(gzip.decompress((ROOT/'experiments/decision-windows/evidence/turn-known-prefix-probe.json.gz').read_bytes()))
        scene = prepare_scene(probe['fixture']['request'])
        d, gates = decompose(scene), landmarks(scene)
        corridor = corridor_for_points(d, probe['fixture']['spatialPoints'], gates)
        adapter = CorridorAdapter(scene, corridor, gates, decomposition=d)
        cp = checkpoint(corridor, gates, dict(tick=probe['checkpoint']['tick'], segment=probe['checkpoint']['segment']))
        work = SearchWork(4, deadline=time.monotonic()+3)
        result = evaluate_program(scene, probe['program'], self.compiler, adapter, work,
            _motion_bound(scene, self.compiler, 2), checkpoint=cp, seconds=2, candidate_seconds=.7,
            solver_calls=4, python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(result['status'], 'feasible', result)
        self.assertGreaterEqual(result['best']['candidate']['objective'], probe['knownPrefixWitness']['candidate']['objective']-1e-7)
        self.assertTrue(result['comparisonStage']['accepted'])
        self.assertTrue(replay_witness(scene, result['best'], self.compiler)['passed'])

    def test_common_tick_scores_distinct_y_with_normal_momentum_carry(self):
        scene = flat_scene()
        d, gates = decompose(scene), landmarks(scene)
        r = corridor_for_points(d, [[.5,1,.5],[5.5,1,.5]], gates)
        adapter = CorridorAdapter(scene, r, gates, decomposition=d)
        cp = checkpoint(r, gates, dict(tick=2))
        options = [p for p in programs(scene, 2) if p['profile']['airtime']==2]
        chosen = [next(p for p in options if p['profile']['jump'][0]),
                  next(p for p in options if not any(p['profile']['jump']) and p['profile']['grounded'][-1])]
        work = SearchWork(12, deadline=time.monotonic()+10)
        motion = _motion_bound(scene, self.compiler, 2)
        scores = []
        for p in chosen:
            result = evaluate_program(scene, p, self.compiler, adapter, work, motion, checkpoint=cp,
                seconds=3, candidate_seconds=1, solver_calls=4, python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertEqual(result['status'], 'feasible', result)
            best = result['best']
            self.assertEqual(best['plan']['ticks'], 2)
            self.assertEqual(best['route']['physicsThreshold'], 0.)
            self.assertEqual(best['vertical']['threshold'], 0.)
            self.assertEqual(best['candidate']['positions'][0], [.5,.5])
            self.assertEqual(best['candidate']['velocity'][0], [.25,0])
            self.assertAlmostEqual(best['candidate']['objective'], best['candidate']['positions'][2][0])
            self.assertTrue(best['geometryCheck']['feasible'])
            scores.append(best)
        self.assertNotEqual(scores[0]['candidate']['y'][-1], scores[1]['candidate']['y'][-1])

    def test_prefixes_continue_to_an_accepted_goal_without_freezing_the_start(self):
        from standalone.autonomy.cell_routes.checkpoint_search import find_checkpoint_route
        from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
        request = json.loads((ROOT/'experiments/cell-routes/controls/open.json').read_text())
        request['movementSearch'].update(types=['normal'],maxPrefixes=96)
        points = json.loads((ROOT/'experiments/cell-routes/controls/open-route.json').read_text())
        result = find_checkpoint_route(request,self.compiler,checkpoints=[dict(tick=4)],spatial_points=points,
            seconds=10,max_candidates=12,beam_width=3,max_solver_calls=32,score_call_limit=4,
            candidate_seconds=.7,score_seconds=2,python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(result['status'],'feasible',result['termination'])
        self.assertLessEqual(result['best']['plan']['ticks'],12)
        self.assertEqual(result['searchState']['kind'],'ordered_goal_dfs')
        self.assertEqual(result['checkpoints'][0]['tick'],4)
        self.assertTrue(result['finalAttempts'])
        for record in result['finalAttempts']:
            self.assertEqual(record['goal'],dict(kind='landing',block=request['goalBlock']))
            self.assertEqual(record['witness']['route']['initial'],request['initial'])
            self.assertFalse(any(r['mode']=='F' for r in record['witness']['plan']['walls']))
        self.assertTrue(replay_witness(prepare_scene(request),result['best'],self.compiler)['passed'])
        self.assertFalse(result['objectiveOptimalityProven'])

    def test_a_later_constraint_can_reoptimize_facings_before_the_previous_checkpoint(self):
        scene = flat_scene()
        d, gates = decompose(scene), landmarks(scene)
        r = corridor_for_points(d, [[.5,1,.5],[5.5,1,.5]], gates)
        adapter = CorridorAdapter(scene,r,gates,decomposition=d)
        motion = _motion_bound(scene,self.compiler,2)
        work = SearchWork(12,deadline=time.monotonic()+10)
        first_program = next(p for p in programs(scene,2) if p['profile']['airtime']==2 and p['profile']['jump'][0])
        first = evaluate_program(scene,first_program,self.compiler,adapter,work,motion,
            checkpoint=checkpoint(r,gates,dict(tick=2)),seconds=2,solver_calls=4,
            python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(first['status'],'feasible')
        retained = retain_prefix(first_program,first['best'])
        second_program = next(p for p in programs(scene,4) if p['profile']['airtime']==4 and p['profile']['jump'][0])
        second_program['profile']['positionConstraints'] = copy.deepcopy(retained['profile']['positionConstraints'])
        second_program['profile']['positionConstraints'].append(dict(mode='Z',t1=2,cmp='GE',rhs=.95,name='later_turn'))
        second_program['guideSeedYaw'] = retained['guideSeedYaw']
        second = evaluate_program(scene,second_program,self.compiler,adapter,work,motion,
            checkpoint=checkpoint(r,gates,dict(tick=4)),seconds=3,solver_calls=4,
            python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(second['status'],'feasible')
        before, after = first['best']['candidate'], second['best']['candidate']
        self.assertLess(before['positions'][2][1],.9)
        self.assertGreaterEqual(after['positions'][2][1],.95-1e-7)
        self.assertGreater(max(abs(a-b) for a,b in zip(before['yaw'],after['yaw'])),1.)
        self.assertEqual(before['positions'][0],after['positions'][0])
        self.assertEqual(before['velocity'][0],after['velocity'][0])

    def test_turn_route_reaches_the_goal_and_replays_without_a_clock_barrier(self):
        from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness, run_fixture
        fixture = json.loads((ROOT/'experiments/checkpoint-guidance/fixtures/turn-blockage.json').read_text())
        result = run_fixture(fixture,self.compiler,python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(result['status'],'feasible',result['termination'])
        self.assertEqual(result['searchState']['kind'],'ordered_goal_dfs')
        self.assertEqual(result['checkpoints'][0]['tick'],6)
        accepted = [c for c in result['finalAttempts'] if c.get('witness')]
        self.assertTrue(accepted)
        for record in accepted:
            self.assertEqual(record['goal'],dict(kind='landing',block=fixture['request']['goalBlock']))
            self.assertTrue(replay_witness(prepare_scene(fixture['request']),record['witness'],self.compiler)['passed'])
        self.assertTrue(replay_witness(prepare_scene(fixture['request']),result['best'],self.compiler)['passed'])


if __name__ == '__main__':
    unittest.main()
