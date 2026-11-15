"""Shared test builders extracted from tests.unit.movement.test_ladder_schedules."""
from parkour_solver.physics.vertical import CLIMB_CARRY, REST_CARRY, Y_DRAG, f32

def request():
    return {'version': 1, 'initial': {'position': 2., 'velocity': REST_CARRY},
            'region': {'minY': 0, 'maxY': 5},
            'entries': [{'family': 'front', 'firstValidatedState': 1, 'unresolvedStates': [0]}],
            'targetTop': 0., 'maxInputs': 25, 'entryDelay': 0, 'exitStates': [7],
            'exitKinds': ['side'], 'maxSchedules': 300, 'maxSkeletons': 100, 'maxMaskWork': 10000}
