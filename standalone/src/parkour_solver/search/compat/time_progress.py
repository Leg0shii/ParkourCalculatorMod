import json
import math

from parkour_solver.schema import integer, number
from parkour_solver.search.identity import motion_record, processing_key
from parkour_solver.search.comparison import compare_candidates, measurements as _measurements, TOLERANCE


def protections(candidate, peer, progress_margin):
    return compare_candidates(candidate,peer,context=dict(purpose='common_clock',
        progressMargin=progress_margin))['protectionReasons']


def clearly_worse(candidate, peer, *, time_margin, progress_margin):
    return compare_candidates(candidate,peer,context=dict(purpose='common_clock',
        timeMargin=time_margin,progressMargin=progress_margin))['action']=='defer_preparation'


def retain_two_goals(candidates, *, cap, unknown_slots=1, time_margin=1, progress_margin=.25, prune=True):
    integer(cap,'candidate retention cap',1,100000)
    integer(unknown_slots,'unknown reserve',0,cap)
    if number(time_margin,'time margin')<0 or number(progress_margin,'progress margin')<0:
        raise ValueError('Comparison margins must be nonnegative')
    records = [record for record,_ in candidates]
    ids = [r['id'] for r in records]
    if len(ids)!=len(set(ids)):
        raise ValueError('Candidate identities must be unique')
    decisions = {}
    pair_cache = {}
    def pair(candidate, peer):
        key=(candidate['id'],peer['id'])
        if key not in pair_cache:
            pair_cache[key]=compare_candidates(candidate,peer,context=dict(purpose='common_clock',
                timeMargin=time_margin,progressMargin=progress_margin))
        return pair_cache[key]
    eligible,unknown = [],[]
    for i,(record,program) in enumerate(candidates):
        times,progress = _measurements(record)
        peer = next((r['id'] for r in records if r['id']!=record['id'] and
            pair(record,r)['action']=='defer_preparation'),None) if prune else None
        if peer is not None:
            decisions[record['id']] = dict(reason='markedly_worse',peer=peer)
        elif not times and not progress:
            unknown.append(i)
        else:
            eligible.append(i)
    def priority(i):
        return processing_key(candidates[i][1], records[i]['id'])
    available = sorted(eligible+unknown, key=priority) if prune else sorted(range(len(candidates)), key=priority)
    selected = available[:cap] if prune else available
    deferred = available[len(selected):]
    selected_set=set(selected)
    for i,record in enumerate(records):
        if i in selected_set:
            decisions[record['id']]=dict(reason='retained')
        elif record['id'] not in decisions:
            decisions[record['id']]=dict(reason='deferred')
        if decisions[record['id']]['reason'] != 'markedly_worse':
            guards = {r['id']: pair(record,r)['protectionReasons'] for r in records if r['id'] != record['id']}
            decisions[record['id']]['protections'] = {k: v for k, v in guards.items() if v}
    return [candidates[i] for i in selected],dict(policy='two_goal_margin' if prune else 'unpruned_reference',
        timeMargin=time_margin,progressMargin=progress_margin,hardCap=cap,unknownReserve=unknown_slots,
        decisions=decisions,markedlyWorse=sum(d['reason']=='markedly_worse' for d in decisions.values()),
        resourceDrops=0, deferred=[records[i]['id'] for i in deferred], deferredCount=len(deferred),
        retained=len(selected),objectiveOptimalityProven=False)


def clock_result(checkpoint, result):
    context = dict(tick=checkpoint.tick,direction=list(checkpoint.direction),anchor=list(checkpoint.start),
                   signature=checkpoint.required_signature,region=list(checkpoint.region),segment=checkpoint.segment)
    key=json.dumps(context,sort_keys=True)
    best=result.get('best')
    value=None
    if best is not None and checkpoint.tick<len(best['candidate']['positions']):
        x,z=best['candidate']['positions'][checkpoint.tick]
        value=checkpoint.direction[0]*(x-checkpoint.start[0])+checkpoint.direction[1]*(z-checkpoint.start[2])
    state = None
    if value is not None and best.get('profiles') and best['candidate'].get('velocity'):
        vx, vz = best['candidate']['velocity'][checkpoint.tick]
        state = dict(position=list(best['candidate']['positions'][checkpoint.tick]), velocity=[vx, vz],
            forwardVelocity=checkpoint.direction[0]*vx+checkpoint.direction[1]*vz,
            lateralPosition=-checkpoint.direction[1]*(x-checkpoint.start[0])+checkpoint.direction[0]*(z-checkpoint.start[2]),
            lateralVelocity=-checkpoint.direction[1]*vx+checkpoint.direction[0]*vz,
            witnessProblem=best.get('problemHash'), solveIdentity=result.get('solveIdentity'),
            speed=math.hypot(vx, vz), motion=motion_record(dict(profile=best['profiles'][0])), scope=result.get('solveScope'))
    return key,dict(context=context,status='feasible' if value is not None else 'unknown',objective=value,state=state,
                    unavailableReason='terminal_before_clock' if best is not None and value is None else None,
                    interpretation='fixed_absolute_tick_progress_proxy_not_arrival_time')
