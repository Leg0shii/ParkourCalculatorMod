import copy
import json
import unittest

from parkour_solver.spatial.event_routes import RouteDomain, build_route_domain, support_episodes
from parkour_solver.spatial.cells import contains, decompose
from parkour_solver.spatial.regions import build_geometry_graph, support_components
from tests.support.route_regions import task_scene
from parkour_solver.scene.route import HALF_WIDTH


class EventDomainTests(unittest.TestCase):
    def test_rim_only_overlap_is_not_a_certified_surface_connection(self):
        gap=2*HALF_WIDTH
        scene=task_scene([[0,0,0,1,1,1],[1+gap,0,0,2+gap,1,1]])
        surfaces=build_route_domain(scene,None).to_dict()['surfaces']
        self.assertEqual(len(surfaces),2)
    def test_raw_graph_keeps_every_cell_portal_and_physical_component(self):
        scene = task_scene([[0, 0, 0, 1, 1, 1], [3, 0, 0, 4, 2, 1]])
        d = decompose(scene)
        graph = build_geometry_graph(scene, d)
        air = [r for r in graph.regions if r.kind == 'air_volume']
        self.assertEqual([r.cover[0] for r in air], [c.bounds for c in d.cells])
        self.assertEqual(sum(t.kind == 'open_portal' for t in graph.transitions), 2*len(d.portals))
        self.assertEqual(graph.supports, support_components(d))
        self.assertTrue(all(t.patches for t in graph.transitions if t.kind == 'support_arrival'))

    def test_portable_immutable_domain_and_content_validation(self):
        scene = task_scene([[0, 0, 0, 1, 1, 1], [3, 0, 0, 4, 2, 1]])
        domain = build_route_domain(scene, dict(kind='landing', block='1'))
        saved = json.loads(json.dumps(domain.to_dict(), allow_nan=False))
        self.assertEqual(domain, RouteDomain.from_dict(saved))
        saved['scene']['initial']['position'][0] = 99
        self.assertEqual(domain.to_dict()['scene']['initial']['position'][0], .5)
        with self.assertRaisesRegex(ValueError, 'domainId'):
            RouteDomain.from_dict(saved)
        self.assertEqual(domain.to_dict()['coverage']['enclosure'], 'unknown')
        with self.assertRaises(TimeoutError):
            build_route_domain(scene, None, deadline=0)
        with self.assertRaises(ValueError):
            build_route_domain(scene, None, max_cells=1)

    def test_airborne_start_cannot_acquire_support_permission(self):
        scene = task_scene([[0, 0, 0, 1, 1, 1]])
        scene['initial']['onGround'] = False
        graph = build_geometry_graph(scene, decompose(scene))
        regions = {r.key: r for r in graph.regions}
        self.assertTrue(graph.starts)
        self.assertTrue(all(regions[k].kind == 'air_volume' for k in graph.starts))

    def test_coplanar_group_preserves_hole_provenance_and_owner_task(self):
        scene = task_scene([[0, 0, 0, 3, 1, 1], [0, 0, 2, 3, 1, 3],
                            [0, 0, 1, 1, 1, 2], [2, 0, 1, 3, 1, 2]])
        data = build_route_domain(scene, dict(kind='landing', block='1')).to_dict()
        self.assertEqual(len(data['surfaces']), 1)
        group = data['surfaces'][0]
        self.assertEqual(group['owners'], ['0', '1', '2', '3'])
        self.assertFalse(any(contains(b, [1.5, 1, 1.5]) for b in group['cover']))
        self.assertEqual(len(data['geometry']['supports']), 4)
        self.assertEqual(data['task']['block'], '1')

    def test_equivalent_encoding_preserves_semantic_surface_geometry(self):
        a = task_scene([[0, 0, 0, 2, 1, 1]], ['floor'])
        b = task_scene([[1, 0, 0, 2, 1, 1], [0, 0, 0, 1, 1, 1]], ['floor', 'floor'])
        x, y = [build_route_domain(s, None).to_dict()['surfaces'] for s in (a, b)]
        self.assertEqual([(s['id'], s['cover']) for s in x], [(s['id'], s['cover']) for s in y])

    def test_running_rows_are_silent_but_landings_revisits_and_named_cases_survive(self):
        scene = task_scene([[0, 0, 0, 1, 1, 1]])
        domain = build_route_domain(scene, None)
        event = dict(axis='Y', direction=-1, boxes=[scene['collisionScene']['solids'][0]])
        profile = dict(airtime=4, position=[1]*5, grounded=[True, True, False, True, True],
            jump=[False, True, False, False], contactEvents=[dict(event, offset=i) for i in (0, 2, 3)],
            caseEvents=[dict(input=3, case='blip', boxes=event['boxes'])])
        episodes = support_episodes(domain, profile)
        self.assertEqual([e['kind'] for e in episodes], ['support_visit', 'named_contact'])
        self.assertEqual(episodes[0]['tick'], 3)
        self.assertEqual(len(support_episodes(domain, profile, required_owners=['0'])), 4)


if __name__ == '__main__':
    unittest.main()
