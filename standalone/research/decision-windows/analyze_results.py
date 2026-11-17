import csv
import gzip
import json
from pathlib import Path
from statistics import median

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt


def read(path):
    return json.loads(gzip.decompress(path.read_bytes()))


def main():
    out = Path(__file__).parent/'evidence'
    cold = read(out/'decision-windows-final.json.gz')
    diagnostic = read(out/'decision-window-diagnostic-final.json.gz')
    rows = []
    for name in sorted({s['name'] for s in cold['summaries']}):
        for gap in (None,.25,.5):
            runs = [s for s in cold['summaries'] if s['name']==name and s['gap']==gap]
            if not runs:
                continue
            rows.append(dict(scene=name,gap=gap,repeats=len(runs),
                feasible=sum(s['status']=='feasible' for s in runs),
                ticks=sorted({s['ticks'] for s in runs if s['ticks'] is not None}),
                medianSeconds=median(s['elapsedSeconds'] for s in runs),
                minSeconds=min(s['elapsedSeconds'] for s in runs),maxSeconds=max(s['elapsedSeconds'] for s in runs),
                calls=median(s['calls'] for s in runs),expandedPrefixes=median(s['expandedPrefixes'] for s in runs),
                retained=runs[0]['retained'],outsideBand=runs[0]['outsideBand'],scoredCapDrops=runs[0]['scoredCapDrops']))
    with (out/'cold-summary.csv').open('w') as stream:
        writer = csv.DictWriter(stream,fieldnames=list(rows[0]),lineterminator='\n')
        writer.writeheader()
        writer.writerows(rows)
    groups = diagnostic['comparisons'][0]['groups']
    dominance = []
    fig,axes = plt.subplots(2,2,figsize=(10,6),layout='constrained')
    for row,(name,h) in enumerate([('ceiling',11),('step-or-jump',10)]):
        runs = [r for r in diagnostic['runs'] if r['repeat']==0 and r['name']==name and r['h']==h]
        runs.sort(key=lambda r:r['bestCompletion']['ticks'])
        samples = [{s['tick']:s for s in r['features']['samples']} for r in runs]
        common = sorted(set(samples[0]) & set(samples[1]))
        dominates = all(samples[1][t]['progress']>=samples[0][t]['progress'] and
                        samples[1][t]['forwardVelocity']>=samples[0][t]['forwardVelocity'] for t in common)
        dominance.append(dict(scene=name,h=h,commonClearTicks=common,
            faster=runs[0]['label'],slower=runs[1]['label'],
            fasterTicks=runs[0]['bestCompletion']['ticks'],slowerTicks=runs[1]['bestCompletion']['ticks'],
            slowerAheadAndFasterAtEveryRecordedCommonTick=bool(common) and dominates,
            endpoints=[dict(label=r['label'],**r['features']['samples'][-1]) for r in runs]))
        for ax,key,label in zip(axes[row],['progress','forwardVelocity'],['Horizontal progress','Forward stored velocity']):
            for r,points,color in zip(runs,samples,['#387b9b','#bd6739']):
                ax.plot(common,[points[t][key] for t in common],marker='o',color=color,
                        label=r['label'].replace('_',' ')+'; finish '+str(r['bestCompletion']['ticks']))
            ax.set_title(name+': '+label,fontsize=10)
            ax.set_xlabel('Absolute pre-input state tick')
            ax.set_ylabel('Blocks' if key=='progress' else 'Blocks / tick')
            ax.set_xticks(common)
            ax.spines[['top','right']].set_visible(False)
            ax.legend(fontsize=8)
    fig.savefig(out/'horizontal-feature-counterexamples.svg',metadata={'Date':None})
    fig.savefig(out/'horizontal-feature-counterexamples.png',dpi=160)
    svg = out/'horizontal-feature-counterexamples.svg'
    svg.write_text('\n'.join(line.rstrip() for line in svg.read_text().splitlines())+'\n')
    summary = dict(coldSource=cold['source'],diagnosticSource=diagnostic['source'],
        coldRuns=len(cold['runs']),diagnosticRuns=len(diagnostic['runs']),
        independentReplays=len(cold['validation'])+len(diagnostic['validation']),
        allReplaysPassed=all(v['passed'] for r in (cold,diagnostic) for v in r['validation']),
        cold=rows,diagnosticComparisons=groups,recordedForwardDominance=dominance)
    (out/'summary.json').write_text(json.dumps(summary,indent=2,allow_nan=False)+'\n')
    print(json.dumps({k:v for k,v in summary.items() if k not in ('cold','diagnosticComparisons')},indent=2))


if __name__=='__main__':
    main()
