"""Shared test builders extracted from tests.unit.search.test_search."""
def route(jumps=2):
    return {'version': 1, 'initial': {'position': [.5, 1, .5], 'velocity': [0, 0, 0], 'yaw': -90},
            'blocks': [{'bounds': [3 * i, 3 * i + 1, 0, 1], 'top': 1} for i in range(jumps + 1)],
            'airtimes': [12] * jumps, 'runBounds': [[0, 2] for _ in range(jumps)],
            'maxTotalRunTicks': 4}
