from tests.support.paths import AUTONOMY_DATA
import copy
import hashlib
import json
import os
from pathlib import Path
import threading
import time
import unittest
from unittest.mock import patch

from parkour_solver.constraints.contacts import floor_checks
from parkour_solver.search.repair import solve_contact_branches
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.search.support_profiles import compile_block_path
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.movement.ladder.episodes import compose_ladder_episodes
from parkour_solver.scene.ladders import ladder_regions, climb_walls, entry_channels, exit_faces
from parkour_solver.movement.ladder.schedules import generate_ladder_schedules
from parkour_solver.movement.ladder.transfers import append_ladder_programs, chain_programs, open_program
from parkour_solver.movement.units import compile_units, movement_program
from parkour_solver.validation.scene import check_geometry
from parkour_solver.scene.route import prepare_route, schedule
from tests.support.ladder_composition import airborne_case
from parkour_solver.numerics import prepare, validate
from parkour_solver.physics.vertical import REST_CARRY


ROOT = AUTONOMY_DATA


class MovementCompositionTests(unittest.TestCase):
    def test_direct_transfer_seam_preserves_both_regions_and_entry_hit(self):
        events = []
        for region, e, x in [('a', 1, 3), ('b', 3, 6)]:
            s = {'entryState': e, 'exitState': x, 'exitKind': 'side', 'ticks': 8,
                 'hit': [e - 1 <= t < x - 1 for t in range(8)], 'sneak': [False] * 8}
            events.append({'schedule': s, 'column': {'id': region}})
        units = movement_program(8, events)
        self.assertEqual([(u['start'],u['end']) for u in units], [(0,2),(2,6),(6,8)])
        masks = compile_units(8, units)
        self.assertEqual(masks['ladderTicks'], [False] + [True] * 5 + [False] * 3)
        self.assertEqual(masks['hit'], [True] * 5 + [False] * 3)

    def test_endpoint_stops_keep_their_unit_and_change_actual_y_carry(self):
        request = json.loads((ROOT / 'fixtures/ladder-schedule-generator.json').read_text())
        request.update(finishAtExit=True, maxSchedules=2000)
        pool = generate_ladder_schedules(request)['schedules']
        variants = {(s['beginningStop'],s['endStop']): s for s in pool
                    if s['climbWindow'] == [0,6] and not any(s['sneak'])}
        self.assertEqual(set(variants), {(False,False),(True,False),(False,True),(True,True)})
        baseline = variants[False,False]
        for key, s in variants.items():
            self.assertEqual([u['type'] for u in s['movementUnits']], ['ladder_climb'])
            self.assertEqual(s['ticks'], 7)
            self.assertEqual(s['trace']['position'][0], 2)
            self.assertEqual(s['trace']['velocity'][0], REST_CARRY)
            self.assertEqual(s['movementUnits'][0]['stopInputs'], s['stopInputs'])
            if key != (False,False):
                self.assertLess(s['trace']['position'][-1], baseline['trace']['position'][-1])
            if key[1]:
                self.assertFalse(s['trace']['climbRefreshed'][5])
                self.assertNotEqual(s['trace']['velocity'][6], baseline['trace']['velocity'][6])

    def test_units_retime_together_with_runs_hits_and_sneak(self):
        scene, _, p = airborne_case()
        edge = dict(source='start',target='b',profileKind='ladder',profile=p['profile'],
                    ladderEvents=p['ladderEvents'],entryFace=None,exitFace=None)
        route = compile_block_path(scene, ['start','b'], [edge])['route']
        route.update(maxTotalRunTicks=2, runBounds=[[0,2]])
        route = prepare_route(route)
        a,b = schedule(route,[0]),schedule(route,[2])
        self.assertEqual(b['movementUnits'][0], {'type':'normal','start':0,'end':2,'grounded':True})
        for before,after in zip(a['movementUnits'],b['movementUnits'][1:]):
            self.assertEqual(after['type'],before['type'])
            self.assertEqual(after['start'],before['start']+2)
            self.assertEqual(after['end'],before['end']+2)
            for key in ('hitWindow','occupiedStates','hitInputs','stopInputs','sneakInputs'):
                if key in before:
                    self.assertEqual(after[key],[t+2 for t in before[key]])

    def test_actual_extra_wall_contact_cannot_restart_climbing(self):
        _,_,p = airborne_case()
        event = copy.deepcopy(p['ladderEvents'][1])
        s = event['schedule']
        s['hit'][14:18] = [False]*4
        s['trace']['climbRefreshed'][14:18] = [False]*4
        with self.assertRaises(ValueError):
            movement_program(s['ticks'],[event])

    def test_legacy_saved_route_without_units_reconstructs_the_same_program(self):
        scene,_,p=airborne_case()
        edge=dict(source='start',target='b',profileKind='ladder',profile=p['profile'],
                  ladderEvents=p['ladderEvents'],entryFace=None,exitFace=None)
        route=prepare_route(compile_block_path(scene,['start','b'],[edge])['route'])
        expected=schedule(route,[0])['movementUnits']
        del route['movementUnits']
        self.assertEqual(schedule(route,[0])['movementUnits'],expected)

    def test_chain_cancellation_and_prefix_cap_are_explicit(self):
        _,episodes,p = airborne_case()
        first=p['ladderEvents'][0]
        cancel=threading.Event()
        cancel.set()
        r=chain_programs(first,[],[],{'top':3,'bounds':[0,1,2,3]},cancel_event=cancel)
        self.assertEqual(r['termination'],'cancelled')
        self.assertEqual(r['programs'],[])
        r=chain_programs(first,[],[],{'top':3,'bounds':[0,1,2,3]},deadline=time.monotonic()-1)
        self.assertEqual(r['termination'],'deadline')
        with self.assertRaises(ValueError):
            chain_programs(first,[],[],{'top':3,'bounds':[0,1,2,3]},max_pending=0)
        extension=compose_ladder_episodes(1,None,episodes,max_inputs=60)
        with patch('parkour_solver.movement.ladder.transfers.append_ladder_programs',
                   return_value={'programs':[extension,copy.deepcopy(extension)],'generated':2}), \
                patch('parkour_solver.scene.ladders.movement_contexts',return_value=[episodes[1]['wall']]):
            r=chain_programs(first,[episodes[1]['column']],[],{'top':3,'bounds':[0,1,2,3]},
                             max_prefixes=1,max_pending=1)
        self.assertEqual(r['termination'],'prefix_limit')
        self.assertEqual(r['queueDrops'],1)
        self.assertEqual(r['remainingPrefixes'],1)
        self.assertFalse(r['minimumTicksProven'])

    def test_chain_queue_can_extend_through_five_airborne_prefixes(self):
        evidence=json.loads((ROOT/'experiments/contact-combinations/five-airborne-ladders.json').read_text())
        episodes=evidence['episodes']
        prefixes=[compose_ladder_episodes(20,None,episodes[:n],max_inputs=100) for n in range(1,6)]
        columns=[e['column'] for e in episodes]
        first=prefixes[0]['ladderEvents'][0]
        def extend(prefix,column,wall,**kwargs):
            n=len(prefix['episodes'])
            return {'programs':[prefixes[n]] if column['id']==columns[n]['id'] else [],'generated':1}
        with patch('parkour_solver.movement.ladder.transfers.append_ladder_programs',side_effect=extend), \
                patch('parkour_solver.scene.ladders.movement_contexts',return_value=[episodes[0]['wall']]):
            r=chain_programs(first,columns,[],{'top':8,'bounds':[-1,1,9,15]},max_inputs=100,max_interactions=5)
        self.assertTrue(any(len(p['episodes'])==5 for p in r['programs']))
        final=next(p for p in r['programs'] if len(p['episodes'])==5)
        self.assertEqual(final['profile']['airtime'],58)
        self.assertEqual(sum(final['profile']['jump']),1)
        self.assertFalse(any(final['profile']['grounded'][1:-1]))
        self.assertEqual(r['expandedPrefixes'],5)

    def test_append_reoptimizes_a_complete_prefix_instead_of_requiring_a_floor(self):
        _,episodes,p=airborne_case()
        prefix=open_program(p['ladderEvents'][0])
        result=append_ladder_programs(prefix,episodes[1]['column'],episodes[1]['wall'],
            toward=[.7,2.5],max_inputs=48,max_entry=6,max_programs=64)
        self.assertTrue(result['programs'])
        self.assertTrue(result['unprobedEntries'])
        next_prefix=next(p for p in result['programs'] if p['episodes'][-1]['entryState']==11
                         and p['episodes'][-1]['exitKind']=='top')
        self.assertEqual(next_prefix['terminal'],'airborne')
        self.assertEqual(next_prefix['episodes'][0]['entryState'],1)
        self.assertEqual(next_prefix['trace']['position'][:7],prefix['trace']['position'])
        self.assertEqual(next_prefix['trace']['velocity'][:7],prefix['trace']['velocity'])
        self.assertEqual(sum(next_prefix['profile']['jump']),1)
        self.assertFalse(any(next_prefix['profile']['grounded'][1:]))

    def test_real_append_generator_repeats_across_five_climbing_regions(self):
        _,_,p=airborne_case()
        prefix=open_program(p['ladderEvents'][0])
        for i in range(1,5):
            column={'id':'climb:'+str(i),'x':0,'z':2*i,'minY':0,'maxY':i+2}
            wall=copy.deepcopy(p['ladderEvents'][1]['wall'])
            wall['parts']=[{'solidIndex':i,'box':[1,0,2*i,2,i+2,2*i+1]}]
            result=append_ladder_programs(prefix,column,wall,toward=[.7,9],
                max_inputs=110,max_entry=6,max_programs=64)
            options=[p for p in result['programs'] if p['episodes'][-1]['entryState']==prefix['profile']['airtime']+5
                     and p['episodes'][-1]['exitKind']=='top']
            self.assertTrue(options,{'count':i+1,'generation':result.get('generation')})
            next_prefix=options[0]
            cut=prefix['profile']['airtime']
            self.assertEqual(next_prefix['trace']['position'][:cut+1],prefix['trace']['position'])
            self.assertEqual(next_prefix['trace']['velocity'][:cut+1],prefix['trace']['velocity'])
            prefix=next_prefix
        self.assertEqual(len(prefix['episodes']),5)
        self.assertEqual(prefix['terminal'],'airborne')
        self.assertEqual(sum(prefix['profile']['jump']),1)
        self.assertFalse(any(prefix['profile']['grounded'][1:]))
        self.assertEqual([e['exitState'] for e in prefix['episodes']],[6,25,43,62,81])


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set PKC_RUN_TICKS_COMPILER and PKC_IPOPT_PYTHON')
class MovementCompositionIntegrationTests(unittest.TestCase):
    def test_five_airborne_ladders_and_bottom_exit_solve_from_episode_contracts(self):
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            for name,count in [('five-airborne-ladders',5),('ladder-bottom-exit',1)]:
                with self.subTest(name=name):
                    e=json.loads((ROOT/'experiments/contact-combinations'/(name+'.json')).read_text())
                    fixture=ROOT/e['fixture']
                    self.assertEqual(hashlib.sha256(fixture.read_bytes()).hexdigest(),e['fixtureSha256'])
                    scene=prepare_scene(json.loads(fixture.read_text()))
                    p=compose_ladder_episodes(scene['initial']['position'][1],scene['blocks'][-1]['top'],
                                              e['episodes'],max_inputs=100)
                    edge=dict(source='start',target='goal',profileKind='ladder',profile=p['profile'],
                              ladderEvents=p['ladderEvents'],entryFace=None,exitFace=None)
                    c=compile_block_path(scene,['start','goal'],[edge])
                    r=solve_contact_branches(c['route'],c['profiles'],floor_checks(scene,['start','goal'],c['profiles']),
                        compiler,seconds=30,candidate_seconds=5,retries=1,geometry=scene['collisionScene'],
                        motion_bound={'storedSpeedNorm':2,'movementAfterSupportCheck':2},
                        python_executable=os.environ['PKC_IPOPT_PYTHON'])
                    self.assertEqual(r['status'],'feasible',r)
                    b=r['best']
                    self.assertTrue(b['geometryCheck']['feasible'])
                    self.assertEqual(len(b['route']['ladderEvents']),count)
                    self.assertFalse(any(b['candidate']['onGround'][1:-1]))
                    self.assertEqual(sum(b['plan']['jump']),1)
                    if name=='ladder-bottom-exit':
                        s=b['route']['ladderEvents'][0]['schedule']
                        self.assertEqual(s['exitKind'],'bottom')
                        self.assertGreaterEqual(b['candidate']['y'][s['exitState']-1],20)
                        self.assertLess(b['candidate']['y'][s['exitState']],20)
                    problem=prepare(e['problem'])
                    candidate=validate(problem,e['candidate']['yaw'])
                    self.assertTrue(candidate['feasible'])
                    self.assertEqual(candidate['positions'],e['candidate']['positions'])
                    self.assertEqual(candidate['velocity'],e['candidate']['velocity'])
                    self.assertEqual(candidate['y'],e['candidate']['y'])
                    vertical=dict(vertical=problem['vertical'],y=candidate['y'],vy=candidate['vy'],onGround=candidate['onGround'])
                    self.assertTrue(check_geometry(e['geometry'],e['plan'],e['candidate'],vertical,compiler,5)['feasible'])


if __name__ == '__main__':
    unittest.main()
