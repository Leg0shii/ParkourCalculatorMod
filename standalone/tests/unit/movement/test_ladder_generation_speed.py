import copy
import random
import unittest
from unittest.mock import patch

import parkour_solver.movement.ladder.schedules as schedules
import parkour_solver.movement.vertical_profiles as vertical
from tests.unit.movement.test_ladder_schedules import request
from parkour_solver.physics.vertical import CLIMB_CARRY, REST_CARRY


class LadderGenerationSpeedTests(unittest.TestCase):
    def test_open_trace_skips_the_extra_floor_prediction(self):
        original=vertical.vertical_move
        with patch.object(vertical,'vertical_move',wraps=original) as prediction:
            result=schedules.trace_ladder_schedule({'position':2,'velocity':REST_CARRY},
                [False]*11,[False]*10,[False]*10)
        self.assertEqual(result['ticks'],10)
        self.assertEqual(prediction.call_count,0)

    def test_landing_keeps_exact_downward_clip_prediction(self):
        original=vertical.vertical_move
        with patch.object(vertical,'vertical_move',wraps=original) as prediction:
            result=schedules.trace_ladder_schedule({'position':.15,'velocity':-.15},
                [False]*4,[False]*3,[False]*3,target_top=0)
        self.assertEqual(result['ticks'],2)
        self.assertEqual(result['onGround'],[False,False,True])
        self.assertEqual(prediction.call_count,2)

    def test_invalid_occupied_height_stops_at_the_first_failure(self):
        result=schedules.trace_ladder_schedule({'position':1,'velocity':CLIMB_CARRY},
            [True]*101,[False]*100,[True]*100,occupied_y_bounds=(0,2))
        self.assertEqual(result['status'],'invalid')
        self.assertLess(result['ticks'],10)
        self.assertEqual(result['failures'][-1],{'state':result['ticks'],'reason':'occupied_y_outside_region'})
        self.assertGreaterEqual(result['position'][-1],2)
        self.assertTrue(all(y<2 for y in result['position'][:-1]))

    def test_invalid_initial_occupancy_preserves_the_incoming_carry(self):
        result=schedules.trace_ladder_schedule({'position':2,'velocity':.1,'previousHorizontalCollision':True},
            [True,True],[False],[False],occupied_y_bounds=(0,2))
        self.assertEqual(result['ticks'],0)
        self.assertEqual(result['resume']['velocity'],.1)
        self.assertTrue(result['resume']['previousHorizontalCollision'])

    def test_real_top_and_bottom_exits_are_not_rejected(self):
        for initial,bounds in [({'position':.95,'velocity':CLIMB_CARRY},(0,1)),
                               ({'position':.05,'velocity':-.8},(0,1))]:
            result=schedules.trace_ladder_schedule(initial,[True,False],[False],[False],occupied_y_bounds=bounds)
            self.assertEqual(result['status'],'simulated')
            self.assertEqual(result['ticks'],1)

    def test_random_valid_traces_are_identical_and_invalid_traces_stop_at_first_bad_state(self):
        rng=random.Random(7412)
        for _ in range(250):
            n=rng.randrange(1,35)
            ladder=[bool(rng.randrange(2)) for _ in range(n+1)]
            sneak=[bool(rng.randrange(2)) for _ in range(n)]
            hit=[bool(rng.randrange(2)) for _ in range(n)]
            initial={'position':rng.uniform(-1,3),'velocity':rng.uniform(-.8,.42)}
            full=schedules.trace_ladder_schedule(initial,ladder,sneak,hit)
            fast=schedules.trace_ladder_schedule(initial,ladder,sneak,hit,occupied_y_bounds=(0,2))
            bad=next((t for t,y in enumerate(full['position']) if ladder[t] and not 0<=y<2),None)
            if bad is None:
                self.assertEqual(fast,full)
            else:
                self.assertEqual(fast['status'],'invalid')
                self.assertEqual(fast['ticks'],bad)
                self.assertEqual(fast['position'],full['position'][:bad+1])
                self.assertEqual(fast['velocity'],full['velocity'][:bad+1])

    def test_generator_emits_identical_candidates_with_full_or_short_circuit_validation(self):
        original=schedules.trace_ladder_schedule
        def complete_trace(*args,**kwargs):
            kwargs.pop('occupied_y_bounds',None)
            return original(*args,**kwargs)
        for open_terminal in (False,True):
            config=request()
            config.update(maxInputs=10,exitStates=[2,3,4,5,6],maxSkeletons=400,
                          maxMaskWork=10000,maxSchedules=500,finishAtExit=open_terminal)
            saved=copy.deepcopy(config)
            fast=schedules.generate_ladder_schedules(config)
            with patch.object(schedules,'trace_ladder_schedule',side_effect=complete_trace):
                full=schedules.generate_ladder_schedules(config)
            self.assertEqual(fast['schedules'],full['schedules'])
            self.assertEqual(fast['timingGroups'],full['timingGroups'])
            self.assertEqual(fast['termination'],full['termination'])
            self.assertEqual(fast['stats']['maskWork'],full['stats']['maskWork'])
            self.assertEqual(config,saved)


if __name__=='__main__':
    unittest.main()
