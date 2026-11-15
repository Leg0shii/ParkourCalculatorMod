from tests.support.ladder_schedules import request
import copy
import itertools
import math
import random
import threading
import unittest

from parkour_solver.movement.ladder.schedules import climb_variants, generate_ladder_schedules, prepare_ladder_request, sneak_patterns, trace_ladder_schedule
from parkour_solver.numerics import prepare, validate
from parkour_solver.physics.vertical import CLIMB_CARRY, REST_CARRY, Y_DRAG, f32


def reference_y(y, vy, ladder, sneak, hit, target=None, landing_from=0):
    positions, velocities, grounded, moves = [y], [vy], [False], []
    for t in range(len(hit)):
        move = vy if abs(vy) >= .005 else 0.0
        if ladder[t]:
            if move < -.15:
                move = -.15
            if sneak[t] and move < 0:
                move = 0.0
        landed = target is not None and t + 1 >= landing_from and move < 0 and y >= target and y + move < target
        if landed:
            y, move = target, 0.0
        else:
            y += move
        carried = .2 if hit[t] and ladder[t + 1] else move
        vy = (carried - .08) * f32(.98)
        positions.append(y)
        velocities.append(vy)
        grounded.append(landed)
        moves.append(move)
        if landed:
            break
    return positions, velocities, grounded, moves


class ClimbPatternTests(unittest.TestCase):
    def test_four_stop_choices_omit_first_final_or_both_hits(self):
        choices = climb_variants(2, 7, 9)
        self.assertEqual(len(choices), 4)
        expected = {(False, False): [2, 3, 4, 5, 6], (True, False): [3, 4, 5, 6],
                    (False, True): [2, 3, 4, 5], (True, True): [3, 4, 5]}
        for variant in choices:
            self.assertEqual([t for t, hit in enumerate(variant['hit']) if hit],
                             expected[variant['beginningStop'], variant['endStop']])
        end = next(v for v in choices if v['endStop'] and not v['beginningStop'])
        self.assertFalse(end['hit'][6])
        self.assertTrue(end['hit'][5])

    def test_two_stops_are_not_adjacent_and_short_windows_remain_valid(self):
        for length in (1, 2):
            choices = climb_variants(0, length, 3)
            self.assertFalse(any(v['beginningStop'] and v['endStop'] for v in choices))
        both = climb_variants(0, 3, 3)[3]
        self.assertEqual(both['hit'], [False, True, False])

    def test_invalid_climb_windows_are_rejected(self):
        for values in [(-1, 2, 3), (0, 0, 3), (2, 1, 3), (0, 4, 3), (True, 2, 3), (0, 1, 0)]:
            with self.subTest(values=values), self.assertRaises(ValueError):
                climb_variants(*values)

    def test_all_256_sneak_masks_include_every_four_sneak_arrangement(self):
        patterns = list(sneak_patterns(8, range(8)))
        actual = {tuple(v) for v in patterns}
        self.assertEqual(actual, set(itertools.product((False, True), repeat=8)))
        self.assertEqual(len(patterns), 256)
        self.assertEqual(sum(sum(v) == 4 for v in patterns), math.comb(8, 4))
        self.assertIn((True, False) * 4, actual)
        self.assertIn((False, True) * 4, actual)

    def test_cap_priority_changes_order_without_losing_masks(self):
        patterns = list(sneak_patterns(5, [1, 2, 4], first_clamped=2))
        self.assertEqual(patterns[0], [False] * 5)
        self.assertEqual(patterns[1], [False, False, True, False, False])
        self.assertEqual(len(patterns), 8)
        self.assertTrue(all(not v[0] and not v[3] for v in patterns))
        self.assertEqual(list(sneak_patterns(3, [])), [[False] * 3])

    def test_invalid_sneak_domains_and_returned_masks_are_independent(self):
        for eligible in ([0, 0], [-1], [3], [True]):
            with self.subTest(eligible=eligible), self.assertRaises(ValueError):
                list(sneak_patterns(3, eligible))
        patterns = list(sneak_patterns(3, range(3)))
        second = patterns[1][:]
        patterns[0][0] = True
        self.assertEqual(patterns[1], second)


class LadderHeightTests(unittest.TestCase):
    def trace(self, hit, *, sneak=None, ladder=None, y=2., vy=REST_CARRY):
        return trace_ladder_schedule({'position': y, 'velocity': vy},
            ladder if ladder is not None else [True] * (len(hit) + 1),
            sneak if sneak is not None else [False] * len(hit), hit)

    def test_final_inside_hit_stop_lowers_exit_height_on_the_following_input(self):
        ladder = [False] + [True] * 6 + [False] * 4
        baseline = self.trace([True] * 6 + [False] * 4, ladder=ladder)
        stopped = self.trace([True] * 5 + [False] * 5, ladder=ladder)
        self.assertEqual(baseline['position'][:7], stopped['position'][:7])
        self.assertEqual(baseline['velocity'][6], CLIMB_CARRY)
        decay = (CLIMB_CARRY - .08) * Y_DRAG
        self.assertEqual(stopped['velocity'][6], decay)
        self.assertAlmostEqual(baseline['position'][7] - stopped['position'][7], CLIMB_CARRY - decay)
        self.assertGreater(baseline['position'][8], stopped['position'][8])
        self.assertFalse(stopped['hit'][5])
        self.assertTrue(stopped['hit'][4])

    def test_hit_ending_outside_does_not_refresh_or_change_y(self):
        ladder = [True, True, False, False, False]
        baseline = self.trace([True, False, False, False], ladder=ladder)
        ineffective = self.trace([True, True, False, False], ladder=ladder)
        self.assertEqual(baseline['position'], ineffective['position'])
        self.assertEqual(baseline['velocity'], ineffective['velocity'])
        self.assertFalse(ineffective['climbRefreshed'][1])

    def test_a_stop_can_preserve_more_upward_velocity_than_a_first_hit(self):
        baseline = self.trace([True, False], vy=f32(.42))
        stopped = self.trace([False, False], vy=f32(.42))
        self.assertEqual(baseline['position'][1], stopped['position'][1])
        self.assertEqual(baseline['velocity'][1], CLIMB_CARRY)
        self.assertGreater(stopped['velocity'][1], baseline['velocity'][1])
        self.assertGreater(stopped['position'][2], baseline['position'][2])

    def test_a_hit_at_exact_point_two_vertical_movement_has_no_extra_y_effect(self):
        baseline = self.trace([True, False], vy=.2)
        stopped = self.trace([False, False], vy=.2)
        self.assertEqual(baseline['position'], stopped['position'])
        self.assertEqual(baseline['velocity'], stopped['velocity'])

    def test_beginning_stop_allows_a_capped_fall_instead_of_the_next_climb(self):
        ladder = [False] + [True] * 5
        baseline = self.trace([True] * 4 + [False], ladder=ladder)
        stopped = self.trace([False] + [True] * 3 + [False], ladder=ladder)
        self.assertEqual(baseline['position'][1], stopped['position'][1])
        self.assertEqual(stopped['movement'][1], -.15)
        self.assertEqual(baseline['movement'][1], CLIMB_CARRY)
        self.assertAlmostEqual(baseline['position'][2] - stopped['position'][2], CLIMB_CARRY + .15)
        self.assertEqual(stopped['velocity'][2], CLIMB_CARRY)

    def test_beginning_stop_with_sneak_arrests_fall_but_does_not_invent_a_hit(self):
        hits = [False, True, True]
        falling = self.trace(hits)
        held = self.trace(hits, sneak=[True, True, False])
        self.assertEqual(held['movement'][:2], [0, 0])
        self.assertEqual(held['velocity'][1], REST_CARRY)
        self.assertFalse(held['climbRefreshed'][0])
        self.assertEqual(held['velocity'][2], CLIMB_CARRY)
        self.assertGreater(held['position'][2], falling['position'][2])

    def test_both_stops_preserve_the_middle_hits_and_both_height_changes(self):
        baseline = self.trace([True] * 5 + [False])
        begin = self.trace([False] + [True] * 4 + [False])
        both = self.trace([False] + [True] * 3 + [False, False])
        self.assertEqual(begin['position'][:6], both['position'][:6])
        self.assertLess(begin['position'][2], baseline['position'][2])
        self.assertLess(both['position'][6], begin['position'][6])
        self.assertEqual(both['velocity'][4], CLIMB_CARRY)

    def test_fall_cap_applies_at_start_not_on_an_uncapped_entering_input(self):
        trace = self.trace([False] * 3, ladder=[False, True, True, True], vy=-.8)
        self.assertEqual(trace['movement'], [-.8, -.15, -.15])
        self.assertEqual(trace['velocity'][2], (-.15 - .08) * Y_DRAG)

    def test_enter_and_hit_establishes_carry_for_the_following_input(self):
        trace = self.trace([True, True], ladder=[False, True, True], vy=-.8)
        self.assertEqual(trace['movement'], [-.8, CLIMB_CARRY])
        self.assertEqual(trace['velocity'][1], CLIMB_CARRY)

    def test_sneak_during_fall_zeroes_movement_not_the_stored_carry(self):
        trace = self.trace([False] * 3, sneak=[True, True, False], vy=-.8)
        self.assertEqual(trace['movement'], [0, 0, REST_CARRY])
        self.assertEqual(trace['velocity'][1:3], [REST_CARRY, REST_CARRY])
        self.assertEqual(trace['position'][:3], [2., 2., 2.])

    def test_sneak_outside_does_not_arrest_descent(self):
        trace = self.trace([False] * 2, ladder=[False] * 3, sneak=[True, True], vy=-.8)
        self.assertEqual(trace['movement'][0], -.8)
        self.assertEqual(trace['movement'][1], (-.8 - .08) * Y_DRAG)

    def test_sneak_does_not_stop_positive_climb_carry_or_continued_hits(self):
        continued = self.trace([True] * 4, sneak=[True] * 4, vy=CLIMB_CARRY)
        self.assertEqual(continued['movement'], [CLIMB_CARRY] * 4)
        stopped = self.trace([False] * 4, sneak=[True] * 4, vy=CLIMB_CARRY)
        self.assertEqual(stopped['movement'][:2], [CLIMB_CARRY, (CLIMB_CARRY - .08) * Y_DRAG])
        self.assertEqual(stopped['movement'][2:], [0, 0])
        falling = self.trace([False] * 4, vy=CLIMB_CARRY)
        self.assertLess(falling['movement'][2], 0)

    def test_interspersed_sneaks_change_height_even_with_equal_sneak_counts(self):
        alternating = self.trace([False] * 8, sneak=[True, False] * 4, vy=-.8)
        late = self.trace([False] * 8, sneak=[False] * 4 + [True] * 4, vy=-.8)
        early = self.trace([False] * 8, sneak=[True] * 4 + [False] * 4, vy=-.8)
        self.assertAlmostEqual(alternating['position'][-1], 2 + 4 * REST_CARRY)
        self.assertAlmostEqual(late['position'][-1], 2 - 4 * .15)
        self.assertAlmostEqual(early['position'][-1], 2 + REST_CARRY - 3 * .15)
        self.assertGreater(alternating['position'][-1], early['position'][-1])
        self.assertGreater(early['position'][-1], late['position'][-1])

    def test_incoming_y_threshold_is_strict_and_precedes_ladder_rules(self):
        for vy in [-.0049999, .0049999]:
            self.assertEqual(self.trace([False], vy=vy)['movement'], [0])
        for vy in [-.005, .005]:
            self.assertEqual(self.trace([False], vy=vy)['movement'], [vy])

    def test_floor_arrival_without_clipping_is_not_a_landing_yet(self):
        trace = trace_ladder_schedule({'position': .15, 'velocity': -.15},
                                      [True] * 4, [False] * 3, [False] * 3, target_top=0.)
        self.assertEqual(trace['ticks'], 2)
        self.assertEqual(trace['position'], [.15, 0, 0])
        self.assertEqual(trace['onGround'], [False, False, True])
        self.assertEqual(trace['velocity'][-1], REST_CARRY)

    def test_ground_jump_order_and_invalid_air_jump_are_preserved(self):
        trace = trace_ladder_schedule({'position': 0., 'velocity': REST_CARRY, 'onGround': True},
                                      [True, True], [True], [False], jump=[f32(.42)])
        self.assertEqual(trace['movement'], [f32(.42)])
        invalid = trace_ladder_schedule({'position': 0., 'velocity': 0.},
                                        [True, True], [False], [False], jump=[f32(.42)])
        self.assertEqual(invalid['status'], 'invalid')
        self.assertEqual(invalid['failures'][0]['reason'], 'jump_requires_ground')

    def test_state_seams_preserve_ladder_carry_sneak_and_previous_hit(self):
        ladder = [False, True, True, True, True, False, False]
        hit, sneak = [True, True, False, True, False, False], [False, False, True, False, True, False]
        full = self.trace(hit, sneak=sneak, ladder=ladder)
        for seam in range(1, len(hit)):
            prefix = self.trace(hit[:seam], sneak=sneak[:seam], ladder=ladder[:seam + 1])
            initial = {k: prefix['resume'][k] for k in ('position', 'velocity', 'onGround', 'previousHorizontalCollision')}
            tail = trace_ladder_schedule(initial, ladder[seam:], sneak[seam:], hit[seam:])
            self.assertEqual(tail['position'], full['position'][seam:])
            self.assertEqual(tail['velocity'], full['velocity'][seam:])

    def test_random_traces_match_independent_minecraft_order_equations(self):
        rng = random.Random(154483)
        for case in range(500):
            n = rng.randrange(1, 17)
            ladder = [bool(rng.randrange(2)) for _ in range(n + 1)]
            sneak = [bool(rng.randrange(2)) for _ in range(n)]
            hit = [bool(rng.randrange(2)) for _ in range(n)]
            vy = rng.choice([-.8, -.15, -.005, -.004, 0., .004, .005, CLIMB_CARRY, .2, .42])
            result = self.trace(hit, sneak=sneak, ladder=ladder, vy=vy)
            expected = reference_y(2., vy, ladder, sneak, hit)
            self.assertEqual((result['position'], result['velocity'], result['onGround'], result['movement']),
                             expected, case)

    def test_traces_cross_check_the_existing_independent_scalar_validator(self):
        rng = random.Random(9183)
        for _ in range(100):
            n = rng.randrange(1, 13)
            ladder = [bool(rng.randrange(2)) for _ in range(n + 1)]
            hit = [bool(rng.randrange(2)) for _ in range(n)]
            sneak = [bool(rng.randrange(2)) for _ in range(n)]
            trace = self.trace(hit, sneak=sneak, ladder=ladder, vy=rng.uniform(-.8, .42))
            x, contacts = 0., []
            for t, active in enumerate(hit):
                if active:
                    x += .05
                    contacts.append({'state': t + 1, 'axis': 0, 'plane': x, 'direction': 1})
                else:
                    x += .1
            problem = prepare({'version': 1, 'ticks': n, 'position': [0, 0], 'velocity': [0, 0],
                               'magnitude': [.1] * n, 'friction': [0] * n, 'base': [0] * n,
                               'yaw': [0] * n, 'contacts': contacts, 'ladderTicks': ladder,
                               'vertical': trace['vertical'], 'objective': {'axis': 'X', 'sense': 'MAX', 'tick': n}})
            checked = validate(problem, problem['yaw'])
            self.assertTrue(checked['feasible'], checked)
            self.assertEqual(checked['y'], trace['position'])
            self.assertEqual(checked['vy'], trace['velocity'])


class LadderGeneratorTests(unittest.TestCase):
    def test_first_hit_reach_bound_preserves_every_remaining_small_family_witness(self):
        config = request()
        config.update(initial={'position': .3, 'velocity': REST_CARRY}, maxInputs=8,
                      entries=[{'family': 'front', 'firstValidatedState': 0}],
                      exitStates=[4], maxSkeletons=1000, maxSchedules=10000, maxMaskWork=200000)
        original = generate_ladder_schedules(config)
        config['entries'][0]['firstHitLowerBounds'] = {'0': 2}
        bounded = generate_ladder_schedules(config)
        def key(s):
            return tuple(s['hit']), tuple(s['sneak']), tuple(s['trace']['position'])
        expected = {key(s) for s in original['schedules'] if not any(s['hit'][:2])}
        self.assertTrue(original['generationComplete'])
        self.assertTrue(bounded['generationComplete'])
        self.assertEqual({key(s) for s in bounded['schedules']}, expected)
        self.assertTrue(any(any(s['hit']) for s in bounded['schedules']))
        self.assertTrue(any(not any(s['hit']) for s in bounded['schedules']))

    def test_malformed_first_hit_bounds_are_rejected(self):
        for bounds in ([], None, {'x': 2}, {1: 2}, {'1': True}, {'1': 26}):
            config = request()
            config['entries'][0]['firstHitLowerBounds'] = bounds
            with self.subTest(bounds=bounds), self.assertRaises(ValueError):
                prepare_ladder_request(config)

    def test_generated_end_stop_lowers_exit_height_and_lands_one_input_earlier(self):
        result = generate_ladder_schedules(request())
        matches = [s for s in result['schedules'] if s['climbWindow'] == [0, 6]
                   and not s['beginningStop'] and not any(s['sneak'])]
        baseline = next(s for s in matches if not s['endStop'])
        stopped = next(s for s in matches if s['endStop'])
        self.assertEqual((baseline['ticks'], stopped['ticks']), (17, 16))
        self.assertEqual((stopped['exitInput'], stopped['lastClimbEligibleInput']), (6, 5))
        self.assertFalse(stopped['hit'][5])
        self.assertFalse(stopped['hit'][6])
        self.assertTrue(stopped['hit'][4])
        self.assertEqual(baseline['trace']['position'][6], stopped['trace']['position'][6])
        self.assertLess(stopped['trace']['position'][7], baseline['trace']['position'][7])

    def test_front_side_and_all_six_entry_times_are_preserved(self):
        config = request()
        config.update(entryDelay=5, exitStates=[8], maxSchedules=150, maxSkeletons=300)
        config['initial']['position'] = 4.
        config['entries'].append({'family': 'side', 'firstValidatedState': 1})
        result = generate_ladder_schedules(config)
        self.assertEqual({(s['entryFamily'], s['entryState']) for s in result['schedules']},
                         {(family, state) for family in ('front', 'side') for state in range(1, 7)})
        self.assertEqual(result['entryWindows'][0]['unresolvedStates'], [0])
        config['entryDelay'] = 3
        self.assertEqual(generate_ladder_schedules(config)['entryWindows'][0]['entryStates'], [1, 2, 3, 4])

    def test_exit_step_cannot_be_a_climb_hit_and_first_cap_uses_entry_state(self):
        for schedule in generate_ladder_schedules(request())['schedules']:
            self.assertFalse(any(schedule['hit'][schedule['exitState'] - 1:]))
            self.assertEqual(schedule['firstClampedInput'], schedule['entryState'])
            self.assertFalse(schedule['ladderTicks'][schedule['entryState'] - 1])
            self.assertTrue(schedule['ladderTicks'][schedule['entryState']])
            self.assertTrue(schedule['ladderTicks'][schedule['exitState'] - 1])
            self.assertFalse(schedule['ladderTicks'][schedule['exitState']])

    def test_small_family_matches_exhaustive_hit_and_sneak_enumeration(self):
        config = request()
        config.update(initial={'position': .3, 'velocity': REST_CARRY}, maxInputs=8,
                      entries=[{'family': 'front', 'firstValidatedState': 0}],
                      exitStates=[2], maxSkeletons=100, maxSchedules=10000, maxMaskWork=100000)
        result = generate_ladder_schedules(config)
        self.assertTrue(result['generationComplete'], result['stats'])
        actual = {(tuple(s['hit']), tuple(s['sneak']), tuple(s['trace']['position']),
                   tuple(s['trace']['velocity'])) for s in result['schedules']}
        expected = set()
        ladder = [True, True] + [False] * 7
        for first_hit in (False, True):
            hits = [first_hit] + [False] * 7
            for sneak in itertools.product((False, True), repeat=8):
                ys, vs, gs, _ = reference_y(.3, REST_CARRY, ladder, sneak, hits, 0., 2)
                n = len(ys) - 1
                if not gs[-1] or not 0 <= ys[1] < 5 or not 0 <= ys[2] < 5:
                    continue
                expected.add((tuple(hits[:n]), tuple(sneak[:n]), tuple(ys), tuple(vs)))
        self.assertEqual(actual, expected)
        self.assertEqual(len(actual), len(result['schedules']))

    def test_larger_contact_family_matches_exhaustive_windows_and_sneaks(self):
        config = request()
        config.update(initial={'position': .3, 'velocity': REST_CARRY}, maxInputs=8, targetTop=.1,
                      entries=[{'family': 'front', 'firstValidatedState': 1}], exitStates=[4],
                      maxSchedules=10000, maxMaskWork=100000)
        result = generate_ladder_schedules(config)
        self.assertTrue(result['generationComplete'])
        actual = {(tuple(s['hit']), tuple(s['sneak']), tuple(s['trace']['position'])) for s in result['schedules']}
        expected = set()
        windows = [(None, None)] + [(a, b) for a in range(3) for b in range(a + 1, 4)]
        ladder = [False, True, True, True] + [False] * 5
        for first, end in windows:
            hit = [first is not None and first <= t < end for t in range(8)]
            for sneak in itertools.product((False, True), repeat=8):
                if any(sneak[t] and hit[t] and t != 1 for t in range(8)):
                    continue
                ys, vs, gs, _ = reference_y(.3, REST_CARRY, ladder, sneak, hit, .1, 4)
                n = len(ys) - 1
                if not gs[-1] or any(ladder[t] and not 0 <= y < 5 for t, y in enumerate(ys)) or not 0 <= ys[4] < 5:
                    continue
                expected.add((tuple(hit[:n]), tuple(sneak[:n]), tuple(ys)))
        self.assertEqual(actual, expected)
        self.assertEqual(len(actual), len(result['schedules']))

    def test_generator_preserves_all_eight_tick_nonclimb_sneak_prefixes(self):
        config = request()
        config.update(initial={'position': 2., 'velocity': -.8}, maxInputs=22,
                      entries=[{'family': 'front', 'firstValidatedState': 0}], exitStates=[8],
                      maxSkeletons=1, maxSchedules=1200, maxMaskWork=10000)
        result = generate_ladder_schedules(config)
        prefixes = {tuple(s['sneak'][:8]) for s in result['schedules']}
        self.assertEqual(prefixes, set(itertools.product((False, True), repeat=8)))
        self.assertEqual(sum(sum(mask) == 4 for mask in prefixes), 70)
        self.assertTrue(all(not any(s['hit']) for s in result['schedules']))

    def test_top_exit_requires_actual_y_crossing_and_records_the_stop_consequence(self):
        config = request()
        config.update(initial={'position': .8, 'velocity': CLIMB_CARRY}, region={'minY': 0, 'maxY': 1},
                      entries=[{'family': 'front', 'firstValidatedState': 0}], exitStates=[2],
                      exitKinds=['top'], targetTop=1., maxInputs=9, maxSchedules=1000)
        result = generate_ladder_schedules(config)
        self.assertTrue(result['schedules'])
        self.assertTrue(all(s['trace']['position'][2] >= 1 for s in result['schedules']))
        self.assertTrue(all(s['hit'][0] for s in result['schedules']))
        self.assertTrue(all(s['movementUnits'][0]['type'] == 'ladder_climb' for s in result['schedules']))

    def test_timing_buckets_keep_post_exit_time_and_cover_every_schedule_once(self):
        config = request()
        config.update(exitStates=[6, 7], maxSchedules=500)
        result = generate_ladder_schedules(config)
        indices = []
        for group in result['timingGroups']:
            for index in group['scheduleIndices']:
                schedule = result['schedules'][index]
                self.assertEqual((group['airtime'], group['postExitTicks']),
                                 (schedule['ticks'], schedule['ticks'] - schedule['exitState']))
                indices.append(index)
        self.assertEqual(sorted(indices), list(range(len(result['schedules']))))
        totals = {}
        for group in result['timingGroups']:
            totals.setdefault(group['airtime'], set()).add(group['postExitTicks'])
        self.assertTrue(any(len(values) > 1 for values in totals.values()))

    def test_caps_are_explicit_and_do_not_claim_complete_generation(self):
        for key, value, reason in [('maxSchedules', 1, 'schedule_limit'),
                                   ('maxSkeletons', 1, 'skeleton_limit'), ('maxMaskWork', 1, 'mask_work_limit')]:
            config = request()
            config[key] = value
            if key == 'maxSkeletons':
                config.update(maxInputs=8, initial={'position': .3, 'velocity': REST_CARRY},
                              entries=[{'family': 'front', 'firstValidatedState': 0}], exitStates=[2],
                              maxMaskWork=10000, maxSchedules=10000)
            result = generate_ladder_schedules(config)
            self.assertEqual(result['termination'], reason)
            self.assertFalse(result['generationComplete'])
            self.assertTrue(result['heuristicDrops'])

    def test_input_output_and_separate_candidates_do_not_alias(self):
        config = request()
        original = copy.deepcopy(config)
        first = generate_ladder_schedules(config)
        self.assertEqual(config, original)
        second = generate_ladder_schedules(config)
        self.assertEqual(first, second)
        original_second = copy.deepcopy(first['schedules'][1])
        first['schedules'][0]['trace']['position'][0] = -999
        first['schedules'][0]['sneak'][0] = not first['schedules'][0]['sneak'][0]
        self.assertEqual(first['schedules'][1], original_second)
        self.assertEqual(config, original)

    def test_bad_requests_and_nonfinite_physics_are_rejected(self):
        for key, value in [('maxInputs', True), ('entryDelay', 6), ('targetTop', float('nan')),
                           ('maxSchedules', 0), ('exitKinds', ['side', 'side']), ('exitStates', [0]),
                           ('region', {'minY': 0, 'maxY': 0}), ('jumpAtStart', True), ('unexpected', 1)]:
            config = request()
            config[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                prepare_ladder_request(config)
        with self.assertRaises(ValueError):
            trace_ladder_schedule({'position': 0., 'velocity': float('inf')}, [True, True], [False], [False])
        with self.assertRaises(ValueError):
            trace_ladder_schedule({'position': 0., 'velocity': 0.}, [True], [False], [False])

    def test_cancelled_generation_retains_explicit_status(self):
        cancel = threading.Event()
        cancel.set()
        result = generate_ladder_schedules(request(), cancel_event=cancel)
        self.assertEqual(result['termination'], 'cancelled')
        self.assertFalse(result['generationComplete'])
        self.assertEqual(result['schedules'], [])
