"""Shared test builders extracted from tests.unit.search.test_depth_first."""
from parkour_solver.movement.program import empty_program, append_choice, truncate_program

def program(scene, n, family):
    p = empty_program(scene['initial'], threshold=0.)
    jumps = [8] if family==17 else [0,12]
    p['profile'].update(airtime=n,position=[1.]*(n+1),velocity=[0.]*(n+1),
        grounded=[(t<8 if family==17 else False) or t in jumps or t==n for t in range(n+1)],
        jump=[t in jumps for t in range(n)],sneak=[False]*n,ladderTicks=[False]*(n+1))
    p.update(terminalSupport='goal',family=family,trace=dict(hit=[False]*n))
    return p

import copy
from unittest.mock import patch
from parkour_solver.search.engine import find_checkpoint_route
from parkour_solver.spatial.checkpoints import Checkpoint
from tests.support.checkpoint_guidance import flat_scene
import unittest


class DepthFirstHarness(unittest.TestCase):
    def run_search(self, limit, state=None):
        scene=flat_scene()
        cp=Checkpoint(30,-1,(1.,0.),(.5,1.,.5),(20.,1.,.5),())
        def generate(s,*,report,continuation=None,initial_programs=None,checkpoint_tick=None,**kwargs):
            saved=copy.deepcopy(continuation) if continuation else dict(cursor=0,expanded=0,exhausted=False)
            report['continuation']=saved
            choices=([program(scene,21,21),program(scene,17,17)] if checkpoint_tick is None else
                [program(scene,30,initial_programs[0]['family'])])
            if checkpoint_tick:
                # The continuation must adopt the selected arrival's inputs and
                # hard decision rows, without fixing its optimized endpoint.
                self.assertEqual(initial_programs[0]['profile']['airtime'],initial_programs[0]['family'])
                self.assertTrue(any(r['name']=='selected_avoid' for r in initial_programs[0]['profile']['positionConstraints']))
            while saved['cursor']<len(choices):
                p=choices[saved['cursor']];saved['cursor']+=1
                yield p
            saved['exhausted']=True;report['termination']='exhausted'
        def evaluate(s,p,compiler,adapter,work,motion,**kwargs):
            work.before_solve({})
            n=p['profile']['airtime']
            self.assertEqual(kwargs['first_witness'],n!=30)
            self.calls.append((p['family'],n))
            if getattr(self,'interrupt_once',None)==(p['family'],n):
                self.interrupt_once=None
                kwargs['on_progress'](dict(marker='unfinished',pending=[dict(branch='active')]))
                raise TimeoutError('suspended_in_flight')
            candidate=dict(yaw=[0.]*n,positions=[[.5,0.]]*(n+1),velocity=[[.2,0.]]*(n+1),objective=float(p['family']))
            best=dict(candidate=candidate,plan=dict(ticks=n,walls=[dict(mode='X',t1=1,cmp='LE',rhs=4.,name='selected_avoid')]),
                profiles=[p['profile']],allocation=[0],cost=0)
            repair=dict(pending=[],unresolved=[],constraintGeneration=dict(exhausted=True))
            if getattr(self,'alternate_once',False) and n==17:
                self.alternate_once=False
                alternate=copy.deepcopy(best)
                alternate['candidate']['objective']-=1
                alternate['plan']['walls'][0]['rhs']=5.
                repair['acceptedVariants']=[dict(branch='a',best=best),dict(branch='b',best=alternate)]
            return dict(status='feasible',termination='done',best=best,work=dict(solverCalls=1),repairState=repair)
        class Policy:
            initial_jump_inputs=[0,8]
            checkpoint_identity=dict(initialJumpInputs=[0,8])
            def __call__(self,p):return True
        with patch('parkour_solver.search.engine._motion_bound',return_value={}), \
             patch('parkour_solver.search.dfs.iter_movement_programs',side_effect=generate), \
             patch('parkour_solver.search.dfs.generate_choices',side_effect=lambda *args,**kwargs:(v for v in ())), \
             patch('parkour_solver.search.engine.evaluate_program',side_effect=evaluate), \
             patch('parkour_solver.validation.witness.replay_witness',return_value=dict(passed=True)):
            return find_checkpoint_route(scene,object(),spatial_points=[[.5,1,.5],[5.5,1,.5]],
                checkpoints=[] if getattr(self,'no_clocks',False) else [dict(tick=10)],spatial_goals=[dict(kind='landing',block='goal')],terminal_checkpoint=cp,
                seconds=3,max_solver_calls=limit,resume_state=state,program_filter=Policy(),
                on_progress=getattr(self,'progress',None))
