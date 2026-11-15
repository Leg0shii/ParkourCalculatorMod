from tools.paths import RESEARCH_ROOT
import argparse
import gzip
import json
from pathlib import Path
import statistics

from research.route_guidance.experiment import similarity


def read_report(path):
    path = Path(path)
    return json.loads(gzip.decompress(path.read_bytes()) if path.suffix == '.gz' else path.read_text())


def aggregate(report):
    result = []
    for name in sorted({c['name'] for c in report['cases']}):
        cases = [c for c in report['cases'] if c['name'] == name]
        item = dict(name=name, ticks=cases[0]['ticks'], assignments=cases[0]['assignments'],
                    acceptedMin=min(c['acceptedAssignments'] for c in cases),
                    acceptedMax=max(c['acceptedAssignments'] for c in cases), arms={})
        for arm in cases[0]['arms']:
            rows = [c['arms'][arm] for c in cases]
            solved = [r for r in rows if r['root']['status'] == 'solved']
            if not solved:
                item['arms'][arm] = dict(solved=0)
                continue
            def median(values):
                values = [v for v in values if v is not None]
                return statistics.median(values) if values else None
            item['arms'][arm] = dict(solved=len(solved),
                rootSeconds=median(r['root']['elapsedSeconds']+
                    (r['root']['buildSeconds'] if arm == 'convex_disk' else 0) for r in solved),
                rootGap=median(r['objectiveGap'] for r in solved if 'objectiveGap' in r),
                positionRmse=median(r['referenceSimilarity']['positionRmse'] for r in solved if 'referenceSimilarity' in r),
                routeRmse=median(r['referenceSimilarity']['routeRmse'] for r in solved if 'referenceSimilarity' in r),
                greedyRegret=median(r.get('guidedRegret') for r in solved),
                beamRegret=median(r.get('beamRegret') for r in solved),
                guidedSeconds=median(r['guided']['elapsedSeconds'] for r in solved),
                beamSeconds=median(r['beam']['elapsedSeconds'] for r in solved),
                correctPairs=sum(v['correctPairs'] for r in solved for v in r['rankings']),
                comparablePairs=sum(v['comparablePairs'] for r in solved for v in r['rankings']),
                reducedTicks=median(r['controlStats']['reducedAccelerationTicks'] for r in solved),
                fractionalTicks=median(r['controlStats']['fractionalTicks'] for r in solved),
                maxFlowFractionality=max(r['controlStats']['maxFlowFractionality'] for r in solved),
                variables=rows[0]['root']['variables'], constraints=rows[0]['root']['rows'])
            nearest = []
            for case in cases:
                data = case['arms'][arm]
                if data['root']['status'] != 'solved' or not case['bestFeasible']:
                    continue
                pool = [r for r in case['real'] if r['status'] == 'feasible']
                for variant in case['arms'].values():
                    pool.append(variant.get('rootRefinement', {}))
                    pool.extend(variant.get('guided', {}).get('refinementAttempts', []))
                    pool.extend(variant.get('beam', {}).get('refinementAttempts', []))
                pool = [r for r in pool if r.get('status') == 'feasible'
                        and r['value'] >= case['bestFeasible']['value']-.001]
                if pool:
                    nearest.append(min(similarity(data['root'], r['candidate'])['positionRmse'] for r in pool))
            item['arms'][arm]['nearestNearOptimalPositionRmse'] = median(nearest)
        result.append(item)
    return result


def plots(report, fixtures, output):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib.patches import Rectangle
    import numpy as np
    selected = ['cross-passage', 'narrow-fast-entry', 'timing-and-passage', 'turning-support']
    fig, axes = plt.subplots(2, 2, figsize=(13, 8), constrained_layout=True)
    for ax, name in zip(axes.flat, selected):
        case = next(c for c in report['cases'] if c['name'] == name)
        fixture = json.loads((fixtures/(name+'.json')).read_text())
        for block in fixture['route']['blocks']:
            a, b, c, d = block['bounds']
            ax.add_patch(Rectangle((a, c), b-a, d-c, color='#bcc5cf', alpha=.5))
        for box in fixture.get('collisionScene', {}).get('solids', []):
            if box[4] > 1:
                ax.add_patch(Rectangle((box[0]-.3, box[2]-.3), box[3]-box[0]+.6,
                                      box[5]-box[2]+.6, color='#eb7777', alpha=.3))
        data = case['arms']['convex_disk']
        root = np.array(data['root']['states'])
        ax.plot(root[:, 0], root[:, 1], '--', color='#2874a6', label='Uncommitted convex reference')
        final = np.array(data['guided']['finalReference']['states'])
        ax.plot(final[:, 0], final[:, 1], ':', color='#e18522', label='Greedy committed convex reference')
        if case['bestFeasible']:
            real = np.array(case['bestFeasible']['candidate']['positions'])
            ax.plot(real[:, 0], real[:, 1], '.-', color='#222222', markersize=3, label='Best accepted enumerated path')
        ax.set_title(name)
        ax.set_xlabel('X (blocks)')
        ax.set_ylabel('Z (blocks)')
        ax.grid(alpha=.2)
        ax.set_aspect('equal', adjustable='datalim')
    axes[0, 0].legend(fontsize=8)
    fig.savefig(output/'reference-trajectories.svg')
    fig.savefig(output/'reference-trajectories.png', dpi=180)
    plt.close(fig)
    data = aggregate(report)
    names = [d['name'] for d in data]
    positions = np.arange(len(data))
    fig, ax = plt.subplots(figsize=(13, 4), constrained_layout=True)
    for index, (arm, label, color) in enumerate([
            ('compact_circle', 'Compact circle', '#6878b8'), ('compact_disk', 'Compact disk', '#c18b34'),
            ('convex_disk', 'Convex disk', '#369a73')]):
        ax.bar(positions+(index-1)*.25, [d['arms'][arm]['rootSeconds'] for d in data],
               width=.25, color=color, label=label)
    ax.set_xticks(positions, names, rotation=25, ha='right')
    ax.set_yscale('log')
    ax.set_ylabel('Median model construction + solve (seconds)')
    ax.grid(axis='y', alpha=.2)
    ax.legend()
    fig.savefig(output/'model-times.svg')
    plt.close(fig)
    fig, axes = plt.subplots(1, 3, figsize=(13, 4), constrained_layout=True)
    for ax, name in zip(axes, ['cross-passage', 'timing-and-passage', 'turning-support']):
        case = next(c for c in report['cases'] if c['name'] == name)
        rows = case['arms']['convex_disk']['rankings']
        row = next((r for r in rows if r['decision'].startswith('passage:')), rows[0])
        choices = sorted(row['choices'], key=lambda c: c['choice'])
        ax.plot([c['choice'] for c in choices if c['relaxedValue'] is not None],
                [c['relaxedValue'] for c in choices if c['relaxedValue'] is not None], 'o-',
                color='#2874a6', label='Conditional optimistic value')
        ax.plot([c['choice'] for c in choices if c['feasibleValue'] is not None],
                [c['feasibleValue'] for c in choices if c['feasibleValue'] is not None], 's-',
                color='#222222', label='Best accepted feasible value')
        ax.set_title(name)
        ax.set_xlabel('Relative position-state offset' if row['decision'].startswith('passage:') else 'Departure input index')
        ax.set_ylabel('Normalized MAX objective')
        ax.grid(alpha=.2)
    axes[0].legend(fontsize=8)
    fig.savefig(output/'conditional-rankings.svg')
    plt.close(fig)
    fig, axes = plt.subplots(1, 2, figsize=(13, 4), constrained_layout=True)
    for ax, name in zip(axes, ['cross-passage', 'five-jump-control']):
        case = next(c for c in report['cases'] if c['name'] == name)
        for arm, color in [('compact_circle', '#6878b8'), ('compact_disk', '#c18b34'), ('convex_disk', '#369a73')]:
            points = np.array(case['arms'][arm]['root']['states'])
            ax.plot(points[:, 0], points[:, 1], '--', color=color, label=arm)
        real = np.array(case['bestFeasible']['candidate']['positions'])
        ax.plot(real[:, 0], real[:, 1], color='#222222', label='Best accepted enumerated path')
        ax.set_title(name)
        ax.set_xlabel('X (blocks)')
        ax.set_ylabel('Z (blocks)')
        ax.grid(alpha=.2)
    axes[0].legend(fontsize=8)
    fig.savefig(output/'reference-nonuniqueness.svg')
    plt.close(fig)
    for name in ('reference-trajectories.svg', 'model-times.svg', 'conditional-rankings.svg', 'reference-nonuniqueness.svg'):
        path = output/name
        path.write_text('\n'.join(line.rstrip() for line in path.read_text().splitlines())+'\n')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--fixtures', type=Path, default=RESEARCH_ROOT/'route-guidance/fixtures')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = read_report(args.input)
    args.output.mkdir(parents=True, exist_ok=True)
    data = aggregate(report)
    (args.output/'aggregate.json').write_text(json.dumps(data, indent=2, allow_nan=False)+'\n')
    plots(report, args.fixtures, args.output)
    print(json.dumps(data, indent=2, allow_nan=False))


if __name__ == '__main__':
    main()
