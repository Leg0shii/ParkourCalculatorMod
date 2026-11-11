import copy
import unittest
from unittest.mock import patch

from standalone.autonomy.block_contacts import solve_contact_branches
from standalone.autonomy.movement_search import iter_movement_programs, movement_settings
from standalone.autonomy.ranking import objective_seed
from standalone.autonomy.tests.test_checkpoint_guidance import flat_scene


class SearchFixTests(unittest.TestCase):
    def test_completed_profiles_survive_partial_queue_and_expansion_caps(self):
        scene = flat_scene()
        cfg = dict(movement_settings(scene), maxPrefixes=1, maxQueuedPrefixes=1, types=['normal'])
        def child(program, choice, **kwargs):
            result = copy.deepcopy(program)
            t, goal = choice
            result['profile'].update(airtime=t, grounded=[False, True])
            result['terminalSupport'] = scene['goalBlock'] if goal else scene['startBlock']
            result['interactions'] = 1
            result['marker'] = choice
            return result
        choices = [(1, False), (2, False), (11, True), (12, True), (3, False)]
        report = {}
        with patch('standalone.autonomy.movement_search.generate_choices', return_value=iter(choices)), \
             patch('standalone.autonomy.movement_composition.append_choice', side_effect=child), \
             patch('standalone.autonomy.movement_search.entry_possible', return_value=True):
            programs = list(iter_movement_programs(scene, settings=cfg, threshold=0, report=report))
        self.assertEqual([p['marker'] for p in programs], [(11, True)])
        self.assertEqual(report['expandedPrefixes'], 1)
        self.assertEqual(report['completeQueueDrops'], 1)
        self.assertGreater(report['queueDrops'], 0)
        self.assertEqual(report['termination'], 'prefix_limit')

    def test_outer_incumbent_survives_invalid_polish_and_repair_continues(self):
        self.exercise_incumbent(repair=True)

    def test_invalid_seed_is_never_retained(self):
        result = self.exercise_incumbent(valid_seed=False)
        self.assertIsNone(result['best'])

    def test_outer_incumbent_survives_later_solver_error(self):
        self.exercise_incumbent(error=True)

    def test_outer_incumbent_survives_later_cancellation(self):
        self.exercise_incumbent(cancel=True)

    def exercise_incumbent(self, valid_seed=True, repair=False, error=False, cancel=False):
        route = dict(constraints=[], passages=[], hitEvents=[], maxTotalRunTicks=0,
                     supportClearance=1e-6, objective=dict(axis='X', sense='MAX'))
        def best(value):
            return dict(allocation=[0], cost=0, plan=dict(ticks=2, events=[]),
                        candidate=dict(yaw=[value, value], positions=[[0, 0]]*3, objective=value))
        calls = []
        def find(branch, compiler, **kwargs):
            calls.append(branch)
            if len(calls) == 1:
                kwargs['on_candidate'](best(1 if valid_seed else 2))
                status = 'error' if error else 'cancelled' if cancel else 'feasible'
                return dict(status=status, termination=status, attempts=[], best=None if error or cancel else best(2))
            return dict(status='feasible', termination='done', attempts=[], best=best(3))
        def filter_candidate(plan, candidate, vertical):
            value = candidate['objective']
            return dict(accepted=value != 2, alternatives=[[dict(leg=0, offset=0, mode='X', cmp='GE', rhs=0)]] if repair else [])
        vertical = dict(feasible=True, vertical={}, y=[1]*3, vy=[0]*3, onGround=[True]*3)
        with patch('standalone.autonomy.block_contacts.find_route', side_effect=find), \
             patch('standalone.autonomy.block_contacts.validate_vertical_schedule', return_value=vertical):
            result = solve_contact_branches(route, [], [], object(), seconds=1, candidate_seconds=.1,
                retries=0, max_branches=2, rank_equal_cost=True, optimize_goal=True, candidate_filter=filter_candidate)
        if valid_seed:
            self.assertEqual(result['best']['candidate']['objective'], 3 if repair else 1)
            self.assertEqual(result['status'], 'feasible')
        self.assertEqual(len(calls), 2 if repair else 1)
        return result

    def test_only_complete_plans_publish_outer_incumbents(self):
        from standalone.autonomy.search import find_route
        from standalone.autonomy.tests.test_acceptance import NoMovementCompiler
        from standalone.autonomy.tests.test_search import route
        from standalone.ipopt import validate
        request = route(2)
        request['blocks'] = [dict(bounds=[0, 1, 0, 1], top=1) for _ in range(3)]
        request.update(runBounds=[[1, 1], [1, 1]], maxTotalRunTicks=2)
        seen = []
        def solve(problem, **kwargs):
            candidate = validate(problem, problem['yaw'])
            kwargs['on_incumbent'](candidate)
            return dict(status='feasible', feasible=True, candidate=candidate)
        with patch('standalone.autonomy.search.solve', side_effect=solve):
            result = find_route(request, NoMovementCompiler(), on_candidate=seen.append)
        self.assertEqual(result['status'], 'feasible')
        self.assertEqual([p['plan']['ticks'] for p in seen], [26])
        self.assertEqual(seen[0]['allocation'], [1, 1])
        self.assertEqual(seen[0]['candidate']['positions'][0], request['initial']['position'][::2])

    def test_custom_projection_controls_fallback_direction(self):
        problem = dict(yaw=[0], base=[0], objective=dict(axis='X', sense='MAX', customYaw=0))
        self.assertEqual(objective_seed(problem), [10.])
        problem['objective']['customYaw'] = -180
        self.assertEqual(objective_seed(problem), [-10.])


if __name__ == '__main__':
    unittest.main()
