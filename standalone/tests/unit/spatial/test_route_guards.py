import copy
import json
import unittest

from parkour_solver.spatial.compat.corridor_guards import movement_regions
from parkour_solver.constraints.route_guards import RouteGuards
from tests.unit.spatial.test_route_progress import line_route


class RouteGuardTests(unittest.TestCase):
    def test_three_dimensional_sweeps_join_adjacent_cells_without_filling_gap(self):
        boxes=[(0,1,0,2,3,1),(2,1,0,4,3,1)]
        self.assertIn((0,1,0,4,3,1),movement_regions(boxes,0))
        self.assertNotIn((0,1,0,5,3,1),movement_regions(boxes+[(5,1,0,6,3,1)],0))
        vertical=[(0,1,0,1,2,1),(0,2,0,1,4,1)]
        self.assertEqual(movement_regions(vertical,1),[(0,1,0,1,4,1)])
        self.assertEqual(movement_regions([(0,0,0,1,0,1),(1,0,0,2,0,1)],0),[(0,0,0,2,0,1)])

    def test_fixed_guards_can_cross_several_portals_in_one_input(self):
        route=line_route(with_supports=False)
        profile=dict(position=[2,2],contactEvents=[],caseEvents=[])
        scene=dict(blocks=[],collisionScene=dict(solids=[]))
        states=[dict(X=[1,1],Z=[0,0],Y=[2,2]),dict(X=[5,5],Z=[0,0],Y=[2,2])]
        guards=RouteGuards(scene,route,profile,1,complete=True,
                           envelopes=dict(states=states,phases=[],contradiction=None))
        cursor={}
        result=guards.option(cursor)
        self.assertIsNotNone(result)
        self.assertTrue(any(r['mode']=='X' and r['offset']==0 and r['cmp']=='LE' and r['rhs']<=2 for r in result['rows']))
        self.assertTrue(any(r['mode']=='X' and r['offset']==1 and r['cmp']=='GE' and r['rhs']>=4 for r in result['rows']))
        self.assertTrue(all(r['offset'] in (0,1) for r in result['rows']))
        for row in result['rows']:
            value=states[row['offset']][row['mode']][0]
            self.assertTrue(value>=row['rhs'] if row['cmp']=='GE' else value<=row['rhs'])
        saved=json.loads(json.dumps(cursor,allow_nan=False))
        other=RouteGuards(scene,route,profile,1,complete=True,
                         envelopes=dict(states=states,phases=[],contradiction=None))
        self.assertEqual(guards.option(cursor),other.option(saved))

    def test_wrong_layer_cannot_produce_a_fixed_guard_model(self):
        route=line_route(with_supports=False)
        profile=dict(position=[5,5],contactEvents=[],caseEvents=[])
        guards=RouteGuards(dict(blocks=[],collisionScene=dict(solids=[])),route,profile,1,complete=True)
        self.assertIsNone(guards.option({}))

    def test_unknown_named_probe_does_not_become_endpoint_height_exclusion(self):
        route=line_route(with_supports=False)
        profile=dict(position=[5,5],contactEvents=[],caseEvents=[dict(input=0)])
        guards=RouteGuards(dict(blocks=[],collisionScene=dict(solids=[])),route,profile,1,complete=True)
        self.assertEqual(guards.option({}),dict(rows=[]))


if __name__=='__main__':unittest.main()
