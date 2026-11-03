import math

from standalone.ipopt.ladder import clamp


def reachable_envelopes(problem, y=None):
    bounds = {}
    for row in problem.get('walls', []):
        if row['mode'] not in ('X', 'Z') or row.get('t2') is not None:
            continue
        key = (row['t1'], 0 if row['mode'] == 'X' else 1)
        lo, hi = bounds.get(key, (-math.inf, math.inf))
        if row['cmp'] in ('GE', 'EQ'):
            lo = max(lo, row['rhs']-1e-7)
        if row['cmp'] in ('LE', 'EQ'):
            hi = min(hi, row['rhs']+1e-7)
        bounds[key] = lo, hi
    p_lo, p_hi = list(problem['position']), list(problem['position'])
    v_lo, v_hi = list(problem['velocity']), list(problem['velocity'])
    if 'initialIntervals' in problem:
        p_lo, p_hi, v_lo, v_hi = [list(v) for v in problem['initialIntervals']]
    contacts = {(c['state'], c['axis']): c for c in problem.get('contacts', [])}
    ladder = problem.get('ladderTicks', [False]*(problem['ticks']+1))
    states, velocities, movements, contradiction = [], [], [], None
    for tick in range(problem['ticks']+1):
        for axis in range(2):
            lo, hi = bounds.get((tick, axis), (-math.inf, math.inf))
            p_lo[axis], p_hi[axis] = max(p_lo[axis], lo), min(p_hi[axis], hi)
            if p_lo[axis] > p_hi[axis]+1e-9*(1+abs(p_lo[axis])+abs(p_hi[axis])):
                contradiction = dict(state=tick, axis=axis, reason='interval_reach_contradiction')
        states.append(dict(X=[p_lo[0], p_hi[0]], Z=[p_lo[1], p_hi[1]],
                           Y=[y[tick], y[tick]] if y is not None else [-math.inf, math.inf]))
        velocities.append([v_lo[:], v_hi[:]])
        if contradiction is not None or tick == problem['ticks']:
            break
        magnitude, friction = problem['magnitude'][tick], problem['friction'][tick]
        move_bounds = []
        for axis in range(2):
            movement_lo, movement_hi = min(v_lo[axis], 0.)-magnitude, max(v_hi[axis], 0.)+magnitude
            if ladder[tick]:
                movement_lo, movement_hi = clamp(movement_lo), clamp(movement_hi)
            move_bounds.append([movement_lo-1e-8, movement_hi+1e-8])
            contact = contacts.get((tick+1, axis))
            if contact is not None:
                plane, direction = contact['plane'], contact['direction']
                if (direction > 0 and (p_lo[axis] > plane+1e-7 or p_hi[axis]+movement_hi < plane-1e-7)
                    or direction < 0 and (p_hi[axis] < plane-1e-7 or p_lo[axis]+movement_lo > plane+1e-7)):
                    contradiction = dict(state=tick+1, axis=axis, reason='contact_reach_contradiction')
                p_lo[axis] = p_hi[axis] = plane
                v_lo[axis] = v_hi[axis] = 0.
                continue
            padding = 1e-12*(1+abs(p_lo[axis])+abs(p_hi[axis])+abs(movement_lo)+abs(movement_hi))
            p_lo[axis] += movement_lo-padding
            p_hi[axis] += movement_hi+padding
            v_lo[axis] = movement_lo*friction-padding
            v_hi[axis] = movement_hi*friction+padding
        movements.append(move_bounds)
    if contradiction is None:
        for _ in range(2):
            for t in range(len(states)-2, -1, -1):
                for i, axis in enumerate(('X', 'Z')):
                    lo, hi = movements[t][i]
                    states[t][axis][0] = max(states[t][axis][0], states[t+1][axis][0]-hi)
                    states[t][axis][1] = min(states[t][axis][1], states[t+1][axis][1]-lo)
            for t in range(len(states)-1):
                for i, axis in enumerate(('X', 'Z')):
                    lo, hi = movements[t][i]
                    states[t+1][axis][0] = max(states[t+1][axis][0], states[t][axis][0]+lo)
                    states[t+1][axis][1] = min(states[t+1][axis][1], states[t][axis][1]+hi)
            for t, state in enumerate(states):
                for i, axis in enumerate(('X', 'Z')):
                    if state[axis][0] > state[axis][1]+1e-7:
                        contradiction = dict(state=t, axis=i, reason='interval_reach_contradiction')
    phases = []
    for a, b in zip(states, states[1:]):
        sweep = lambda axis: [min(a[axis][0], b[axis][0]), max(a[axis][1], b[axis][1])]
        phases.append(dict(Y=dict(X=a['X'], Y=sweep('Y'), Z=a['Z']),
            X=dict(X=sweep('X'), Y=b['Y'], Z=a['Z']),
            Z=dict(X=b['X'], Y=b['Y'], Z=sweep('Z'))))
    # Named step/grind events describe realized Y, not the heights of both
    # legacy step probes. Until their probe geometry is bounded explicitly,
    # retain unknown Y in interaction phases; X/Z envelopes remain valid.
    # Ordinary movement and ordinary floor/ceiling clips keep exact Y sweeps.
    for contact in problem.get('vertical', {}).get('contacts', []):
        tick = contact['state']-1
        if contact.get('case') and 0 <= tick < len(phases):
            for phase in phases[tick].values():
                phase['Y'] = [-math.inf, math.inf]
            for phase in ('X','Z'):
                for i, axis in enumerate(('X','Z')):
                    lo, hi = states[tick][axis]
                    move_lo, move_hi = movements[tick][i]
                    realized = states[tick+1][axis]
                    phases[tick][phase][axis] = [min(lo,lo+move_lo,realized[0]),
                                                max(hi,hi+move_hi,realized[1])]
    speed = math.hypot(*problem['velocity'])
    if 'initialIntervals' in problem:
        speed = math.hypot(*(max(abs(a), abs(b)) for a,b in zip(problem['initialIntervals'][2],problem['initialIntervals'][3])))
    radii = [0.]
    for magnitude, friction in zip(problem['magnitude'], problem['friction']):
        move = speed+magnitude+1e-8
        radii.append(radii[-1]+move)
        speed = friction*move
    if contradiction is None:
        for j in range(1,len(states)):
            for i in range(j):
                gap = math.hypot(*(max(0.,states[j][axis][0]-states[i][axis][1],states[i][axis][0]-states[j][axis][1])
                                   for axis in ('X','Z')))
                if gap > radii[j]-radii[i]+1e-7:
                    contradiction = dict(state=j, previousState=i, reason='norm_reach_contradiction',
                                         gap=gap, travelUpperBound=radii[j]-radii[i])
                    break
            if contradiction is not None:
                break
    return dict(states=states, phases=phases, velocities=velocities, contradiction=contradiction,
                radii=radii, origin=list(problem['position']) if 'initialIntervals' not in problem else None, model=problem)


def interaction_reachability(envelopes, interaction, *, refine=False):
    if isinstance(interaction, list):
        interaction = dict(rows=interaction)
    if envelopes.get('contradiction'):
        return dict(ruledOut=True, **envelopes['contradiction'])
    if 'alternatives' in interaction:
        results = [interaction_reachability(envelopes, item, refine=refine) for item in interaction['alternatives']]
        if results and all(r['ruledOut'] for r in results):
            return dict(ruledOut=True, reason='all_interaction_alternatives_excluded', alternatives=results)
        return dict(ruledOut=False, reason='not_ruled_out')
    tightened = {}
    for row in interaction.get('rows', []):
        axis, t = row.get('mode'), row.get('t1', row.get('offset'))
        if axis not in ('X', 'Z') or t is None or not 0 <= t < len(envelopes['states']):
            continue
        lo, hi = tightened.get((t,axis), envelopes['states'][t][axis])
        if row.get('t2') is not None:
            t2 = row['t2']
            if not 0 <= t2 < len(envelopes['states']):
                continue
            a, b = envelopes['states'][t2][axis]
            lo, hi = (lo-b, hi-a) if row.get('op') == 'MINUS' else (lo+a, hi+b)
        rhs, cmp = row['rhs'], row['cmp']
        if (cmp in ('GE', 'EQ') and hi < rhs-1e-7) or (cmp in ('LE', 'EQ') and lo > rhs+1e-7):
            return dict(ruledOut=True, reason='interaction_row_unreachable', tick=t, phase='state',
                        axis=axis, reachable=[lo, hi], required=dict(cmp=cmp, rhs=rhs))
        if row.get('t2') is None:
            tightened[(t,axis)] = [max(lo,rhs-1e-7) if cmp in ('GE','EQ') else lo,
                                   min(hi,rhs+1e-7) if cmp in ('LE','EQ') else hi]
    for region in interaction.get('regions', []):
        t, phase = region['tick'], region.get('phase', 'state')
        domains = envelopes['states'] if phase == 'state' else envelopes['phases']
        if not 0 <= t < len(domains):
            continue
        domain = domains[t] if phase == 'state' else domains[t][phase]
        box = region['bounds']
        if envelopes.get('origin') is not None and 'radii' in envelopes:
            radius = envelopes['radii'][t if phase in ('state','Y') else t+1]
            gap = math.hypot(*(max(box[i]-coordinate,0.,coordinate-box[i+3])
                               for i,coordinate in zip((0,2),envelopes['origin'])))
            if gap > radius+1e-7:
                return dict(ruledOut=True,reason='interaction_distance_unreachable',tick=t,phase=phase,
                            gap=gap,travelUpperBound=radius)
        for i, axis in enumerate(('X', 'Y', 'Z')):
            lo, hi = domain[axis]
            excluded = (hi <= box[i] or lo >= box[i+3]) if region.get('interior') else (hi < box[i]-1e-7 or lo > box[i+3]+1e-7)
            if excluded:
                return dict(ruledOut=True, reason='interaction_region_unreachable', tick=t, phase=phase,
                            axis=axis, reachable=[lo, hi], required=[box[i], box[i+3]])
    if refine and envelopes.get('model') is not None:
        from .linear_reachability import linear_exclusion
        excluded=linear_exclusion(envelopes['model'],interaction.get('rows',[]))
        if excluded is not None:
            return dict(ruledOut=True,**excluded)
    return dict(ruledOut=False, reason='not_ruled_out')
