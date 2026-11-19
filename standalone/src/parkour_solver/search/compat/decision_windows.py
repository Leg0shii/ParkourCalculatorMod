import copy

from parkour_solver.schema import integer, number


KINDS = ('jump', 'stepping', 'ceiling', 'landing', 'passage')


def plan_windows(opportunities, *, gap=2, max_inputs=200):
    integer(gap, 'cluster gap', 0, 200)
    integer(max_inputs, 'maximum inputs', 1, 200)
    items, ids = [], set()
    for source in opportunities:
        if not isinstance(source, dict) or set(source)-{'id','inputs','kinds','dependsOn','readyState','segment','region'}:
            raise ValueError('Unsupported decision opportunity')
        item = copy.deepcopy(source)
        name = item.get('id')
        if not isinstance(name,str) or not name or name in ids:
            raise ValueError('Decision opportunity IDs must be unique strings')
        ids.add(name)
        interval = item.get('inputs')
        if not isinstance(interval,list) or len(interval)!=2:
            raise ValueError('Opportunity inputs must be an inclusive [first,last] range')
        lo, hi = [integer(t, 'decision input', 0, max_inputs-1) for t in interval]
        if lo > hi:
            raise ValueError('Decision input range is empty')
        item.setdefault('kinds',list(KINDS))
        if not isinstance(item['kinds'],list) or not item['kinds'] or any(k not in KINDS for k in item['kinds']):
            raise ValueError('Unsupported decision kinds')
        item['readyState'] = integer(item.get('readyState',hi+1),'ready state',hi+1,max_inputs)
        item.setdefault('dependsOn',[])
        if not isinstance(item['dependsOn'],list) or any(not isinstance(v,str) for v in item['dependsOn']):
            raise ValueError('dependsOn must contain opportunity IDs')
        items.append(item)
    if any(v not in ids for item in items for v in item['dependsOn']):
        raise ValueError('Unknown opportunity dependency')
    items.sort(key=lambda item:(item['inputs'][0],item['inputs'][1],item['id']))
    groups = [[item] for item in items]
    while True:
        merged = False
        for i in range(len(groups)-1):
            left, right = groups[i], groups[i+1]
            hi = max(v['inputs'][1] for v in left)
            ready = max(v['readyState'] for v in left)
            locations = {v['id']:j for j,g in enumerate(groups) for v in g}
            crossing = any(min(locations[v['id']],locations[d]) <= i < max(locations[v['id']],locations[d])
                           for v in items for d in v['dependsOn'])
            if right[0]['inputs'][0]-hi <= gap or right[0]['inputs'][0] < ready or crossing:
                groups[i:i+2] = [left+right]
                merged = True
                break
        if not merged:
            break
    clusters, checkpoints = [], []
    for i, group in enumerate(groups):
        onset = groups[i+1][0]['inputs'][0] if i+1 < len(groups) else None
        spec = dict(tick=onset) if onset is not None else None
        for key in ('segment','region'):
            if key in group[-1] and spec is not None:
                spec[key] = group[-1][key]
        cluster = dict(ids=[v['id'] for v in group],firstInput=group[0]['inputs'][0],
                       lastInput=max(v['inputs'][1] for v in group),
                       readyState=max(v['readyState'] for v in group), checkpoint=spec,
                       nextDecision=groups[i+1][0]['id'] if onset is not None else None)
        clusters.append(cluster)
        if spec is not None:
            checkpoints.append(spec)
    return dict(opportunities=items,clusters=clusters,checkpoints=checkpoints,gap=gap,
                timingSource='supplied_inclusive_input_ranges',indexing='absolute_pre_input_state_zero_based')


def decision_events(program, goal_box=None):
    p = program['profile']
    result = [dict(input=t,kind='jump') for t,value in enumerate(p['jump']) if value]
    owned = set()
    for event in p.get('caseEvents',[]):
        kind = 'ceiling' if event.get('case')=='grind' or event.get('type')=='ceiling' else 'stepping'
        owned.add(event['input'])
        result.append(dict(input=event['input'],kind=kind,case=event.get('case')))
    for event in p.get('contactEvents',[]):
        t = event['offset']
        if event['axis']!='Y' or t in owned:
            continue
        if event['direction'] > 0:
            result.append(dict(input=t,kind='ceiling'))
        elif goal_box not in event['boxes'] and not (p['grounded'][t] and not p['jump'][t]
                and abs(p['position'][t+1]-p['position'][t]) < 1e-7):
            result.append(dict(input=t,kind='landing'))
    return sorted(result,key=lambda event:(event['input'],event['kind']))


class DecisionPolicy:
    def __init__(self, plan, scene):
        self.plan = plan
        goal = next(b for b in scene['blocks'] if b['id']==scene['goalBlock'])
        self.goal_box = scene['collisionScene']['solids'][goal['solidIndex']]

    def audit(self, program):
        events = decision_events(program,self.goal_box)
        unmatched = [e for e in events if not any(e['kind'] in v['kinds'] and v['inputs'][0] <= e['input'] <= v['inputs'][1]
                                                for v in self.plan['opportunities'])]
        return dict(accepted=not unmatched,events=events,unmatched=unmatched,
                    coverage='selected_jump_step_ceiling_and_optional_landing_events')

    def __call__(self, program):
        return self.audit(program)['accepted']


def retain_candidates(solved, unknown, *, cap, unknown_slots=1, epsilon=None, order='guided'):
    integer(cap,'retention cap',1,100000)
    integer(unknown_slots,'unknown slots',0,cap)
    if epsilon is not None and number(epsilon,'objective gap') < 0:
        raise ValueError('Objective gap must be nonnegative')
    peak = max((record['value'] for record,_ in solved),default=None)
    eligible = [(r,p) for r,p in solved if epsilon is None or peak-r['value'] <= epsilon+1e-9]
    if order=='guided':
        eligible.sort(key=lambda pair:-pair[0]['value'])
    limit = cap-min(unknown_slots,len(unknown))
    accepted = eligible[:limit]
    reserve = unknown[:min(unknown_slots,cap-len(accepted)) if epsilon is not None else cap-len(accepted)]
    retained = accepted+reserve
    return retained,dict(policy='objective_band' if epsilon is not None else 'fixed_width',
        epsilon=epsilon,bestScore=peak,eligible=len(eligible),
        outsideBand=len(solved)-len(eligible),scoredCapDrops=max(0,len(eligible)-len(accepted)),
        unknownCapDrops=len(unknown)-len(reserve),
        hardCap=cap,retainedScored=len(accepted),retainedUnknown=len(reserve),
        objectiveOptimalityProven=False)


def score_features(best, cp, *, goal_box=None, samples=4):
    candidate, p = best['candidate'], best['profiles'][0]
    events = decision_events(dict(profile=p),goal_box)
    quiet = max([0]+[e['input']+1 for e in events if e['input'] < cp.tick])
    states = range(max(quiet,cp.tick-samples+1),cp.tick+1)
    dx,dz = cp.direction
    return dict(quietFromState=quiet,events=events,scoring='position_at_checkpoint_only',
        samples=[dict(tick=t,progress=dx*candidate['positions'][t][0]+dz*candidate['positions'][t][1],
                      forwardVelocity=dx*candidate['velocity'][t][0]+dz*candidate['velocity'][t][1],
                      lateralVelocity=-dz*candidate['velocity'][t][0]+dx*candidate['velocity'][t][1],
                      y=candidate['y'][t],vy=candidate['vy'][t],onGround=candidate['onGround'][t]) for t in states])
