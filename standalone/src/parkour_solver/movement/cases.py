import copy

from parkour_solver.physics.vertical import REST_CARRY, f32
from parkour_solver.movement.vertical_profiles import trace_ladder_schedule
from parkour_solver.movement.contact_combinations import (bounding_region, contact_event, finite_rows, region_offset,
                                                          scene_contact_options)
from parkour_solver.scene.geometry import expanded_box
from parkour_solver.spatial.surfaces import scene_geometry
from parkour_solver.movement.units import profile_program
from parkour_solver.scene.route import HALF_WIDTH, center_bounds


CASES = {'normal_step','slab_boost','blip','blip_up','corner_jump_cancel','corner_blip_up','grind'}


def region_rows(block, state, name='case:region', margin=1e-6):
    bounds = center_bounds(block)
    return [dict(mode=axis,t1=state,cmp=cmp,rhs=value,name=name)
            for axis,a in [('X',0),('Z',2)] for cmp,value in [('GE',bounds[a]+margin),('LE',bounds[a+1]-margin)]]


def outside_row(box, axis, sign, state, name='case:outside', margin=1e-6):
    a = 0 if axis == 'X' else 2
    return dict(mode=axis,t1=state,cmp='GE' if sign>0 else 'LE',
                rhs=(box[a+3]+HALF_WIDTH+margin if sign>0 else box[a]-HALF_WIDTH-margin),name=name)


def tangent_rows(box, axis, state):
    a = 0 if axis == 'X' else 2
    return [dict(mode=axis,t1=state,cmp=cmp,rhs=v,name='case:finite') for cmp,v in
            [('GE',box[a]-HALF_WIDTH+1e-6),('LE',box[a+3]+HALF_WIDTH-1e-6)]]


def _faces(scene, block, axis, sign):
    return scene_geometry(scene['collisionScene']).contact_options(
        'XYZ'.index(axis), sign, clearance=0., members=[block['solidIndex']])


def _height(scene, block, y):
    # A height above the top tests the face's top edge; below its expanded bottom, the bottom edge.
    box = expanded_box(scene['collisionScene']['solids'][block['solidIndex']])
    return min(max(y, box[1] + 1e-7), box[4] - 1e-7)


def exposed_side(scene, block, axis, sign, y):
    """Whether the block's expanded side face (`axis` X or Z, outward `sign`) is exposed at feet height y.

    Feet exactly on a flush neighbour's top touch the face above it, so the lower edge counts.
    """
    if 'collisionScene' not in scene:
        return True
    y, (lo, hi) = _height(scene, block, y), ((0, 2) if axis == 'X' else (1, 3))
    return any(r[lo] <= y < r[hi] for option in _faces(scene, block, axis, sign) for r in option.regions)


def convex_corner(scene, block, x_direction, z_escape, y):
    """Whether both side faces meeting at a top corner are exposed at that corner; a seam against a flush
    neighbour is not a corner."""
    if 'collisionScene' not in scene:
        return True
    box, y = expanded_box(scene['collisionScene']['solids'][block['solidIndex']]), _height(scene, block, y)
    x, z = (box[0] if x_direction > 0 else box[3]), (box[5] if z_escape > 0 else box[2])
    return (any(r[0] <= y < r[2] and r[1] <= z <= r[3] for o in _faces(scene, block, 'X', -x_direction) for r in o.regions)
            and any(r[1] <= y < r[3] and r[0] <= x <= r[2] for o in _faces(scene, block, 'Z', z_escape) for r in o.regions))


def top_choices(context, block, state, name='case:region'):
    """(rows, regionChoices) per exposed piece of the block's top at `state`."""
    scene = context['scene']
    if 'collisionScene' not in scene:
        return [(region_rows(block, state, name), [])]
    result = []
    for option in scene_contact_options(scene, 1, 1, members=[block['solidIndex']]):
        rows = [dict(r, name=name) for r in finite_rows(1, bounding_region(option.regions), t1=state)]
        choices = [dict(offset=state, surface=option.surface, owner=block['id'],
                        options=[[dict(r, name=name) for r in finite_rows(1, region, leg=0, offset=state)]
                                 for region in option.regions])] if len(option.regions) > 1 and context.get('regionItems') else []
        result.append((rows, choices))
    return result


def corner_rows(box, first, second, x_direction, z_escape):
    result = [outside_row(box,'X',-x_direction,first),outside_row(box,'Z',z_escape,second)]
    result += tangent_rows(box,'Z',first)
    r = outside_row(box,'X',-x_direction,second)
    r.update(cmp='GE' if x_direction>0 else 'LE', rhs=r['rhs']+x_direction*2e-6, name='case:cross_x')
    return result+[r]+tangent_rows(box,'X',second)


def case_profile(source_y, target_y, event, *, max_inputs=200, jump_at_start=True, jump_after=None,
                 initial_velocity=REST_CARRY, initial_ground=True, constraints=(), contacts=(), finish_at=None):
    event = copy.deepcopy(event)
    n = finish_at if finish_at is not None else max_inputs
    at = event['input']
    if not 0 <= at < n or n > 200:
        return None
    impulses = [None]*n
    if jump_at_start:
        impulses[0] = f32(.42)
    if jump_after is not None:
        if not 0 <= jump_after < n:
            return None
        impulses[jump_after] = f32(.42)
    contact = {k:v for k,v in event.items() if k in ('case','plane','lowerHeight','higherHeight','clearance')}
    contact.update(state=at+1,direction=-1)
    trace = trace_ladder_schedule(dict(position=source_y,velocity=initial_velocity,onGround=initial_ground),
        [False]*(n+1),[False]*n,[any(c['axis']!='Y' and c['offset']==t for c in contacts) for t in range(n)],
        jump=impulses,contacts=[contact],target_top=None if finish_at is not None else target_y,
        min_landing_state=min(n,at+2))
    if trace['status']=='invalid' or not trace['onGround'][-1] or abs(trace['position'][-1]-target_y)>1e-8:
        return None
    event.setdefault('type','ceiling' if event['case']=='grind' else 'stepping')
    event.setdefault('anchors',{})
    profile = dict(airtime=trace['ticks'],landingPhase='post' if finish_at is not None else 'pre',
        position=trace['position'],velocity=trace['velocity'],grounded=trace['onGround'],contacts=trace['vertical']['contacts'],
        jump=[v is not None for v in trace['vertical']['jumpVelocity']],caseEvents=[event],
        positionConstraints=list(constraints),contactEvents=list(contacts))
    profile['generatedUnits'] = profile_program(profile)
    return profile


def landing_wall_options(scene, block, landing, y):
    """Avoid or approach each exposed side of the higher block around the lower landing."""
    if landing < 1:
        return
    box = scene['collisionScene']['solids'][block['solidIndex']]
    for axis in ('X','Z'):
        a = 0 if axis=='X' else 2
        lo, hi = (0, 2) if axis == 'X' else (1, 3)
        for direction in (-1,1):
            if exposed_side(scene, block, axis, direction, y):
                yield [outside_row(box,axis,direction,landing,'blip:wall_avoid')], []
            for option in scene_contact_options(scene, a, -direction, members=[block['solidIndex']]):
                if any(r[lo] <= _height(scene, block, y) < r[hi] for r in option.regions):
                    yield ([outside_row(box,axis,-direction,landing-1,'blip:wall_approach')],
                           [contact_event(option, axis, direction, landing-1, 'contact')])


def entry_possible(profile, bounds):
    intervals = {'X': bounds[:2], 'Z': bounds[2:]}
    regions = [row for e in profile.get('contactEvents', [])
               for row in finite_rows('XYZ'.index(e['axis']), bounding_region(e['regions']), t1=region_offset(e))]
    for row in profile.get('positionConstraints', []) + regions:
        if row['t1'] != 0:
            continue
        interval = intervals[row['mode']]
        if row['cmp'] in ('GE', 'EQ'):
            interval[0] = max(interval[0], row['rhs'])
        if row['cmp'] in ('LE', 'EQ'):
            interval[1] = min(interval[1], row['rhs'])
    return all(lo <= hi for lo, hi in intervals.values())
