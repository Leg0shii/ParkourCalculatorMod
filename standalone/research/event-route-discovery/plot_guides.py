"""Render complete unseeded event guides; archived trajectory is coverage only."""
import gzip
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle
from matplotlib.backends.backend_pdf import PdfPages

here=Path(__file__).resolve().parent
report=json.loads(gzip.decompress((here/'coverage-contact-regions-2026-11-03.json.gz').read_bytes()))
discovery=report['discovery'];data=discovery['domain'];scene=data['scene']
archive=json.loads(gzip.decompress((here.parent/'virtual-finish/evidence/initial0-cold.json.gz').read_bytes()))['result']['best']
selected={g for c in report['projection'] for g in c.get('guides',[])}
events={e['id']:e for e in discovery['graph']['events']}
solids=scene['collisionScene']['solids']
view=(min(b[0] for b in solids)-1.5,max(b[3] for b in solids)+1.5,
      min(b[2] for b in solids)-1.5,max(b[5] for b in solids)+1.5)

def rect(ax,b,**kw):ax.add_patch(Rectangle((b[0],b[2]),b[3]-b[0],b[5]-b[2],**kw))
def terrain(ax):
    for block in scene['blocks']:
        box=solids[block['solidIndex']]
        tall=box[4]>10
        rect(ax,box,facecolor='#e7b7b2' if tall else '#d5d9dc',edgecolor='#95443d' if tall else '#5d666b',zorder=4)
        ax.annotate(block.get('sourceBlock',block['id']).upper()+f" (Y={box[4]:g})",((box[0]+box[3])/2,(box[2]+box[5])/2),
            xytext=(8,10),textcoords='offset points',fontsize=8,zorder=8,
            bbox=dict(facecolor='white',edgecolor='none',alpha=.8,pad=1))
    for surface in data['surfaces']:
        for box in surface['cover']:rect(ax,box,fill=False,edgecolor='#1676aa',linestyle='--',linewidth=.8,zorder=3)
    ax.set(xlim=view[:2],ylim=view[2:],xlabel='X',ylabel='Z');ax.set_aspect('equal');ax.grid(alpha=.15)
    ax.ticklabel_format(useOffset=False,style='plain')
def draw(ax,guide,color='#d58b22',alpha=1):
    points=guide['points']
    ax.plot([p[0] for p in points],[p[2] for p in points],color=color,linestyle='--',marker='.',ms=4,lw=1.3,alpha=alpha,zorder=6)

fig,ax=plt.subplots(figsize=(13,8),layout='constrained');terrain(ax)
for i,item in enumerate(m for m in discovery['materialized'] if m['guide']['id'] in selected):
    draw(ax,item['guide'],plt.cm.tab10(i%10))
positions=archive['candidate']['positions'];ax.plot([p[0] for p in positions],[p[1] for p in positions],color='#7a4aa4',lw=1.6,zorder=5)
ax.set_title('Complete stored Real11: event guides compatible with archived reference\n'
             'Blue rectangles: exposed support domains. Colored dashes: independent optional guides. Purple: archived T64 replay (coverage only).',fontsize=12)
ax.text(.01,.02,'Guide legs use independent landing anchors; they are not one fixed trajectory.',transform=ax.transAxes,fontsize=9,
        bbox=dict(facecolor='white',alpha=.9,edgecolor='none'))
for suffix in ('svg','png'):fig.savefig(here/('full-event-guides.'+suffix),dpi=150)
with PdfPages(here/'full-event-guides.pdf') as pdf:
    pdf.savefig(fig);plt.close(fig)
    sources=[]
    for item in discovery['materialized']:
        if item['source'] not in sources:sources.append(item['source'])
    for source in sources:
        fig,ax=plt.subplots(figsize=(13,8),layout='constrained');terrain(ax)
        items=[m for m in discovery['materialized'] if m['source']==source]
        for i,item in enumerate(items):
            draw(ax,item['guide'],plt.cm.tab10(i%10),.8)
        label='initial B1 attachment' if source=='initial' else events[source]['goal'].get('block','finish')
        ax.set_title(f'Potential next-event guides from {label}\nOne witness per request; other physical side/layer alternatives remain latent.',fontsize=12)
        pdf.savefig(fig);plt.close(fig)
fig,ax=plt.subplots(figsize=(10,8),layout='constrained')
for region in data['geometry']['regions']:
    if region['kind']=='air_volume':
        for box in region['cover']:rect(ax,box,fill=False,edgecolor='#999999',alpha=.35,lw=.5)
for surface in data['surfaces']:
    for box in surface['cover']:rect(ax,box,fill=False,edgecolor='#1676aa',lw=.8)
ax.set(xlabel='X',ylabel='Z',title='Full raw-cell extents (configuration-space coverage)\nQuery bounds are not collision walls or guide midpoints.')
ax.autoscale();ax.set_aspect('equal');ax.ticklabel_format(useOffset=False,style='plain');fig.savefig(here/'raw-cell-extents.svg');plt.close(fig)
print('Wrote complete guide SVG/PNG/PDF and raw-cell extents.')
