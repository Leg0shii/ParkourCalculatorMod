import copy

from parkour_solver.physics.vertical import f32, vertical_move
from parkour_solver.movement.profiles import ordinary_jump_profile, stair_jump_profile
from parkour_solver.movement.cases import (case_profile, convex_corner, corner_rows, exposed_side, landing_wall_options,
                                          outside_row, top_choices)
from parkour_solver.movement.units import profile_program


def box_for(block, context):
    return context['scene']['collisionScene']['solids'][block['solidIndex']]


def stepping_profiles(context):
    source,target = context['source'],context['target']
    scene = context['scene']
    source_y,target_y = source['top'],target['top']
    horizon = context.get('maxInputs',200)
    allowed = context.get('cases')
    def enabled(kind):
        return allowed is None or kind in allowed
    if enabled('normal_step') and 0 < target_y-source_y <= f32(.6):
        box = box_for(target,context)
        for axis in ('X','Z'):
            for direction in (-1,1):
                if not exposed_side(scene,target,axis,-direction,source_y):
                    continue
                for top,_ in top_choices(context,target,1,jumping=False):
                    rows=[outside_row(box,axis,-direction,0)] + top
                    e=dict(case='normal_step',input=0,plane=target_y,boxes=[box],anchors={'hit':1})
                    p=case_profile(source_y,target_y,e,jump_at_start=False,finish_at=1,constraints=rows)
                    if p:
                        yield 'step',p,None
    if enabled('slab_boost') and scene.get('stairBoost',True):
        p=stair_jump_profile(source_y,target_y)
        if p:
            box=box_for(target,context)
            for axis in ('X','Z'):
                for direction in (-1,1):
                    if not exposed_side(scene,target,axis,-direction,source_y):
                        continue
                    q=copy.deepcopy(p)
                    q['caseEvents']=[dict(type='stepping',case='slab_boost',input=0,plane=target_y,boxes=[box],anchors={'beforeJump':0,'jump':1})]
                    q['generatedUnits']=profile_program(q)
                    yield 'stair',q,dict(axis=axis,direction=direction)
    for block in scene['blocks']:
        if context.get('checkBudget'):
            context['checkBudget']()
        box=box_for(block,context)
        if enabled('corner_jump_cancel') and f32(.42)<block['top']-source_y<=f32(.6):
            for xdir in (-1,1):
                for zdir in (-1,1):
                    if not convex_corner(scene,block,xdir,zdir,source_y):
                        continue
                    e=dict(case='corner_jump_cancel',input=0,plane=source_y,higherHeight=block['top'],boxes=[box],anchors={'jump':1})
                    p=case_profile(source_y,target_y,e,max_inputs=horizon,constraints=corner_rows(box,0,1,xdir,zdir))
                    if p:
                        yield 'corner_jump_cancel',p,None
    for lower in scene['blocks']:
        if context.get('checkBudget'):
            context['checkBudget']()
        flight=ordinary_jump_profile(source_y,lower['top'],max_inputs=horizon)
        if flight is None:
            continue
        hit=flight['airtime'];landing=hit-1;y=flight['position'][landing]
        free=y+vertical_move(dict(threshold=.005,jumpVelocity=[None]*hit,sneak=[False]*hit),landing,flight['velocity'][landing],False)
        if free>=lower['top']:
            continue
        for low_rows,_ in top_choices(context,lower,landing,'case:lower_landing',jumping=True):
            yield from _flight_cases(context,source_y,target_y,lower,low_rows,landing,hit,y,horizon,enabled)


def _flight_cases(context, source_y, target_y, lower, low_rows, landing, hit, y, horizon, enabled):
    scene=context['scene']
    target=context['target']
    if enabled('blip') and lower['top']<target_y and 0<=target_y-y<=f32(.6):
        box=box_for(target,context)
        for avoid,contacts in landing_wall_options(scene,target,landing,y):
            for top,_ in top_choices(context,target,hit,jumping=True):
                e=dict(case='blip',input=landing,plane=target_y,lowerHeight=lower['top'],higherHeight=target_y,
                       boxes=[box],lowerBox=box_for(lower,context),anchors={'landing':landing,'hit':hit})
                p=case_profile(source_y,target_y,e,finish_at=hit,constraints=low_rows+avoid+top,contacts=contacts)
                if p:
                    yield 'blip',p,None
    for higher in scene['blocks']:
        if higher['top']<=lower['top']:
            continue
        box=box_for(higher,context)
        for kind in ('blip_up','corner_blip_up'):
            if not enabled(kind) or not (y>higher['top'] if kind=='blip_up' else 0<=higher['top']-y<=f32(.6)):
                continue
            conditions=[low_rows+[outside_row(box,axis,sign,landing,'blip_up:outside')]+top
                        for axis in ('X','Z') for sign in (-1,1) if exposed_side(scene,higher,axis,sign,y)
                        for top,_ in top_choices(context,higher,hit,jumping=True)] if kind=='blip_up' else [
                low_rows+corner_rows(box,landing,hit,xd,zd) for xd in (-1,1) for zd in (-1,1) if convex_corner(scene,higher,xd,zd,y)]
            for rows in conditions:
                for jump_after in (None,hit):
                    e=dict(case=kind,input=landing,plane=y,lowerHeight=lower['top'],higherHeight=higher['top'],
                           boxes=[box],lowerBox=box_for(lower,context),anchors={'landing':landing,'hit':hit})
                    p=case_profile(source_y,target_y,e,max_inputs=horizon,jump_after=jump_after,constraints=rows)
                    if p:
                        yield kind,p,None


class SteppingGenerator:
    def generate(self, context, *, start=0, ends=None):
        if context.get('mode') == 'open':
            yield from open_stepping_choices(context)
            return
        from parkour_solver.movement.units import shifted_unit
        if context.get('mode')=='profile':
            profile = copy.deepcopy(context['profile'])
            kind = context['profileKind']
            if not profile.get('caseEvents') and kind in ('stair', 'step'):
                profile['caseEvents'] = [dict(type='stepping', case='slab_boost' if kind == 'stair' else 'normal_step',
                                              input=0, anchors={'jump' if kind == 'stair' else 'hit': 1})]
            if not profile.get('caseEvents'):
                raise ValueError('A stepping profile requires its named case')
            profile['generatedUnits'] = profile_program(profile)
            options=[(kind,profile,context.get('entryFace'))]
        else:
            options=stepping_profiles(context)
        limit=context.get('limit',128)
        stats=context.setdefault('generation', {})
        stats.update(emitted=0, complete=False, termination='profile_limit')
        for index,(kind,profile,face) in enumerate(options):
            if index>=limit:
                return
            stats['emitted'] += 1
            units=profile.get('generatedUnits',profile_program(profile))
            profile['generatedUnits']=units
            unit=next(u for u in units if u['type']=='stepping')
            yield dict(unit=shifted_unit(unit,start),units=[shifted_unit(u,start) for u in units],profile=profile,
                       profileKind=kind,entryFace=face)
        stats.update(complete=True, termination='exhausted')


def _sequence_allowed(context, initial, event, jumping, rows, support=None):
    """Check the common named outcome before creating its side alternatives."""
    gate=context.get('interactionGate')
    if gate is None or not hasattr(gate,'sequence'):
        return True
    from parkour_solver.movement.normal import named_choice
    common=named_choice(context,'stepping',initial,event,jumping=jumping,
                        constraints=rows,support=support)
    return common is not None and gate.sequence(context,common) is None


def open_stepping_choices(context):
    from parkour_solver.reachability.service import candidate_blocks, interaction_allowed, walk_off_allowed
    from parkour_solver.movement.program import scalar_initial
    from parkour_solver.movement.normal import choice_approaches, feet_allowed, first_descent, named_choice
    initial = scalar_initial(context['state'])
    scene = context['scene']
    y0 = initial['position']
    allowed = context.get('cases')
    def enabled(case):
        return allowed is None or case in allowed
    blocks = candidate_blocks(context, 'stepping')
    if initial['onGround']:
        for block in blocks:
            if not feet_allowed(context, block):
                continue
            box = box_for(block, context)
            rise = block['top'] - y0
            for case, jumping in [('normal_step', False), ('slab_boost', True)]:
                if (not enabled(case) or not 0 < rise <= f32(.6) or jumping and rise <= f32(.42) or
                        not jumping and not context.get('runTickChoices', True)):
                    continue
                if not interaction_allowed(context, box, 0, jumping=jumping, label=dict(owner=block['id'], role=case)):
                    continue
                for axis in ('X', 'Z'):
                    for direction in (-1, 1):
                        if not exposed_side(scene, block, axis, -direction, y0):
                            continue
                        for top, regions in top_choices(context, block, 1, jumping=jumping):
                            rows = [outside_row(box, axis, -direction, 0)] + top
                            event = dict(case=case, input=0, plane=block['top'], boxes=[box],
                                         anchors={'beforeJump' if jumping else 'beforeStep': 0, 'jump' if jumping else 'hit': 1})
                            choice = named_choice(context, 'stepping', initial, event, jumping=jumping, constraints=rows,
                                                  support=block['id'] if not jumping else None, region_choices=regions)
                            if choice:
                                yield choice
            if (enabled('corner_jump_cancel') and f32(.42) < rise <= f32(.6) and
                    interaction_allowed(context, box, 0, jumping=True, label=dict(owner=block['id'], role='corner_jump_cancel'))):
                for xdir in (-1, 1):
                    for zdir in (-1, 1):
                        if not convex_corner(scene, block, xdir, zdir, y0):
                            continue
                        event = dict(case='corner_jump_cancel', input=0, plane=y0, higherHeight=block['top'],
                                     boxes=[box], anchors={'beforeJump': 0, 'jump': 1})
                        choice = named_choice(context, 'stepping', initial, event, jumping=True,
                                              constraints=corner_rows(box, 0, 1, xdir, zdir))
                        if choice:
                            yield choice
    for initial, jumping, trace in choice_approaches(context):
        for lower in blocks:
            if context.get('checkBudget'):
                context['checkBudget']()
            if not feet_allowed(context, lower):
                continue
            landing = first_descent(trace, lower['top'])
            if landing is None:
                continue
            if not interaction_allowed(context, box_for(lower, context), landing, jumping=jumping,
                                       label=dict(owner=lower['id'], role='lower landing')):
                continue
            if (initial['onGround'] and not jumping and lower['top'] < initial['position'] and
                    not walk_off_allowed(context, [box_for(lower, context)], label=dict(owner=lower['id'], role='lower landing'))):
                continue
            y = trace['position'][landing]
            for lower_rows, lower_regions in top_choices(context, lower, landing, 'case:lower_landing', jumping=jumping):
                yield from _open_flight_cases(context, initial, jumping, blocks, lower, lower_rows, lower_regions, landing, y,
                                              enabled)


def _open_flight_cases(context, initial, jumping, blocks, lower, lower_rows, lower_regions, landing, y, enabled):
    from parkour_solver.reachability.service import interaction_allowed
    from parkour_solver.movement.normal import feet_allowed, named_choice
    scene = context['scene']
    for higher in blocks:
        if higher['top'] <= lower['top'] or not feet_allowed(context, higher):
            continue
        box = box_for(higher, context)
        if not interaction_allowed(context, box, landing, jumping=jumping, label=dict(owner=higher['id'], role='flight case')):
            continue
        if enabled('blip') and 0 <= higher['top'] - y <= f32(.6):
            for top, regions in top_choices(context, higher, landing + 1, jumping=jumping):
                shared_event=dict(case='blip',input=landing,plane=higher['top'],lowerHeight=lower['top'],
                    higherHeight=higher['top'],boxes=[box],lowerBox=box_for(lower,context),
                    anchors={'landing':landing,'hit':landing+1})
                options = ()
                if _sequence_allowed(context,initial,shared_event,jumping,lower_rows+top,higher['id']):
                    options = landing_wall_options(scene, higher, landing, y) if landing else (
                        ([outside_row(box, axis, sign, 0, 'blip:wall_avoid')], [])
                        for axis in ('X', 'Z') for sign in (-1, 1) if exposed_side(scene, higher, axis, sign, y))
                for rows, finite in options:
                    event = dict(case='blip', input=landing, plane=higher['top'], lowerHeight=lower['top'],
                        higherHeight=higher['top'], boxes=[box], lowerBox=box_for(lower, context),
                        anchors={'landing': landing, 'hit': landing + 1})
                    choice = named_choice(context, 'stepping', initial, event, jumping=jumping,
                        constraints=lower_rows + rows + top, finite=finite, support=higher['id'],
                        region_choices=lower_regions + regions)
                    if choice:
                        yield choice
        for case in ('blip_up', 'corner_blip_up'):
            if not enabled(case) or not (y > higher['top'] if case == 'blip_up' else 0 <= higher['top'] - y <= f32(.6)):
                continue
            shared_event=dict(case=case,input=landing,plane=y,lowerHeight=lower['top'],higherHeight=higher['top'],
                boxes=[box],lowerBox=box_for(lower,context),anchors={'landing':landing,'hit':landing+1})
            tops = top_choices(context, higher, landing + 1, jumping=jumping) if case == 'blip_up' else [([], [])]
            if not any(_sequence_allowed(context,initial,shared_event,jumping,lower_rows+top) for top, _ in tops):
                continue
            options = ([([outside_row(box, axis, sign, landing, 'blip_up:outside')] + top, regions)
                        for axis in ('X', 'Z') for sign in (-1, 1) if exposed_side(scene, higher, axis, sign, y)
                        for top, regions in tops] if case == 'blip_up' else
                       [(corner_rows(box, landing, landing + 1, xdir, zdir), [])
                        for xdir in (-1, 1) for zdir in (-1, 1) if convex_corner(scene, higher, xdir, zdir, y)])
            for constraint, regions in options:
                event = dict(case=case, input=landing, plane=y, lowerHeight=lower['top'], higherHeight=higher['top'],
                             boxes=[box], lowerBox=box_for(lower, context), anchors={'landing': landing, 'hit': landing + 1})
                choice = named_choice(context, 'stepping', initial, event, jumping=jumping,
                                      constraints=lower_rows + constraint, region_choices=lower_regions + regions)
                if choice:
                    yield choice
