from tests.support.cell_geometry import scene_for
import itertools
import random
import unittest

from parkour_solver.spatial.cells import contains, decompose, dimension, intersection, segment_covered
from parkour_solver.scene.geometry import BODY_HEIGHT, expanded_box
from parkour_solver.scene.route import HALF_WIDTH


class CellGeometryTests(unittest.TestCase):
    def test_random_points_and_disjoint_interiors_match_obstacle_union(self):
        solids = [[-1, 0, -1, 1, 1, 1], [0, 0, 0, 2, 2, 2], [.2, .2, .2, .4, .4, .4]]
        scene = scene_for(solids)
        d = decompose(scene)
        rng = random.Random(42)
        for _ in range(1500):
            point = [rng.uniform(d.bounds[a], d.bounds[a+3]) for a in range(3)]
            occupied = any(all(b[a] < point[a] < b[a + 3] for a in range(3)) for b in map(expanded_box, solids))
            self.assertEqual(bool(d.locate(point)), not occupied)
        volumes = [c for c in d.cells if c.dimension == 3]
        for a, b in itertools.combinations(volumes, 2):
            overlap = intersection(a.bounds, b.bounds)
            self.assertTrue(overlap is None or dimension(overlap) < 3)

    def test_contact_boundary_between_floor_and_exact_body_height_ceiling_survives(self):
        solids = [[-3, -1, -3, 3, 0, 3], [-3, BODY_HEIGHT, -3, 3, 3, 3]]
        d = decompose(scene_for(solids))
        self.assertTrue(d.locate([0, 0, 0]))
        self.assertFalse(d.locate([0, -.001, 0]))
        self.assertFalse(d.locate([0, .001, 0]))
        self.assertTrue(any(c.dimension == 2 and contains(c.bounds, [0, 0, 0]) for c in d.cells))
        self.assertTrue(segment_covered(d, [-1, 0, 0], [1, 0, 0]))
        kinds = {p.kind for p in d.contacts if contains(p.bounds, [0, 0, 0])}
        self.assertEqual(kinds, {'support', 'ceiling'})

    def test_partial_portals_and_physical_contact_provenance(self):
        d = decompose(scene_for([[0, 0, 0, 1, 1, 1], [2, 0, 1, 3, 2, 2]]))
        for portal in d.portals:
            a, b = [d.cells[i] for i in portal.cells]
            self.assertEqual(portal.bounds, intersection(a.bounds, b.bounds))
            self.assertGreaterEqual(portal.dimension, min(a.dimension, b.dimension) - 1)
        self.assertTrue(any(p.dimension == 2 and p.bounds != d.cells[p.cells[0]].bounds for p in d.portals))
        self.assertTrue(all(p.owner == str(p.solid) for p in d.contacts))
        self.assertTrue(all(p.bounds[p.axis] == p.bounds[p.axis + 3] for p in d.contacts))

    def test_split_and_reordered_solids_preserve_free_space_on_faces_and_volume(self):
        whole = [[0, 0, 0, 2, 1, 1]]
        split = [[1, 0, 0, 2, 1, 1], [0, 0, 0, 1, 1, 1]]
        a, b = decompose(scene_for(whole)), decompose(scene_for(split))
        expanded = list(map(expanded_box, split + whole))
        axes = [sorted({-5, 6, 0, .5, 1, 1.5, 2} | {v[i] for v in expanded} | {v[i+3] for v in expanded}) for i in range(3)]
        for point in itertools.product(*axes):
            self.assertEqual(bool(a.locate(point)), bool(b.locate(point)), point)
        c = decompose(scene_for(list(reversed(split))))
        self.assertEqual([v.bounds for v in b.cells], [v.bounds for v in c.cells])

    def test_segment_crosses_many_cells_and_rejects_interior_crossing(self):
        d = decompose(scene_for([[0, 0, 0, 1, 1, 1]]))
        self.assertTrue(segment_covered(d, [-4, 3, 0], [4, 3, 0]))
        self.assertFalse(segment_covered(d, [-4, .5, .5], [4, .5, .5]))
        self.assertFalse(segment_covered(d, [8, 3, 0], [9, 3, 0]))

    def test_corner_only_free_connection_has_an_incidence_route(self):
        h = HALF_WIDTH
        d = decompose(scene_for([[h,0,h,3-h,10,3-h],[-3+h,0,-3+h,-h,10,-h]],bounds=(-3,0,-3,3,10,3)))
        pending = d.locate([-1,1,1])
        target,seen = set(d.locate([1,1,-1])),set(pending)
        adjacent = d.neighbors()
        while pending:
            cell=pending.pop()
            for neighbor,_ in adjacent[cell]:
                if neighbor not in seen:
                    seen.add(neighbor)
                    pending.append(neighbor)
        self.assertTrue(seen & target)
        self.assertTrue(d.locate([0,1,0]))

    def test_geometry_limit_and_deadline_do_not_silently_truncate(self):
        scene = scene_for([[0, 0, 0, 1, 1, 1]])
        with self.assertRaises(ValueError):
            decompose(scene, max_cells=1)
        with self.assertRaises(TimeoutError):
            decompose(scene, deadline=0)


if __name__ == '__main__':
    unittest.main()
