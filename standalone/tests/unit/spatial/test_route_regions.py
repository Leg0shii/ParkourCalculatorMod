from tests.support.route_regions import task_scene
import itertools
import json
import random
import unittest

from parkour_solver.spatial.cells import contains, decompose, dimension, intersection
from parkour_solver.spatial.regions import build_route_graph, canonical_cover, free_transit_cover, support_components
from tools.plotting.regions import diagnostic_svg
from tests.support.cell_geometry import scene_for


class RouteRegionTests(unittest.TestCase):
    def test_overlapping_transit_cover_preserves_space_without_subtraction_walls(self):
        from parkour_solver.scene.geometry import expanded_box
        scene=task_scene([[0,0,0,4,1,2],[4,0,1,5,2,2]],bounds=(-5,-5,-5,8,7,6))
        d=decompose(scene);cover=free_transit_cover(scene,d)
        self.assertTrue(any(contains(b,[2,1.2,.5]) and contains(b,[2,1.2,1.5]) for b in cover))
        rng=random.Random(811)
        for _ in range(1000):
            point=[rng.uniform(d.bounds[a],d.bounds[a+3]) for a in range(3)]
            self.assertEqual(bool(d.locate(point)),any(contains(b,point) for b in cover))
        for free_box in cover:
            for solid in map(expanded_box,scene['collisionScene']['solids']):
                self.assertFalse(all(max(free_box[a],solid[a])<min(free_box[a+3],solid[a+3]) for a in range(3)))
        with self.assertRaises(ValueError):free_transit_cover(scene,d,max_regions=1)
        with self.assertRaises(TimeoutError):free_transit_cover(scene,d,deadline=0)
    def test_canonical_union_discards_encoding_seams_but_keeps_hole_and_concavity(self):
        whole = [(0, 1, 0, 2, 1, 1)]
        split = [(0, 1, 0, 1.3, 1, 1), (1, 1, 0, 2, 1, 1)]
        self.assertEqual(canonical_cover(whole), canonical_cover(split))
        lshape = [(0, 1, 0, 1, 1, 3), (1, 1, 0, 3, 1, 1)]
        cover = canonical_cover(lshape)
        self.assertFalse(any(contains(b, [2, 1, 2]) for b in cover))
        ring = [(0, 1, 0, 3, 1, 1), (0, 1, 2, 3, 1, 3),
                (0, 1, 1, 1, 1, 2), (2, 1, 1, 3, 1, 2)]
        self.assertFalse(any(contains(b, [1.5, 1, 1.5]) for b in canonical_cover(ring)))
        self.assertEqual(cover, canonical_cover(reversed(lshape)))

    def test_lines_points_and_slivers_are_dropped_but_exact_doubles_kept(self):
        boxes = [(0, 1, 0, 1, 1, 1), (1, 1, 0, 2, 1, 0), (3, 1, 0, 3, 1, 0), (5, 1, 0, 5+1e-15, 1, 1)]
        cover = canonical_cover(boxes)
        self.assertEqual(cover, ((0, 1, 0, 1, 1, 1),))
        self.assertEqual(cover, canonical_cover(cover))
        self.assertNotEqual(canonical_cover([(0, 1, 0, 1, 1, 1)]),
                            canonical_cover([(0, 1, 0, 1.000000000000001, 1, 1)]))

    def test_support_beside_a_flush_wall_is_one_rectangle(self):
        # Real11's B5 under B6: the zero-width line where the player's box meets
        # B5's top beside B6's east face is not a second landing rectangle.
        scene = task_scene([[730, 0, -1096, 731, 6, -1095], [730, 0, -1095, 731, 20, -1094]],
                           bounds=(720, -5, -1105, 740, 30, -1085))
        low, = [s for s in support_components(decompose(scene)) if s.height == 6]
        self.assertEqual(len(low.cover), 1)

    def test_local_refinement_preserves_free_volume_boundaries_and_residual_air(self):
        scene = task_scene([[0, 0, 0, 1, 1, 1], [2, 0, 1, 3, 2, 2]])
        before, after = decompose(scene, refine_support=False), decompose(scene)
        volume = lambda d: sum((c.bounds[3]-c.bounds[0])*(c.bounds[4]-c.bounds[1])*(c.bounds[5]-c.bounds[2])
                               for c in d.cells if c.dimension == 3)
        self.assertAlmostEqual(volume(before), volume(after))
        self.assertGreater(len(after.cells), len(before.cells))
        rng = random.Random(18)
        points = [[rng.uniform(before.bounds[a], before.bounds[a+3]) for a in range(3)] for _ in range(1000)]
        points += [list(p) for c in before.cells for p in itertools.product(*[(c.bounds[a], c.bounds[a+3]) for a in range(3)])]
        for point in points:
            self.assertEqual(bool(before.locate(point)), bool(after.locate(point)), point)
        for cell in after.cells:
            self.assertTrue(after.parent_cells[cell.id])
            self.assertTrue(all(intersection(cell.bounds, before.cells[p].bounds) == cell.bounds
                                for p in after.parent_cells[cell.id]))
        for a, b in itertools.combinations(after.cells, 2):
            overlap = intersection(a.bounds, b.bounds)
            self.assertTrue(overlap is None or dimension(overlap) < 3)
        # Above-footprint pieces and broad residual cells both survive.
        from parkour_solver.scene.geometry import expanded_box
        support_box = expanded_box(scene['collisionScene']['solids'][0])
        self.assertTrue(any(c.bounds[0] == support_box[0] and c.bounds[3] == support_box[3]
                            and c.bounds[1] == 1 for c in after.cells))
        self.assertTrue(after.locate([4, 3, 4]))

    def test_exposed_component_keys_survive_same_owner_box_split(self):
        whole = task_scene([[0, 0, 0, 2, 1, 1]], ['floor'])
        split = task_scene([[0, 0, 0, 1, 1, 1], [1, 0, 0, 2, 1, 1]], ['floor', 'floor'])
        a, b = (support_components(decompose(s)) for s in (whole, split))
        self.assertEqual([(s.key, s.cover) for s in a], [(s.key, s.cover) for s in b])
        self.assertEqual(b[0].solids, (0, 1))

    def test_covered_top_not_support_and_disconnected_same_owner_stays_separate(self):
        scene = task_scene([[0, 0, 0, 2, 1, 2], [0, 1, 0, 1, 3, 1], [4, 0, 0, 5, 1, 1]],
                           ['floor', 'obstacle', 'floor'], bounds=(-5, -5, -5, 8, 7, 6))
        surfaces = support_components(decompose(scene))
        floors = [s for s in surfaces if s.owner == 'floor']
        self.assertEqual(len(floors), 2)
        self.assertFalse(any(contains(b, [.5, 1, .5]) for s in floors for b in s.cover))
        self.assertTrue(any(contains(b, [1.8, 1, 1.8]) for s in floors for b in s.cover))

    def test_graph_represents_overflight_separately_from_physical_contact(self):
        scene = task_scene([[0, 0, 0, 1, 1, 1], [3, 0, 0, 4, 1, 1]])
        d = decompose(scene)
        graph = build_route_graph(scene, d)
        regions = {r.key: r for r in graph.regions}
        self.assertEqual(len(graph.starts), 1)
        self.assertEqual(regions[graph.starts[0]].kind, 'support_surface')
        support = next(s for s in graph.supports if s.owner == '0')
        above = [r for r in graph.regions if r.kind == 'air_volume' and any(contains(b, [.5, 2, .5]) for b in r.cover)]
        self.assertTrue(above)
        self.assertNotIn(support.key, {r.key for r in above})
        links = [t for t in graph.transitions if t.target == support.key]
        self.assertTrue(links)
        self.assertTrue(all(t.kind == 'support_arrival' and t.patches and t.evidence == 'geometry_only' for t in links))
        self.assertTrue(all(b[1] == b[4] == 1 for t in links for b in t.cover))
        self.assertTrue(all(not t.movement_families for t in graph.transitions if t.kind == 'open_portal'))
        self.assertEqual(len(json.loads(json.dumps(graph.to_dict(), allow_nan=False))['regions']), len(graph.regions))
        self.assertIn('stroke="red"', diagnostic_svg(graph, y=1))

    def test_exact_height_ceiling_keeps_surface_boundary_region(self):
        from parkour_solver.scene.geometry import BODY_HEIGHT
        scene = task_scene([[-3, -1, -3, 3, 0, 3], [-3, BODY_HEIGHT, -3, 3, 3, 3]])
        graph = build_route_graph(scene, decompose(scene))
        floor = next(s for s in graph.supports if s.owner == '0')
        self.assertTrue(any(contains(b, [0, 0, 0]) for b in floor.cover))
        self.assertTrue(any(r.kind == 'air_volume' and any(dimension(b) == 2 for b in r.cover) for r in graph.regions))

    def test_virtual_goal_keeps_one_sided_predicate_without_finite_floor(self):
        scene = task_scene([[0, 0, 0, 1, 1, 1], [3, 0, 0, 4, 1, 1]])
        spec = dict(kind='landing_height_region', height=1, rows=[dict(mode='X', cmp='GE', rhs=3)])
        graph = build_route_graph(scene, decompose(scene), spec)
        terminal = next(r for r in graph.regions if r.key in graph.goals)
        self.assertEqual(terminal.predicate, spec)
        self.assertEqual(terminal.cover, ())
        self.assertTrue(all(not t.cover for t in graph.transitions if t.kind == 'finish'))
        self.assertTrue(any(r.kind == 'support_surface' and r.owners == ('1',) for r in graph.regions))


if __name__ == '__main__':
    unittest.main()
