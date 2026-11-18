import copy

from parkour_solver.physics.vertical import f32, vertical_move
from parkour_solver.movement.program import movement_choice, scalar_initial
from parkour_solver.movement.vertical_profiles import trace_ladder_schedule


def replay(initial, ticks, *, jumping=False, contacts=(), finite=(), ladder=None, sneak=None, threshold=.005):
    initial = scalar_initial(initial)
    ladder = list(ladder) if ladder is not None else [False] * (ticks + 1)
    sneak = list(sneak) if sneak is not None else [False] * ticks
    hit = [any(c['axis'] != 'Y' and c['offset'] == t for c in finite) for t in range(ticks)]
    return trace_ladder_schedule(initial, ladder, sneak, hit,
        jump=[f32(.42) if jumping else None] + [None] * (ticks - 1),
        contacts=contacts, stop_on_landing=False, threshold=threshold)


def approaches(context):
    initial = scalar_initial(context['state'])
    horizon = min(context.get('maxInputs', 200), 200)
    for jumping in ([True, False] if initial['onGround'] else [False]):
        trace = replay(initial, horizon, jumping=jumping, threshold=context.get('threshold', .005))
        if trace['status'] != 'invalid':
            yield initial, jumping, trace


def first_descent(trace, top):
    for t, move in enumerate(trace['movement']):
        if move < 0 and trace['position'][t] >= top > trace['position'][t + 1]:
            return t
    return None


def event_choice(context, kind, initial, duration, *, jumping=False, vertical=(), finite=(), cases=(),
                 constraints=(), units=None, ladder=None, sneak=None, ladder_events=(), support=None,
                 landing_phase='post', region_choices=()):
    trace = replay(initial, duration, jumping=jumping, contacts=vertical, finite=finite,
                   ladder=ladder, sneak=sneak, threshold=context.get('threshold', .005))
    if trace['status'] == 'invalid':
        return None
    return movement_choice(kind, initial, trace, contacts=finite, cases=cases, constraints=constraints,
                           units=units, ladder_events=ladder_events, support=support, landing_phase=landing_phase,
                           region_choices=region_choices)


def named_choice(context, kind, initial, event, *, jumping=False, constraints=(), finite=(), support=None,
                 region_choices=()):
    event = copy.deepcopy(event)
    event['type'] = kind
    contact = {k: v for k, v in event.items() if k in ('case', 'plane', 'lowerHeight', 'higherHeight', 'clearance')}
    contact.update(state=event['input'] + 1, direction=-1)
    return event_choice(context, kind, initial, event['input'] + 1, jumping=jumping,
                        vertical=[contact], finite=finite, cases=[event], constraints=constraints, support=support,
                        region_choices=region_choices)


def normal_choices(context):
    from parkour_solver.movement.contact_combinations import (contact_event, reachable_option, region_choices,
                                                              scene_contact_options)
    for initial, jumping, trace in approaches(context):
        for block in context['scene']['blocks']:
            if context.get('checkBudget'):
                context['checkBudget']()
            if context.get('supportAllowed') is not None and not context['supportAllowed'](block):
                continue
            t = first_descent(trace, block['top'])
            if t is None:
                continue
            for option in scene_contact_options(context['scene'], 1, 1, members=[block['solidIndex']]):
                option = reachable_option(context, option, t, jumping=jumping, label=dict(owner=block['id'], role='landing'))
                if option is None:
                    continue
                event = contact_event(option, 'Y', -1, t, 'landing')
                choice = event_choice(context, 'normal', initial, t + 1, jumping=jumping,
                    vertical=[dict(state=t + 1, direction=-1, plane=block['top'])], finite=[event],
                    support=block['id'], landing_phase='pre', region_choices=region_choices(context, event, block['id']))
                if choice:
                    yield choice
        if context.get('normalGap', False):
            choice = event_choice(context, 'normal', initial, context.get('normalGapInputs', 1), jumping=jumping)
            if choice:
                yield choice
