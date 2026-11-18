"""Trace a finished solve per partial route and render it as a Markdown report."""

import argparse
from collections import Counter
import json
from pathlib import Path

from parkour_solver.constraints.encounters import PHASES
from parkour_solver.io.checkpoint_store import read_report
from parkour_solver.io.reports import save
from parkour_solver.search.repair import FALLBACK_GAP, FALLBACK_LIMIT
from parkour_solver.search.repair_state import branch_key


def frames(state):
    seen = set()
    for group in ('stack','suspended','refinements','goalQueue','frames'):
        for frame in state['dfs'].get(group,[]):
            if frame['id'] not in seen:
                seen.add(frame['id'])
                yield frame


def route(frame, record):
    return dict(frame=frame['id'],goal=frame['goal'].get('block'),record=record['id'],
                recordStatus=record['status'],node=(record.get('nodeFeasibility') or {}).get('status'))


def request_owners(state):
    owners = {}
    for frame in frames(state):
        for record in frame['records']:
            for attempt in record['program'].get('repairState',{}).get('attemptLog',[]):
                rows = owners.setdefault(attempt['problemHash'],[])
                owner = route(frame,record)
                if owner not in rows:
                    rows.append(owner)
    return owners


def stop_report(state, requests):
    suspended = []
    for frame in state['dfs'].get('suspended',[]):
        repairs = [r['program'].get('repairState',{}) for r in frame['records']]
        suspended.append(dict(frame=frame['id'],goal=frame['goal'].get('block'),records=len(frame['records']),
            unresolvedBranches=sum(len(r.get('unresolved') or []) for r in repairs),
            pendingBranches=sum(len(r.get('pending') or []) for r in repairs),
            deferredNodes=len(frame['generation'].get('continuation',{}).get('deferredNodes') or [])))
    rejected = [route(frame,record) for frame in frames(state) for record in frame['records']
                if (record.get('nodeFeasibility') or {}).get('status') == 'oracle_rejected']
    unknown = [dict(index=r['index'],workerStatus=r.get('workerStatus'),owners=r.get('owners',[]))
               for r in requests if r.get('status') == 'unknown']
    return dict(suspendedFrames=suspended,oracleRejectedRoutes=rejected,unknownRequests=unknown)


def encounter_trace(entry):
    def face(i):
        return side(entry['sides'][i])
    def phase(at):
        return [at//3,PHASES[at%3]]
    return dict(owner=entry['owner'],members=entry['members'],segment=entry['segment'],
                possiblePhases=entry['possiblePhases'],
                coveredPhases=[dict(phase=phase(at),certainFaces=[face(i) for i in certain])
                               for at,certain in entry['coveredPhases']],
                excludedFaces=[dict(phase=phase(at),face=face(i)) for at,i in entry['excludedFaces']],
                verticalClear=[dict(phase=phase(at),face=face(i)) for at,i in entry['verticalClear']],
                uncertain=[phase(at) for at in entry['uncertain']])


def item_label(item):
    if 'clusterId' in item:
        return item.get('owner'), [item['begin'],item['end']]
    if 'contactCheck' in item:
        return 'contact check', [item['contactCheck']['offset']]*2
    rows = [r for option in item.get('rows',[]) for r in option]
    ticks = [r.get('t1',r.get('offset')) for r in rows]
    name = rows[0].get('name','') if rows else ''
    if item.get('role') == 'contact_region':
        name = item['owner']+' region'
    return (name.split(':')[1] if name.startswith('event:') else name or 'route guard'), [min(ticks),max(ticks)] if ticks else None


def family_sides(choices, family, regions=()):
    first = family['points'][family['order'][0]]['keys'][0]
    chosen = {tuple(r) for r in json.loads(first)[0]}
    rectangles = [f'{item["owner"].upper()}: '+region_text(option) for item in regions for option in item['rows']
                  if all((r['leg'],r['offset'],r['mode'],r['cmp'],r['rhs']) in chosen for r in option)]
    return '; '.join([f'{p["blockId"].upper()}: '+' then '.join(side(f) for f in p['faces'])
                      for p in choices.get(first,[])]+rectangles) or 'no obstacle sides'


def family_point(point, index):
    ticks = plural(len(point['switches']),'input').split()[1]+' '+', '.join(map(str,point['switches'])) if point['switches'] else 'no switch'
    solved = ', '.join(f'#{index[k]}' for k in point['keys'] if k in index)
    outcome = (number(point['objective']) if point['rank'] is not None else
               'infeasible' if point['status'] == 'settled' else 'not solved')
    return f'{ticks} ({solved+": " if solved else ""}{outcome})'


def repair_trace(repair, final=False):
    generation = repair.get('constraintGeneration') or {}
    attempts = {a['problemHash']:dict(status=a['status'],reason=(a.get('certificate') or {}).get('reason'))
                for a in repair.get('attemptLog',[])}
    items = generation.get('items',[])
    return dict(
        encounters=[dict(owner=i['owner'],members=i['members'],begin=i['begin'],end=i['end'])
                    for i in items if 'clusterId' in i],
        contactChecks=sum('contactCheck' in i for i in items),
        contactRegions=[dict(ticks=sorted({r['offset'] for option in i['rows'] for r in option}),owner=i['owner'],
                             surface=i['surface'],options=i['rows']) for i in items if i.get('role') == 'contact_region'],
        generatorMetrics=generation.get('metrics',{}),
        preparedEncounters=[encounter_trace(e) for e in generation.get('encounterLog',[])],
        faceOwners=sorted({(e['owner'],name,b['axis'],'GE' if b['direction'] > 0 else 'LE',b['plane'])
                           for e in generation.get('encounterLog',[]) if e['possiblePhases']
                           for s in e['sides'] for b in s['bounds']
                           for name in {dict(zip(e['members'],e['names']))[o] for o in b['owners']}}),
        droppedOptions=[dict(d,owner=next((i['owner'] for i in items if i.get('clusterId') == d['clusterId']),None))
                        for d in generation.get('droppedOptions',[])],
        screenRejections=[dict(rowsHash=e['rowsHash'],**e['proof'],
                               **(dict(zip(('encounter','segment'),item_label(items[e['item']])),
                                       optionRows=e['optionRows'],passage=e['passage'])
                                  if 'item' in e else {}))
                          for e in generation.get('screenExclusions',[])],
        alternatives=[dict(branch=c['branch'],rows=json.loads(c['branch'])[0],status=c['status'],validation=c.get('validationStatus'),
                           oracleRejected=c.get('oracleRejected',False),proven=c.get('proven',False),
                           certificates=[e['reason'] for e in c.get('certificates',[])],
                           solves=[dict(problemHash=h,**attempts.get(h,{})) for h in c.get('problemHashes',[])])
                      for c in repair.get('completed',[])],
        families=[dict(sides=family_sides(repair.get('interactionChoices',{}),f,
                                          [i for i in items if i.get('role') == 'contact_region']),
                       points=[dict({k:f['points'][n][k] for k in ('switches','status','rank','objective','keys')},
                                    held=len(f['points'][n]['held'])) for n in f['order']])
                  for f in (repair.get('families') or {}).values()],
        kept=branch_key(repair['incumbent']['rows'],repair['incumbent']['passages'],repair['incumbent']['hits'])
             if repair.get('incumbent') else None,
        fallbacks=None if final else [v['branch'] for v in repair.get('fallbackVariants') or []],
        unresolved=[b['rows'] for b in repair.get('unresolved') or []],
        pending=[b['rows'] for b in repair.get('pending') or []])


def block_names(scene):
    return {tuple(box):block['id'] for block in (scene or {}).get('blocks',[]) for box in block.get('boxes',[])}


def movement(profile, names):
    def where(event):
        boxes = event['boxes'] if 'boxes' in event else [event['box']] if 'box' in event else []  # reports before member boxes
        members = sorted({names.get(tuple(box),'unnamed box') for box in boxes}) or ['unnamed box']
        return dict(block=event.get('blockId') or '+'.join(members),
                    tick=event.get('offset'))
    events = profile.get('contactEvents',[])
    return dict(jumps=[t for t,jump in enumerate(profile.get('jump',[])) if jump],
                landings=[where(e) for e in events if e.get('role') == 'landing'],
                ceilings=[where(e) for e in events if e.get('role') == 'ceiling'],
                walls=[dict(where(e),side=e['axis']+('+' if e['direction'] > 0 else '-'))
                       for e in events if e.get('role') == 'contact'],
                cases=[dict(case=e.get('case'),tick=e.get('input',e.get('offset'))) for e in profile.get('caseEvents',[])])


def side(face):
    if face['axis'] == 'Y':
        return 'over' if face['direction'] > 0 else 'under'
    return ''.join(b['axis']+('+' if b['direction'] > 0 else '-') for b in face.get('bounds') or [face])


def route_trace(state, scene=None):
    names = block_names(scene)
    records = {record['id']:record for frame in frames(state) for record in frame['records']}
    routes = []
    for frame in sorted(frames(state),key=lambda f:f['id']):
        owners, accepted = {}, {}
        for record in frame['records']:
            repair = record['program'].get('repairState',{})
            if repair.get('completed') and record['candidateKey'] not in owners:
                owners[record['candidateKey']] = record
                for c in repair['completed']:
                    for h in c.get('problemHashes',[]):
                        accepted[h] = json.loads(c['branch'])[0]
        parent = records.get((frame.get('parent') or {}).get('candidate'))
        walls = (parent.get('witness') or {}).get('plan',{}).get('walls',[]) if parent else []
        retained = [{k:v for k,v in w.items() if k in ('mode','t1','t2','op','cmp','rhs','name')} for w in walls
                    if w['mode'] != 'F' and not w.get('name','').startswith('checkpoint_probe:')]
        for record in frame['records']:
            rows = record['program']['profile']['positionConstraints']
            witness = record.get('witness')
            owner = owners.get(record['candidateKey'])
            routes.append(dict(route(frame,record),parent=parent['id'] if parent else None,
                ticks=record.get('ticks'),repairOwner=owner['id'] if owner else None,
                solvedTicks=witness['plan']['ticks'] if witness else None,
                objective=witness['candidate'].get('objective') if witness else None,
                acceptedAlternative=accepted.get(witness['problemHash']) if witness else None,
                inheritedRows=[r for r in retained if r in rows],
                movement=movement(record['program']['profile'],names),
                passages=[dict(block=p['blockId'],ticks=p['segment'],sides=[side(f) for f in p['faces']],kind=p['kind'])
                          for p in witness.get('passages',[])] if witness else None,
                seededFacings='guideSeedYaw' in record['program'],
                repair=repair_trace(record['program']['repairState'],record.get('complete',False)) if record is owner else None))
    return routes


TERMINATIONS = dict(
    deadline='used its whole time budget',
    exhausted='explored every runnable alternative',
    numerical_unknown='ran out of runnable work; the only work left is retrying solves that came back unknown',
    cancelled='was cancelled')
CHECKS = dict(
    interval_reach_contradiction='interval reachability',
    norm_reach_contradiction='travel-distance reachability',
    linear_control_relaxation_infeasible='linear control relaxation',
    passage_timing='passage timing',
    vertical_schedule='vertical schedule',
    interaction_row_unreachable='single-row reachability',
    interaction_region_unreachable='region reachability',
    interaction_distance_unreachable='distance reachability',
    required_interaction_unreachable='required contact reachability',
    composition_rejected='composition',
    contact_reach_contradiction='contact reachability')


def generator_exclusions(result):
    """The movement generators' reachability rejections per DFS frame, one row per choice and check."""
    names = block_names(result.get('effectiveRequest'))
    grouped = {}
    for entry in (result.get('interactionGate') or {}).get('exclusions', []):
        proof = entry['proof']
        if 'cases' in entry:
            owner = '+'.join(sorted({names.get(tuple(box), 'unnamed box') for case in entry['cases']
                                     for box in case.get('boxes', [])})) or 'unnamed box'
            role = ' + '.join(case['case'] for case in entry['cases'])+' (joint prerequisites)'
            ticks, area, example = [proof.get('input', entry['input'])], None, innermost(proof)
        else:
            owner, role, ticks = entry['owner'], entry['role'], [entry['input']]
            proof = example = innermost(proof)
            area = 'X [{:.3f}, {:.3f}] × Z [{:.3f}, {:.3f}]'.format(*(entry['horizontal'][i] for i in (0, 2, 1, 3)))
        row = grouped.setdefault((entry.get('frame'), owner, role, area, proof.get('reason')),
                                 dict(owner=owner, role=role, area=area, check=proof.get('reason'), ticks=set(), count=0,
                                      example=example))
        row['ticks'].update(t for t in ticks if t is not None)
        row['count'] += 1
    frames = {}
    for (frame, *_), row in grouped.items():
        frames.setdefault(frame, []).append(dict(row, ticks=sorted(row['ticks'])))
    return frames


def innermost(proof):
    """The first concrete reason inside a nested reachability proof."""
    while True:
        if isinstance(proof.get('proof'), dict):
            proof = proof['proof']
        elif proof.get('phases'):
            proof = proof['phases'][0]
        elif proof.get('alternatives'):
            proof = proof['alternatives'][0]
        else:
            return proof


def exclusion_lines(rows):
    if not rows:
        return []
    return ([f'Choices the movement generators ruled out by reachability before building them: {sum(r["count"] for r in rows)} '
             '(a choice is checked again for every prefix the frame extends, for a jump and a no-jump start, and by every '
             'generator that offers it, so one choice can be ruled out several times).','']+
            table(['Choice','Player-centre area','Ticks','Check','Times','Example'],
                  [[f'{r["owner"].upper()} {r["role"]}',r['area'] or '-',ticks_text(r['ticks']),CHECKS.get(r['check'],r['check']),
                    r['count'],proof_text(r['example'])]
                   for r in sorted(rows, key=lambda r: (r['owner'], r['role'], r['area'] or '', r['ticks']))]))


def ticks_text(ticks):
    runs, start = [], None
    for i, t in enumerate(ticks):
        start = t if start is None else start
        if i+1 == len(ticks) or ticks[i+1] != t+1:
            runs.append(f't{start}' if start == t else f't{start}-{t}')
            start = None
    return ', '.join(runs) or '-'


def solve_text(solve, request_index):
    request = request_index.get(solve['problemHash'], {})
    if solve.get('status') == 'blocked':
        verdict = f'infeasible ({CHECKS.get(solve.get("reason"), solve.get("reason"))})'
    elif request.get('workerStatus') == 'Infeasible_Problem_Detected':
        verdict = 'infeasible (IPOPT local)'
    else:
        verdict = solve.get('status', '?')
    return ((f'request {request["index"]} ' if request else '')+verdict+
            (f', IPOPT {request["workerStatus"]}' if request.get('workerStatus') else '')+
            (f', {request["seconds"]:.2f} s' if request.get('seconds') is not None else ''))


def alternative_outcome(alternative, request_index):
    """An alternative without a solution is infeasible when every attempt was proved infeasible or IPOPT
    reported local infeasibility for all of them; the search then does not retry it."""
    if alternative['proven']:
        return 'infeasible ('+', '.join(sorted({CHECKS.get(r, r) for r in alternative['certificates']}))+')'
    if alternative['oracleRejected']:
        statuses = sorted({request_index[s['problemHash']]['workerStatus'] for s in alternative['solves']
                           if request_index.get(s['problemHash'], {}).get('workerStatus')})
        return 'infeasible (IPOPT local)' if statuses == ['Infeasible_Problem_Detected'] else 'rejected (IPOPT '+', '.join(statuses)+')'
    return alternative['status']


def plural(count, noun):
    return f'{count} {noun}' if count == 1 else f'{count} {noun}s'


def number(value):
    return f'{value:.3f}' if isinstance(value,(int,float)) else str(value)


def rows_text(rows, owners=()):
    parts = {}
    for row in rows:
        if isinstance(row,(list,tuple)):
            tick, axis, cmp, rhs = row[1], row[2], row[3], row[4]
        else:
            tick, axis, cmp, rhs = row.get('t1',row.get('offset')), row['mode'], row['cmp'], row['rhs']
        parts.setdefault((axis,cmp,rhs),[]).append(tick)
    text = []
    for (axis,cmp,rhs),ticks in sorted(parts.items(),key=lambda kv:min(kv[1])):
        names = sorted({o for o,a,c,plane in owners if a == axis and c == cmp and abs(plane-rhs) < 1e-4})
        ticks = sorted(set(ticks))
        spans, start = [], ticks[0]
        for previous, tick in zip(ticks,ticks[1:]+[None]):
            if tick != previous+1:
                spans.append(f't{start}' if start == previous else f't{start}-{previous}')
                start = tick
        text.append(('/'.join(names)+': ' if names else '')+
                    f'{axis} {"≥" if cmp == "GE" else "≤" if cmp == "LE" else "="} {rhs:.3f} at {", ".join(spans)}')
    return '; '.join(text) or 'none'


def region_text(rows):
    bounds = {}
    for row in rows:
        bounds.setdefault(row['mode'],{})[row['cmp']] = row['rhs']
    if not bounds or not set(bounds) <= {'X','Z'} or any(set(v) != {'GE','LE'} for v in bounds.values()):
        return rows_text(rows) if rows else ''
    return ' × '.join(f'{axis} [{bounds[axis]["GE"]:.3f}, {bounds[axis]["LE"]:.3f}]' for axis in sorted(bounds))


def phases_text(phases):
    keys = sorted(3*tick+PHASES.index(sweep) for tick,sweep in phases)
    label = lambda at:f'{at//3}{PHASES[at%3]}'
    spans, start = [], keys[0] if keys else None
    for previous, at in zip(keys,keys[1:]+[None]):
        if at != previous+1:
            spans.append(label(start) if start == previous else f'{label(start)}-{label(previous)}')
            start = at
    return ', '.join(spans)


def proof_text(entry):
    reason = entry.get('reason')
    if reason == 'norm_reach_contradiction':
        return (f'state {entry["state"]} unreachable from state {entry["previousState"]} '
                f'(gap {number(entry["gap"])} > travel {number(entry["travelUpperBound"])})')
    if reason == 'interval_reach_contradiction':
        return f'empty {"XZ"[entry["axis"]]} interval at state {entry["state"]}'
    if reason == 'interaction_row_unreachable':
        required = entry['required']
        return (f'{entry["axis"]} at tick {entry["tick"]} must be {"≥" if required["cmp"] == "GE" else "≤"} '
                f'{required["rhs"]:.3f}, reachable [{entry["reachable"][0]:.3f}, {entry["reachable"][1]:.3f}]')
    if reason == 'interaction_region_unreachable':
        return (f'{entry["axis"]} at tick {entry["tick"]} must lie in [{entry["required"][0]:.3f}, {entry["required"][1]:.3f}], '
                f'reachable [{entry["reachable"][0]:.3f}, {entry["reachable"][1]:.3f}]')
    if reason == 'linear_control_relaxation_infeasible':
        return 'no input sequence satisfies the linearised dynamics and these rows'
    if reason == 'interaction_distance_unreachable':
        return (f'at tick {entry["tick"]} the nearest point is {entry["gap"]:.3f} blocks away, '
                f'travel at most {entry["travelUpperBound"]:.3f}')
    return ', '.join(f'{k} {number(v)}' for k,v in entry.items()
                     if k in ('state','tick','phase','gap','travelUpperBound','dynamicsMargin','constraintMargin'))


def table(header, rows):
    if not rows:
        return []
    return (['| '+' | '.join(header)+' |','|'+'|'.join('---' for _ in header)+'|']+
            ['| '+' | '.join(str(c).replace('|','\\|') for c in row)+' |' for row in rows]+[''])


def support_text(landings):
    spans = []
    for l in landings:
        if spans and spans[-1][0] == l['block'] and spans[-1][2] == l['tick']-1:
            spans[-1][2] = l['tick']
        else:
            spans.append([l['block'],l['tick'],l['tick']])
    text = []
    for block, start, end in spans:
        verb = 'starts on' if start == 0 else 'lands'
        text.append(f'{verb} {block.upper()} t{start}'+(f', runs to t{end}' if end > start else ''))
    return '; '.join(text) or 'no landing'


def movement_text(m):
    parts = ['jumps at t'+', t'.join(map(str,m['jumps'])) if m['jumps'] else 'no jump',support_text(m['landings'])]
    parts.append('hits the ceiling of '+', '.join(f'{c["block"].upper()} t{c["tick"]}' for c in m['ceilings'])
                 if m['ceilings'] else 'no ceiling hit')
    if m['walls']:
        parts.append('touches '+', '.join(f'{w["block"].upper()} {w["side"]} t{w["tick"]}' for w in m['walls']))
    if m['cases']:
        parts.append(', '.join(f'{c["case"]} t{c["tick"]}' for c in m['cases']))
    return '; '.join(parts)


def passages_text(passages):
    if passages is None:
        return '-'
    return '; '.join(f'{p["block"].upper()}: '+' then '.join(p['sides'])+f' (t{p["ticks"][0]}-{p["ticks"][1]})'
                     for p in passages) or 'no obstacle in reach'


def row_owners(repair, goal, passages=None):
    used = {(p['block'],side[i:i+2]) for p in passages or () for side in p['sides'] for i in range(0,len(side),2)}
    faces = [(name.upper(),a,c,plane) for cluster,name,a,c,plane in repair['faceOwners']
             if passages is None or (cluster,a+('+' if c == 'GE' else '-')) in used]
    regions = [(f'{region["owner"].upper()} region',r['mode'],r['cmp'],r['rhs']) for region in repair.get('contactRegions',[])
               for option in region['options'] for r in option]
    return faces+regions


def alternative_text(rows, owners=()):
    if rows is None:
        return '-'
    return rows_text(rows,owners) if rows else 'no avoidance rows'


def render(report, requests, routes, stop):
    result = report.get('result',report)
    best, dfs = result.get('best'), result['searchState']['dfs']
    replays = report.get('independentReplays') or []
    provenance = report.get('provenance') or {}
    request_index = {r['fingerprint']:r for r in requests}
    by_frame = {}
    for r in routes:
        by_frame.setdefault(r['frame'],[]).append(r)
    route_frame = {r['record']:r['frame'] for r in routes}
    numbers = {}
    for fid, group in by_frame.items():
        for i, r in enumerate(group,1):
            numbers[r['record']] = f'{fid}.{i}'
    by_record = {r['record']:r for r in routes}
    def name(record):
        r = by_record[record]
        landed = r['movement']['landings']
        return f'route {numbers[record]} (lands '+(f'{landed[-1]["block"].upper()} t{landed[-1]["tick"]}' if landed else 'nowhere')+')'
    children = {}
    for fid, group in by_frame.items():
        parent = group[0]['parent']
        if parent is not None:
            children.setdefault(route_frame.get(parent),[]).append(fid)
    termination = result['termination']
    lines = [f'# Solve report', '',
             f'Source revision `{provenance.get("sourceRevision",report.get("sourceRevision","unknown"))}`'
             +(f', command `{" ".join(provenance["command"][1:])}`' if provenance.get('command') else '')+'.', '',
             '## Outcome', '']
    lines += table(['Measure','Value'],[
        ['Best route ticks',best['plan']['ticks'] if best else 'none'],
        ['Best objective',best['candidate']['objective'] if best else 'none'],
        ['First full solution',f'{dfs.get("firstFullSolutionSeconds"):.1f} s' if dfs.get('firstFullSolutionSeconds') is not None else 'none'],
        ['Search ended',f'{result["elapsedSeconds"]:.1f} s, `{termination}`'],
        ['Solver requests / worker launches',f'{result["work"].get("solverCalls")} / {result["work"].get("workerLaunches")}'],
        ['Expanded prefixes',result.get('expandedPrefixes')],
        ['Independent replays',f'{sum(v["passed"] for v in replays)}/{len(replays)} passed' if replays else 'not run']])
    lines += [f'The search {TERMINATIONS.get(termination,"ended with `"+termination+"`")}. '
              'A timeout or an unknown solve is not an infeasibility or optimality certificate.','',
              '## Why the search stopped','']
    if stop['suspendedFrames']:
        lines += ['Frames left suspended (not fully explored):','']
        lines += table(['Frame','Goal','Routes','Unresolved alternatives','Pending alternatives','Deferred nodes'],
                       [[f['frame'],f['goal'],f['records'],f['unresolvedBranches'],f['pendingBranches'],f['deferredNodes']]
                        for f in stop['suspendedFrames']])
    else:
        lines += ['No frame was left suspended.','']
    if stop['unknownRequests']:
        lines += ['Solver requests without a solution:','']
        lines += table(['Request','IPOPT status','Counted as','Owning routes'],
                       [[r['index'],r['workerStatus'],
                         'infeasible (IPOPT local)' if r['workerStatus'] == 'Infeasible_Problem_Detected' else 'unknown',
                         ', '.join(f'{name(o["record"])}, {o["node"]}' for o in r['owners']) or 'none kept']
                        for r in stop['unknownRequests']])
    if stop['oracleRejectedRoutes']:
        lines += ['Routes closed because every alternative is infeasible (IPOPT local or a pre-solve proof): '+
                  ', '.join(name(o['record']) for o in stop['oracleRejectedRoutes'])+'.','']
    linear = (result.get('interactionGate') or {}).get('linear')
    if linear is not None:
        total = lambda key: sum(counts.get(key, 0) for counts in linear.values())
        lines += [f'Linear pre-checks: {total("solved")} solved ({total("cacheHits")} more from cache), '
                  f'{total("infeasible")} proved infeasible, {total("undecided")} undecided by the LP solver and counted '
                  f'as not ruled out; {total("ipmRetries")} needed the interior-point retry.','']
    lines += ['## Route tree','','Each frame extends one parent route to the next anchor. Route `F.N` is route N of frame F.','']
    def tree(fid, depth):
        group = by_frame[fid]
        counts = Counter(r['node'] for r in group)
        lines.append('  '*depth+f'- Frame {fid} → {group[0]["goal"].upper()}: {plural(len(group),"route")} ('+
                     ', '.join(f'{n} {k}' for k,n in counts.items())+')'+
                     (f', from {name(group[0]["parent"])}' if group[0]['parent'] in by_record else ''))
        for child in sorted(children.get(fid,[])):
            tree(child, depth+1)
    for fid in sorted(f for f,g in by_frame.items() if g[0]['parent'] is None or route_frame.get(g[0]['parent']) is None):
        tree(fid, 0)
    lines += ['','## Partial routes','',
              'How to read the sections below:','',
              '- **Movement**: the fixed part of a route, its jumps and landings. Routes sharing a movement differ only in '
              'which side of each obstacle they pass.',
              '- **Rows** read `obstacle: axis ≥/≤ value at ticks`, on the player\'s centre at the start of each tick, so a '
              'block edge appears shifted by the 0.3 half-width (B7\'s +X face at 730 is `X ≥ 730.3`). '
              'The prefix names the obstacle whose side the row keeps; a plane shared by two boxes names both.',
              '- **Obstacle**: a group of touching blocks the player cannot pass between, named like `B6+B7`. '
              '**Obstacle check**: every obstacle is paired with every landing segment (the ticks from one landing or jump '
              'to the next). A ground tick on a block is its own segment.',
              '- **Phase**: one sweep of one tick. Minecraft moves the player along Y, then X, then Z each tick and checks '
              'collisions per sweep, so `56X` is the X sweep of input 56.',
              '- **Already clear**: phases where the player\'s reach box cannot touch the obstacle. **Possible phases**: '
              'where it can, so a side must be chosen. **Faces ruled out**: sides the player cannot be on at that phase. '
              '**Vertical clear**: phases where the player is certainly above or below it. **Uncertain**: phases decided '
              'by a named step or grind contact the model cannot settle, left to replay.',
              '- **Sides** (`X+`, `Z-`, a corner such as `X+Z+`, `over`, `under`) are the largest free rectangles around '
              'an obstacle at the player\'s height. A side never runs through a neighbouring block, and a face two flush '
              'blocks share is one side. Sides change when the player rises above or drops below one of the blocks.',
              '- **Decision**: one thing to choose, either the sides to keep around one obstacle or which rectangle of a '
              'contacted face to touch (a face a neighbour clips or two touching blocks merge into one can have '
              'several). **Option**: one choice for one decision. **Alternative**: one '
              'surviving option per decision, combined. Each leg keeps only its best feasible alternative.',
              '- **Family**: alternatives that differ only in when the route switches sides of an obstacle. A switch is '
              'named by the input whose sweep first uses the new side, so that side\'s rows start at the next tick. '
              'The climb solves one switch input, then its neighbours, and stops when neither is better; the inputs it '
              f'never needed are counted as not solved. **Fallbacks**: up to {FALLBACK_LIMIT} other alternatives within '
              f'{FALLBACK_GAP} blocks of the best (same ticks), restored only if the route fails further on.',
              '- Request numbers index `requests.json`.','']
    excluded = generator_exclusions(result)
    for fid in sorted(by_frame):
        group = by_frame[fid]
        first = group[0]
        lines += [f'### Frame {fid}: reach {first["goal"].upper()}'+
                  (f' from {name(first["parent"])}' if first['parent'] in by_record else ' from the start'),'']
        if first['parent']:
            names = Counter(r.get('name','') for r in first['inheritedRows'])
            lines += [f'Inherited {plural(len(first["inheritedRows"]),"constraint row")} from the parent\'s solved route ('+
                      ', '.join(f'{n} {k or "unnamed"}' for k,n in names.most_common())+
                      f'); starting facings seeded from it: {"yes" if first["seededFacings"] else "no"}.','']
        lines += exclusion_lines(excluded.get(fid, []))
        for owner in (r for r in group if r['repair']):
            repair = owner['repair']
            members = [r for r in group if r['repairOwner'] == owner['record']]
            goal = first['goal'].upper()
            lines += [f'#### Movement: {movement_text(owner["movement"])}','',
                      ('This movement produced one route.' if len(members) == 1 else
                       f'{len(members)} routes use this movement: same jumps and landings, different sides of the obstacles.'),'']
            lines += table(['Route','Outcome','Solved ticks','Objective','Obstacles passed','Accepted avoidance rows'],
                [[numbers[r['record']],r['node'],r['solvedTicks'] if r['solvedTicks'] is not None else '-',
                  number(r['objective']) if r['objective'] is not None else '-',
                  passages_text(r['passages']),
                  alternative_text(r['acceptedAlternative'],row_owners(repair,goal,r['passages']))] for r in members])
            prepared = repair['preparedEncounters']
            interacting = [e for e in prepared if e['possiblePhases']]
            lines += [f'Obstacle checks: {len(prepared)}; {len(prepared)-len(interacting)} cannot touch the player anywhere in '
                      f'their segment. A check is repeated when the rows chosen so far change what is reachable.','']
            lines += table(['Obstacle','Segment ticks','Possible phases','Already clear','Faces ruled out','Vertical clear','Uncertain'],
                [[e['owner'],f'{e["segment"][0]}-{e["segment"][1]}',phases_text(e['possiblePhases']) or 'none',
                  len(e['coveredPhases']),
                  '; '.join(f'{f} at {phases_text([x["phase"] for x in e["excludedFaces"] if x["face"] == f])}'
                            for f in sorted({x['face'] for x in e['excludedFaces']})) or 'none',
                  phases_text([x['phase'] for x in e['verticalClear']]) or 'none',
                  phases_text(e['uncertain']) or 'none'] for e in interacting])
            decisions = sorted({(e['owner'],e['segment'][0],e['segment'][1]) for e in interacting})
            contact_regions = repair.get('contactRegions',[])
            if decisions or contact_regions:
                lines += ['Decisions:','']+[f'- which sides of {o.upper()} to keep (segment t{a}-{b})' for o,a,b in decisions]+[
                    f'- which of {r["owner"].upper()}\'s {len(r["options"])} face rectangles to touch at '
                    f't{", t".join(map(str,r["ticks"]))}: '+' or '.join(region_text(o) for o in r['options'])
                    for r in contact_regions]+['']
            rejected = repair['screenRejections']
            lines += [f'Options rejected before solving: {len(rejected)}'+
                      (' ('+', '.join(f'{n} {CHECKS.get(k,k)}' for k,n in Counter(e['reason'] for e in rejected).most_common())+')' if rejected else '')+'.','']
            lines += table(['#','Decision','Option','Check','Detail'],
                [[i,(e['encounter'].upper() if e['encounter'].endswith(' region') else f'{e["encounter"].upper()} sides')+
                    (f' t{e["segment"][0]}' if e['segment'][0] == e['segment'][1] else f' t{e["segment"][0]}-{e["segment"][1]}')
                  if e.get('segment') else e.get('encounter','whole branch'),
                  (region_text(e['optionRows']) if e['encounter'].endswith('region') else rows_text(e['optionRows'],row_owners(repair,goal)))
                  if 'optionRows' in e else 'the rows already selected',
                  CHECKS.get(e['reason'],e['reason']),proof_text(e)] for i,e in enumerate(rejected,1)])
            dropped = repair['droppedOptions']
            if dropped:
                lines += [f'Options dropped without solving: {len(dropped)} ('+
                          ', '.join(f'{n} {k}' for k,n in Counter(d['reason'] for d in dropped).most_common())+').','']
                lines += table(['#','Decision','Reason','Option'],
                    [[i,f'{(d["owner"] or "cluster "+str(d["clusterId"])).upper()} sides t{d["segment"][0]}-{d["segment"][1]}',
                      d['reason'],rows_text(d['rows'],row_owners(repair,goal))]
                     for i,d in enumerate(dropped,1)])
            choice = interacting or any(a['rows'] for a in repair['alternatives'])
            lines += [f'Alternatives solved: {len(repair["alternatives"])}. Each combines one surviving option per decision; '
                      'the best feasible one is kept.' if choice else
                      'No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.','']
            lines += table(['#','Rows the alternative adds' if choice else 'Rows added','Outcome','Scene validation','Solves'],
                [[i,alternative_text(a['rows'],row_owners(repair,goal)),alternative_outcome(a,request_index),a['validation'] or '-',
                  '; '.join(solve_text(s,request_index) for s in a['solves'])]
                 for i,a in enumerate(repair['alternatives'],1)])
            timed = [request_index[h]['seconds'] for h in {s['problemHash'] for a in repair['alternatives'] for s in a['solves']}
                     if request_index.get(h,{}).get('seconds') is not None]
            if timed:
                lines += [f'Solver time for this movement: {sum(timed):.2f} s over {plural(len(timed),"request")}.','']
            if repair['families']:
                index = {a['branch']:i for i,a in enumerate(repair['alternatives'],1)}
                lines += table(['Family','Switches solved','Best','Not solved'],
                    [[f['sides'],'; '.join(family_point(p,index) for p in f['points'] if p['status'] != 'held') or '-',
                      next((family_point(p,index) for p in sorted((p for p in f['points'] if p['rank'] is not None),
                                                                 key=lambda p:p['rank'])),'none feasible'),
                      sum(p['held'] for p in f['points'])] for f in repair['families']])
                kept = f'Kept: alternative #{index[repair["kept"]]}' if repair['kept'] in index else 'Kept: none'
                if repair['fallbacks'] is None:
                    lines += [kept+' (the last leg keeps no fallbacks).','']
                else:
                    lines += [kept+'; fallbacks: '+(', '.join(f'alternative #{index[b]}' for b in repair['fallbacks'] if b in index)
                                                    or 'none')+'.','']
            for label, branches in (('Unresolved',repair['unresolved']),('Pending',repair['pending'])):
                if branches:
                    lines += [f'{label} alternatives (kept for an explicit retry):','']+[f'- {rows_text(b,row_owners(repair,goal))}' for b in branches]+['']
        follow = sorted(children.get(fid,[]))
        if follow:
            lines += ['Next partial solves built from this frame: '+
                      ', '.join(f'frame {c} from {name(by_frame[c][0]["parent"])}' for c in follow)+'.','']
    goals = {f['id']: f['goal'].get('block') for f in frames(result['searchState'])}
    for fid in sorted((f for f in excluded if f not in by_frame), key=lambda f: (f is None, f)):
        lines += [f'### Frame {fid}: reach {(goals.get(fid) or "?").upper()}, no route' if fid is not None else
                  '### Generator checks outside the frames','']+exclusion_lines(excluded[fid])
    return '\n'.join(lines)+'\n'


def write(directory, report, requests):
    state = report.get('result',report)['searchState']
    owners = request_owners(state)
    for request in requests:
        request['owners'] = owners.get(request['fingerprint'],[])
    routes = route_trace(state,report.get('result',report).get('effectiveRequest'))
    stop = stop_report(state,requests)
    save(Path(directory)/'routes.json',routes)
    (Path(directory)/'report.md').write_text(render(report,requests,routes,stop))
    return stop


def main():
    parser = argparse.ArgumentParser(description='Write routes.json and report.md for a finished solve.')
    parser.add_argument('source',type=Path,help='profiled run directory, or a saved solve report')
    parser.add_argument('--output',type=Path,help='directory for routes.json and report.md (default: the run directory)')
    args = parser.parse_args()
    run_dir = args.source if args.source.is_dir() else None
    report = read_report(run_dir/'report.json.gz' if run_dir else args.source)
    requests_path = run_dir/'requests.json' if run_dir else None
    requests = json.loads(requests_path.read_text()) if requests_path and requests_path.exists() else []
    output = args.output or run_dir or args.source.parent
    output.mkdir(parents=True,exist_ok=True)
    write(output,report,requests)
    print(output/'report.md')


if __name__ == '__main__':
    main()
