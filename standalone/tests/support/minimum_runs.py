"""Shared test builders extracted from tests.unit.search.test_minimum_runs."""
from parkour_solver.movement.program import empty_program, append_choice, boundary_state

from parkour_solver.movement.normal import normal_choices, event_choice

def flight(scene,runs):
    p=empty_program(scene['initial'],threshold=0.)
    for _ in range(runs):
        options=normal_choices(dict(scene=scene,state=boundary_state(p),maxInputs=20,threshold=0.,interactionGate=None))
        p=append_choice(p,next(c for c in options if not c['profile']['jump'][0] and c['terminalSupport']=='start'))
    options=normal_choices(dict(scene=scene,state=boundary_state(p),maxInputs=20,threshold=0.,interactionGate=None))
    return append_choice(p,next(c for c in options if c['profile']['jump'][0] and c['terminalSupport']=='goal'))
