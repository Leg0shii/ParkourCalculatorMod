"""Trace a finished solve per partial route and render it as a Markdown report."""

import argparse
from collections import Counter
import json
from pathlib import Path

from parkour_solver.constraints.encounters import PHASES
from parkour_solver.io.checkpoint_store import read_report
from parkour_solver.io.reports import save


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
        f = entry['faces'][i]
        return f['axis']+('+' if f['direction'] > 0 else '-')
    def phase(at):
        return [at//3,PHASES[at%3]]
    return dict(owner=entry['owner'],solidIndex=entry['solidIndex'],segment=entry['segment'],
                possiblePhases=entry['possiblePhases'],
                coveredPhases=[dict(phase=phase(at),certainFaces=[face(i) for i in certain])
                               for at,certain in entry['coveredPhases']],
                excludedFaces=[dict(phase=phase(at),face=face(i)) for at,i in entry['excludedFaces']],
                verticalClear=[dict(phase=phase(at),face=face(i)) for at,i in entry['verticalClear']],
                uncertain=[phase(at) for at in entry['uncertain']])


def item_label(item):
    if 'box' in item:
        return item.get('owner'), [item['begin'],item['end']]
    if 'contactCheck' in item:
        return 'contact check', [item['contactCheck']['offset']]*2
    rows = [r for option in item.get('rows',[]) for r in option]
    ticks = [r.get('t1',r.get('offset')) for r in rows]
    name = rows[0].get('name','') if rows else ''
    if item.get('role') == 'required_contact':
        name = 'landing region'
    return (name.split(':')[1] if name.startswith('event:') else name or 'route guard'), [min(ticks),max(ticks)] if ticks else None


def repair_trace(repair):
    generation = repair.get('constraintGeneration') or {}
    attempts = {a['problemHash']:dict(status=a['status'],reason=(a.get('certificate') or {}).get('reason'))
                for a in repair.get('attemptLog',[])}
    items = generation.get('items',[])
    return dict(
        encounters=[dict(owner=i['owner'],box=i['box'],begin=i['begin'],end=i['end'])
                    for i in items if 'box' in i],
        contactChecks=sum('contactCheck' in i for i in items),
        landingRegions=[dict(ticks=sorted({r.get('t1',r.get('offset')) for option in i['rows'] for r in option}),
                             options=i['rows']) for i in items if i.get('role') == 'required_contact'],
        generatorMetrics=generation.get('metrics',{}),
        preparedEncounters=[encounter_trace(e) for e in generation.get('encounterLog',[])],
        droppedOptions=[dict(d,owner=next((i['owner'] for i in items if i.get('solidIndex') == d['solidIndex']),None))
                        for d in generation.get('droppedOptions',[])],
        screenRejections=[dict(rowsHash=e['rowsHash'],**e['proof'],
                               **(dict(zip(('encounter','segment'),item_label(items[e['item']])),
                                       optionRows=e['optionRows'],passage=e['passage'])
                                  if 'item' in e else {}))
                          for e in generation.get('screenExclusions',[])],
        alternatives=[dict(rows=json.loads(c['branch'])[0],status=c['status'],validation=c.get('validationStatus'),
                           solves=[dict(problemHash=h,**attempts.get(h,{})) for h in c.get('problemHashes',[])])
                      for c in repair.get('completed',[])],
        unresolved=[b['rows'] for b in repair.get('unresolved') or []],
        pending=[b['rows'] for b in repair.get('pending') or []])


def block_names(scene):
    return {tuple(box):block['id'] for block in (scene or {}).get('blocks',[]) for box in block.get('boxes',[])}


def movement(profile, names):
    def where(event):
        return dict(block=event.get('blockId') or names.get(tuple(event.get('box') or ()),'unnamed box'),
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
    return face['axis']+('+' if face['direction'] > 0 else '-')


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
                repair=repair_trace(record['program']['repairState']) if record is owner else None))
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
    interaction_row_unreachable='single-row reachability',
    interaction_region_unreachable='region reachability',
    interaction_distance_unreachable='distance reachability',
    contact_reach_contradiction='contact reachability')


def plural(count, noun):
    return f'{count} {noun}' if count == 1 else f'{count} {noun}s'


def number(value):
    return f'{value:.3f}' if isinstance(value,(int,float)) else str(value)


def rows_text(rows):
    parts = {}
    for row in rows:
        if isinstance(row,(list,tuple)):
            tick, axis, cmp, rhs = row[1], row[2], row[3], row[4]
        else:
            tick, axis, cmp, rhs = row.get('t1',row.get('offset')), row['mode'], row['cmp'], row['rhs']
        parts.setdefault((axis,cmp,rhs),[]).append(tick)
    text = []
    for (axis,cmp,rhs),ticks in sorted(parts.items(),key=lambda kv:min(kv[1])):
        ticks = sorted(set(ticks))
        spans, start = [], ticks[0]
        for previous, tick in zip(ticks,ticks[1:]+[None]):
            if tick != previous+1:
                spans.append(f't{start}' if start == previous else f't{start}-{previous}')
                start = tick
        text.append(f'{axis} {"≥" if cmp == "GE" else "≤" if cmp == "LE" else "="} {rhs:.3f} at {", ".join(spans)}')
    return '; '.join(text) or 'none'


def region_text(rows):
    bounds = {}
    for row in rows:
        bounds.setdefault(row['mode'],{})[row['cmp']] = row['rhs']
    if set(bounds) != {'X','Z'} or any(set(v) != {'GE','LE'} for v in bounds.values()):
        return rows_text(rows)
    return ' × '.join(f'{axis} [{bounds[axis]["GE"]:.3f}, {bounds[axis]["LE"]:.3f}]' for axis in ('X','Z'))


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


def alternative_text(rows):
    if rows is None:
        return '-'
    return rows_text(rows) if rows else 'no avoidance rows'


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
        lines += ['Solver requests that came back `unknown`:','']
        lines += table(['Request','IPOPT status','Owning routes'],
                       [[r['index'],r['workerStatus'],', '.join(f'{name(o["record"])}, {o["node"]}'
                                                              for o in r['owners']) or 'none kept']
                        for r in stop['unknownRequests']])
    if stop['oracleRejectedRoutes']:
        lines += ['Routes closed as oracle-rejected (IPOPT reported every alternative infeasible): '+
                  ', '.join(name(o['record']) for o in stop['oracleRejectedRoutes'])+'.','']
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
              '- **Rows** read `axis ≥/≤ value at ticks`, on the player position at the start of each tick.',
              '- **Obstacle check**: every solid is paired with every landing segment (the ticks from one landing or jump '
              'to the next). A ground tick on a block is its own segment.',
              '- **Phase**: one sweep of one tick. Minecraft moves the player along Y, then X, then Z each tick and checks '
              'collisions per sweep, so `56X` is the X sweep of input 56.',
              '- **Already clear**: phases where the player\'s reach box cannot touch the obstacle. **Possible phases**: '
              'where it can, so a side must be chosen. **Faces ruled out**: sides the player cannot be on at that phase. '
              '**Vertical clear**: phases where the player is certainly above or below it. **Uncertain**: phases decided '
              'by a named step or grind contact the model cannot settle, left to replay.',
              '- **Sides** (`X+`, `Z-`, `over`, `under`) are sides of each solid\'s own box that keep the player clear of '
              'it. They ignore neighbouring blocks, so a side covered by an adjacent block can still appear.',
              '- **Decision**: one thing to choose, either the sides to keep around one obstacle or which landing-cover '
              'rectangle of the goal block to land in. **Option**: one choice for one decision. **Alternative**: one '
              'surviving option per decision, combined; each is solved, and each feasible one becomes a route.',
              '- Request numbers index `requests.json`.','']
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
        for owner in (r for r in group if r['repair']):
            repair = owner['repair']
            members = [r for r in group if r['repairOwner'] == owner['record']]
            lines += [f'#### Movement: {movement_text(owner["movement"])}','',
                      ('This movement produced one route.' if len(members) == 1 else
                       f'{len(members)} routes use this movement: same jumps and landings, different sides of the obstacles.'),'']
            lines += table(['Route','Outcome','Solved ticks','Objective','Obstacles passed','Accepted avoidance rows'],
                [[numbers[r['record']],r['node'],r['solvedTicks'] if r['solvedTicks'] is not None else '-',
                  number(r['objective']) if r['objective'] is not None else '-',
                  passages_text(r['passages']),alternative_text(r['acceptedAlternative'])] for r in members])
            prepared = repair['preparedEncounters']
            interacting = [e for e in prepared if e['possiblePhases']]
            goal = first['goal'].upper()
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
            regions = [r for r in repair['landingRegions'] if len(r['options']) > 1]
            if decisions or regions:
                lines += ['Decisions:','']+[f'- which sides of {o.upper()} to keep (segment t{a}-{b})' for o,a,b in decisions]+[
                    f'- which of {goal}\'s {len(r["options"])} landing-cover rectangles to land in at '
                    f't{", t".join(map(str,r["ticks"]))}: '+' or '.join(region_text(o) for o in r['options']) for r in regions]+['']
            rejected = repair['screenRejections']
            lines += [f'Options rejected before solving: {len(rejected)}'+
                      (' ('+', '.join(f'{n} {CHECKS.get(k,k)}' for k,n in Counter(e['reason'] for e in rejected).most_common())+')' if rejected else '')+'.','']
            lines += table(['#','Decision','Option','Check','Detail'],
                [[i,(f'{goal} landing region' if e['encounter'] == 'landing region' else f'{e["encounter"].upper()} sides')+
                    (f' t{e["segment"][0]}' if e['segment'][0] == e['segment'][1] else f' t{e["segment"][0]}-{e["segment"][1]}')
                  if e.get('segment') else e.get('encounter','whole branch'),
                  (region_text(e['optionRows']) if e['encounter'] == 'landing region' else rows_text(e['optionRows']))
                  if 'optionRows' in e else 'the rows already selected',
                  CHECKS.get(e['reason'],e['reason']),proof_text(e)] for i,e in enumerate(rejected,1)])
            dropped = repair['droppedOptions']
            if dropped:
                lines += [f'Options dropped without solving: {len(dropped)} ('+
                          ', '.join(f'{n} {k}' for k,n in Counter(d['reason'] for d in dropped).most_common())+').','']
                lines += table(['#','Decision','Reason','Option'],
                    [[i,f'{(d["owner"] or "solid "+str(d["solidIndex"])).upper()} sides t{d["segment"][0]}-{d["segment"][1]}',
                      d['reason'],rows_text(d['rows'])]
                     for i,d in enumerate(dropped,1)])
            choice = interacting or any(a['rows'] for a in repair['alternatives'])
            lines += [f'Alternatives solved: {len(repair["alternatives"])}. Each combines one surviving option per decision; '
                      'each feasible one becomes a route.' if choice else
                      'No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.','']
            lines += table(['#','Rows the alternative adds' if choice else 'Rows added','Outcome','Scene validation','Solves'],
                [[i,alternative_text(a['rows']),a['status'],a['validation'] or '-',
                  '; '.join((f'request {request_index[s["problemHash"]]["index"]} ' if s['problemHash'] in request_index else '')+
                            s.get('status','?')+
                            (f' ({s["reason"]})' if s.get('reason') else '')+
                            (f', IPOPT {request_index[s["problemHash"]]["workerStatus"]}'
                             if request_index.get(s['problemHash'],{}).get('workerStatus') else '')
                            for s in a['solves'])]
                 for i,a in enumerate(repair['alternatives'],1)])
            for label, branches in (('Unresolved',repair['unresolved']),('Pending',repair['pending'])):
                if branches:
                    lines += [f'{label} alternatives (kept for an explicit retry):','']+[f'- {rows_text(b)}' for b in branches]+['']
        follow = sorted(children.get(fid,[]))
        if follow:
            lines += ['Next partial solves built from this frame: '+
                      ', '.join(f'frame {c} from {name(by_frame[c][0]["parent"])}' for c in follow)+'.','']
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
