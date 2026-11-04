import copy
import json
import unittest
from unittest.mock import patch

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.cell_routes.checkpoint_search import find_checkpoint_route
from standalone.autonomy.cell_routes.event_routes import build_route_domain, build_event_graph, initial_cursor, next_goals
from standalone.autonomy.cell_routes.event_adapter import EventGuideAdapter
from standalone.autonomy.cell_routes.event_search import discovery_settings
from standalone.autonomy.tests.test_checkpoint_guidance import flat_scene
from standalone.autonomy.tests.test_depth_first import program
from standalone.autonomy.movement_composition import empty_program, boundary_state
from standalone.autonomy.movement_generation import generate_choices


class EventSearchTests(unittest.TestCase):
    def test_same_support_setup_is_local_without_removing_its_physical_choices(self):
        scene=flat_scene();data=build_route_domain(scene,dict(kind='landing',block='goal')).to_dict()
        adapter=EventGuideAdapter(data,build_event_graph(data))
        root=empty_program(scene['initial'],threshold=0.);root['terminalSupport']='start'
        cursor=adapter.initial_cursor([root])
        requests,_,_=adapter.next_goals(cursor,[root])
        self.assertTrue(any(r['localSetupAlias'] for r in requests))
        view=adapter.for_goal(next(r for r in requests if r['terminal']),cursor)
        ctx=dict(mode='open',scene=scene,program=root,state=boundary_state(root),maxInputs=20,threshold=0.)
        view.context(ctx)
        choices=list(generate_choices('normal',ctx))
        self.assertIn('start',[c['terminalSupport'] for c in choices])
        self.assertIn('goal',[c['terminalSupport'] for c in choices])
    def test_adapter_has_no_inherited_raw_cell_rejection_paths(self):
        scene=flat_scene();data=build_route_domain(scene,dict(kind='landing',block='goal')).to_dict()
        adapter=EventGuideAdapter(data,build_event_graph(data))
        cursor=adapter.initial_cursor(None)
        requests,_,_=adapter.next_goals(cursor, [program(scene,1,1)])
        view=adapter.for_goal(next(r for r in requests if r['terminal']),cursor)
        for name in ('box_allowed','region_allowed','prefix','constraint_guards','program_states'):
            self.assertFalse(hasattr(view,name),name)
        ctx=dict(program=dict(terminalSupport='start'))
        view.context(ctx)
        self.assertEqual(set(ctx),{'program','supportAllowed'})
        self.assertNotIn('geometry',view.corridor.to_dict())

    def test_default_dynamic_goals_share_prefix_and_publish_actual_child_atomically(self):
        scene=prepare_scene(dict(version=1,sceneModel='collision_boxes',maxWallHits=0,
            initial=dict(position=[.5,1,.5],velocity=[0,-.0784000015258789,0],yaw=0),
            startBlock='start',goalBlock='goal',geometryBounds=[-5,-5,-5,10,10,5],blocks=[
                dict(id='start',boxes=[[0,0,0,1,1,1]]),dict(id='mid',boxes=[[2,0,0,3,2,1]]),
                dict(id='goal',boxes=[[4,0,0,5,3,1]])],movementSearch=dict(types=['normal'],maxInputs=30)))
        dispatched=[];published=[]
        def generate(s,*,report,initial_programs,**kwargs):
            report['continuation']=dict(expanded=1,exhausted=True)
            parent=initial_programs[0]
            n=parent['profile']['airtime']+10
            p=program(s,n,n);p['terminalSupport']=s['goalBlock']
            target=next(b for b in s['blocks'] if b['id']==s['goalBlock'])
            p['profile']['position'][-1]=target['top']
            p['profile']['contactEvents']=[dict(offset=n-1,axis='Y',direction=-1,box=s['collisionScene']['solids'][target['solidIndex']])]
            yield p
        def evaluate(s,p,compiler,adapter,work,motion,**kwargs):
            work.before_solve({});dispatched.append((p['terminalSupport'],p['profile']['airtime']))
            n=p['profile']['airtime']
            best=dict(candidate=dict(yaw=[0.]*n,positions=[[.5,0.]]*(n+1),velocity=[[0.,0.]]*(n+1),objective=0.),
                plan=dict(ticks=n,walls=[]),profiles=[p['profile']],allocation=[0],cost=0)
            return dict(status='feasible',termination='done',best=best,work=dict(solverCalls=1),fullSceneValidated=True)
        def progress(state):
            dfs=state['dfs']
            chosen=[t for t in dfs['transitions'] if t['kind']=='choose' and t['depth']==0]
            if chosen:
                owner=next(d for d in dfs['discovery'].values() if d['parent'] and d['parent']['candidate']==chosen[-1]['candidate'])
                published.append(owner['owner'])
        with patch('standalone.autonomy.cell_routes.checkpoint_search._motion_bound',return_value=dict(movementAfterSupportCheck=.5)), \
             patch('standalone.autonomy.cell_routes.depth_first.iter_movement_programs',side_effect=generate), \
             patch('standalone.autonomy.cell_routes.checkpoint_search.evaluate_program',side_effect=evaluate):
            result=find_checkpoint_route(scene,object(),seconds=2,score_seconds=1,max_solver_calls=1,
                                         score_call_limit=1,on_progress=progress)
        self.assertEqual(dispatched,[('mid',10)])
        self.assertTrue(published)
        dfs=result['searchState']['dfs']
        self.assertEqual(dfs['version'],7)
        child_owner=dfs['discovery'][dfs['pendingGoalOwner']]
        self.assertEqual(child_owner['index'],1)
        self.assertEqual(child_owner['parents'][0]['profile']['airtime'],10)
        root=next(d for d in dfs['discovery'].values() if d['parent'] is None)
        mid=[f for f in dfs['stack']+dfs['goalQueue'] if f['owner']==root['owner'] and f['goal']['block']=='mid']
        self.assertEqual(len(mid),1)
        json.dumps(result['searchState'],allow_nan=False)

    def test_explicit_old_catalogue_settings_and_states_are_not_reinterpreted(self):
        with self.assertRaisesRegex(ValueError,'Legacy spatialSettings'):
            find_checkpoint_route(flat_scene(),None,spatial_settings=dict(max_routes=1))
        with self.assertRaisesRegex(ValueError,'Incompatible automatic event'):
            find_checkpoint_route(flat_scene(),None,resume_state=dict(version=1,kind='semantic_route_queue'))
        with self.assertRaises(ValueError):discovery_settings(dict(maxGuideMaterializations=0))
        with self.assertRaises(ValueError):discovery_settings(dict(max_visits=2))


if __name__=='__main__':unittest.main()
