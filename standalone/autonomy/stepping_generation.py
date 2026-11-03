import copy

from standalone.ipopt.ladder import f32, vertical_move
from .block_profiles import ordinary_jump_profile, stair_jump_profile
from .case_profiles import case_profile, region_rows, outside_row, corner_rows, landing_wall_options
from .movement_units import profile_program


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
                rows=[outside_row(box,axis,-direction,0)] + region_rows(target,1)
                e=dict(case='normal_step',input=0,plane=target_y,box=box,anchors={'hit':1})
                p=case_profile(source_y,target_y,e,jump_at_start=False,finish_at=1,constraints=rows)
                if p:
                    yield 'step',p,None
    if enabled('slab_boost') and scene.get('stairBoost',True):
        p=stair_jump_profile(source_y,target_y)
        if p:
            box=box_for(target,context)
            for axis in ('X','Z'):
                for direction in (-1,1):
                    q=copy.deepcopy(p)
                    q['caseEvents']=[dict(type='stepping',case='slab_boost',input=0,plane=target_y,box=box,anchors={'beforeJump':0,'jump':1})]
                    q['generatedUnits']=profile_program(q)
                    yield 'stair',q,dict(axis=axis,direction=direction)
    for block in scene['blocks']:
        if context.get('checkBudget'):
            context['checkBudget']()
        box=box_for(block,context)
        if enabled('corner_jump_cancel') and f32(.42)<block['top']-source_y<=f32(.6):
            for xdir in (-1,1):
                for zdir in (-1,1):
                    e=dict(case='corner_jump_cancel',input=0,plane=source_y,higherHeight=block['top'],box=box,anchors={'jump':1})
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
        low_rows=region_rows(lower,landing,'case:lower_landing')
        if enabled('blip') and lower['top']<target_y and 0<=target_y-y<=f32(.6):
            box=box_for(target,context)
            for avoid,contacts in landing_wall_options(box,landing):
                e=dict(case='blip',input=landing,plane=target_y,lowerHeight=lower['top'],higherHeight=target_y,
                       box=box,lowerBox=box_for(lower,context),anchors={'landing':landing,'hit':hit})
                p=case_profile(source_y,target_y,e,finish_at=hit,constraints=low_rows+avoid+region_rows(target,hit),contacts=contacts)
                if p:
                    yield 'blip',p,None
        for higher in scene['blocks']:
            if higher['top']<=lower['top']:
                continue
            box=box_for(higher,context)
            for kind in ('blip_up','corner_blip_up'):
                if not enabled(kind) or not (y>higher['top'] if kind=='blip_up' else 0<=higher['top']-y<=f32(.6)):
                    continue
                conditions=[low_rows+[outside_row(box,axis,sign,landing,'blip_up:outside')]+region_rows(higher,hit)
                            for axis in ('X','Z') for sign in (-1,1)] if kind=='blip_up' else [low_rows+corner_rows(box,landing,hit,xd,zd) for xd in (-1,1) for zd in (-1,1)]
                for rows in conditions:
                    for jump_after in (None,hit):
                        e=dict(case=kind,input=landing,plane=y,lowerHeight=lower['top'],higherHeight=higher['top'],
                               box=box,lowerBox=box_for(lower,context),anchors={'landing':landing,'hit':hit})
                        p=case_profile(source_y,target_y,e,max_inputs=horizon,jump_after=jump_after,constraints=rows)
                        if p:
                            yield kind,p,None


class SteppingGenerator:
    def generate(self, context, *, start=0, ends=None):
        if context.get('mode') == 'open':
            yield from open_stepping_choices(context)
            return
        from .movement_units import shifted_unit
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


def open_stepping_choices(context):
    from .interactions import interaction_allowed
    from .movement_composition import scalar_initial
    from .open_movement import approaches, first_descent, named_choice
    initial = scalar_initial(context['state'])
    scene = context['scene']
    y0 = initial['position']
    allowed = context.get('cases')
    def enabled(case):
        return allowed is None or case in allowed
    if initial['onGround']:
        for block in scene['blocks']:
            box = box_for(block, context)
            rise = block['top'] - y0
            for case, jumping in [('normal_step', False), ('slab_boost', True)]:
                if not enabled(case) or not 0 < rise <= f32(.6) or jumping and rise <= f32(.42):
                    continue
                if not interaction_allowed(context, box, 0, jumping=jumping):
                    continue
                for axis in ('X', 'Z'):
                    for direction in (-1, 1):
                        rows = [outside_row(box, axis, -direction, 0)] + region_rows(block, 1)
                        event = dict(case=case, input=0, plane=block['top'], box=box,
                                     anchors={'beforeJump' if jumping else 'beforeStep': 0, 'jump' if jumping else 'hit': 1})
                        choice = named_choice(context, 'stepping', initial, event, jumping=jumping,
                                              constraints=rows, support=block['id'] if not jumping else None)
                        if choice:
                            yield choice
            if enabled('corner_jump_cancel') and f32(.42) < rise <= f32(.6) and interaction_allowed(context, box, 0, jumping=True):
                for xdir in (-1, 1):
                    for zdir in (-1, 1):
                        event = dict(case='corner_jump_cancel', input=0, plane=y0, higherHeight=block['top'],
                                     box=box, anchors={'beforeJump': 0, 'jump': 1})
                        choice = named_choice(context, 'stepping', initial, event, jumping=True,
                                              constraints=corner_rows(box, 0, 1, xdir, zdir))
                        if choice:
                            yield choice
    for initial, jumping, trace in approaches(context):
        for lower in scene['blocks']:
            if context.get('checkBudget'):
                context['checkBudget']()
            landing = first_descent(trace, lower['top'])
            if landing is None:
                continue
            if not interaction_allowed(context, box_for(lower, context), landing, jumping=jumping):
                continue
            y = trace['position'][landing]
            lower_rows = region_rows(lower, landing, 'case:lower_landing')
            for higher in scene['blocks']:
                if higher['top'] <= lower['top']:
                    continue
                box = box_for(higher, context)
                if not interaction_allowed(context, box, landing, jumping=jumping):
                    continue
                if enabled('blip') and 0 <= higher['top'] - y <= f32(.6):
                    options = landing_wall_options(box, landing) if landing else (
                        ([outside_row(box, axis, sign, 0, 'blip:wall_avoid')], [])
                        for axis in ('X', 'Z') for sign in (-1, 1))
                    for rows, finite in options:
                        event = dict(case='blip', input=landing, plane=higher['top'], lowerHeight=lower['top'],
                            higherHeight=higher['top'], box=box, lowerBox=box_for(lower, context),
                            anchors={'landing': landing, 'hit': landing + 1})
                        choice = named_choice(context, 'stepping', initial, event, jumping=jumping,
                            constraints=lower_rows + rows + region_rows(higher, landing + 1), finite=finite, support=higher['id'])
                        if choice:
                            yield choice
                for case in ('blip_up', 'corner_blip_up'):
                    if not enabled(case) or not (y > higher['top'] if case == 'blip_up' else 0 <= higher['top'] - y <= f32(.6)):
                        continue
                    rows = ([outside_row(box, axis, sign, landing, 'blip_up:outside')] + region_rows(higher, landing + 1)
                            for axis in ('X', 'Z') for sign in (-1, 1)) if case == 'blip_up' else (
                            corner_rows(box, landing, landing + 1, xdir, zdir) for xdir in (-1, 1) for zdir in (-1, 1))
                    for constraint in rows:
                        event = dict(case=case, input=landing, plane=y, lowerHeight=lower['top'], higherHeight=higher['top'],
                                     box=box, lowerBox=box_for(lower, context), anchors={'landing': landing, 'hit': landing + 1})
                        choice = named_choice(context, 'stepping', initial, event, jumping=jumping,
                                              constraints=lower_rows + constraint)
                        if choice:
                            yield choice
