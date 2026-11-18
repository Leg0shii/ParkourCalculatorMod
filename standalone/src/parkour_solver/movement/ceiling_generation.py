import copy

from parkour_solver.physics.vertical import f32
from parkour_solver.movement.cases import case_profile, exposed_side, outside_row
from parkour_solver.movement.ceiling_profiles import ceiling_profile, ceiling_sequence
from parkour_solver.movement.contact_combinations import (bounding_region, contact_event, finite_rows, region_choices,
                                                          scene_contact_options)
from parkour_solver.movement.units import profile_program, shifted_unit


def grind_options(context, feet, allowed=None):
    """(event, rows, regionChoices) for each exposed side of a member under each low merged underside."""
    scene = context['scene']
    blocks = {b['solidIndex']: b for b in scene['blocks']}
    for option in scene_contact_options(scene, 1, -1):
        if context.get('checkBudget'):
            context['checkBudget']()
        clearance = option.plane - feet
        if not 0 <= clearance < f32(.42) or allowed is not None and not any(allowed(list(box)) for box in option.boxes):
            continue
        under = [dict(r, name='case:finite') for r in finite_rows(1, bounding_region(option.regions), t1=1)]
        owner = '+'.join(sorted(blocks[i]['id'] for i in option.members))
        regions = [dict(offset=1, surface=option.surface, owner=owner,
                        options=[[dict(r, name='case:finite') for r in finite_rows(1, region, leg=0, offset=1)]
                                 for region in option.regions])] if len(option.regions) > 1 and context.get('regionItems') else []
        for index in option.members:
            box = scene['collisionScene']['solids'][index]
            for axis in ('X', 'Z'):
                for sign in (-1, 1):
                    if exposed_side(scene, blocks[index], axis, sign, option.plane):
                        event = dict(case='grind', input=0, plane=feet, clearance=clearance,
                                     boxes=[list(b) for b in option.boxes], anchors={'beforeJump': 0, 'jump': 1})
                        yield event, [outside_row(box, axis, sign, 0, 'grind:outside')] + under, regions


def ceiling_profiles(context):
    source, target, scene = context['source'], context['target'], context['scene']
    names = {b['solidIndex']: b['sourceBlock'] for b in scene['blocks']}
    for option in scene_contact_options(scene, 1, -1):
        if context.get('checkBudget'):
            context['checkBudget']()
        ordinary = ceiling_profile(source['top'], target['top'], option, '+'.join(sorted({names[i] for i in option.members})),
                                   max_inputs=context.get('maxInputs', 200))
        if ordinary:
            ordinary['generatedUnits'] = profile_program(ordinary)
            yield 'ceiling', ordinary
    for event, rows, _ in grind_options(context, source['top']):
        profile = case_profile(source['top'], target['top'], event, max_inputs=context.get('maxInputs', 200), constraints=rows)
        if profile:
            yield 'grind', profile


class CeilingGenerator:
    def generate(self, context, *, start=0, ends=None):
        if context.get('mode') == 'open':
            yield from open_ceiling_choices(context)
            return
        if context.get('mode') == 'timeline':
            schedule = context['schedule']
            for option in context['options']:
                events = ceiling_sequence(schedule, option, start=start)
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
    from parkour_solver.reachability.service import interaction_allowed
    from parkour_solver.movement.program import scalar_initial
    from parkour_solver.movement.normal import approaches, event_choice, named_choice
    initial = scalar_initial(context['state'])
    scene = context['scene']
    if not scene.get('ceilingHits', True):
        return
    names = {b['solidIndex']: b['id'] for b in scene['blocks']}
    for option in scene_contact_options(scene, 1, -1):
        if context.get('checkBudget'):
            context['checkBudget']()
        plane, owner = option.plane, '+'.join(sorted(names[i] for i in option.members))
        for state, jumping, trace in approaches(context):
            hit = next((t for t, move in enumerate(trace['movement'])
                        if move > 0 and trace['position'][t] <= plane < trace['position'][t + 1]), None)
            if hit is None:
                continue
            if not any(interaction_allowed(context, list(box), hit, jumping=jumping, phases=('Y',)) for box in option.boxes):
                continue
            event = contact_event(option, 'Y', 1, hit, 'ceiling', blockId=owner)
            choice = event_choice(context, 'ceiling', state, hit + 1, jumping=jumping, finite=[event],
                                  vertical=[dict(state=hit + 1, plane=plane, direction=1)],
                                  region_choices=region_choices(context, event, owner))
            if choice:
                yield choice
    if not initial['onGround']:
        return
    allowed = lambda box: interaction_allowed(context, box, 0, jumping=True)
    for event, rows, regions in grind_options(context, initial['position'], allowed):
        choice = named_choice(context, 'ceiling', initial, event, jumping=True, constraints=rows, region_choices=regions)
        if choice:
            yield choice
