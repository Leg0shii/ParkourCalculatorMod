from tests.support.route_progress import line_route
import copy
import unittest
from dataclasses import asdict

from parkour_solver.spatial.regions import RouteRegion, RouteTransition
from parkour_solver.spatial.compat.corridors import Corridor
from parkour_solver.spatial.compat.progress import advance, initial_cursor


def segment(a,b,phase=0,**kwargs):
    return dict(start=a,end=b,phase=phase,**kwargs)


class RouteProgressTests(unittest.TestCase):
    def test_multiple_portals_one_input_then_actual_landing(self):
        route = line_route()
        phases = [segment([.5,1,0],[.5,2,0],0), segment([.5,2,0],[5.5,2,0],1),
                  segment([5.5,2,0],[5.5,1,0],2)]
        no_contact = advance(route, initial_cursor(route), phases, scope=dict(complete=True))
        self.assertEqual(no_contact['status'], 'ruled_out')
        result = advance(route, initial_cursor(route), phases, [dict(phase=2,owner='goal')], dict(complete=True))
        self.assertEqual(result['status'], 'compatible')
        self.assertTrue(result['complete'])

    def test_many_inputs_in_same_cell_and_touch_return_are_not_extra_progress(self):
        route = line_route(with_supports=False)
        cursor = initial_cursor(route)
        for a,b in (([1,2,0],[2,2,0]), ([2,2,0],[1,2,0])):
            result = advance(route,cursor,[segment(a,b)])
            self.assertEqual(result['status'],'compatible')
            cursor=result['cursor']
        self.assertTrue(all(p['occurrence']==0 for p in cursor['possible']))
        for _ in range(8):
            result=advance(route,cursor,[segment([1,2,0],[1,2,0])])
            self.assertEqual(result['status'],'compatible')
            cursor=result['cursor']

    def test_touch_of_final_portal_does_not_certify_crossing(self):
        route=line_route(with_supports=False)
        result=advance(route,initial_cursor(route),[segment([1,2,0],[4,2,0])],scope=dict(complete=True))
        self.assertFalse(result['complete'])
        self.assertEqual(result['status'],'ruled_out')
        result=advance(route,initial_cursor(route),[segment([1,2,0],[4.1,2,0])],scope=dict(complete=True))
        self.assertTrue(result['complete'])

    def test_unexpected_support_cannot_be_ignored_on_overflight_route(self):
        route=line_route(with_supports=False)
        result=advance(route,initial_cursor(route),[segment([1,2,0],[3,2,0])],
                       [dict(phase=0,owner='unexpected')])
        self.assertEqual(result['status'],'ruled_out')

    def test_same_horizontal_trace_wrong_vertical_layer_rejected(self):
        route=line_route(with_supports=False)
        result=advance(route,initial_cursor(route),[segment([1,5,0],[5,5,0])])
        self.assertEqual(result['status'],'ruled_out')
        result=advance(route,initial_cursor(route),[segment([1,2,0],[5,2,0])])
        self.assertEqual(result['status'],'compatible')

    def test_unknown_named_probes_do_not_falsely_exclude_or_certify(self):
        route=line_route(with_supports=False)
        result=advance(route,initial_cursor(route),[segment([1,2,0],[5,9,0],uncertainProbes=True)],
                       scope=dict(complete=True))
        self.assertEqual(result['status'],'unknown')
        self.assertEqual(result['cursor']['possible'],initial_cursor(route)['possible'])

    def test_alternative_paths_cannot_be_spliced_at_shared_region(self):
        route=line_route(with_supports=False)
        other=copy.deepcopy(route.region_graph['regions'][0])
        other.update(key='d',cover=((0,0,1,2,4,3),))
        route.region_graph['regions'].append(other)
        route.region_graph['transitions'].append(asdict(RouteTransition('db','d','b','open_portal',((2,0,1,2,4,1),))))
        route.route_variants.append(dict(regions=['d','b'],transitions=['db']))
        # Start in first variant's a, reach common b, then attempt second
        # variant's d. No full variant accepts that concatenation.
        phases=[segment([1,2,0],[3,2,0]),segment([3,2,0],[2,2,0],1),
                segment([2,2,0],[2,2,2],2),segment([2,2,2],[1,2,2],3)]
        result=advance(route,initial_cursor(route),phases)
        self.assertEqual(result['status'],'ruled_out')

    def test_progress_rejects_changed_context(self):
        route=line_route()
        cursor=initial_cursor(route)
        cursor['context']='different'
        with self.assertRaisesRegex(ValueError,'context changed'):
            advance(route,cursor,[])

    def test_meaningful_return_uses_a_new_occurrence_of_the_same_region(self):
        route=line_route(with_supports=False)
        route.route_variants=[dict(regions=['a','b','a'],transitions=['ab','ba'])]
        route.region_graph['transitions'].append(asdict(RouteTransition('ba','b','a','open_portal',((2,0,-1,2,4,1),))))
        result=advance(route,initial_cursor(route),[segment([1,2,0],[3,2,0]),segment([3,2,0],[1,2,0],1)],
                       scope=dict(complete=True))
        self.assertEqual(result['status'],'compatible')
        self.assertTrue(result['complete'])
        self.assertTrue(any(p['occurrence']==2 for p in result['cursor']['possible']))


if __name__ == '__main__':
    unittest.main()
