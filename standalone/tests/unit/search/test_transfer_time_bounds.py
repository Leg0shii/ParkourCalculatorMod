from tests.support.paths import REGRESSION_FIXTURES
from tests.support.paths import AUTONOMY_DATA
import copy
import gzip
import json
from pathlib import Path
import unittest

from parkour_solver.physics.vertical import REST_CARRY
from parkour_solver.reachability.service import InteractionGate
from parkour_solver.spatial.anchor_guides import build_guides
from parkour_solver.search.bounds.remaining import remaining_bound
from parkour_solver.search.bounds.flight import transfer_bounds, transfer_bound, state_covers
from parkour_solver.movement.program import append_choice, boundary_state
from parkour_solver.movement.normal import event_choice
from tests.support.checkpoint_guidance import flat_scene
from tests.support.proposal_analysis import Coefficients


class TransferTimeBoundTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        base=REGRESSION_FIXTURES
        cls.data=json.loads(gzip.decompress((base/'anchor-contexts.json.gz').read_bytes()))

    def real11(self):
        from parkour_solver.scene.normalize import prepare_scene
        x=self.data;s=prepare_scene(x['request'])
        s['movementSearch']['types']=['normal','ceiling','stepping']
        g=build_guides(x['domain'],x['policy']);c=Coefficients()
        table=transfer_bounds(s,g,x['motion'],InteractionGate(s,c))
        return s,g,table,c

    def test_real11_vertical_clocks_need_no_stepping_absence_or_numerical_solve(self):
        s,g,table,c=self.real11()
        self.assertEqual([r['ticks'] for r in table.values()],[9,9,10,15,9])
        self.assertTrue(all(r['assumption']=='hard_transfer_without_stepping' for r in table.values()))
        self.assertEqual(c.calls,0,'No ceiling has a height window in this pillar scene')

    def test_sum_remaining_flights_combines_with_horizontal_using_max_and_requires_latches(self):
        s,g,table,c=self.real11();m=self.data['motion']
        p=dict(terminalSupport='b3',profile=dict(airtime=38,grounded=[True],position=[7.],velocity=[REST_CARRY]))
        b=remaining_bound(g,2,p,m,transfers=table,hard_latches=table)
        self.assertEqual(b['verticalTicks'],34)
        self.assertEqual(b['remainingTicks'],34)
        self.assertEqual(38+b['remainingTicks'],72)
        self.assertEqual(remaining_bound(g,2,p,m,transfers=table)['verticalTicks'],0)
        p.update(terminalSupport='b4');p['profile']['position']=[7.5]
        self.assertEqual(remaining_bound(g,2,p,m,transfers=table,hard_latches=table)['verticalTicks'],24)

    def test_airborne_clock_subtracts_paid_launch_inputs_without_freezing_a_witness(self):
        s,g,table,c=self.real11()
        parent=self.data['contexts']['b3->b4']['parent']
        ctx=dict(scene=s,program=parent,maxInputs=3,threshold=0.)
        flight=event_choice(ctx,'normal',boundary_state(parent),3,jumping=True)
        p=append_choice(parent,flight)
        b=remaining_bound(g,2,p,self.data['motion'],transfers=table,hard_latches=table)
        self.assertEqual(p['profile']['airtime'],31)
        self.assertEqual(b['verticalTicks'],31)  # 7 paid-flight residual + 15 + 9.
        p['profile']['caseEvents'].append(dict(input=28,type='ceiling',case='grind'))
        self.assertEqual(remaining_bound(g,2,p,self.data['motion'],transfers=table,hard_latches=table)['verticalTicks'],24)

    def test_pre_landing_enclosure_includes_the_last_horizontal_move(self):
        g=dict(covers=dict(s=[[0,1,0,1,1,1]]),coverPhase='pre_or_post')
        cover=state_covers(g,'s',dict(movementAfterSupportCheck=.7))[0]
        self.assertEqual(cover,[-.7,1,-.7,1.7,1,1.7])
        self.assertTrue(cover[0]<=1.5<=cover[3])
        self.assertEqual(state_covers(g,'s',{}),[])

    def ceiling_scene(self, x):
        s=flat_scene();s['movementSearch']['types']=['normal','ceiling']
        box=[x,2.9,-2,x+1,3.9,2];index=len(s['collisionScene']['solids'])
        s['collisionScene']['solids'].append(box)
        s['blocks'].append(dict(id='ceiling',sourceBlock='ceiling',solidIndex=index,
                                bounds=[x,x+1,-2,2],top=3.9))
        g=dict(anchors=['start','goal'],coverPhase='pre_or_post',covers={
            'start':[[-2.3,1,-2.3,3.3,1,2.3]],'goal':[[4.7,1,-1.3,6.3,1,1.3]]})
        return s,g

    def test_reachable_ceiling_disables_clock_far_ceiling_reuses_shared_reachability(self):
        for x,expected in [(0.,0),(15.,12)]:
            with self.subTest(x=x):
                s,g=self.ceiling_scene(x);c=Coefficients()
                result=transfer_bound(s,g,'start','goal',dict(storedSpeedNorm=.4,movementAfterSupportCheck=.8),InteractionGate(s,c))
                self.assertEqual(result['ticks'],expected)
                self.assertTrue(result['ceilings'])
                self.assertEqual(c.calls,1)

    def test_no_ordinary_profile_and_unknown_ceiling_launch_bounds_are_not_infeasibility(self):
        s,g=self.ceiling_scene(0.);service=InteractionGate(s,Coefficients())
        self.assertEqual(transfer_bound(s,g,'start','goal',{},service)['ticks'],0)
        next(b for b in s['blocks'] if b['id']=='goal')['top']=4.
        r=transfer_bound(s,g,'start','goal',{},service)
        self.assertEqual((r['ticks'],r['reason']),(0,'no_ordinary_profile'))


if __name__=='__main__':
    unittest.main()
