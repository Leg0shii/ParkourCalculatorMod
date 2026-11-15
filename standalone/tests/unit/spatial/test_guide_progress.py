import unittest

from parkour_solver.spatial.event_routes import build_route_domain, build_event_graph, initial_cursor, next_goals
from parkour_solver.spatial.guides import guide_for, segment_in_cover, outgoing_objective
from tests.support.route_regions import task_scene


class GuideTests(unittest.TestCase):
    def guide(self,bounds):
        scene=task_scene([[0,0,0,1,1,1],[3,0,0,4,1,1]],bounds=bounds)
        scene['objective']=dict(axis='Z',sense='MAX')
        domain=build_route_domain(scene,dict(kind='landing',block='1')).to_dict()
        graph=build_event_graph(domain)
        requests,_,_=next_goals(domain,graph,initial_cursor(domain))
        request=next(r for r in requests if r['terminal'])
        return domain,graph,request,guide_for(domain,graph,request,scene['initial']['position'])

    def test_witness_points_use_local_interfaces_and_are_covered(self):
        data,graph,request,guide=self.guide((-5,-5,-5,8,7,6))
        self.assertEqual(guide['status'],'witness')
        self.assertFalse(guide['hardConstraints'])
        boxes=[b for r in data['geometry']['regions'] for b in r['cover']]
        self.assertTrue(all(segment_in_cover(boxes,a,b) for a,b in zip(guide['points'],guide['points'][1:])))
        self.assertEqual(guide['points'][-1][1],1)
        _,_,_,remote=self.guide((-100,-20,-100,100,70,100))
        self.assertEqual(guide['target'],remote['target'])
        self.assertEqual(outgoing_objective(data,graph,request,guide),dict(axis='Z',sense='MAX'))

    def test_interruption_resumes_witness_and_never_calls_direct_hint_a_witness(self):
        data,graph,request,expected=self.guide((-5,-5,-5,8,7,6))
        guide=guide_for(data,graph,request,[.5,1,.5],max_expansions=0)
        self.assertEqual(guide['status'],'unresolved')
        self.assertEqual(len(guide['points']),1)
        for _ in range(100):
            guide=guide_for(data,graph,request,[.5,1,.5],max_expansions=1,continuation=guide['continuation'])
            if guide['witness']:break
        self.assertEqual(guide['witness'],expected['witness'])
        self.assertEqual(guide['points'],expected['points'])

    def test_segment_shortcut_cannot_fill_a_hole(self):
        boxes=[(0,0,0,1,2,3),(1,0,0,3,2,1)]
        self.assertFalse(segment_in_cover(boxes,[.5,1,2.5],[2.5,1,.5]))
        self.assertTrue(segment_in_cover(boxes,[.5,1,.5],[2.5,1,.5]))


if __name__=='__main__':unittest.main()
