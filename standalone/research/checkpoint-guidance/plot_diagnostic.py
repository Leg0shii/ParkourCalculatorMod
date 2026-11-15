import gzip
import json
from pathlib import Path

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt


def main():
    root = Path(__file__).parent
    report = json.loads(gzip.decompress((root/'evidence/checkpoint-guidance-greedy-final.json.gz').read_bytes()))
    scores, ticks, labels = [], [], []
    for arm in ('generation', 'guided'):
        run = next(r for r in report['runs'] if r['name'] == 'ceiling' and r['repeat'] == 0 and r['result']['arm'] == arm)
        result = run['result']
        window = result['windows'][0]
        selected = next(c for c in window['candidates'] if c['id'] in window['retained'])
        jumps = selected['witness']['profiles'][0]['jump']
        labels.append('Jump at input '+str(jumps.index(True)))
        scores.append(selected['value'])
        ticks.append(result['best']['plan']['ticks'])
    fig, axes = plt.subplots(1,2,figsize=(8,3.4),layout='constrained')
    colors = ['#387b9b','#bd6739']
    for ax, values, title, ylabel in zip(axes,[scores,ticks],
            ['Prefix score at the same absolute tick 7','Completed route from each selected history'],
            ['Horizontal projection (higher scores first)','Elapsed ticks (lower is better)']):
        bars = ax.bar(labels,values,color=colors,width=.55)
        ax.bar_label(bars,labels=[f'{v:.3f}' if ax is axes[0] else str(v) for v in values],padding=4)
        ax.set_title(title,fontsize=10)
        ax.set_ylabel(ylabel,fontsize=9)
        ax.set_ylim(0,max(values)*1.2)
        ax.spines[['top','right']].set_visible(False)
    fig.savefig(root/'evidence/ceiling-ranking.svg',metadata={'Date':None})
    fig.savefig(root/'evidence/ceiling-ranking.png',dpi=170)
    svg = root/'evidence/ceiling-ranking.svg'
    svg.write_text('\n'.join(line.rstrip() for line in svg.read_text().splitlines())+'\n')


if __name__ == '__main__':
    main()
