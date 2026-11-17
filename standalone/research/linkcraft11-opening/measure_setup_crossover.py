import csv
import gzip
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent


def main():
    with gzip.open(ROOT / 'evidence/real11-terminal-variants.json.gz', 'rt') as handle:
        source = json.load(handle)
    runs = {r['initialTakeoff'][0]: r for r in source['runs']
            if r['jumpAfterB5'] and r['initialTakeoff'] in ([0], [8])}
    with gzip.open(ROOT / 'evidence/real11-clock64-event-priority.json.gz', 'rt') as handle:
        direction = json.load(handle)['result']['windows'][0]['checkpoint']['direction']
    anchor = source['fixture']['request']['initial']['position']
    a, b = (runs[j]['result']['best']['candidate'] for j in (0, 8))
    rows = []
    for tick in range(65):
        progress = [sum(direction[i]*(c['positions'][tick][i]-anchor[2*i]) for i in range(2)) for c in (a, b)]
        rows.append(dict(state=tick, immediateProgress=progress[0], delayedProgress=progress[1],
                         progressAdvantage0=progress[0]-progress[1],
                         immediateZ=a['positions'][tick][1], delayedZ=b['positions'][tick][1],
                         zAdvantage0=a['positions'][tick][1]-b['positions'][tick][1]))
    def first_lead(key):
        return next(row['state'] for row in rows if row['state'] > 10 and row[key] > 1e-7)
    first = first_lead('progressAdvantage0')
    assert first == 23 and all(row['progressAdvantage0'] > 0 for row in rows[first:])
    assert first_lead('zAdvantage0') == 28
    boxes = {tuple(box): block['id'] for block in source['fixture']['request']['blocks'] for box in block['boxes']}
    contacts = {}
    for j, run in runs.items():
        arrivals = {}
        for e in run['program']['profile']['contactEvents']:
            block = boxes[tuple(e['box'])]
            if e['axis'] == 'Y' and e['direction'] == -1 and block in source['fixture']['requiredSupports']:
                arrivals[block] = min(arrivals.get(block, 65), e['offset']+1)
        contacts[j] = arrivals
    assert contacts == {0: dict(b2=21, b3=30, b4=40, b5=55), 8: dict(b2=17, b3=28, b4=40, b5=58)}
    summary = dict(source='evidence/real11-terminal-variants.json.gz',
        measurement='Observed state samples of the two accepted complete trajectories optimized for Z at64. No numerical re-solves at earlier horizons; later discrete decisions differ.',
        initialSetupInputs=dict(immediateFirstJump=0, delayedFirstJump=8, delayedGroundRun=list(range(8))),
        comparedDirection=direction, anchor=anchor,
        firstRegainedDirectionalLeadAfterState10=first, ticksBeyondFirstComparison=first-10,
        firstRegainedPureZLeadAfterState10=first_lead('zAdvantage0'),
        keepsDirectionalLeadThrough64=True,
        supportArrivalStates=contacts, tick64ZAdvantage=rows[64]['zAdvantage0'],
        selectedSamples=[row for row in rows if row['state'] in (10, 12, 13, 17, 20, 21, 22, 23, 27, 28, 30, 40, 55, 58, 64)],
        decision='Retain both initial setup families, per user instruction. No dominance proof follows from a local score.',
        limitations=['not per-horizon optimized progress', 'not a certified crossover between optimal continuation value functions',
                     'does not isolate initial timing from all later decisions'])
    (ROOT / 'audit/setup-crossover.json').write_text(json.dumps(summary, indent=2)+'\n')
    with (ROOT / 'audit/setup-crossover.csv').open('w', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]), lineterminator='\n')
        writer.writeheader()
        writer.writerows(rows)

    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    shown = rows[10:56]
    fig, ax = plt.subplots(figsize=(8, 4.5), constrained_layout=True)
    ax.plot([r['state'] for r in shown], [r['progressAdvantage0'] for r in shown],
            label='Same route-directed progress measure', color='#2563eb')
    ax.plot([r['state'] for r in shown], [r['zAdvantage0'] for r in shown],
            label='Pure Z progress', color='#ea580c', linestyle='--')
    ax.axhline(0, color='#64748b', linewidth=1)
    ax.axvline(23, color='#2563eb', alpha=.35, linestyle=':')
    ax.axvline(28, color='#ea580c', alpha=.35, linestyle=':')
    ax.annotate('Route progress overtakes at23', (23, rows[23]['progressAdvantage0']),
                xytext=(15, .35), arrowprops=dict(arrowstyle='->', color='#2563eb'), fontsize=9)
    ax.annotate('Z overtakes at28', (28, rows[28]['zAdvantage0']),
                xytext=(31, -.25), arrowprops=dict(arrowstyle='->', color='#ea580c'), fontsize=9)
    ax.set(xlabel='Absolute state tick', ylabel='Jump-at-0 advantage over jump-at-8 (blocks)',
           title='Saved complete routes: early disadvantage reverses later')
    ax.grid(alpha=.2)
    ax.legend(loc='upper left', fontsize=9)
    fig.savefig(ROOT / 'audit/setup-crossover.png', dpi=160)
    plt.close(fig)
    print(json.dumps(dict(firstRegainedDirectionalLead=first, firstPureZLead=28,
                          supportArrivalStates=contacts, finalZAdvantage=rows[64]['zAdvantage0'])))


if __name__ == '__main__':
    main()
