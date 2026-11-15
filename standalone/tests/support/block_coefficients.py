"""Shared test builders extracted from tests.unit.search.test_block_search."""
from parkour_solver.scene.route import HALF_WIDTH, schedule

class Coefficients:
    def compile(self, route, plan, seconds):
        return {'magnitude': [.3 if j else .1 if g else .02 for g, j in zip(plan['grounded'], plan['jump'])],
                'friction': [.5 if g else .9 for g in plan['grounded']], 'base': [0] * len(plan['jump'])}

    def derive_footprints(self, route, seconds):
        return [[b['bounds'][0] - HALF_WIDTH, b['bounds'][1] + HALF_WIDTH,
                 b['bounds'][2] - HALF_WIDTH, b['bounds'][3] + HALF_WIDTH] for b in route['blocks']]
