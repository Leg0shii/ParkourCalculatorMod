import copy
import json
import threading
import time
import unittest
from unittest.mock import patch

from parkour_solver.search.engine import find_checkpoint_route, ScopedWork
from parkour_solver.search.feasibility import solve_due
from tests.support.anchor_policy import scene, history


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
        boxes=[s['collisionScene']['solids'][b['solidIndex']]]))
    p['terminalSupport']=target
    p['decisionDepth']=parent.get('decisionDepth',0)+1
    return p


class AnchorDFSTests(unittest.TestCase):
    def run_tree(self, *, logging=True, resume=None, calls=20, interval=1, setup=False, seconds=3,
                 on_progress=None,cancel_event=None,requests_per_evaluation=1,advance=None,node_probe=None,node_best=None,**settings):
        s=scene();s['maxTotalRunTicks']=60;s['maxRunTicksPerJump']=60;dispatched=[]
        def generate(target_scene,*,report,initial_programs,continuation=None,node_evaluator,**kwargs):
            saved=copy.deepcopy(continuation) if continuation else dict(expanded=1,cursor=0,exhausted=False)
            report['continuation']=saved
            parent=initial_programs[0];target=target_scene['goalBlock']
            if target=='mid':
                choices=[extend(s,parent,'mid',10),extend(s,parent,'mid',11)]
                if setup:
                    prepared=extend(s,parent,'mid',11)
                    prepared['profile']['jump'][1]=True
                    start=next(b for b in s['blocks'] if b['id']=='start')
                    prepared['profile']['contactEvents'].insert(0,dict(offset=0,axis='Y',direction=-1,
                        boxes=[s['collisionScene']['solids'][start['solidIndex']]]))
                    choices.append(prepared)
            else:
                # Certifying subtree learns the global hard latch. A later
                # incoming history must not reopen a two-jump local structure.
                choices=[extend(s,parent,'goal',20,extra=parent['profile']['airtime']==11)]
            while saved['cursor']<len(choices):
                p=choices[saved['cursor']];saved['cursor']+=1
                if node_probe is not None and target=='mid':node_probe(node_evaluator,p)
                yield p
            saved['exhausted']=True
        def evaluate(target_scene,p,compiler,adapter,work,motion,**kwargs):
            scoped=ScopedWork(work,kwargs['solver_calls'],min(work.deadline,time.monotonic()+kwargs['seconds']))
            for _ in range(requests_per_evaluation):
                scoped.before_solve({})
                if advance is not None:advance()
            dispatched.append((p['terminalSupport'],p['profile']['airtime'],kwargs.get('solve_policy')))
            n=p['profile']['airtime']
            candidate=dict(yaw=[0.]*n,positions=[[.5,.5]]*(n+1),velocity=[[0.,0.]]*(n+1),objective=0.)
            best=dict(candidate=candidate,plan=dict(ticks=n,walls=[]),allocation=[0],cost=0)
            if node_best is not None and (kwargs.get('solve_policy') or {}).get('purpose')=='node':
                extra=node_best(n)
                best['plan'].update(walls=extra['walls'],events=extra['events'])
                best['alternativeRows']=extra['alternativeRows']
            return dict(status='feasible',termination='done',best=best,work=dict(solverCalls=requests_per_evaluation),
                        fullSceneValidated=True,nodeFeasibility=dict(status='feasible',model=str(n)))
        with patch('parkour_solver.search.engine._motion_bound',return_value={}), \
             patch('parkour_solver.search.dfs.iter_movement_programs',side_effect=generate), \
             patch('parkour_solver.search.engine.evaluate_program',side_effect=evaluate), \
             patch('parkour_solver.validation.witness.replay_witness',return_value=dict(passed=True)):
            options=dict(seconds=seconds,score_seconds=2,max_solver_calls=calls)
            options.update(settings)
            result=find_checkpoint_route(s,object(),anchor_search=dict(anchors=['mid'],observability=logging,
                solveEveryDecisionLevels=interval),minimum_run_pruning=False,
                resume_state=resume,on_progress=on_progress,cancel_event=cancel_event,**options)
        return result,dispatched

    def test_anchor_search_uses_run_budget_across_routes_and_constraint_requests(self):
        clock=[100.]
        def advance():clock[0]+=.05
        with patch('time.monotonic',side_effect=lambda:clock[0]):
            result,calls=self.run_tree(calls=100,seconds=100,requests_per_evaluation=30,advance=advance,
                score_seconds=.01,score_call_limit=1,final_call_limit=1)
        self.assertEqual([(b,t) for b,t,_ in calls],[('mid',10),('goal',20),('mid',11)])
        self.assertEqual(result['work']['solverCalls'],90)
        allocations=result['searchState']['allocations']
        self.assertEqual(len(allocations),1)
        self.assertEqual(allocations[0]['calls'],90)
        self.assertEqual(result['best']['plan']['ticks'],20)

    def test_global_deadline_still_stops_anchor_search_and_keeps_continuation(self):
        clock=[100.]
        def advance():clock[0]+=2.
        with patch('time.monotonic',side_effect=lambda:clock[0]):
            first,calls1=self.run_tree(seconds=1,advance=advance)
        self.assertEqual(first['termination'],'deadline')
        self.assertEqual([(b,t) for b,t,_ in calls1],[('mid',10)])
        second,calls2=self.run_tree(resume=json.loads(json.dumps(first['searchState'])))
        self.assertEqual([(b,t) for b,t,_ in calls1+calls2],[('mid',10),('goal',20),('mid',11)])
        self.assertEqual(second['best']['plan']['ticks'],20)

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
        self.assertTrue(all(p['purpose']=='anchor' and not p['stopOnFeasible'] and p['rankEqualCost'] for b,t,p in on_calls if b=='mid'))
        self.assertTrue(all(p['purpose']=='terminal' and not p['stopOnFeasible'] for b,t,p in on_calls if b=='goal'))

    def test_latch_resume_keeps_exact_parent_and_does_not_repeat_scored_nodes(self):
        first,calls1=self.run_tree(calls=2)
        saved=json.loads(json.dumps(first['searchState']))
        second,calls2=self.run_tree(resume=saved)
        self.assertEqual([(b,t) for b,t,_ in calls1+calls2],[('mid',10),('goal',20),('mid',11)])
        self.assertEqual(first['searchState']['dfs']['hardLatches'],second['searchState']['dfs']['hardLatches'])

    def test_resume_keeps_nested_incumbent_even_without_time_for_a_service_slice(self):
        first,_=self.run_tree(calls=2)
        saved=json.loads(json.dumps(first['searchState']))
        saved['best']=None  # A callback may publish DFS before the allocator catches up.
        result,calls=self.run_tree(resume=saved,seconds=1e-6)
        self.assertEqual(result['best']['plan']['ticks'],20)
        self.assertEqual(calls,[])
        self.assertIsNone(saved['best'])

    def queued_tree(self, refinement=False):
        first,_=self.run_tree(calls=2)
        saved=json.loads(json.dumps(first['searchState']))
        root=saved['dfs']['stack'][0]
        template=root['records'][0]
        for n in (11,12,13):
            p=extend(scene(),history(scene(),[]),'mid',n)
            record=copy.deepcopy(template)
            record.update(id='queued-'+str(n),program=p,child=copy.deepcopy(p),arrival=n,ticks=n,tried=False,
                witness=dict(plan=dict(ticks=n,walls=[]),allocation=[0],cost=0,
                    candidate=dict(yaw=[0.]*n,positions=[[.5,.5]]*(n+1),velocity=[[0.,0.]]*(n+1),objective=0.)))
            if refinement:
                record.pop('witness')
                record['program']['repairState']=dict(pending=[dict(branch='unfinished')])
            root['records'].append(record)
        root.update(phase='select',ready=[],sourceExhausted=True)
        return saved

    def check_lazy_queue_bound(self, *, refinement=False, remaining=15):
        saved=self.queued_tree(refinement)
        checked=[];cancel=threading.Event()
        def bound(guides,span,program,motion,**kwargs):
            checked.append(program['profile']['airtime'])
            return dict(remainingTicks=remaining)
        def progress(state):
            last=state['dfs']['transitions'][-1]
            if last['kind']=='candidate_exclusion' or last['kind']=='open' and last['depth']==1:
                cancel.set()
        with patch('parkour_solver.search.bounds.remaining.remaining_bound',side_effect=bound):
            result,calls=self.run_tree(resume=saved,on_progress=progress,cancel_event=cancel)
        self.assertEqual(checked,[11])
        self.assertEqual(calls,[])
        root=result['searchState']['dfs']['stack'][0]
        unselected=[r for r in root['records'] if r['id'] in ('queued-12','queued-13')]
        self.assertTrue(all(not r['tried'] and not r.get('timeBoundExclusion') and not r.get('refinementTimeExclusion') for r in unselected))
        return result

    def test_time_bound_checks_only_selected_arrival_not_other_queued_records(self):
        result=self.check_lazy_queue_bound()
        self.assertEqual(result['searchState']['dfs']['transitions'][-1]['kind'],'candidate_exclusion')

    def test_time_bound_checks_only_selected_refinement_not_other_pending_records(self):
        self.check_lazy_queue_bound(refinement=True)

    def test_equal_optimistic_finish_is_kept_without_checking_other_queued_records(self):
        result=self.check_lazy_queue_bound(remaining=9)
        self.assertEqual(result['searchState']['dfs']['stack'][-1]['parents'][0]['profile']['airtime'],11)

    def test_global_latch_accepts_setup_after_certifier_and_backtracking(self):
        result,calls=self.run_tree(setup=True)
        frames=result['searchState']['dfs']['frames']
        prepared=[r for f in frames if f['index']==0 for r in f['records']
                  if r.get('transferStructure',{}).get('preparationJumps')]
        self.assertEqual(len(prepared),1)
        self.assertEqual(prepared[0]['status'],'feasible')
        self.assertEqual(prepared[0]['transferStructure']['transferJumps'],[1])
        from parkour_solver.search.anchors.policy import HARD_JUMP_POLICY_VERSION
        self.assertEqual(result['searchState']['dfs']['hardLatches']['start→mid']['version'],HARD_JUMP_POLICY_VERSION)
        self.assertEqual(prepared[0]['transferStructure']['assumption'],'hard_transfer_without_stepping')

    def test_incompatible_old_latch_policy_is_rejected_on_resume(self):
        first,_=self.run_tree(calls=2)
        with patch('parkour_solver.search.anchors.policy.HARD_JUMP_POLICY_VERSION',1):
            with self.assertRaisesRegex(ValueError,'Incompatible automatic event'):
                self.run_tree(resume=first['searchState'])

    def test_completed_decision_cadence_and_forced_anchor_or_terminal(self):
        for interval,expected in ((1,[1,2,3,4]),(2,[2,4]),(4,[4])):
            solved=[];last=0
            for depth in (1,2,3,4):
                p=dict(decisionDepth=depth,lastSolvedDepth=last)
                if solve_due(p,interval):solved.append(depth);last=depth
            self.assertEqual(solved,expected)
        self.assertTrue(solve_due(dict(decisionDepth=1,lastSolvedDepth=0),4,forced=True))
        self.assertFalse(solve_due(dict(decisionDepth=1,lastSolvedDepth=1),1))

    def test_decision_check_keeps_no_rows_of_its_chosen_alternative(self):
        chosen=dict(leg=0,offset=3,mode='X',cmp='GE',rhs=.25,name='avoidance')
        kept=dict(mode='Z',t1=2,cmp='LE',rhs=.75,name='route:0')
        checked=[]
        def probe(node_evaluator,p):
            p=copy.deepcopy(p);p['decisionNode']='node:'+str(p['profile']['airtime'])
            checked.append(node_evaluator(p,{},None)['program'])
        def node_best(n):
            return dict(walls=[dict(mode='X',t1=3,cmp='GE',rhs=.25,name='avoidance'),kept],
                        events=[dict(jump=0,landed=0,landing=n,runTicks=0)],alternativeRows=[chosen])
        self.run_tree(node_probe=probe,node_best=node_best)
        walls=[{k:r[k] for k in ('mode','t1','cmp','rhs')} for r in checked[0]['profile']['positionConstraints']]
        self.assertIn(dict(mode='Z',t1=2,cmp='LE',rhs=.75),walls)
        self.assertNotIn(dict(mode='X',t1=3,cmp='GE',rhs=.25),walls)

    def run_recovery_tree(self, reject_first=True, oracle=True, include_setup=False, finish_tick=30,
                          fallbacks=False, reject_all=False):
        from parkour_solver.scene.normalize import prepare_scene
        from tests.support.checkpoint_guidance import flat_scene
        from tests.support.minimum_runs import flight
        s=flat_scene();s['goalBlock']='finish';s['maxTotalRunTicks']=60;s['maxRunTicksPerJump']=60
        # Extend the normalized scene through its raw request representation.
        raw={k:v for k,v in s.items() if k not in ('collisionScene',)}
        raw['blocks']=[dict(id=b['id'],boxes=[s['collisionScene']['solids'][b['solidIndex']]]) for b in s['blocks']]
        raw['blocks'].append(dict(id='finish',boxes=[[8,0,0,9,1,1]]))
        raw['movementSearch']['maxInputs']=max(raw['movementSearch']['maxInputs'],finish_tick+1)
        s=prepare_scene(raw);calls=[]
        def generate(target_scene,*,report,initial_programs,continuation=None,**kwargs):
            saved=copy.deepcopy(continuation) if continuation else dict(expanded=1,cursor=0,exhausted=False)
            report['continuation']=saved
            if target_scene['goalBlock']=='goal':
                base=dict(s,goalBlock='goal')
                choices=[flight(base,r) for r in (0,1,2)]
                if include_setup:
                    from parkour_solver.movement.normal import normal_choices
                    from parkour_solver.movement.program import empty_program, append_choice, boundary_state
                    parent=empty_program(base['initial'],threshold=0.);parent['terminalSupport']='start'
                    options=normal_choices(dict(scene=base,state=boundary_state(parent),maxInputs=30,threshold=0.))
                    parent=append_choice(parent,next(c for c in options if c['terminalSupport']=='start' and c['profile']['jump'][0]))
                    options=normal_choices(dict(scene=base,state=boundary_state(parent),maxInputs=30,threshold=0.))
                    choices.append(append_choice(parent,next(c for c in options if c['terminalSupport']=='goal' and c['profile']['jump'][0])))
            else:
                # A restored fallback's child finishes one tick later, which the stub accepts.
                restored=any(r.get('name')=='fallback' for r in initial_programs[0]['profile']['positionConstraints'])
                choices=[extend(s,initial_programs[0],'finish',finish_tick+restored)]
            while saved['cursor']<len(choices):
                p=choices[saved['cursor']];saved['cursor']+=1;yield p
            saved['exhausted']=True
        def evaluate(target_scene,p,compiler,adapter,work,motion,**kwargs):
            work.before_solve({});n=p['profile']['airtime'];calls.append((p['terminalSupport'],n))
            rejected=(reject_first and p['terminalSupport']=='finish' and n==finish_tick and
                      (reject_all or p['profile']['jump'][12]))
            best=None if rejected else dict(plan=dict(ticks=n,walls=p['profile']['positionConstraints']),
                candidate=dict(yaw=[0.]*n,positions=[[.5,.5]]*(n+1),velocity=[[0.,0.]]*(n+1),objective=0.),
                allocation=[0],cost=0)
            repair=dict(pending=[],unresolved=[],constraintGeneration=dict(exhausted=True))
            if fallbacks and p['terminalSupport']=='goal':
                alternatives=[dict(branch=f'{n}:{i}',best=dict(copy.deepcopy(best),plan=dict(ticks=n,walls=list(best['plan']['walls'])+
                    [dict(mode='X',t1=1,cmp='GE',rhs=-100.-i,name='fallback')]))) for i in (0,1)]
                repair.update(fallbackVariants=alternatives,acceptedVariants=alternatives)
            return dict(status='unknown' if rejected else 'feasible',termination='done',best=best,
                nodeFeasibility=dict(status=('oracle_rejected' if oracle else 'suspended') if rejected else 'feasible',model=str(n)),
                work=dict(solverCalls=1),fullSceneValidated=not rejected,repairState=repair)
        with patch('parkour_solver.search.engine._motion_bound',return_value={}), \
             patch('parkour_solver.search.dfs.iter_movement_programs',side_effect=generate), \
             patch('parkour_solver.search.engine.evaluate_program',side_effect=evaluate):
            result=find_checkpoint_route(s,object(),anchor_search=dict(anchors=['goal']),seconds=3,max_solver_calls=20)
        return result,calls

    def test_failed_finish_restores_deferred_runs_on_actual_parent_without_redeferring(self):
        result,calls=self.run_recovery_tree()
        dfs=result['searchState']['dfs']
        self.assertEqual(calls[:4],[('goal',12),('finish',30),('goal',13),('finish',30)])
        self.assertEqual(result['anchorSearch']['runDeferrals'],2)
        self.assertEqual(result['anchorSearch']['runRestorations'],1)
        self.assertEqual(calls.count(('goal',13)),1)
        self.assertEqual(calls.count(('goal',14)),0)
        self.assertIsNotNone(result['best'])
        self.assertTrue(all(r['program']['terminalSupport']=='goal' for r in dfs['minimumRunExclusions']))

    def test_more_jump_history_remains_first_class_after_an_earlier_finish_succeeds(self):
        result,calls=self.run_recovery_tree(reject_first=False,include_setup=True,finish_tick=50)
        self.assertIn(('goal',24),calls)
        self.assertEqual(calls.count(('finish',50)),2)
        self.assertNotIn(('goal',13),calls)
        self.assertEqual(result['anchorSearch']['runRestorations'],0)
        decisions=result['searchState']['dfs']['comparisonDecisions'].values()
        self.assertTrue(any(d['action']=='keep_protected' and 'more_jump_inputs' in d['protectionReasons'] for d in decisions))

    def test_successful_finish_never_restores_extra_hard_jump_runs(self):
        result,calls=self.run_recovery_tree(reject_first=False)
        self.assertEqual(calls,[('goal',12),('finish',30)])
        self.assertEqual(result['anchorSearch']['runRestorations'],0)
        self.assertEqual(result['anchorSearch']['runDeferrals'],2)

    def test_unknown_descendant_does_not_trigger_infeasible_recovery(self):
        result,calls=self.run_recovery_tree(oracle=False)
        self.assertEqual(calls,[('goal',12),('finish',30)])
        self.assertEqual(result['anchorSearch']['runRestorations'],0)

    def test_anchor_leg_keeps_fallbacks_without_sibling_records(self):
        result,calls=self.run_recovery_tree(reject_first=False,fallbacks=True)
        self.assertEqual(calls,[('goal',12),('finish',30)])
        dfs=result['searchState']['dfs']
        self.assertFalse(any(r.get('variantOf') for f in dfs['frames']+dfs['stack'] for r in f['records']))

    def test_failed_finish_restores_a_fallback_before_slower_runs(self):
        result,calls=self.run_recovery_tree(fallbacks=True)
        self.assertEqual(calls,[('goal',12),('finish',30),('finish',31)])
        self.assertEqual(result['anchorSearch']['runRestorations'],0)
        self.assertEqual(sum(t['kind']=='restore_alternative' for t in result['searchState']['dfs']['transitions']),1)
        self.assertEqual(result['best']['plan']['ticks'],31)

    def test_every_failed_record_keeps_its_fallbacks(self):
        result,calls=self.run_recovery_tree(fallbacks=True,include_setup=True,reject_all=True,finish_tick=50)
        dfs=result['searchState']['dfs']
        frame=next(f for f in dfs['frames']+dfs['stack'] if f['index']==0)
        chosen=[t['candidate'] for t in dfs['transitions'] if t['kind']=='choose' and t['frame']==frame['id']]
        restored=[t['candidate'] for t in dfs['transitions'] if t['kind']=='restore_alternative']
        self.assertGreaterEqual(len(chosen),2)
        self.assertEqual(frame['failedCandidates'][:2],chosen[:2])
        records={r['id']:r for r in frame['records']}
        self.assertEqual(records[restored[0]]['variantOf'],chosen[0])
        self.assertEqual(result['best']['plan']['ticks'],51)

    def test_external_resume_is_isolated_from_owned_internal_service(self):
        first,_=self.run_tree(calls=2)
        saved=json.loads(json.dumps(first['searchState']))
        before=copy.deepcopy(saved)
        self.run_tree(resume=saved)
        self.assertEqual(saved,before)


if __name__=='__main__':unittest.main()
