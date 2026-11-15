from tests.support.paths import AUTONOMY_DATA
import json
from pathlib import Path
import unittest

from parkour_solver.api import prepare_route, schedule
from parkour_solver.scene.route import supports


FULL_ROUTE = AUTONOMY_DATA / 'fixtures' / 'linkcraft11-j0-j16.json'


class ContactScheduleTests(unittest.TestCase):
    def test_full_route_keeps_all_jumps_runoff_step_and_run_cost(self):
        r = prepare_route(json.loads(FULL_ROUTE.read_text()))
        self.assertEqual(r['kinds'].count('jump'), 17)
        self.assertEqual(r['kinds'].count('walk_off'), 1)
        self.assertEqual(r['kinds'].count('step'), 1)
        self.assertFalse(r['source']['recordedFacingsUsed'])
        self.assertFalse(r['source']['authoredScalarRulesUsed'])
        self.assertEqual(r['constraints'], [])
        p = schedule(r, r['source']['recordedAllocation'])
        self.assertEqual(p['ticks'], 130)
        self.assertEqual([e['jump'] for e in p['events']], r['source']['departures'])
        self.assertEqual([e['landing'] for e in p['events']], r['source']['landings'])
        self.assertEqual(sum(r['source']['recordedAllocation']) + r['kinds'].count('step'), 12)
        self.assertFalse(p['jump'][118])
        self.assertTrue(p['grounded'][118])
        self.assertIn((119, 17, 'landing'), p['supportChecks'])

    def test_reduced_jump_must_enter_target_on_first_movement(self):
        r = prepare_route(json.loads(FULL_ROUTE.read_text()))
        p = schedule(r, r['source']['recordedAllocation'])
        leg = r['source']['jumpIndices'].index(10)
        outside = [w for w in p['walls'] if w['name'] == 'contact_outside:' + str(leg)]
        inside = [w for w in p['walls'] if w['name'] == 'contact_enter:' + str(leg)]
        self.assertEqual([(w['t1'], w['mode'], w['cmp']) for w in outside], [(83, 'Z', 'GE')])
        self.assertEqual({w['t1'] for w in inside}, {84})
        self.assertEqual({w['mode'] for w in inside}, {'X', 'Z'})
        self.assertIn((84, leg + 1, 'contact'), p['supportChecks'])

    def test_step_landing_is_after_horizontal_movement_with_no_early_landing(self):
        r = prepare_route(json.loads(FULL_ROUTE.read_text()))
        p = schedule(r, r['source']['recordedAllocation'])
        leg = r['source']['jumpIndices'].index(13)
        self.assertIn((113, leg + 1, 'landing'), p['supportChecks'])
        outside = [w['t1'] for w in p['walls'] if w['name'] == 'contact_outside:' + str(leg)]
        self.assertEqual(outside, [111, 112])
        self.assertNotIn((112, leg + 1, 'landing'), p['supportChecks'])

    def test_run_support_is_not_limited_to_the_landing_rectangle(self):
        r = prepare_route(json.loads(FULL_ROUTE.read_text()))
        leg = r['source']['jumpIndices'].index(10)
        block = r['blocks'][leg]
        self.assertFalse(supports(block, 732.8, -1088.2))
        self.assertTrue(supports(block, 732.8, -1088.2, kind='run'))
        p = schedule(r, r['source']['recordedAllocation'])
        run = next(w for w in p['walls'] if w['name'] == 'run:' + str(leg) + ':Z:hi')
        landing = next(w for w in p['walls'] if w['name'] == 'landing:' + str(leg) + ':Z:hi')
        self.assertGreater(run['rhs'], landing['rhs'] + .9)


if __name__ == '__main__':
    unittest.main()
