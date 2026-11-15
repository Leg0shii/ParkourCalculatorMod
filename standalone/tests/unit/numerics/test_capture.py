from tests.support.paths import NUMERICAL_DATA
import copy
import gzip
import json
import pathlib
import unittest

from parkour_solver.io.capture import attach_capture
from parkour_solver.physics.vertical import CLIMB_CARRY, vertical_carry, vertical_step
from parkour_solver.numerics.model import fingerprint, prepare, validate
from tests.unit.numerics.test_model import problem


FIXTURES = NUMERICAL_DATA / 'fixtures'


def read(name):
    with gzip.open(FIXTURES / name, 'rt') as stream:
        return json.load(stream)


class CaptureTests(unittest.TestCase):
    def test_climb_signatures_include_entry_and_idle_ladder_states(self):
        source = read('real11-ladder-evidence.json.gz')
        original = read('real11-400.json.gz')
        snapshot = copy.deepcopy((original, source))
        result = attach_capture(original, source)
        self.assertEqual((original, source), snapshot)
        expected = set(range(136, 153)) | set(range(189, 194)) | set(range(356, 363)) | set(range(395, 401))
        self.assertEqual({t for t, on in enumerate(result['ladderTicks']) if on}, expected)
        self.assertNotIn(189, result['source']['ladderCapture']['climbStates'])
        self.assertNotIn(361, result['source']['ladderCapture']['climbStates'])
        saved = read('real11-400-ladders.json.gz')
        self.assertEqual(fingerprint(saved), fingerprint(result))

    def test_vertical_reference_replays_capture_exactly(self):
        for ticks in (200, 400):
            with self.subTest(ticks=ticks):
                request = read('real11-' + str(ticks) + '-ladders.json.gz')
                config = request['vertical']
                source = read('real11-ladder-evidence.json.gz')['debug']
                y, vy, ground = config['position'], config['velocity'], config['onGround']
                for t in range(ticks):
                    y, movement, ground, errors = vertical_step(config, t, y, vy, request['ladderTicks'][t],
                                                               ground, 1e-7)
                    vy = vertical_carry(movement, request['ladderTicks'][t + 1], source[t + 1]['wallCollision'])
                    self.assertEqual(errors, [])
                    self.assertEqual(y, config['referencePosition'][t + 1])
                    self.assertEqual(vy, config['referenceVelocity'][t + 1])
                    self.assertEqual(ground, source[t + 1]['onGround'])

    def test_mid_climb_start_preserves_incoming_velocity_and_previous_hit(self):
        source = read('real11-ladder-evidence.json.gz')
        state = source['debug'][136]
        request = problem(16)
        request.update(position=[state['pos'][0], state['pos'][2]],
                       velocity=[state['vel'][0], state['vel'][2]])
        result = attach_capture(request, source, 136)
        self.assertTrue(result['ladderTicks'][0])
        self.assertTrue(result['vertical']['previousHorizontalCollision'])
        self.assertEqual(result['vertical']['velocity'], CLIMB_CARRY)

    def test_profile_range_and_initial_state_mismatches_are_rejected(self):
        original = read('real11-200.json.gz')
        source = read('real11-ladder-evidence.json.gz')
        for key, value in [('mcVersion', '1.12.2'), ('debug', []), ('debug', 'bad'),
                           ('angleSolver', None), ('angleSolver', {'ticks': [None]}),
                           ('angleSolver', {'ticks': [{'override': {'medium': 'LADDER'}}]})]:
            changed = dict(source, **{key: value})
            with self.subTest(key=key), self.assertRaises(ValueError):
                attach_capture(original, changed)
        with self.assertRaises(ValueError):
            attach_capture(original, source, 1)

    def test_vertical_reference_failure_cannot_be_accepted_as_feasible(self):
        request = problem(1)
        request['vertical'] = {'profile': 'minecraft-1.8.9', 'position': 0, 'velocity': 0,
                               'onGround': False, 'previousHorizontalCollision': False,
                               'sneak': [False], 'jumpVelocity': [None],
                               'referencePosition': [0, 0.1]}
        result = validate(prepare(request), [0])
        self.assertFalse(result['feasible'])
        self.assertEqual(result['worstConstraint'], 'Y@1')


if __name__ == '__main__':
    unittest.main()
