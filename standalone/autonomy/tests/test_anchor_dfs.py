import copy
import json
import unittest
from unittest.mock import patch

from standalone.autonomy.cell_routes.checkpoint_search import find_checkpoint_route
from standalone.autonomy.cell_routes.node_feasibility import solve_due
from standalone.autonomy.tests.test_anchor_policy import scene, history


def extend(s,parent,target,n,extra=False):
    p=history(s,[]);start=parent['profile']['airtime']
    p['profile'].update(airtime=n,position=[1.]*(n+1),velocity=[0.]*(n+1),grounded=[True]*(n+1),
        jump=[False]*n,sneak=[False]*n,ladderTicks=[False]*(n+1))
    p['profile']['jump'][:start]=parent['profile']['jump']
    p['profile']['jump'][start]=True
    if extra:p['profile']['jump'][start+1]=True
    p['profile']['contactEvents']=copy.deepcopy(parent['profile']['contactEvents'])
    b=next(b for b in s['blocks'] if b['id']==target)
    p['profile']['contactEvents'].append(dict(offset=n-1,axis='Y',direction=-1,
        box=s['collisionScene']['solids'][b['solidIndex']]))
    p['terminalSupport']=target
    p['decisionDepth']=parent.get('decisionDepth',0)+1
    return p


class AnchorDFSTests(unittest.TestCase):
    def run_tree(self, *, logging=True, resume=None, calls=20, interval=1):
        s=scene();s['maxTotalRunTicks']=60;s['maxRunTicksPerJump']=60;dispatched=[]
        def generate(target_scene,*,report,initial_programs,continuation=None,node_evaluator,**kwargs):
            saved=copy.deepcopy(continuation) if continuation else dict(expanded=1,cursor=0,exhausted=False)
            report['continuation']=saved
            parent=initial_programs[0];target=target_scene['goalBlock']
            if target=='mid':
                choices=[extend(s,parent,'mid',10),extend(s,parent,'mid',11)]
            else:
                # Certifying subtree learns the global hard latch. A later
                # incoming history must not reopen a two-jump local structure.
                choices=[extend(s,parent,'goal',20,extra=parent['profile']['airtime']==11)]
            while saved['cursor']<len(choices):
                p=choices[saved['cursor']];saved['cursor']+=1
                yield p
            saved['exhausted']=True
        def evaluate(target_scene,p,compiler,adapter,work,motion,**kwargs):
            work.before_solve({})
            dispatched.append((p['terminalSupport'],p['profile']['airtime'],kwargs.get('solve_policy')))
            n=p['profile']['airtime']
            candidate=dict(yaw=[0.]*n,positions=[[.5,.5]]*(n+1),velocity=[[0.,0.]]*(n+1),objective=0.)
            best=dict(candidate=candidate,plan=dict(ticks=n,walls=[]),allocation=[0],cost=0)
            return dict(status='feasible',termination='done',best=best,work=dict(solverCalls=1),
                        fullSceneValidated=True,nodeFeasibility=dict(status='feasible',model=str(n)))
        with patch('standalone.autonomy.cell_routes.checkpoint_search._motion_bound',return_value={}), \
             patch('standalone.autonomy.cell_routes.depth_first.iter_movement_programs',side_effect=generate), \
             patch('standalone.autonomy.cell_routes.checkpoint_search.evaluate_program',side_effect=evaluate), \
             patch('standalone.autonomy.cell_routes.checkpoint_experiment.replay_witness',return_value=dict(passed=True)):
            result=find_checkpoint_route(s,object(),anchor_search=dict(anchors=['mid'],observability=logging,
                solveEveryDecisionLevels=interval),minimum_run_pruning=False,seconds=3,score_seconds=2,
                max_solver_calls=calls,resume_state=resume)
        return result,dispatched

    def test_global_latch_survives_abandoned_certifier_and_changed_incoming_prefix(self):
        result,calls=self.run_tree()
        self.assertEqual([(b,t) for b,t,_ in calls],[('mid',10),('goal',20),('mid',11)])
        dfs=result['searchState']['dfs']
        self.assertIn('mid→goal',dfs['hardLatches'])
        self.assertIn('start→mid',dfs['hardLatches'])
        self.assertGreaterEqual(sum(t['kind']=='backtrack' for t in dfs['transitions']),2)
        self.assertTrue(any(t['kind']=='candidate_exclusion' for t in dfs['transitions']))
        self.assertEqual(dfs['best']['plan']['ticks'],20)

    def test_logging_does_not_change_solver_allocation_and_anchors_force_queries(self):
        on,on_calls=self.run_tree(logging=True,interval=4)
        off,off_calls=self.run_tree(logging=False,interval=4)
        self.assertEqual(on_calls,off_calls)
        self.assertEqual(on['work']['solverCalls'],off['work']['solverCalls'])
        self.assertEqual(on['searchState']['dfs']['hardLatches'],off['searchState']['dfs']['hardLatches'])
        self.assertTrue(on['searchState']['dfs']['observations']['decisions'])
        self.assertEqual(off['searchState']['dfs']['observations']['decisions'],{})
        self.assertTrue(all(p['stopOnFeasible'] for b,t,p in on_calls if b=='mid'))
        self.assertTrue(all(not p['stopOnFeasible'] for b,t,p in on_calls if b=='goal'))

    def test_latch_resume_keeps_exact_parent_and_does_not_repeat_scored_nodes(self):
        first,calls1=self.run_tree(calls=2)
        saved=json.loads(json.dumps(first['searchState']))
        second,calls2=self.run_tree(resume=saved)
        self.assertEqual([(b,t) for b,t,_ in calls1+calls2],[('mid',10),('goal',20),('mid',11)])
        self.assertEqual(first['searchState']['dfs']['hardLatches'],second['searchState']['dfs']['hardLatches'])

    def test_completed_decision_cadence_and_forced_anchor_or_terminal(self):
        for interval,expected in ((1,[1,2,3,4]),(2,[2,4]),(4,[4])):
            solved=[];last=0
            for depth in (1,2,3,4):
                p=dict(decisionDepth=depth,lastSolvedDepth=last)
                if solve_due(p,interval):solved.append(depth);last=depth
            self.assertEqual(solved,expected)
        self.assertTrue(solve_due(dict(decisionDepth=1,lastSolvedDepth=0),4,forced=True))
        self.assertFalse(solve_due(dict(decisionDepth=1,lastSolvedDepth=1),1))

    def test_failed_finish_restores_deferred_runs_on_actual_parent_without_redeferring(self):
        from standalone.autonomy.block_search import prepare_scene
        from standalone.autonomy.tests.test_checkpoint_guidance import flat_scene
        from standalone.autonomy.tests.test_minimum_runs import flight
        s=flat_scene();s['goalBlock']='finish';s['maxTotalRunTicks']=60;s['maxRunTicksPerJump']=60
        # Extend the normalized scene through its raw request representation.
        raw={k:v for k,v in s.items() if k not in ('collisionScene',)}
        raw['blocks']=[dict(id=b['id'],boxes=[s['collisionScene']['solids'][b['solidIndex']]]) for b in s['blocks']]
        raw['blocks'].append(dict(id='finish',boxes=[[8,0,0,9,1,1]]))
        s=prepare_scene(raw);calls=[]
        def generate(target_scene,*,report,initial_programs,continuation=None,**kwargs):
            saved=copy.deepcopy(continuation) if continuation else dict(expanded=1,cursor=0,exhausted=False)
            report['continuation']=saved
            if target_scene['goalBlock']=='goal':
                base=dict(s,goalBlock='goal')
                choices=[flight(base,r) for r in (0,1,2)]
            else:choices=[extend(s,initial_programs[0],'finish',30)]
            while saved['cursor']<len(choices):
                p=choices[saved['cursor']];saved['cursor']+=1;yield p
            saved['exhausted']=True
        def evaluate(target_scene,p,compiler,adapter,work,motion,**kwargs):
            work.before_solve({});n=p['profile']['airtime'];calls.append((p['terminalSupport'],n))
            rejected=p['terminalSupport']=='finish' and p['profile']['jump'][12]
            best=None if rejected else dict(plan=dict(ticks=n,walls=p['profile']['positionConstraints']),
                candidate=dict(yaw=[0.]*n,positions=[[.5,.5]]*(n+1),velocity=[[0.,0.]]*(n+1),objective=0.),
                allocation=[0],cost=0)
            return dict(status='unknown' if rejected else 'feasible',termination='done',best=best,
                work=dict(solverCalls=1),fullSceneValidated=not rejected,
                repairState=dict(pending=[],unresolved=[],constraintGeneration=dict(exhausted=True)))
        with patch('standalone.autonomy.cell_routes.checkpoint_search._motion_bound',return_value={}), \
             patch('standalone.autonomy.cell_routes.depth_first.iter_movement_programs',side_effect=generate), \
             patch('standalone.autonomy.cell_routes.checkpoint_search.evaluate_program',side_effect=evaluate):
            result=find_checkpoint_route(s,object(),anchor_search=dict(anchors=['goal']),seconds=3,max_solver_calls=20)
        dfs=result['searchState']['dfs']
        self.assertEqual(calls[:4],[('goal',12),('finish',30),('goal',13),('finish',30)])
        self.assertEqual(result['anchorSearch']['runDeferrals'],2)
        self.assertEqual(result['anchorSearch']['runRestorations'],2)
        self.assertEqual(calls.count(('goal',13)),1)
        self.assertEqual(calls.count(('goal',14)),1)
        self.assertIsNotNone(result['best'])
        self.assertTrue(all(r['program']['terminalSupport']=='goal' for r in dfs['minimumRunExclusions']))


if __name__=='__main__':unittest.main()
