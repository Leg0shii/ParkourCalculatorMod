import copy
import unittest
from unittest.mock import patch

from parkour_solver.spatial.cells import Cell, Decomposition, decompose
from parkour_solver.validation.scoped_scene import CorridorAdapter, SearchWork
from parkour_solver.spatial.compat.corridors import Corridor, corridor_for_points, landmarks
from parkour_solver.movement.program import boundary_state, empty_program
from parkour_solver.movement.generation import generate_choices
from parkour_solver.movement.contacts import contact_variants
from parkour_solver.movement.ladder.generation import ladder_choices
from tests.unit.spatial.test_cell_routes import side_scene


class CellLocalTests(unittest.TestCase):
    def test_early_filter_removes_remote_support_before_choice_product(self):
        scene = side_scene()
        scene['collisionScene']['solids'].append([2, 0, 5, 3, 1, 6])
        scene['blocks'].append(dict(id='remote', sourceBlock='remote', solidIndex=3, top=1, bounds=[2, 3, 5, 6]))
        corridor = Corridor(0, [], [], [], (), [(-1, 0, -1, 6, 0, 2)], 0)
        adapter = CorridorAdapter(scene, corridor, [])
        program = empty_program(scene['initial'])
        original = dict(mode='open', scene=scene, program=program, state=boundary_state(program), maxInputs=15)
        before = list(generate_choices('normal', original))
        conditioned = dict(original)
        adapter.context(conditioned)
        after = list(generate_choices('normal', conditioned))
        self.assertGreater(len(before), len(after))
        self.assertNotIn('remote', [c['terminalSupport'] for c in after])
        self.assertIn('remote', [b['id'] for b in scene['blocks']])
        self.assertEqual(conditioned['scene']['collisionScene'], scene['collisionScene'])
        self.assertEqual(adapter.initial, scene['initial'])

    def test_ladder_region_filter_runs_before_schedule_generation(self):
        scene = side_scene()
        scene['climbableCells'] = [[0, 1, 0]]
        context = dict(scene=scene, regionAllowed=lambda r: False, maxInputs=8)
        with patch('parkour_solver.movement.ladder.generation.generate_ladder_schedules') as generate:
            self.assertEqual(list(ladder_choices(context, 'ladder_climb')), [])
            generate.assert_not_called()

    def test_contact_filter_preserves_original_solid_indices(self):
        solids = [[0, 0, 0, 1, 1, 1], [3, 0, 0, 4, 1, 1]]
        result = contact_variants(solids, 3, max_variants=5, max_events=1, box_allowed=lambda b: b == solids[1])
        self.assertTrue(result['variants'])
        for variant in result['variants']:
            for event in variant:
                self.assertEqual(event['box'], solids[1])
                self.assertEqual(event['solidIndex'], 1)

    def test_wrong_side_gets_a_timed_constraint_without_changing_initial_state(self):
        scene = side_scene()
        before = copy.deepcopy(scene['initial'])
        gates = landmarks(scene)
        corridor = corridor_for_points(decompose(scene), [[.5, 1, .5], [1, 1, -1], [4, 1, -1], [5.5, 1, .5]], gates)
        adapter = CorridorAdapter(scene, corridor, gates)
        result = adapter.candidate(dict(events=[dict(jump=0)]),
            dict(positions=[[.5, .5], [1, 2], [4, 2], [5.5, .5]]), dict(y=[1]*4))
        self.assertFalse(result['accepted'])
        self.assertTrue(result['alternatives'])
        self.assertTrue(any(r['mode'] == 'Z' for alternative in result['alternatives'] for r in alternative))
        self.assertEqual(scene['initial'], before)

    def test_forward_domains_reject_reverse_cell_order_but_allow_declared_returns(self):
        scene = side_scene()
        cells = [Cell(i, (lo,-5,-2,hi,8,2), 3) for i,(lo,hi) in enumerate([(-1,2),(2,4),(4,6)])]
        d = Decomposition((-1,-5,-2,6,8,2), cells, [], [], {}, {})
        corridor = Corridor(0, [0,1,2], [], [], (), [(-1,0,-2,6,0,2)], 0, stage_paths=[[0,1,2]])
        program = dict(profile=dict(airtime=2, contactEvents=[], positionConstraints=[
            dict(mode='X',t1=1,cmp='GE',rhs=5), dict(mode='X',t1=2,cmp='LE',rhs=1)]))
        adapter = CorridorAdapter(scene, corridor, [], decomposition=d)
        self.assertFalse(adapter.prefix(program))
        self.assertFalse(adapter.prefix(program))
        self.assertEqual(adapter.counts['forwardCacheHits'], 1)
        corridor.stage_paths = [[0,1,2,1,0]]
        self.assertTrue(CorridorAdapter(scene,corridor,[],decomposition=d).prefix(program))
        corridor.stage_paths = [[0]]
        d.cells[0] = Cell(0, (-1,-5,-2,6,8,2), 3)
        self.assertTrue(CorridorAdapter(scene,corridor,[],decomposition=d).prefix(program))

    def test_numerical_budget_counts_every_call_and_preserves_accepted_state(self):
        work = SearchWork(2)
        work.proposal({})
        work.before_solve({})
        work.before_solve({})
        work.accepted({})
        with self.assertRaises(TimeoutError):
            work.before_solve({})
        self.assertEqual(work.summary()['solverCalls'], 2)
        self.assertEqual(work.summary()['acceptedBranches'], 1)
        self.assertIsNotNone(work.summary()['firstAcceptedSeconds'])
        self.assertEqual(work.termination, 'numerical_call_limit')


if __name__ == '__main__':
    unittest.main()
