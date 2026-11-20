from tests.support.proposal_analysis import Coefficients
from tests.support.paths import REGRESSION_FIXTURES
from tests.support.paths import AUTONOMY_DATA
import gzip
import json
import unittest
from unittest.mock import Mock, patch

from parkour_solver.reachability.service import InteractionGate
from parkour_solver.search.movement import screen_program
from tests.support.checkpoint_guidance import flat_scene
from tests.support.minimum_runs import flight


class ProposalAnalysisTests(unittest.TestCase):
    def test_proposal_and_row_conditioning_share_physics_without_sharing_constraints(self):
        scene=flat_scene();p=flight(scene,0);compiler=Coefficients()
        gate=InteractionGate(scene,compiler)
        first=gate.analyze(p)
        self.assertIs(first,gate.analyze(p))
        conditioned=gate.condition(first,[dict(mode='X',t1=1,cmp='GE',rhs=10000.)])
        self.assertIsNone(first['envelopes']['contradiction'])
        self.assertIsNotNone(conditioned['envelopes']['contradiction'])
        self.assertEqual(compiler.calls,1)
        prepared=gate.prepare(p)
        plan=dict(prepared['plan'],walls=list(prepared['plan']['walls'])+[
            dict(mode='X',t1=1,cmp='LE',rhs=10000.)])
        other=gate.analyze_model(prepared['route'],plan,prepared['vertical'])
        self.assertIs(other['coefficient'],first['coefficient'])
        self.assertNotEqual(first['key'],other['key'])
        self.assertEqual(compiler.calls,1)

    def test_compatibility_screen_reuses_the_exact_proposal_and_reports_real_contradiction(self):
        scene=flat_scene();p=flight(scene,0);compiler=Coefficients();gate=InteractionGate(scene,compiler)
        p['profile']['positionConstraints'].append(dict(mode='X',t1=1,cmp='GE',rhs=10000.))
        p['profile']['constraintOwners'].append(p['profile']['airtime'])
        first=screen_program(scene,p,compiler,1.,gate=gate)
        second=screen_program(scene,p,compiler,1.,gate=gate)
        self.assertEqual(first,second)
        self.assertEqual(first['proof']['state'],1)
        self.assertEqual(first['proof']['reason'],'interval_reach_contradiction')
        self.assertEqual(compiler.calls,1)

    def test_the_creation_screen_solves_the_request_the_repair_loop_sends(self):
        from parkour_solver.bridge.python_compiler import PythonCompiler
        from parkour_solver.numerics import disk
        from parkour_solver.search.ordered_route import find_route
        scene=flat_scene();p=flight(scene,2);objective=dict(axis='X',sense='MAX',customYaw=30.)
        contents=[];real=disk.solve
        def recorded(content,**options):
            contents.append(json.dumps(content,sort_keys=True))
            return real(content,**options)
        with PythonCompiler() as compiler,patch.object(disk,'solve',side_effect=recorded):
            gate=InteractionGate(scene,compiler)
            self.assertIsNone(gate.check_program(p,objective))
            # A decision check's bare branch: the prepared route optimizing the decision objective.
            route=dict(gate.prepare(p)['route'],objective=dict(axis='X',sense='MAX'),guideObjective=objective)
            result=find_route(route,compiler,seconds=5,candidate_seconds=1,retries=0,stop_on_feasible=False,
                              numerical=disk.RelaxedDispatch(gate=gate).solve)
        self.assertEqual(result['status'],'feasible')
        # The creation screen, find_route's pre-screen and the candidate solve share one content.
        self.assertEqual((len(contents),len(set(contents))),(3,1))


class ProposalOwnershipTests(unittest.TestCase):
    def test_resume_skips_composition_for_consumed_raw_ordinals(self):
        from parkour_solver.movement.generation import propose_choices
        context=dict(program={'parent':True})
        choices=[dict(number=i) for i in range(5)]
        with patch('parkour_solver.movement.program.append_choice',side_effect=lambda p,c,**kw:c) as compose:
            proposals=list(propose_choices('normal',context,source=iter(choices),start=3))
        self.assertEqual([p['ordinal'] for p in proposals],[3,4])
        self.assertEqual(compose.call_count,2)

    def test_unprojected_json_resume_screens_identical_model_only_once(self):
        from parkour_solver.movement.generation import assess_proposal
        program={'profile':{'airtime':12}}
        proposal=json.loads(json.dumps(dict(program=program,fullProgram=program,projected=False)))
        gate=Mock();screen=Mock(return_value=None)
        self.assertEqual(assess_proposal(proposal,screen,gate),dict(proof=None,fullProof=None))
        screen.assert_called_once()
        gate.check_program.assert_not_called()

    def test_valid_owned_arrival_survives_an_impossible_unused_tail(self):
        from parkour_solver.search.goals import _landing_prefixes
        from parkour_solver.movement.program import append_choice, boundary_state
        from parkour_solver.movement.generation import assess_proposal
        from parkour_solver.movement.normal import normal_choices
        from parkour_solver.search.comparison import comparison_label
        scene=flat_scene();arrival=flight(scene,0);n=arrival['profile']['airtime']
        hold=next(c for c in normal_choices(dict(scene=scene,state=boundary_state(arrival),
            maxInputs=1,threshold=0.,interactionGate=None)) if c.get('terminalSupport')=='goal' and not c['profile']['jump'][0])
        full=append_choice(arrival,hold)
        full['profile']['positionConstraints'].append(dict(mode='X',t1=n+1,cmp='GE',rhs=10000.))
        full['profile']['constraintOwners'].append(n+1)
        owned=min(_landing_prefixes(scene,full,'goal',reuse_full=True),key=lambda p:p['profile']['airtime'])
        self.assertEqual(owned['profile']['airtime'],n)
        self.assertEqual(owned['profile']['landingPhase'],'pre')
        self.assertEqual(comparison_label(owned)['R'],0)
        self.assertFalse(any(r.get('rhs')==10000. for r in owned['profile']['positionConstraints']))
        gate=InteractionGate(scene,Coefficients())
        result=assess_proposal(dict(program=owned,fullProgram=full,projected=True),gate.check_program,gate)
        self.assertIsNone(result['proof'])
        self.assertIsNotNone(result['fullProof'])


class Real11JointPrerequisiteTests(unittest.TestCase):
    def test_b3_lower_higher_conjunction_rejects_before_wall_and_corner_fanout(self):
        from parkour_solver.bridge.python_compiler import PythonCompiler
        from parkour_solver.scene.normalize import prepare_scene
        from parkour_solver.movement.program import boundary_state
        from parkour_solver.movement.stepping import open_stepping_choices
        from parkour_solver.search.anchors.adapter import AnchorAdapter
        base=REGRESSION_FIXTURES
        data=json.loads(gzip.decompress((base/'anchor-contexts.json.gz').read_bytes()))
        saved=data['contexts']['b3->b4'];p=saved['parent'];scene=prepare_scene(data['request'])
        with PythonCompiler() as compiler:
            adapter=AnchorAdapter(data['domain'],data['graph'],data['policy'],motion=data['motion'],compiler=compiler)
            context=dict(mode='open',scene=scene,program=p,state=boundary_state(p),
                         maxInputs=200-p['profile']['airtime'],threshold=0.)
            # The recorded cursor predates encounter groups; b3->b4 is a one-pair group.
            cursor=dict(saved['cursor'],groupEnd=adapter.group_end(saved['cursor']['anchorIndex']))
            self.assertEqual(cursor['groupEnd'],cursor['anchorIndex'])
            adapter.for_goal(saved['request'],cursor).context(context)
            gate=InteractionGate(scene,compiler);gate.context(context)
            with patch('parkour_solver.movement.stepping.landing_wall_options',side_effect=AssertionError('late wall fanout')), \
                 patch('parkour_solver.movement.stepping.corner_rows',side_effect=AssertionError('late corner fanout')):
                choices=list(open_stepping_choices(context))
            self.assertEqual(choices,[])
            self.assertGreaterEqual(gate.metrics['sequenceExclusions'],2)


if __name__=='__main__':
    unittest.main()
