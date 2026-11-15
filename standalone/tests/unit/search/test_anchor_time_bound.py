import unittest

from parkour_solver.search.bounds.remaining import remaining_bound


class AnchorTimeBoundTests(unittest.TestCase):
    def guide(self):
        return dict(anchors=['s','e','n'],points=[[0,1,0],[8,1,0],[14,1,0]],
            covers=dict(s=[[0,1,0,2,1,2]],e=[[6,1,0,10,1,2]],n=[[12,1,0,16,1,2]]))

    def program(self,support,ground=True):
        return dict(terminalSupport=support,profile=dict(airtime=8,grounded=[ground]))

    def test_wide_regions_relax_guides_and_keep_momentum_speed(self):
        b=remaining_bound(self.guide(),0,self.program('s'),dict(movementAfterSupportCheck=3.))
        self.assertEqual(b['distanceLowerBound'],6.)
        self.assertEqual(b['remainingTicks'],2)
        self.assertEqual(b['guideDistanceEstimate'],14.)

    def test_completed_anchor_excludes_its_approach_and_final_is_zero(self):
        g=self.guide();m=dict(movementAfterSupportCheck=1.)
        self.assertEqual(remaining_bound(g,0,self.program('e'),m)['remainingTicks'],2)
        self.assertEqual(remaining_bound(g,1,self.program('n'),m)['remainingTicks'],0)

    def test_airborne_endpoint_is_not_pinned_to_previous_support(self):
        b=remaining_bound(self.guide(),0,self.program('s',False),dict(movementAfterSupportCheck=1.))
        self.assertEqual(b['remainingTicks'],2)
        self.assertEqual([s['source'] for s in b['segments']],['e'])

    def test_unknown_motion_falls_back_to_zero(self):
        self.assertEqual(remaining_bound(self.guide(),0,self.program('s'),{})['remainingTicks'],0)

    def test_diagonal_and_translated_regions(self):
        g=self.guide();g['covers']['s']=[[100,1,100,101,1,101]]
        g['covers']['e']=[[104,1,105,105,1,106]]
        g['covers']['n']=g['covers']['e']
        b=remaining_bound(g,0,self.program('s'),dict(movementAfterSupportCheck=1.))
        self.assertEqual(b['distanceLowerBound'],5.)
        self.assertEqual(b['remainingTicks'],5)
