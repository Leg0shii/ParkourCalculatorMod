"""Plot the complete newly discovered trajectory after final independent replay."""
import gzip
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle
from standalone.autonomy.cell_routes.event_routes import support_episodes

here=Path(__file__).resolve().parent
report=json.loads(gzip.decompress((here/'matched-forward-service-2026-11-03/automatic.json.gz').read_bytes()))
result=report['result'];state=result['searchState'];dfs=state['dfs'];best=result['best'];data=state['domain']
scene=data['scene'];solids=scene['collisionScene']['solids'];positions=best['candidate']['positions']
frames={f['id']:f for k in ('frames','stack','suspended','refinements','goalQueue') for f in dfs.get(k,[])}
identity=best['movementProgram']['searchParent']
frame=next(f for f in frames.values() if any(r['id']==identity for r in f['records']))
path=[]
while frame:
    path.append(frame)
    parent=frame['parent'];frame=frames.get(parent['frame']) if parent else None
path.reverse()
fig,(ax,ay)=plt.subplots(1,2,figsize=(17,8),gridspec_kw=dict(width_ratios=[2.1,1]),layout='constrained')
for block in scene['blocks']:
    box=solids[block['solidIndex']];tall=box[4]>10
    ax.add_patch(Rectangle((box[0],box[2]),box[3]-box[0],box[5]-box[2],facecolor='#e6b6b2' if tall else '#d5d9dc',edgecolor='#95443d' if tall else '#5d666b',zorder=3))
    name=block.get('sourceBlock',block['id']);offset={'b6':(20,25),'b7':(-30,25),'b5':(20,-18)}.get(name,(8,10))
    ax.annotate(name.upper()+f" (Y={box[4]:g})",((box[0]+box[3])/2,(box[2]+box[5])/2),xytext=offset,textcoords='offset points',fontsize=8,zorder=7,
        bbox=dict(facecolor='white',edgecolor='none',alpha=.8,pad=1))
for surface in data['surfaces']:
    for b in surface['cover']:ax.add_patch(Rectangle((b[0],b[2]),b[3]-b[0],b[5]-b[2],fill=False,edgecolor='#1676aa',linestyle='--',linewidth=.8,zorder=2))
for i,f in enumerate(path):
    points=f['guide']['points']
    ax.plot([p[0] for p in points],[p[2] for p in points],color=plt.cm.tab10(i%10),linestyle='--',marker='.',ms=4,lw=1.1,alpha=.8,zorder=4)
ax.plot([p[0] for p in positions],[p[1] for p in positions],color='#078a67',lw=2.2,zorder=6,label='New cold result, independently replayed')
episodes=support_episodes(data,best['movementProgram']['profile'])
for e in episodes:
    if e['kind']!='support_visit':continue
    p=positions[e['input']]
    ax.scatter(*p,color='#078a67',s=20,zorder=8)
    ax.annotate(str(e['tick']),p,xytext=(-12,-15),textcoords='offset points',fontsize=9,color='#00664a',zorder=8)
ax.set(xlim=(min(b[0] for b in solids)-1.5,max(b[3] for b in solids)+1.5),ylim=(min(b[2] for b in solids)-1.5,max(b[5] for b in solids)+1.5),xlabel='X',ylabel='Z')
ax.set_aspect('equal');ax.grid(alpha=.15);ax.ticklabel_format(useOffset=False,style='plain');ax.legend(loc='lower left',fontsize=8)
y=best['candidate']['y'];ay.plot(range(len(y)),y,color='#078a67',lw=1.8)
ay.axhline(7,color='#999999',linestyle=':',label='Nominal finish height')
for e in episodes:
    if e['kind']=='support_visit':ay.scatter(e['tick'],y[e['tick']],color='#1676aa',s=24);ay.annotate('/'.join(e['owners']),(e['tick'],y[e['tick']]),xytext=(3,6),textcoords='offset points',fontsize=8)
ay.set(xlabel='Actual input tick',ylabel='Feet Y');ay.grid(alpha=.15);ay.legend(fontsize=8)
first=dfs.get('firstFullSolutionSeconds')
fig.suptitle(f"Stored Real11: new unseeded event search, T={best['plan']['ticks']}, Z={best['candidate']['objective']:.6f}\n"
    f"First full finish at {first:.2f}s. Dashed legs are independent optional guides; blue rectangles are physical support domains.",fontsize=13)
for suffix in ('svg','png','pdf'):fig.savefig(here/('cold-full-route.'+suffix),dpi=150)
plt.close(fig)
print('Wrote complete cold trajectory and height profile.')
