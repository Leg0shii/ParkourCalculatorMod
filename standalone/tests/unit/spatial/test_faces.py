"""Physical face unions checked against source geometry and an occupancy oracle."""
import copy
from fractions import Fraction
import itertools
import math
import random
import unittest

from parkour_solver.spatial.faces import TANGENT_AXES, merge_faces
from tests.support.cell_geometry import scene_for


def oriented(catalog, axis, sign, plane=None):
    return [f for f in catalog.faces if f.axis == axis and f.normal_sign == sign
            and (plane is None or f.plane == plane)]


def inside(cover, point):
    return any(r[0] <= point[0] <= r[2] and r[1] <= point[1] <= r[3] for r in cover)


class FaceDetectionTests(unittest.TestCase):
    def test_touching_boxes_remove_internal_faces_and_merge_all_outer_orientations(self):
        boxes = [[0, 0, 0, 1, 1, 1], [1, 0, 0, 2, 1, 1]]
        for permutation in itertools.permutations(range(3)):
            with self.subTest(permutation=permutation):
                mapped = [[b[a] for a in permutation] + [b[a+3] for a in permutation] for b in boxes]
                scene = scene_for(mapped)
                original = copy.deepcopy(scene)
                catalog = merge_faces(scene)
                self.assertEqual(scene, original)
                self.assertEqual(len(catalog.faces), 6)
                self.assertEqual(sum(not f.exposed_cover for f in catalog.source_faces), 2)
                for axis in range(3):
                    for sign in (-1, 1):
                        faces = oriented(catalog, axis, sign)
                        self.assertEqual(len(faces), 1)
                        self.assertEqual(faces[0].tangent_axes, TANGENT_AXES[axis])

    def test_partial_occlusion_retains_exact_l_shape(self):
        catalog = merge_faces(scene_for([[0, 0, 0, 2, 2, 1], [0, 1, 1, 1, 2, 2]]))
        source = next(s for s in catalog.source_faces if s.key == (0, 2, 1))
        self.assertEqual(source.original_rect, (0, 0, 2, 2))
        self.assertEqual(source.exposed_cover, ((0, 0, 1, 1), (1, 0, 2, 2)))
        self.assertFalse(inside(source.exposed_cover, (.5, 1.5)))
        self.assertTrue(inside(source.exposed_cover, (1.5, 1.5)))

    def test_containment_and_duplicate_solids_keep_correct_provenance(self):
        box = [0, 0, 0, 3, 3, 3]
        catalog = merge_faces(scene_for([box, [1, 1, 1, 2, 2, 2]]))
        self.assertEqual(len(catalog.faces), 6)
        self.assertTrue(all(not f.exposed_cover for f in catalog.source_faces if f.key[0] == 1))
        duplicate = merge_faces(scene_for([box, box]))
        self.assertEqual(len(duplicate.faces), 6)
        self.assertTrue(all(len(f.contributions) == 2 for f in duplicate.faces))

    def test_corner_contact_and_real_ulp_gap_do_not_merge(self):
        first = [0, 0, 0, 1, 1, 1]
        for second in ([1, 0, 1, 2, 1, 2], [math.nextafter(1, math.inf), 0, 0, 2, 1, 1],
                       [1, 0, 0, 2, math.nextafter(1, math.inf), 1]):
            with self.subTest(second=second):
                catalog = merge_faces(scene_for([first, second]))
                self.assertEqual(len(oriented(catalog, 1, 1)), 2)

    def test_partial_shared_edge_and_transitive_chain_connect(self):
        catalog = merge_faces(scene_for([[0, 0, 0, 1, 1, 2], [1, 0, .5, 2, 1, 1.5],
                                        [2, 0, 1, 3, 1, 2]]))
        top = oriented(catalog, 1, 1)
        self.assertEqual(len(top), 1)
        self.assertEqual(len(top[0].contributions), 3)
        self.assertFalse(inside(top[0].cover, (1.5, .25)))

    def test_ring_keeps_hole(self):
        catalog = merge_faces(scene_for([[0, 0, 0, 3, 1, 1], [0, 0, 2, 3, 1, 3],
                                        [0, 0, 1, 1, 1, 2], [2, 0, 1, 3, 1, 2]]))
        top, = oriented(catalog, 1, 1)
        self.assertFalse(inside(top.cover, (1.5, 1.5)))
        self.assertEqual(sum((r[2]-r[0])*(r[3]-r[1]) for r in top.cover), 8)

    def test_one_source_split_between_components_has_no_contribution_leak(self):
        catalog = merge_faces(scene_for([[0, 0, 0, 3, 1, 1], [1, 1, 0, 2, 2, 1]]))
        tops = oriented(catalog, 1, 1, 1)
        self.assertEqual(len(tops), 2)
        self.assertEqual({f.cover for f in tops}, {((0, 0, 1, 1),), ((2, 0, 3, 1),)})
        for face in tops:
            contribution, = face.contributions
            self.assertEqual(contribution.source_face_key, (0, 1, 1))
            self.assertEqual(contribution.cover, face.cover)

    def test_geometric_ids_ignore_order_owner_names_splits_and_numeric_encoding(self):
        whole = merge_faces(scene_for([[0, 0, 0, 2, 1, 1]]))
        split_boxes = [[0, 0, 0, 1, 1, 1], [1, 0, 0, 2, 1, 1]]
        split = merge_faces(scene_for(split_boxes))
        reversed_scene = scene_for(list(reversed(split_boxes)))
        reversed_scene['blocks'][0]['sourceBlock'] = 'other'
        reversed_catalog = merge_faces(reversed_scene)
        floating = merge_faces(scene_for([[-0.0, 0., 0., 2., 1., 1.]]))
        ids = lambda c: tuple(f.id for f in c.faces)
        self.assertEqual(ids(whole), ids(split))
        self.assertEqual(ids(split), ids(reversed_catalog))
        self.assertEqual(ids(whole), ids(floating))
        self.assertEqual(whole.scene_key, floating.scene_key)
        self.assertNotEqual(split.scene_key, reversed_catalog.scene_key)

    def test_empty_single_box_and_compound_part_ownership(self):
        self.assertEqual(merge_faces(scene_for([])).faces, ())
        self.assertEqual(len(merge_faces(scene_for([[0, 0, 0, 1, 1, 1]])).faces), 6)
        scene = scene_for([[0, 0, 0, 1, 1, 1], [1, 0, 0, 2, 1, 1]])
        for i, b in enumerate(scene['blocks']):
            b.update(sourceBlock='stair', id='stair#'+str(i))
        catalog = merge_faces(scene)
        self.assertEqual([s.block_id for s in catalog.solids], ['stair', 'stair'])
        self.assertEqual([s.part_id for s in catalog.solids], ['stair#0', 'stair#1'])

    def test_invalid_coordinates_and_ambiguous_source_mappings_rejected(self):
        valid = scene_for([[0, 0, 0, 1, 1, 1]])
        for value in (math.nan, math.inf, True, 2**54+1):
            scene = copy.deepcopy(valid)
            scene['collisionScene']['solids'][0][0] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                merge_faces(scene)
        invalid = [scene_for([[0, 0, 0, 0, 1, 1]]), dict(valid, blocks=[])]
        for index in (True, -1, 1):
            scene = copy.deepcopy(valid)
            scene['blocks'][0]['solidIndex'] = index
            invalid.append(scene)
        duplicate = scene_for([[0, 0, 0, 1, 1, 1], [1, 0, 0, 2, 1, 1]])
        duplicate['blocks'][1]['solidIndex'] = 0
        invalid.append(duplicate)
        for scene in invalid:
            with self.subTest(scene=scene), self.assertRaises(ValueError):
                merge_faces(scene)

    def test_budget_interruption_does_not_mutate_input_or_later_result(self):
        scene = scene_for([[0, 0, 0, 1, 1, 1], [1, 0, 0, 2, 1, 1]])
        original = copy.deepcopy(scene)
        count = 0
        def check():
            nonlocal count
            count += 1
            if count == 12:
                raise TimeoutError('test interruption')
        with self.assertRaises(TimeoutError):
            merge_faces(scene, check_budget=check)
        self.assertEqual(scene, original)
        self.assertEqual(len(merge_faces(scene).faces), 6)

    def test_independent_rational_occupancy_oracle_on_small_scenes(self):
        rng = random.Random(5371)
        for trial in range(8):
            boxes = []
            for _ in range(5):
                low = [rng.randrange(-2, 3) for _ in range(3)]
                boxes.append(low + [v+rng.randrange(1, 4)/2 for v in low])
            catalog = merge_faces(scene_for(boxes))
            rational = [tuple(Fraction(v) for v in box) for box in boxes]
            coordinates = [sorted({b[a] for b in rational} | {b[a+3] for b in rational}) for a in range(3)]
            def occupied(point):
                return any(all(b[a] < point[a] < b[a+3] for a in range(3)) for b in rational)
            for axis, (u, v) in enumerate(TANGENT_AXES):
                planes = coordinates[axis]
                for i, plane in enumerate(planes):
                    before = ((planes[i-1] if i else plane-1)+plane)/2
                    after = (plane+(planes[i+1] if i+1 < len(planes) else plane+1))/2
                    for ulo, uhi in zip(coordinates[u], coordinates[u][1:]):
                        for vlo, vhi in zip(coordinates[v], coordinates[v][1:]):
                            uv = (ulo+uhi)/2, (vlo+vhi)/2
                            left, right = [None]*3, [None]*3
                            left[axis], right[axis] = before, after
                            left[u] = right[u] = uv[0]
                            left[v] = right[v] = uv[1]
                            low, high = occupied(left), occupied(right)
                            for sign, expected in ((1, low and not high), (-1, high and not low)):
                                actual = any(inside(f.cover, uv) for f in oriented(catalog, axis, sign, plane))
                                self.assertEqual(actual, expected, (trial, axis, plane, sign, uv))


if __name__ == '__main__':
    unittest.main()
