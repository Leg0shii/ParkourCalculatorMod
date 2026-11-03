import copy

from standalone.ipopt.ladder import f32
from .case_profiles import case_profile, outside_row, tangent_rows
from .ceiling_primitives import ceiling_profile, ceiling_sequence
from .movement_units import profile_program, shifted_unit
from .obstacle_geometry import BODY_HEIGHT


def ceiling_profiles(context):
    source, target, scene = context['source'], context['target'], context['scene']
    for block in scene['blocks']:
        if context.get('checkBudget'):
            context['checkBudget']()
        index = block['solidIndex']
        box = scene['collisionScene']['solids'][index]
        ordinary = ceiling_profile(source['top'], target['top'], box, index, block['sourceBlock'],
                                   max_inputs=context.get('maxInputs', 200))
        if ordinary:
            ordinary['generatedUnits'] = profile_program(ordinary)
            yield 'ceiling', ordinary
        clearance = box[1] - BODY_HEIGHT - source['top']
        if not 0 <= clearance < f32(.42):
            continue
        for axis in ('X', 'Z'):
            for sign in (-1, 1):
                rows = [outside_row(box, axis, sign, 0, 'grind:outside')]
                rows += tangent_rows(box, 'X', 1) + tangent_rows(box, 'Z', 1)
                event = dict(case='grind', input=0, plane=source['top'], clearance=clearance,
                             box=box, anchors={'beforeJump': 0, 'jump': 1})
                profile = case_profile(source['top'], target['top'], event,
                                       max_inputs=context.get('maxInputs', 200), constraints=rows)
                if profile:
                    yield 'grind', profile


class CeilingGenerator:
    def generate(self, context, *, start=0, ends=None):
        if context.get('mode') == 'open':
            yield from open_ceiling_choices(context)
            return
        if context.get('mode') == 'timeline':
            schedule = context['schedule']
            for box in context['solids']:
                events = ceiling_sequence(schedule, box, start=start)
                events = [e for e in events if not schedule['ladderTicks'][e['offset']]
                          and not (schedule['ladderTicks'][e['offset'] + 1] and schedule['hit'][e['offset']])]
                if events:
                    event = events[0]
                    unit = dict(type='ceiling', case='ceiling_hit', start=event['offset'], end=event['offset'] + 1,
                                anchors={'hit': event['offset'] + 1})
                    yield dict(unit=unit, contactEvents=events)
            return
        if context.get('mode') == 'profile':
            options = [(context['profileKind'], copy.deepcopy(context['profile']))]
        else:
            options = ceiling_profiles(context)
        for kind, profile in options:
            units = profile_program(profile)
            profile['generatedUnits'] = units
            unit = next(u for u in units if u['type'] == 'ceiling')
            yield dict(unit=shifted_unit(unit, start), units=[shifted_unit(u, start) for u in units],
                       profile=profile, profileKind=kind, entryFace=None)


def open_ceiling_choices(context):
    from .interactions import interaction_allowed
    from .movement_composition import scalar_initial
    from .open_movement import approaches, event_choice, named_choice
    initial = scalar_initial(context['state'])
    scene = context['scene']
    if not scene.get('ceilingHits', True):
        return
    for block in scene['blocks']:
        if context.get('checkBudget'):
            context['checkBudget']()
        box = scene['collisionScene']['solids'][block['solidIndex']]
        plane = box[1] - BODY_HEIGHT
        for state, jumping, trace in approaches(context):
            hit = next((t for t, move in enumerate(trace['movement'])
                        if move > 0 and trace['position'][t] <= plane < trace['position'][t + 1]), None)
            if hit is None:
                continue
            if not interaction_allowed(context, box, hit, jumping=jumping, phases=('Y',)):
                continue
            finite = [dict(leg=0, offset=hit, axis='Y', direction=1, plane=plane, box=box,
                           role='ceiling', blockId=block['id'], solidIndex=block['solidIndex'])]
            choice = event_choice(context, 'ceiling', state, hit + 1, jumping=jumping, finite=finite,
                                  vertical=[dict(state=hit + 1, plane=plane, direction=1)])
            if choice:
                yield choice
        clearance = plane - initial['position']
        if initial['onGround'] and 0 <= clearance < f32(.42) and interaction_allowed(context, box, 0, jumping=True):
            for axis in ('X', 'Z'):
                for sign in (-1, 1):
                    event = dict(case='grind', input=0, plane=initial['position'], clearance=clearance,
                                 box=box, anchors={'beforeJump': 0, 'jump': 1})
                    rows = [outside_row(box, axis, sign, 0, 'grind:outside')]
                    rows += tangent_rows(box, 'X', 1) + tangent_rows(box, 'Z', 1)
                    choice = named_choice(context, 'ceiling', initial, event, jumping=True, constraints=rows)
                    if choice:
                        yield choice
