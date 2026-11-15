import collections
import csv
import gzip
import hashlib
import itertools
import json
from pathlib import Path

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.cell_routes.geometry import decompose
from standalone.autonomy.cell_routes.routes import corridor_for_points, landmarks


ROOT = Path(__file__).resolve().parent
OUT = ROOT / 'audit'


def read(name):
    with gzip.open(ROOT / 'evidence' / (name + '.json.gz'), 'rt') as handle:
        return json.load(handle)


def save(name, value):
    path = OUT / name
    if path.suffix == '.gz':
        with gzip.open(path, 'wt') as handle:
            json.dump(value, handle)
    else:
        path.write_text(json.dumps(value, indent=2) + '\n')


def csv_file(name, rows):
    with (OUT / name).open('w', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]), lineterminator='\n')
        writer.writeheader()
        writer.writerows({k: json.dumps(v) if isinstance(v, (list, dict)) else v for k, v in row.items()} for row in rows)


def ranges(values):
    groups = []
    for _, group in itertools.groupby(enumerate(values), lambda x: x[1]-x[0]):
        members = [v for _, v in group]
        groups.append(str(members[0]) if len(members) == 1 else f'{members[0]}..{members[-1]}')
    return ','.join(groups) or '-'


def main():
    OUT.mkdir(exist_ok=True)
    fixture = json.loads((ROOT / 'fixtures/clock64-event-priority.json').read_text())
    scene = prepare_scene(fixture['request'])
    boxes = {tuple(box): b['id'] for b in fixture['request']['blocks'] for box in b['boxes']}
    decomposition = decompose(scene)
    gates = landmarks(scene)
    corridor = corridor_for_points(decomposition, fixture['spatialPoints'], gates)
    geometry = decomposition.to_dict()
    geometry['statistics'].pop('elapsedSeconds')
    geometry.update(corridor=corridor.to_dict(), gates=gates)
    save('cells-and-route.json', geometry)

    def decisions(program):
        p = program['profile']
        return dict(jumps=[i for i, jump in enumerate(p['jump']) if jump],
                    groundNoJump=[i for i in range(p['airtime']) if p['grounded'][i] and not p['jump'][i]],
                    contacts=[dict(state=e['offset']+1, block=boxes.get(tuple(e['box']), '?'), axis=e['axis'],
                                   direction=e['direction'], role=e.get('role')) for e in p.get('contactEvents', [])],
                    cases=p.get('caseEvents', []), terminalSupport=program.get('terminalSupport'),
                    inputTicks=p['airtime'])

    ledger, snapshots, totals, windows = [], [], [], []
    def search(run, result):
        spent = 0
        for wi, window in enumerate(result['windows']):
            h = window['checkpoint']['tick']
            labels = {c['id']: f'{run}/H{h}-{i+1:02}' for i, c in enumerate(window['candidates'])}
            for i, c in enumerate(window['candidates']):
                p = c['decision_program']
                spatial = c.get('spatial_probe', {})
                clock_calls = c['work'].get('solverCalls', 0)
                spatial_calls = spatial.get('measurement', {}).get('work', {}).get('solverCalls', 0)
                spent += clock_calls + spatial_calls
                selection = window['retention']['decisions'][c['id']]
                row = dict(run=run, label=labels[c['id']], horizon=h, id=c['id'], parent=c.get('parent'),
                           **decisions(p), status=c['status'], termination=c['termination'],
                           clockProgress=[v.get('objective') for v in c['clock_results'].values()],
                           spatialGoal=[v.get('goal') for v in c['spatial_results'].values()],
                           spatialTimes=[v.get('ticks') for v in c['spatial_results'].values()],
                           clockCalls=clock_calls, spatialCalls=spatial_calls,
                           selection=selection['reason'], peer=selection.get('peer'),
                           retainedRank=window['retained'].index(c['id'])+1 if c['id'] in window['retained'] else None)
                ledger.append(row)
                snapshots.append(dict(label=row['label'], program=p,
                    carriedProgram=c['program'], bestWitness=c.get('witness'),
                    spatialBestProgram=spatial.get('program'), spatialBest=spatial.get('best'),
                    spatialAttemptSummaries=spatial.get('measurement', {}).get('attempts', [])))
            windows.append(dict(run=run, horizon=h, candidates=len(window['candidates']),
                clockAccepted=window['scored'], clockUnknown=window['unknown'], retention=window['retention'],
                retainedLabels=[labels[key] for key in window['retained']], generation=window['generation']))
        for i, c in enumerate(result['finalAttempts']):
            p = c['decision_program']
            calls = c['work'].get('solverCalls', 0)
            spent += calls
            ledger.append(dict(run=run, label=f'{run}/FINAL-{i+1:02}', horizon=p['profile']['airtime'],
                id=c['id'], parent=c.get('parent'), **decisions(p), status=c['status'], termination=c['termination'],
                clockProgress=[v.get('objective') for v in c['clock_results'].values()], spatialGoal=[], spatialTimes=[],
                clockCalls=calls, spatialCalls=0, selection='final_evaluated', peer=None, retainedRank=None))
            snapshots.append(dict(label=ledger[-1]['label'], program=p, bestWitness=c.get('witness')))
        for i, rescue in enumerate(result['rescues']):
            spent += search(f'{run}/RESCUE-{i+1}', rescue['result'])
        assert spent == result['work'].get('solverCalls', 0), (run, spent, result['work'])
        return spent

    for name in ('real11-clock64-first', 'real11-clock64-near-goals', 'real11-clock64-event-priority', 'real11-clock64-resumed'):
        data = read(name)
        calls = search(name, data['result'])
        totals.append(dict(run=name, solverCalls=calls,
            unevaluated=[dict(id=u['id'], window=u['window'], selection=u['selection'],
                              decisions=decisions(u['program'])) for u in data['result']['unevaluatedCandidates']],
            termination=data['result']['termination']))
    csv_file('candidate-ledger.csv', ledger)
    save('candidate-programs.json.gz', snapshots)
    save('windows-and-generation.json', windows)
    save('call-accounting.json', totals)

    detailed, branch_attempts, branch_sets = [], [], []
    def detail(label, program, result):
        detailed.append(dict(label=label, decisions=decisions(program), status=result['status'],
            termination=result['termination'], work=result['work'],
            objective=result['best']['candidate']['objective'] if result['best'] else None))
        outer, inner = result.get('contactAttempts', []), result.get('attempts', [])
        aligned = len(outer) == len(inner)
        for i, attempt in enumerate(outer):
            numerical = inner[i] if aligned else {}
            branch_attempts.append(dict(evaluation=label, branch=i+1,
                repairRows=attempt['constraintCount'], passages=attempt['passageCount'],
                rawStatus=numerical.get('status'), problemHash=numerical.get('problemHash'),
                numericalCalls=len(numerical.get('diagnostics', [])), outerStatus=attempt['status'],
                termination=attempt['termination'], filterReason=attempt.get('candidateFilter', {}).get('reason'),
                geometry=attempt.get('geometryCheck'), conflicts=attempt.get('conflicts', []),
                certificate=numerical.get('certificate')))
        state = result.get('repairState', {})
        def key(branch):
            from standalone.autonomy.repair_state import branch_key
            return branch_key(branch['rows'], branch['passages'], branch['hits'])
        pending = {key(b) for b in state.get('pending', [])}
        unresolved = {key(b) for b in state.get('unresolved', [])}
        incumbent = key(state['incumbent']) if state.get('incumbent') else None
        branch_sets.append(dict(label=label, context=state.get('context'),
            branches=[dict(id=hashlib.sha256(k.encode()).hexdigest(), selection=json.loads(k),
                           pending=k in pending, unresolved=k in unresolved, incumbent=k == incumbent)
                      for k in state.get('seen', [])],
            note='Seen keys store exact unique rows/face gates/hits, not chronological pop order. Duplicate rows and names are normalized away.'))

    constructive = read('real11-constructive-probe')
    for run in constructive['runs']:
        first = run['initialTakeoff'][0]
        for stage in run['stages']:
            detail(f'constructive/start{first}/{stage["target"]}/delay{stage["delay"]}', stage['program'], stage['result'])
        for i, final in enumerate(run['finals']):
            detail(f'constructive/start{first}/final{i+1}', final['program'], final['result'])
    terminal = read('real11-terminal-variants')
    for run in terminal['runs']:
        detail(f'terminal/start{run["initialTakeoff"][0]}/jump{run["jumpAfterB5"]}', run['program'], run['result'])
    csv_file('detailed-evaluations.csv', detailed)
    csv_file('repair-attempts.csv', branch_attempts)
    save('detailed-repair-branches.json.gz', branch_sets)

    winner = terminal['runs'][0]
    best = winner['result']['best']
    save('winning-fixed-call.json', dict(program=winner['program'], route=best['route'], plan=best['plan'],
        profiles=best['profiles'], problemHash=best['problemHash'],
        context=winner['result']['repairState']['context'],
        selectedRepair=winner['result']['repairState']['incumbent']))
    visits = [tuple(decomposition.locate((xz[0], best['candidate']['y'][t], xz[1]), 1e-7))
              for t, xz in enumerate(best['candidate']['positions'])]
    save('winning-cell-visits.json', [dict(first=group[0][0], last=group[-1][0], cells=ids)
        for ids, items in itertools.groupby(enumerate(visits), lambda x:x[1]) if (group := list(items))])

    lines = ['# Saved candidate combinations and retention', '',
        'Generated from the archived event-priority run, without rerunning IPOPT. Full exact programs and parent IDs are in candidate-programs.json.gz and candidate-ledger.csv. `J` lists jump input ticks; `R` lists grounded inputs with no jump. Contacts show resulting state ticks. Repeated ordinary B1 ground-contact entries before the first jump are compressed out of this table only. `F` means the outer scalar/support/corridor/scene checks accepted the returned fixed-clock witness; `U` is unknown, not proven infeasible. Calls are fixed-clock + spatial-probe numerical calls. No scalar-facing values are committed.', '']
    for window in [w for w in windows if w['run'] == 'real11-clock64-event-priority']:
        h = window['horizon']
        lines.extend([f'## State {h}', '', '| Candidate | J | R | Nontrivial contacts | Clock | Progress | Goal time | Calls | Retention |',
                      '| --- | --- | --- | --- | --- | ---: | ---: | --- | --- |'])
        for row in [r for r in ledger if r['run'] == 'real11-clock64-event-priority' and r['horizon'] == h]:
            first_jump = min(row['jumps'], default=row['inputTicks'])
            contacts = [e for e in row['contacts'] if e['block'] != 'b1' or e['state'] > first_jump]
            contact_text = ','.join(e['block'].upper()+'@'+str(e['state']) for e in contacts) or '-'
            p = row['clockProgress'][0]
            time = row['spatialTimes'][0]
            alias = row['label'].split('/')[-1]
            lines.append(f'| {alias} | {ranges(row["jumps"])} | {ranges(row["groundNoJump"])} | {contact_text} | {"F" if row["status"] == "feasible" else "U"} | {p:.5f} | {time if time is not None else "?"} | {row["clockCalls"]}+{row["spatialCalls"]} | {row["selection"]} |' if p is not None else
                         f'| {alias} | {ranges(row["jumps"])} | {ranges(row["groundNoJump"])} | {contact_text} | U | ? | {time if time is not None else "?"} | {row["clockCalls"]}+{row["spatialCalls"]} | {row["selection"]} |')
        lines.extend(['', 'Retained, in next-window parent order: '+', '.join(x.split('/')[-1] for x in window['retainedLabels'])+'.', ''])
    (OUT / 'CANDIDATES.md').write_text('\n'.join(lines).rstrip() + '\n')
    cells = ['# Reconstructed cells', '',
        'All33 cells from the exact archived fixture, using unchanged decomposition code. Bounds are player feet/center coordinates, rounded to six decimals here. Full precision, all92 portals and247 contact patches are in cells-and-route.json. Guide means selected by the hand-supplied polyline, not mandatory visitation by every accepted numerical trajectory.', '',
        '| Cell | Dimension | X | Y | Z | Guide |', '| --- | ---: | --- | --- | --- | --- |']
    for c in decomposition.cells:
        b = c.bounds
        intervals = [f'[{b[a]:.6f}, {b[a+3]:.6f}]' for a in range(3)]
        cells.append(f'| C{c.id} | {c.dimension} | {intervals[0]} | {intervals[1]} | {intervals[2]} | {"yes" if c.id in corridor.cells else ""} |')
    (OUT / 'CELLS.md').write_text('\n'.join(cells) + '\n')
    repair_lines = ['# Winning terminal evaluation: every recorded repair attempt', '',
        'Evaluation terminal/start0/jumpTrue. Each row is one outer branch pop, with its aligned scalar allocation attempt. Zero calls means screened before IPOPT. Rows is the count of added repair rows before deduplication, excluding the compiled movement/support/contact rows. The exact selected call is in winning-fixed-call.json. See detailed-repair-branches.json.gz for every offered unique row set; its chronology is not recorded.', '',
        '| Branch | Rows | Calls | Scalar status | Outer result | Reason |', '| --- | ---: | ---: | --- | --- | --- |']
    for a in branch_attempts:
        if a['evaluation'] == 'terminal/start0/jumpTrue':
            reason = a['filterReason'] or ('scene_collision at input '+str(a['geometry']['tick']) if a['geometry'] and not a['geometry'].get('feasible') else a['termination'])
            repair_lines.append(f'| {a["branch"]} | {a["repairRows"]} | {a["numericalCalls"]} | {a["rawStatus"]} | {a["outerStatus"]} | {reason} |')
    (OUT / 'FINAL_REPAIRS.md').write_text('\n'.join(repair_lines) + '\n')
    counts = collections.Counter(row['selection'] for row in ledger)
    print(json.dumps(dict(candidateRows=len(ledger), detailedEvaluations=len(detailed),
        repairAttempts=len(branch_attempts), controlCalls=sum(t['solverCalls'] for t in totals), selections=counts)))


if __name__ == '__main__':
    main()
