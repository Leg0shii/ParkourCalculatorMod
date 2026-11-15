from tests.support.paths import AUTONOMY_DATA
import json
from pathlib import Path
import unittest

from parkour_solver.api import prepare_route, schedule
from parkour_solver.scene.route import supports
from parkour_solver.numerics import prepare, validate
from parkour_solver.numerics.model import fingerprint


class FullRouteWitnessTests(unittest.TestCase):
    def test_full_linkcraft_0_to_16_solution_is_independently_valid(self):
        directory = AUTONOMY_DATA
        artifacts = directory / 'experiments' / 'linkcraft11-j0-j16'
        request = prepare(json.loads((artifacts / 'request.json').read_text()))
        saved = json.loads((artifacts / 'solution.json').read_text())
        summary = json.loads((artifacts / 'summary.json').read_text())
        route = prepare_route(json.loads((directory / 'fixtures' / 'linkcraft11-j0-j16.json').read_text()))
        result = validate(request, saved['candidate']['yaw'])
        self.assertTrue(result['feasible'], result)
        self.assertLessEqual(result['violation'], 1e-7)
        self.assertEqual(request['ticks'], 130)
        self.assertEqual(len(request['walls']), 166)
        self.assertTrue(all(w['mode'] in ('X', 'Z') for w in request['walls']))
        self.assertEqual(fingerprint(request), saved['problemHash'])
        plan = schedule(route, summary['allocation'])
        for tick, index, kind in plan['supportChecks']:
            self.assertTrue(supports(route['blocks'][index], *result['positions'][tick], kind=kind),
                            (tick, index, kind))
        for tick, index in plan['exitChecks']:
            self.assertFalse(supports(route['blocks'][index], *result['positions'][tick], kind='run'))
        self.assertEqual(summary['groundRunTicks'], 12)
        self.assertFalse(summary['minimumProven'])


if __name__ == '__main__':
    unittest.main()
