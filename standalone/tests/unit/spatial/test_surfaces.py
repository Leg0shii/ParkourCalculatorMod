"""Scene surfaces: physical face unions, player surfaces and obstacle clusters.

Physical faces are checked against source geometry and an occupancy oracle;
clusters against their maximal free rectangles; player surfaces against the cell
decomposition's contact patches.
"""
import copy
from dataclasses import FrozenInstanceError
from fractions import Fraction
import itertools
import json
import math
import random
import unittest

from parkour_solver.scene.geometry import BODY_HEIGHT, expanded_box
from parkour_solver.spatial.cells import decompose
from parkour_solver.spatial.regions import canonical_cover, geometry_key
from parkour_solver.spatial.surfaces import (FaceCatalog, TANGENT_AXES, free_rectangles, merge_faces, nested,
                                             scene_geometry)
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
                                faces = oriented(catalog, axis, sign, plane)
                                actual = any(inside(f.cover, uv) for f in faces)
                                self.assertEqual(actual, expected, (trial, axis, plane, sign, uv))
                                expected_sources = {(j, axis, sign) for j, b in enumerate(rational)
                                    if expected and b[axis if sign < 0 else axis+3] == plane
                                    and b[u] < uv[0] < b[u+3] and b[v] < uv[1] < b[v+3]}
                                actual_sources = {key for f in faces for key in catalog.contributors_at(
                                    f.id, [float(uv[0]), float(uv[1])], boundary='open')}
                                self.assertEqual(actual_sources, expected_sources,
                                                 (trial, axis, plane, sign, uv))


class FaceRepresentationTests(unittest.TestCase):
    def chain(self):
        scene = scene_for([[0, 0, 0, 1, 1, 1], [1, 0, 0, 2, 1, 1], [2, 0, 0, 3, 1, 1]])
        for i, b in enumerate(scene['blocks']):
            b.update(id='chain#'+str(i), sourceBlock='chain')
        return merge_faces(scene)

    def test_reverse_lookups_keep_compound_parts_and_hidden_faces(self):
        catalog = self.chain()
        self.assertEqual(len(catalog.faces_for_block('chain')), 6)
        self.assertEqual(len(catalog.by_part['chain#1']), 4)
        self.assertEqual(len(catalog.faces_for_solid(1)), 4)
        self.assertEqual(catalog.by_source_face[(1, 0, -1)], ())
        top, = oriented(catalog, 1, 1)
        self.assertIs(catalog.face(top.id), top)
        self.assertIn(top.id, catalog.by_source_face[(1, 1, 1)])
        with self.assertRaises(KeyError):
            catalog.faces_for_block('unknown')
        with self.assertRaises(ValueError):
            catalog.faces_for_solid(True)

    def test_point_rectangle_and_boundary_queries_preserve_member_domains(self):
        catalog = self.chain()
        top, = oriented(catalog, 1, 1)
        self.assertEqual(catalog.contributors_at(top.id, [.5, .5], boundary='open'), ((0, 1, 1),))
        self.assertEqual(catalog.contributors_at(top.id, [1, .5], boundary='closed'), ((0, 1, 1), (1, 1, 1)))
        self.assertEqual(catalog.contributors_at(top.id, [1, .5], boundary='open'), ())
        self.assertEqual(catalog.contributors_over(top.id, [.5, .2, 1.5, .8]), ((0, 1, 1), (1, 1, 1)))
        self.assertEqual(catalog.contributors_over(top.id, [1, .2, 1, .8]), ())
        self.assertEqual(catalog.contributors_over(top.id, [1, .2, 1, .8], positive_area=False),
                         ((0, 1, 1), (1, 1, 1)))

    def test_open_membership_does_not_treat_cover_encoding_seams_as_boundaries(self):
        catalog = merge_faces(scene_for([[0, 0, 0, 2, 2, 1], [0, 1, 1, 1, 2, 2]]))
        face, = oriented(catalog, 2, 1, 1)
        self.assertEqual(catalog.contributors_at(face.id, [1, .5], boundary='open'), ((0, 2, 1),))
        self.assertEqual(catalog.contributors_at(face.id, [1, 1], boundary='open'), ())

    def test_duplicate_source_members_all_reported_and_holes_have_none(self):
        catalog = merge_faces(scene_for([[0, 0, 0, 1, 1, 1]]*2))
        top, = oriented(catalog, 1, 1)
        self.assertEqual(catalog.contributors_at(top.id, [.5, .5], boundary='open'), ((0, 1, 1), (1, 1, 1)))
        ring = merge_faces(scene_for([[0, 0, 0, 3, 1, 1], [0, 0, 2, 3, 1, 3],
                                     [0, 0, 1, 1, 1, 2], [2, 0, 1, 3, 1, 2]]))
        top, = oriented(ring, 1, 1)
        self.assertEqual(ring.contributors_over(top.id, [1.1, 1.1, 1.9, 1.9]), ())
        self.assertEqual(ring.contributors_at(top.id, [1.5, 1.5], boundary='closed'), ())

    def test_selected_members_can_disconnect_cover_without_revealing_hidden_geometry(self):
        catalog = self.chain()
        top, = oriented(catalog, 1, 1)
        self.assertEqual(catalog.cover_for_sources(top.id, [(0, 1, 1), (2, 1, 1)]),
                         ((0, 0, 1, 1), (2, 0, 3, 1)))
        self.assertEqual(catalog.cover_for_sources(top.id, []), ())
        self.assertEqual(catalog.cover_for_sources(top.id, [(0, 0, -1)]), ())
        split = merge_faces(scene_for([[0, 0, 0, 3, 1, 1], [1, 1, 0, 2, 2, 1]]))
        for face in oriented(split, 1, 1, 1):
            self.assertEqual(split.cover_for_sources(face.id, [(0, 1, 1)]), face.cover)
        exposed = merge_faces(scene_for([[0, 0, 0, 3, 1, 1]]))
        whole, = oriented(exposed, 1, 1)
        self.assertEqual(whole.cover, ((0, 0, 3, 1),))

    def test_round_trip_and_exports_are_independent_and_records_are_immutable(self):
        catalog = self.chain()
        saved = json.loads(json.dumps(catalog.to_dict(), allow_nan=False))
        loaded = FaceCatalog.from_dict(saved, expected_scene_key=catalog.scene_key)
        self.assertEqual(loaded, catalog)
        saved['solids'][0]['box'][0] = -100
        self.assertEqual(loaded.solids[0].box[0], 0)
        exported = loaded.to_dict()
        exported['faces'].clear()
        self.assertEqual(len(catalog.to_dict()['faces']), 6)
        with self.assertRaises(TypeError):
            catalog.by_block['chain'] = ()
        with self.assertRaises(FrozenInstanceError):
            catalog.faces[0].plane = 99
        with self.assertRaises(TypeError):
            catalog.faces[0].cover[0][0] = 99
        self.assertIs(copy.deepcopy(catalog), catalog)
        self.assertEqual(FaceCatalog.from_dict(merge_faces(scene_for([])).to_dict()).faces, ())

    def test_tampered_geometry_provenance_and_schema_rejected_even_with_new_hash(self):
        original = self.chain().to_dict()
        mutations = [lambda x: x.update(version=True), lambda x: x.update(version=99),
                     lambda x: x.update(sceneKey='other'), lambda x: x.update(extra='unknown'),
                     lambda x: x['sourceFaces'][0].update(exposedCover=[]),
                     lambda x: x['faces'][0]['contributions'][0].update(sourceFaceKey=[999, 0, 1]),
                     lambda x: x['faces'][0].update(cover=[[0, 0, 100, 100]]),
                     lambda x: x['faces'][0].update(normalSign=True),
                     lambda x: x['solids'][0].update(solidIndex=True),
                     lambda x: x['solids'][0].update(extra='unknown')]
        for mutate in mutations:
            saved = copy.deepcopy(original)
            mutate(saved)
            saved['catalogId'] = 'face-catalog:' + geometry_key({k: v for k, v in saved.items() if k != 'catalogId'})
            with self.subTest(mutate=mutate), self.assertRaises(ValueError):
                FaceCatalog.from_dict(saved)
        saved = copy.deepcopy(original)
        saved['faces'][0]['plane'] += .5
        with self.assertRaisesRegex(ValueError, 'hash'):
            FaceCatalog.from_dict(saved)
        with self.assertRaisesRegex(ValueError, 'different scene'):
            FaceCatalog.from_dict(original, expected_scene_key='wrong')

    def test_query_validation_and_catalog_identity_changes(self):
        catalog = self.chain()
        top, = oriented(catalog, 1, 1)
        invalid = [lambda: catalog.contributors_at(top.id, [0, 0], boundary='maybe'),
                   lambda: catalog.contributors_at(top.id, [True, 0], boundary='closed'),
                   lambda: catalog.contributors_over(top.id, [1, 0, 0, 1]),
                   lambda: catalog.contributors_over(top.id, [0, 0, 1, 1], positive_area=1),
                   lambda: catalog.cover_for_sources(top.id, [(True, 1, 1)])]
        for query in invalid:
            with self.assertRaises(ValueError):
                query()
        with self.assertRaises(KeyError):
            catalog.cover_for_sources(top.id, [(99, 1, 1)])
        scene = scene_for([[0, 0, 0, 1, 1, 1], [1, 0, 0, 2, 1, 1], [2, 0, 0, 3, 1, 1]])
        other = merge_faces(scene)
        self.assertEqual({f.id for f in catalog.faces}, {f.id for f in other.faces})
        self.assertNotEqual(catalog.catalog_id, other.catalog_id)



B5 = [730, 0, -1096, 731, 6, -1095]
B6 = [730, 0, -1095, 731, 20, -1094]
B7 = [729, 0, -1095, 730, 7, -1093]


def sides(geometry, cluster, y):
    return {s.bounds: tuple(sorted(set().union(*s.owners))) for s in geometry.sides(cluster, geometry.active(cluster, y, y))}


def interior(box, x, z):
    return box[0] < x < box[3] and box[2] < z < box[5]


def within(region, x, z):
    return region[0] <= x <= region[2] and region[1] <= z <= region[3]


class ClusterTests(unittest.TestCase):
    def test_real11_flush_blocks_form_one_cluster_with_shared_sides(self):
        geometry = scene_geometry(dict(solids=[B5, B6, B7]))
        cluster, = geometry.clusters([0, 1, 2])
        self.assertEqual(cluster.members, (0, 1, 2))
        b6, b7 = expanded_box(B6), expanded_box(B7)
        self.assertEqual(sides(geometry, cluster, 6.5), {
            (('Z', -1, b6[2]),): (1, 2),
            (('X', 1, b6[3]),): (1,),
            (('X', -1, b7[0]),): (2,),
            (('Z', 1, b7[5]),): (2,),
            (('X', 1, b7[3]), ('Z', 1, b6[5])): (1, 2)})
        self.assertEqual(sides(geometry, cluster, 7.5), {
            (('Z', -1, b6[2]),): (1,),
            (('X', 1, b6[3]),): (1,),
            (('X', -1, b6[0]),): (1,),
            (('Z', 1, b6[5]),): (1,)})
        self.assertEqual(geometry.active(cluster, 7., 7.2), (1,))
        self.assertEqual(geometry.active(cluster, 5.5, 6.), (0, 1, 2))

    def test_lower_and_upper_sides_on_one_side_are_nested(self):
        geometry = scene_geometry(dict(solids=[B6, B7]))
        cluster, = geometry.clusters([0, 1])
        low = {s.bounds: s for s in geometry.sides(cluster, (0, 1))}
        high = {s.bounds: s for s in geometry.sides(cluster, (0,))}
        west_low = low[(('X', -1, expanded_box(B7)[0]),)]
        west_high = high[(('X', -1, expanded_box(B6)[0]),)]
        self.assertTrue(nested(west_low.region, west_high.region))
        for side in low.values():
            self.assertTrue(any(nested(side.region, other.region) for other in high.values()))

    def test_ring_encloses_one_bounded_pocket(self):
        ring = [(0, 0, 0, 3, 1, 1), (0, 0, 2, 3, 1, 3), (0, 0, 0, 1, 1, 3), (2, 0, 0, 3, 1, 3)]
        self.assertIn((1, 1, 2, 2), free_rectangles(ring))
        self.assertEqual(sum(all(math.isfinite(v) for v in r) for r in free_rectangles(ring)), 1)

    def test_blocks_a_real_gap_apart_are_separate_clusters(self):
        geometry = scene_geometry(dict(solids=[[0, 0, 0, 1, 2, 1], [1.612, 0, 0, 2.612, 2, 1]]))
        self.assertEqual([c.members for c in geometry.clusters([0, 1])], [(0,), (1,)])
        geometry = scene_geometry(dict(solids=[[0, 0, 0, 1, 2, 1], [1.5, 0, 0, 2.5, 2, 1]]))
        self.assertEqual([c.members for c in geometry.clusters([0, 1])], [(0, 1)])

    def test_out_of_scope_solids_are_ignored(self):
        geometry = scene_geometry(dict(solids=[B5, B6, B7]))
        self.assertEqual([c.members for c in geometry.clusters([1])], [(1,)])

    def test_random_unions_are_covered_exactly_by_maximal_sides(self):
        rng = random.Random(7)
        for _ in range(60):
            boxes = []
            for _ in range(rng.randint(1, 5)):
                x, z = rng.randint(0, 6)/2, rng.randint(0, 6)/2
                boxes.append((x, 0, z, x+rng.randint(1, 4)/2, 1, z+rng.randint(1, 4)/2))
            rects = free_rectangles(boxes)
            for _ in range(300):
                x, z = rng.uniform(-1, 6), rng.uniform(-1, 6)
                free = not any(interior(b, x, z) for b in boxes)
                self.assertEqual(any(within(r, x, z) for r in rects), free, (boxes, x, z))
            for r in rects:
                self.assertFalse(any(b[0] < r[2] and r[0] < b[3] and b[2] < r[3] and r[1] < b[5] for b in boxes))
                for i, step in ((0, -1e-6), (1, -1e-6), (2, 1e-6), (3, 1e-6)):
                    if math.isinf(r[i]):
                        continue
                    grown = list(r)
                    grown[i] += step
                    self.assertTrue(any(b[0] < grown[2] and grown[0] < b[3] and b[2] < grown[3] and grown[1] < b[5]
                                        for b in boxes), (boxes, r, i))



def planar(rects):
    return canonical_cover((r[0], 0, r[1], r[2], 0, r[3]) for r in rects)


class PlayerSurfaceTests(unittest.TestCase):
    def tops(self, geometry, indices=None):
        return [s for s in geometry.player_surfaces(indices) if s.axis == 1 and s.sign == 1]

    def test_taller_neighbour_clips_a_top_into_an_l(self):
        geometry = scene_geometry(dict(solids=[B5, B6, B7]))
        b5, b6, b7 = expanded_box(B5), expanded_box(B6), expanded_box(B7)
        tops = {s.plane: s for s in self.tops(geometry)}
        self.assertEqual(tops[6].cover, ((b5[0], b5[2], b5[3], b6[2]),))
        self.assertEqual(tops[7].cover, ((b7[0], b7[2], b6[0], b7[5]), (b6[0], b6[5], b7[3], b7[5])))
        self.assertEqual((tops[7].members, tops[7].cluster), ((2,), 0))

    def test_modeled_subset_ignores_unmodeled_neighbours(self):
        geometry = scene_geometry(dict(solids=[B5, B6, B7]))
        b5 = expanded_box(B5)
        top, = self.tops(geometry, [0])
        self.assertEqual(top.cover, ((b5[0], b5[2], b5[3], b5[5]),))
        self.assertEqual(top.cluster, 0)
        self.assertEqual([s.cluster for s in self.tops(geometry, [1, 2])], [1, 1])

    def test_touching_coplanar_boxes_form_one_surface_with_both_members(self):
        boxes = [[0, 3, 0, 1, 4, 1], [1, 3, 0, 2, 4, 1]]
        geometry = scene_geometry(dict(solids=boxes))
        a, b = expanded_box(boxes[0]), expanded_box(boxes[1])
        bottom, = [s for s in geometry.player_surfaces() if s.axis == 1 and s.sign == -1]
        self.assertEqual((bottom.plane, bottom.members), (a[1], (0, 1)))
        self.assertEqual(bottom.cover, ((a[0], a[2], b[3], b[5]),))
        self.assertEqual(len(bottom.contributions), 2)

    def test_exact_height_slot_keeps_both_faces(self):
        floor = [0, 0, 0, 1, 1, 1]
        ceiling = [0, 1+BODY_HEIGHT, 0, 1, 3, 1]
        self.assertEqual(expanded_box(ceiling)[1], 1)
        surfaces = scene_geometry(dict(solids=[floor, ceiling])).player_surfaces()
        at = {(s.sign, s.members) for s in surfaces if s.axis == 1 and s.plane == 1}
        self.assertEqual(at, {(1, (0,)), (-1, (1,))})

    def test_ring_top_keeps_its_hole(self):
        ring = [[0, 0, 0, 3, 1, 1], [0, 0, 2, 3, 1, 3], [0, 0, 0, 1, 1, 3], [2, 0, 0, 3, 1, 3]]
        top, = self.tops(scene_geometry(dict(solids=ring)))
        self.assertEqual(top.members, (0, 1, 2, 3))
        self.assertFalse(any(r[0] <= 1.5 <= r[2] and r[1] <= 1.5 <= r[3] for r in top.cover))
        self.assertTrue(any(r[0] <= 1.2 <= r[2] and r[1] <= 1.5 <= r[3] for r in top.cover))

    def test_covers_match_the_cell_contact_patches(self):
        rng = random.Random(20261117)
        for trial in range(30):
            boxes = []
            for _ in range(rng.randint(1, 4)):
                low = [rng.randint(-8, 5)/4 for _ in range(3)]
                boxes.append(low + [v+rng.randint(1, 8)/4 for v in low])
            scene = scene_for(boxes, bounds=(-8, -8, -8, 8, 8, 8))
            expected, by_solid = {}, {}
            for patch in decompose(scene).contacts:
                u, v = TANGENT_AXES[patch.axis]
                plane = patch.bounds[patch.axis]
                rect = patch.bounds[u], patch.bounds[v], patch.bounds[u+3], patch.bounds[v+3]
                expected.setdefault((patch.axis, patch.side, plane), []).append(rect)
                by_solid.setdefault((patch.solid, patch.axis, patch.side), []).append(rect)
            actual, contributed = {}, {}
            for surface in scene_geometry(scene['collisionScene']).player_surfaces():
                actual.setdefault((surface.axis, surface.sign, surface.plane), []).extend(surface.cover)
                for solid, cover in surface.contributions:
                    contributed.setdefault((solid, surface.axis, surface.sign), []).extend(cover)
            self.assertEqual({k: planar(r) for k, r in actual.items()},
                             {k: planar(r) for k, r in expected.items() if planar(r)}, (trial, boxes))
            self.assertEqual({k: planar(r) for k, r in contributed.items()},
                             {k: planar(r) for k, r in by_solid.items() if planar(r)}, (trial, boxes))
