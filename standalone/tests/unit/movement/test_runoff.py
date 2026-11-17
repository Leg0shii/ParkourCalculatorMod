import unittest

from parkour_solver.scene.route import initial_guess, prepare_route, schedule


def runoff_route():
    return prepare_route({'version': 1,
                          'initial': {'position': [.5, 1, .5], 'velocity': [.2, -.0784, 0]},
                          'blocks': [{'bounds': [0, 1, 0, 1], 'top': 1},
                                     {'bounds': [2, 3, 0, 1], 'top': .5}],
                          'airtimes': [4], 'kinds': ['walk_off'],
                          'walkOffExits': [{'axis': 'X', 'direction': 1}],
                          'inputs': {'force45': True},
                          'constraints': [{'leg': 0, 'offset': 1, 'mode': 'Z', 'cmp': 'LE', 'rhs': 1}]})


class RunOffTests(unittest.TestCase):
    def test_departure_is_grounded_without_jump_boost_then_air(self):
        r = runoff_route()
        p = schedule(r, [1])
        self.assertEqual(p['grounded'], [True, True, False, False, False])
        self.assertFalse(any(p['jump']))
        self.assertIn((1, 0), p['exitChecks'])
        self.assertIn((0, 0, 'run'), p['supportChecks'])
        self.assertIn((4, 1, 'landing'), p['supportChecks'])
        exit_row = next(w for w in p['walls'] if w['name'].startswith('walk_off:'))
        self.assertEqual((exit_row['t1'], exit_row['mode'], exit_row['cmp']), (1, 'X', 'GE'))
        self.assertGreater(exit_row['rhs'], 1.3)

    def test_zero_delay_after_a_jump_checks_source_support_at_the_preceding_state(self):
        r = prepare_route({'version': 1,
                           'initial': {'position': [.5, 1, .5], 'velocity': [0, -.0784, 0]},
                           'blocks': [{'bounds': [0, 1, 0, 1], 'top': 1},
                                      {'bounds': [3, 4, 0, 1], 'top': 1,
                                       'standingBounds': [2.5, 4.5, 0, 1]},
                                      {'bounds': [5, 6, 0, 1], 'top': .5}],
                           'airtimes': [12, 4], 'kinds': ['jump', 'walk_off']})
        p = schedule(r, [0, 0])
        self.assertIn((11, 1, 'run'), p['supportChecks'])
        self.assertIn((12, 1), p['exitChecks'])
        self.assertNotIn((12, 1, 'run'), p['supportChecks'])
        rows = [w for w in p['walls'] if w['name'].startswith('walk_off:')]
        self.assertEqual([w['t1'] for w in rows], [12])
        self.assertEqual({w['t1'] for w in p['walls'] if w['name'].startswith('run:1:')}, {11})

    def test_added_runs_retime_preceding_support_and_current_exit_together(self):
        r = runoff_route()
        for delay in (1, 3):
            p = schedule(r, [delay])
            self.assertIn((delay - 1, 0, 'run'), p['supportChecks'])
            self.assertIn((delay, 0), p['exitChecks'])
            self.assertEqual([w['t1'] for w in p['walls'] if w['name'].startswith('walk_off:')], [delay])

    def test_authored_regions_are_not_expanded_twice(self):
        r = runoff_route()
        r['blocks'][1] = {'centerBounds': [2, 2.4, -.3, 1.3], 'top': .5}
        p = schedule(r, [0])
        row = next(w for w in p['walls'] if w['name'] == 'landing:1:X:lo')
        self.assertAlmostEqual(row['rhs'], 2.000001)

    def test_diagonal_policy_only_changes_guesses_not_facing_constraints(self):
        r = runoff_route()
        p = schedule(r, [1])
        self.assertEqual(initial_guess(r, p), [-45] * 5)
        self.assertFalse(any(w['mode'] == 'F' for w in p['walls']))
        self.assertEqual(next(w['t1'] for w in p['walls'] if w['name'] == 'route:0'), 2)


if __name__ == '__main__':
    unittest.main()
