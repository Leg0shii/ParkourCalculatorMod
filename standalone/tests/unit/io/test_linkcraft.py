import unittest

from parkour_solver.io.linkcraft import extract_section


def capture():
    def state(ground, x, y):
        return {'onGround': ground, 'pos': [x, y, .5], 'vel': [.1, -.0784, 0], 'sprinting': True}

    def region(tick, lo, hi):
        return {'tick': tick, 'constraints': [{'field': 'X', 'range': True, 'lo': lo, 'hi': hi},
                                              {'field': 'Z', 'range': True, 'lo': -.3, 'hi': 1.3}]}

    return {'mcVersion': '1.8.9',
            'debug': [state(True, .5, 1), state(True, .5, 1), state(False, 1.2, 1.4), state(True, 2.5, 1)],
            'rows': [{'keys': []}, {'keys': ['JUMP', 'W', 'SPRINT']}, {'keys': ['W', 'A', 'SPRINT']}],
            'angleSolver': {'defaultInputs': 'FORCE_45', 'ticks': [region(0, -.3, 1.3), region(2, 1.7, 3.3)]}}


class LinkcraftExtractionTests(unittest.TestCase):
    def test_extracts_without_any_recorded_facing_field(self):
        route = extract_section(capture(), 1, 3, 'test-source')
        self.assertEqual(route['airtimes'], [2])
        self.assertEqual(route['kinds'], ['jump'])
        self.assertEqual(route['initial']['yaw'], 0)
        self.assertFalse(route['source']['recordedFacingsUsed'])
        self.assertEqual(route['constraints'], [])
        self.assertFalse(route['source']['authoredScalarRulesUsed'])
        self.assertEqual(route['blocks'][1]['centerBounds'], [1.7, 3.3, -.3, 1.3])

    def test_missing_geometry_is_not_invented(self):
        c = capture()
        c['angleSolver']['ticks'].pop()
        with self.assertRaisesRegex(ValueError, 'No complete authored landing region'):
            extract_section(c, 1, 3, 'test-source')

    def test_grounded_height_change_is_not_silently_retimed(self):
        c = capture()
        c['debug'][2]['onGround'] = True
        c['debug'][3]['onGround'] = False
        c['debug'].append({'onGround': True, 'pos': [2.5, 2, .5], 'vel': [0, -.0784, 0], 'sprinting': True})
        c['rows'].append({'keys': ['JUMP']})
        with self.assertRaisesRegex(ValueError, 'grounded height/contact change'):
            extract_section(c, 1, 4, 'test-source')


if __name__ == '__main__':
    unittest.main()
