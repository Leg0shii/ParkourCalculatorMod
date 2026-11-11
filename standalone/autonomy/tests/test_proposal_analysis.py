import gzip
import json
import os
from pathlib import Path
import unittest
from unittest.mock import Mock, patch

from standalone.autonomy.interactions import InteractionGate
from standalone.autonomy.movement_search import screen_program
from standalone.autonomy.tests.test_checkpoint_guidance import flat_scene
from standalone.autonomy.tests.test_minimum_runs import flight


class Coefficients:
    def __init__(self):
        self.calls=0

    def compile(self, route, plan, seconds):
        self.calls+=1
        n=plan['ticks']
        return dict(magnitude=[1.]*n,friction=[.9]*n,base=[0.]*n)


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


class ProposalOwnershipTests(unittest.TestCase):
    def test_resume_skips_composition_for_consumed_raw_ordinals(self):
        from standalone.autonomy.movement_generation import propose_choices
        context=dict(program={'parent':True})
        choices=[dict(number=i) for i in range(5)]
        with patch('standalone.autonomy.movement_composition.append_choice',side_effect=lambda p,c,**kw:c) as compose:
            proposals=list(propose_choices('normal',context,source=iter(choices),start=3))
        self.assertEqual([p['ordinal'] for p in proposals],[3,4])
        self.assertEqual(compose.call_count,2)

    def test_unprojected_json_resume_screens_identical_model_only_once(self):
        from standalone.autonomy.movement_generation import assess_proposal
        program={'profile':{'airtime':12}}
        proposal=json.loads(json.dumps(dict(program=program,fullProgram=program,projected=False)))
        gate=Mock();screen=Mock(return_value=None)
        self.assertEqual(assess_proposal(proposal,screen,gate),dict(proof=None,fullProof=None))
        screen.assert_called_once()
        gate.check_program.assert_not_called()

    def test_valid_owned_arrival_survives_an_impossible_unused_tail(self):
        from standalone.autonomy.cell_routes.comparison_goals import _landing_prefixes
        from standalone.autonomy.movement_composition import append_choice, boundary_state
        from standalone.autonomy.movement_generation import assess_proposal
        from standalone.autonomy.open_movement import normal_choices
        from standalone.autonomy.candidate_comparison import comparison_label
        scene=flat_scene();arrival=flight(scene,0);n=arrival['profile']['airtime']
        hold=next(c for c in normal_choices(dict(scene=scene,state=boundary_state(arrival),
            maxInputs=1,threshold=0.)) if c.get('terminalSupport')=='goal' and not c['profile']['jump'][0])
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


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER'),'Set PKC_RUN_TICKS_COMPILER')
class Real11JointPrerequisiteTests(unittest.TestCase):
    def test_b3_lower_higher_conjunction_rejects_before_wall_and_corner_fanout(self):
        from standalone.autonomy.compiler import JavaCompiler
        from standalone.autonomy.block_search import prepare_scene
        from standalone.autonomy.movement_composition import boundary_state
        from standalone.autonomy.stepping_generation import open_stepping_choices
        from standalone.autonomy.cell_routes.anchor_adapter import AnchorAdapter
        base=Path(__file__).parents[1]/'experiments/anchor-candidate-audit/audited-2026-11-10'
        data=json.loads(gzip.decompress((base/'probe-contexts.json.gz').read_bytes()))
        saved=data['contexts']['b3->b4'];p=saved['parent'];scene=prepare_scene(data['request'])
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            adapter=AnchorAdapter(data['domain'],data['graph'],data['policy'],motion=data['motion'],compiler=compiler)
            context=dict(mode='open',scene=scene,program=p,state=boundary_state(p),
                         maxInputs=200-p['profile']['airtime'],threshold=0.)
            adapter.for_goal(saved['request'],saved['cursor']).context(context)
            gate=InteractionGate(scene,compiler);gate.context(context)
            with patch('standalone.autonomy.stepping_generation.landing_wall_options',side_effect=AssertionError('late wall fanout')), \
                 patch('standalone.autonomy.stepping_generation.corner_rows',side_effect=AssertionError('late corner fanout')):
                choices=list(open_stepping_choices(context))
            self.assertEqual(choices,[])
            self.assertGreaterEqual(gate.metrics['sequenceExclusions'],2)


if __name__=='__main__':
    unittest.main()
