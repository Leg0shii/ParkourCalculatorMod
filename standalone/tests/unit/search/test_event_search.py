import copy
import json
import unittest
from unittest.mock import patch

from parkour_solver.search.support_profiles import prepare_scene
from parkour_solver.search.engine import find_checkpoint_route
from parkour_solver.spatial.event_routes import build_route_domain, build_event_graph, initial_cursor, next_goals
from parkour_solver.search.events.adapter import EventGuideAdapter
from parkour_solver.search.events.engine import discovery_settings
from tests.unit.spatial.test_checkpoint_guidance import flat_scene
from tests.unit.search.test_depth_first import program
from parkour_solver.movement.program import empty_program, boundary_state
from parkour_solver.movement.generation import generate_choices


class EventSearchTests(unittest.TestCase):
    def test_quick_reverse_landings_do_not_preempt_forward_goal_service(self):
        scene=prepare_scene(dict(version=1,sceneModel='collision_boxes',maxWallHits=0,
            initial=dict(position=[.5,1,.5],velocity=[0,-.0784000015258789,0],yaw=0),
            geometryBounds=[-5,-5,-5,12,10,5],startBlock='start',goalBlock='goal',blocks=[
                dict(id=name,boxes=[[x,0,0,x+1,height,1]])
                for name,x,height in [('start',0,1),('mid',2,2),('next',4,3),('goal',6,4)]],
            movementSearch=dict(types=['normal'],maxInputs=30)))
        data=build_route_domain(scene,dict(kind='landing',block='goal')).to_dict()
        adapter=EventGuideAdapter(data,build_event_graph(data),motion=dict(movementAfterSupportCheck=.5))
        p=program(scene,4,4);p['terminalSupport']='mid';p['profile']['position'][-1]=2
        cursor=adapter.initial_cursor([p])
        requests,_,_=adapter.next_goals(cursor,[p])
        concrete=[r for r in requests if not r['localSetupAlias']]
        self.assertEqual(concrete[0]['goal']['block'],'next')
        self.assertIn('start',[r['goal'].get('block') for r in concrete])
    def test_disconnected_regions_on_one_physical_block_have_contact_phase_guards(self):
        scene=prepare_scene(dict(version=1,sceneModel='collision_boxes',maxWallHits=0,
            initial=dict(position=[.5,1,.5],velocity=[0,-.0784000015258789,0],yaw=0),
            geometryBounds=[-5,-5,-7,12,10,7],startBlock='floor',goalBlock='goal',blocks=[
                dict(id='floor',boxes=[[0,0,0,6,1,1]]),dict(id='wall',boxes=[[2,1,-5,3,6,5]]),
                dict(id='goal',boxes=[[8,0,0,9,1,1]])],movementSearch=dict(types=['normal'])))
        data=build_route_domain(scene,dict(kind='landing',block='goal')).to_dict()
        adapter=EventGuideAdapter(data,build_event_graph(data))
        root=empty_program(scene['initial'],threshold=0.);root['terminalSupport']='floor'
        cursor=adapter.initial_cursor([root]);requests,_,_=adapter.next_goals(cursor,[root])
        right=next(s for s in data['surfaces'] if s['owners']==['floor'] and min(b[0] for b in s['cover'])>3)
        request=next(r for r in requests if r['goal'].get('surface')==right['id'])
        p=program(scene,2,2);p['terminalSupport']='floor'
        p['profile']['grounded']=[True,False,True]
        p['profile']['contactEvents']=[dict(offset=1,axis='Y',direction=-1,box=scene['collisionScene']['solids'][0])]
        view=adapter.for_goal(request,cursor).bind_program(p)
        candidate=dict(positions=[[.5,.5],[.5,.5],[.5,.5]])
        self.assertFalse(view.candidate({},candidate,{})['accepted'])
        rows=view.contact_regions(p,2)
        self.assertTrue(rows)
        self.assertTrue(all(r['offset']==1 for option in rows for r in option))
        candidate['positions'][1]=[4.,.5]
        self.assertTrue(view.candidate({},candidate,{})['accepted'])
        self.assertTrue(any(all((candidate['positions'][1][0 if r['mode']=='X' else 1]-r['rhs'])*
            (1 if r['cmp']=='GE' else -1)>=0 for r in option) for option in rows))
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

    def test_declared_anchor_goals_share_prefix_and_publish_actual_child_atomically(self):
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
        with patch('parkour_solver.search.engine._motion_bound',return_value=dict(movementAfterSupportCheck=.5)), \
             patch('parkour_solver.search.dfs.iter_movement_programs',side_effect=generate), \
             patch('parkour_solver.search.engine.evaluate_program',side_effect=evaluate):
            result=find_checkpoint_route(scene,object(),anchor_search=dict(anchors=['mid']),seconds=2,score_seconds=1,max_solver_calls=1,
                                         score_call_limit=1,on_progress=progress)
        self.assertEqual(dispatched,[('mid',10)])
        self.assertTrue(published)
        dfs=result['searchState']['dfs']
        self.assertEqual(dfs['version'],8)
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
