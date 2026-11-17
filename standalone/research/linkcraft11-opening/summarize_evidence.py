import csv
import gzip
import hashlib
import importlib.metadata
import json
import platform
from pathlib import Path


ROOT = Path(__file__).resolve().parent
EVIDENCE = ROOT / 'evidence'
NUMERICAL_SOURCE = 'f21d58eaed448656b85ae43e98c307da31efbe50'


def read(name):
    with gzip.open(EVIDENCE / (name + '.json.gz'), 'rt') as handle:
        return json.load(handle)


def write(name, value):
    (ROOT / name).write_text(json.dumps(value, indent=2) + '\n')


def main():
    from standalone.autonomy.block_search import prepare_scene
    from standalone.autonomy.cell_routes.support_order import SupportOrder

    terminal = read('real11-terminal-variants')
    accepted = [r for r in terminal['runs'] if r['result']['best']]
    winner = max(accepted, key=lambda r: r['result']['best']['candidate']['objective'])
    best = winner['result']['best']
    candidate = best['candidate']
    scene = prepare_scene(terminal['fixture']['request'])
    policy = SupportOrder(scene, terminal['fixture']['requiredSupports'], 64)
    far_edge = -1094 + scene['collisionScene'].get('halfWidth', .30000001192092896)
    summary = dict(
        status='feasible', objectiveOptimalityProven=False, tick=64,
        objective=dict(axis='Z', sense='MAX', value=candidate['objective']),
        position=[candidate['positions'][-1][0], candidate['y'][-1], candidate['positions'][-1][1]],
        velocity=[candidate['velocity'][-1][0], candidate['vy'][-1], candidate['velocity'][-1][1]],
        onGround=candidate['onGround'][-1], scalarViolation=candidate['violation'],
        terminalSupportRequired=best['route']['terminalSupportRequired'],
        b7FarPlayerCenterEdge=far_edge,
        beyondB7FarPlayerCenterEdge=candidate['objective'] - far_edge,
        jumpInputTicks=[t for t, jump in enumerate(winner['program']['profile']['jump']) if jump],
        supportOrder=policy.audit(winner['program']),
        independentReplay=winner['replay'],
        source='evidence/real11-terminal-variants.json.gz',
        sourceCommit=NUMERICAL_SOURCE,
        termination=winner['result']['termination'],
        limitations=['one prescribed support-order spatial route', 'finite nonexhaustive continuation probes',
                     'normal carried momentum with zero inertia thresholds', 'standalone shared-scene validation; no game replay'])
    write('solution-summary.json', summary)
    with gzip.open(ROOT / 'solution.json.gz', 'wt') as handle:
        json.dump(dict(summary=summary, fixture=terminal['fixture'], best=best,
                       movementProgram=winner['program']), handle)
    with (ROOT / 'trajectory.csv').open('w', newline='') as handle:
        writer = csv.writer(handle, lineterminator='\n')
        writer.writerow(['state_tick', 'x', 'y', 'z', 'vx', 'vy', 'vz', 'on_ground', 'yaw_input', 'jump_input'])
        for t, (x, z) in enumerate(candidate['positions']):
            vx, vz = candidate['velocity'][t]
            writer.writerow([t, x, candidate['y'][t], z, vx, candidate['vy'][t], vz,
                             candidate['onGround'][t], candidate['yaw'][t] if t < 64 else '',
                             winner['program']['profile']['jump'][t] if t < 64 else ''])

    control = read('real11-clock64-event-priority')
    first = control['result']['windows'][0]
    counterexample = []
    constructive = read('real11-constructive-probe')
    for takeoff in (0, 8, 9):
        record = next(c for c in first['candidates']
                      if [t for t, j in enumerate(c['program']['profile']['jump']) if j] == [takeoff])
        complete = next(r for r in accepted if r['initialTakeoff'] == [takeoff])
        stages = next(r for r in constructive['runs'] if r['initialTakeoff'] == [takeoff])
        counterexample.append(dict(
            initialTakeoff=takeoff, candidateId=record['id'], retention=first['retention']['decisions'][record['id']],
            clock10=[dict(status=v['status'], objective=v.get('objective')) for v in record['clock_results'].values()],
            spatialB2=[dict(status=v['status'], ticks=v.get('ticks'), minimumProven=v.get('minimumProven'))
                       for v in record['spatial_results'].values()],
            acceptedStages=[dict(target=s['target'], extraGroundInputs=s['delay'], tick=s['tick'])
                            for s in stages['stages'] if s['result']['best']],
            completeTick64Objective=complete['result']['best']['candidate']['objective'],
            completeReplay=complete['replay']))
    write('coupling-counterexample.json', dict(
        interpretation='Local time/progress exclusion loses the best tested complete continuation. This does not prove a global optimum or that other continuations of the retained histories cannot improve.',
        comparisonSource='evidence/real11-clock64-event-priority.json.gz',
        continuationSource='evidence/real11-terminal-variants.json.gz', candidates=counterexample))

    controls = []
    for name in ('real11-clock64-first', 'real11-clock64-near-goals', 'real11-clock64-event-priority', 'real11-clock64-resumed'):
        data = read(name)
        result = data['result']
        controls.append(dict(name=name, source=data['source'], status=result['status'], termination=result['termination'],
                             elapsedSeconds=result['elapsedSeconds'], calls=result['work']['solverCalls'],
                             acceptedWitnessReplays=len(data['validation']),
                             allReplaysPassed=all(r['passed'] for r in data['validation'])))
    write('control-summary.json', controls)

    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib.patches import Rectangle

    fig, (top, height) = plt.subplots(1, 2, figsize=(12, 5), constrained_layout=True)
    for block in terminal['fixture']['request']['blocks']:
        x0, y0, z0, x1, y1, z1 = block['boxes'][0]
        top.add_patch(Rectangle((x0, z0), x1-x0, z1-z0, facecolor='#cbd5e1', edgecolor='#64748b'))
        top.text((x0+x1)/2, (z0+z1)/2, block['id'].upper(), ha='center', va='center', fontsize=9)
    top.plot([p[0] for p in candidate['positions']], [p[1] for p in candidate['positions']], color='#2563eb')
    for tick in [12, 21, 30, 40, 55, 64]:
        x, z = candidate['positions'][tick]
        top.plot(x, z, 'o', color='#ea580c')
        top.annotate(str(tick), (x, z), xytext=(5, -11), textcoords='offset points', fontsize=9)
    top.set(xlabel='X', ylabel='Z', title='Player center; labels are absolute state ticks')
    top.set_aspect('equal', adjustable='datalim')
    top.ticklabel_format(useOffset=False)
    top.grid(alpha=.2)
    height.plot(range(65), candidate['y'], color='#2563eb')
    for tick in [12, 21, 30, 40, 55]:
        height.plot(tick, candidate['y'][tick], 'o', color='#ea580c')
        height.annotate(str(tick), (tick, candidate['y'][tick]), xytext=(3, -14), textcoords='offset points', fontsize=9)
    height.plot(64, candidate['y'][64], '*', markersize=12, color='#ea580c')
    height.set(xlabel='Absolute state tick', ylabel='Feet Y', title='Final state is airborne; B7 landing is optional')
    height.grid(alpha=.2)
    fig.suptitle('Real11 opening: max Z at tick 64 = -1093.267553 (best found)')
    fig.savefig(ROOT / 'trajectory.png', dpi=160)
    fig.savefig(ROOT / 'trajectory.svg')
    svg = ROOT / 'trajectory.svg'
    svg.write_text('\n'.join(line.rstrip() for line in svg.read_text().splitlines()) + '\n')
    plt.close(fig)

    files = {}
    for path in sorted(ROOT.rglob('*')):
        if path.is_file() and path.name != 'evidence-manifest.json':
            files[str(path.relative_to(ROOT))] = dict(bytes=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest())
    versions = {name: importlib.metadata.version(name) for name in ('casadi', 'numpy', 'scipy', 'matplotlib')}
    write('evidence-manifest.json', dict(sourceCommit=summary['sourceCommit'], python=platform.python_version(),
        platform=platform.platform(), versions=versions, threads=dict(OPENBLAS_NUM_THREADS=1, OMP_NUM_THREADS=1), files=files))


if __name__ == '__main__':
    main()
