"""Render saved corridor geometry and audit a cold run without new solves."""

import collections
import gzip
import hashlib
import json
from pathlib import Path
import subprocess

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle, Patch
from matplotlib.lines import Line2D


HERE=Path(__file__).resolve().parent
BASE=HERE.parent
AUTO=BASE/'matched-allocation-context-2026-11-03/automatic.json.gz'
COVERAGE=BASE/'coverage-allocation-context-final-2026-11-03.json.gz'
ARCHIVE=BASE.parent/'virtual-finish/evidence/initial0-cold.json.gz'


def read(path):
    return json.loads(gzip.decompress(path.read_bytes()))


def rectangle(ax,box,**style):
    ax.add_patch(Rectangle((box[0],box[2]),box[3]-box[0],box[5]-box[2],**style))


def terrain(ax,request,regions,route,*,detail=False):
    rectangle(ax,[727.5,0,-1095.3,729.7,0,-1092],facecolor='#e4f3df',
              edgecolor='none',alpha=.8,zorder=0)
    for block in request['blocks']:
        box=block['boxes'][0]
        if detail and (box[0]>736.3 or box[3]<727.8 or box[2]>-1092.5 or box[5]<-1101.5):
            continue
        obstacle=block['id']=='b6'
        rectangle(ax,box,facecolor='#e5b0aa' if obstacle else '#cdd3d8',
                  edgecolor='#ae4438' if obstacle else '#626d77',lw=1.2,zorder=3,
                  hatch='///' if obstacle else None)
        label=block['id'].upper()+f"\nY={box[4]:g}"
        x,z=(box[0]+box[3])/2,(box[2]+box[5])/2
        if detail:
            ax.text(x,z,label,ha='center',va='center',fontsize=9,zorder=9)
        else:
            offsets={'b1':(10,0),'b2':(-20,-22),'b3':(8,16),'b4':(8,-23),
                     'b5':(20,-25),'b6':(28,12),'b7':(-27,0)}
            ax.annotate(label,(x,z),xytext=offsets[block['id']],textcoords='offset points',
                        ha='center',va='center',fontsize=9,zorder=9,
                        bbox=dict(facecolor='white',alpha=.8,edgecolor='none',pad=1))
    for key in route['route_variants'][0]['regions']:
        region=regions[key]
        if region['kind']=='support_surface':
            for box in region['cover']:
                rectangle(ax,box,fill=False,edgecolor='#1672a5',ls='--',lw=1.5,zorder=4)
    ax.set(xlim=(727.5,748.5),ylim=(-1106.5,-1092),xlabel='X',ylabel='Z')
    ax.set_aspect('equal');ax.grid(alpha=.15,zorder=0)
    ax.ticklabel_format(useOffset=False,style='plain')


def guide(ax,route):
    points=route['points']
    ax.plot([p[0] for p in points],[p[2] for p in points],color='#d88724',ls='--',
            marker='.',ms=5,lw=1.8,zorder=5)


def track(ax,witness,color):
    positions=witness['candidate']['positions']
    ax.plot([p[0] for p in positions],[p[1] for p in positions],color=color,lw=2,zorder=6)


def main():
    automatic=read(AUTO);coverage=read(COVERAGE);archived=read(ARCHIVE)['result']['best']
    result=automatic['result'];state=result['searchState'];request=automatic['fixture']['request']
    regions={r['key']:r for r in state['graph']['regions']}
    routes=state['routes'];partial=state['children']['0']['stack'][1]['records'][0]['witness']
    diagnostics=[];records=[]
    for key,child in state['children'].items():
        for frame in child['stack']:
            for record in frame['records']:
                ds=[d for a in record['numericalAttempts'] for d in a.get('diagnostics',[])]
                diagnostics.extend(ds)
                cg=record['program'].get('repairState',{}).get('constraintGeneration',{})
                records.append(dict(route=int(key),goal=frame['goal'].get('block'),ticks=record['ticks'],
                    status=record['status'],termination=record['termination'],calls=len(ds),
                    solverWrapperSeconds=sum(d.get('elapsedSeconds',0.) for d in ds),
                    candidateID=record['id'],constraintVariantsEmitted=cg.get('emitted'),
                    pendingConstraintItems=[n['item'] for n in cg.get('pending',[])],
                    problemHashes=[d['problemHash'] for d in ds]))
    calls=sum(child['cumulativeCalls'] for child in state['children'].values())
    assert calls==len(diagnostics)==13
    worker=sum(d['elapsedSeconds'] for d in diagnostics)
    families=collections.defaultdict(list)
    for route in routes:
        seq=tuple('/'.join(regions[k]['owners']) for k in route['route_variants'][0]['regions']
                  if regions[k]['kind']=='support_surface')
        families[' -> '.join(seq)].append(route['id'])
    summary=dict(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        inputs={str(p.relative_to(BASE.parent)):hashlib.sha256(p.read_bytes()).hexdigest()
                for p in (AUTO,COVERAGE,ARCHIVE)},
        sourceRuntimeHashes=automatic['sourceHashes'],
        routes=len(routes),startedRoutes=sorted(map(int,state['children'])),
        unstartedRoutes=[r['id'] for r in routes if str(r['id']) not in state['children']],
        acceptedArchivedWitnessRoutes=coverage['acceptedRoutes'],
        route26Started='26' in state['children'],supportFamilies=families,
        calls=calls,searchWallSeconds=automatic['externalSearchWall'],
        solverWrapperSeconds=worker,outsideSolverWrapperSeconds=automatic['externalSearchWall']-worker,
        outsideSolverWrapperFraction=1-worker/automatic['externalSearchWall'],
        solverTimingCaveat='Includes worker startup/replay/cleanup; remaining wall time is not an instrumented per-function profile.',
        records=records,
        initialDomain=automatic['fixture']['initialJumpInputs'],
        route0=routes[0],route26=routes[26],
        caveat='Guide polylines are not trajectories or fixed waypoint equalities. All cells shown are a local crop of actual saved regions.')
    (HERE/'routing-audit.json').write_text(json.dumps(summary,indent=2)+'\n')

    plt.rcParams.update({'font.size':10,'axes.spines.top':False,'axes.spines.right':False})
    fig=plt.figure(figsize=(15.2,10.2),layout='constrained')
    gs=fig.add_gridspec(2,2,height_ratios=[1,.12])
    for index,title in [(0,'Route 0: first cold-search corridor'),(26,'Route 26: corridor admitting the archived T64 witness')]:
        ax=fig.add_subplot(gs[0,0 if index==0 else 1]);route=routes[index]
        terrain(ax,request,regions,route);guide(ax,route)
        if index==0:
            track(ax,partial,'#078a67')
            for tick,label in [(17,'B2 at tick 17'),(29,'B3 at tick 29')]:
                p=partial['candidate']['positions'][tick]
                ax.scatter(*p,color='#078a67',s=28,zorder=9)
                ax.annotate(label,p,xytext=(8,14),textcoords='offset points',fontsize=9,color='#00684c',zorder=10)
            ax.scatter([734.5],[-1099.5],marker='x',color='#c34532',s=90,zorder=11,lw=2)
            ax.text(736,-1098.5,'B4 @41: unresolved\nNo validated arrival',fontsize=9,color='#b23128',
                    bbox=dict(facecolor='white',edgecolor='none',alpha=.9),zorder=11)
            subtitle='B1 → B2 → B3 → B4 → B7 contact → finish'
        else:
            track(ax,archived,'#7c4fa2')
            subtitle='B1 → B2 → B3 → B4 → B5 → free height-7 finish'
            ax.text(739.6,-1097.1,'Not started in the cold run\nPurple: archived replay, not a new solve',
                    fontsize=10,color='#67428a',bbox=dict(facecolor='white',edgecolor='none',alpha=.9),zorder=11)
        ax.set_title(title+'\n'+subtitle,fontsize=12,pad=14)
        ax.text(727.8,-1092.5,'Virtual finish X/Z\nX < 729.7, Z ≥ −1095.3',fontsize=8,color='#32792b',zorder=12)
    bar=fig.add_subplot(gs[1,:]);colors=[]
    for i in range(50):
        ch=state['children'].get(str(i));depth=max((f['index'] for f in ch['stack']),default=0) if ch else -1
        colors.append('#078a67' if depth>=2 else '#4099c1' if depth==1 else '#f0b767' if depth==0 else '#e0e5e9')
    bar.scatter(range(50),[0]*50,c=colors,s=75,marker='s')
    bar.scatter([26],[0],s=135,facecolors='none',edgecolors='#7c4fa2',lw=2)
    bar.annotate('26: coverage witness fits; never started',(26,0),xytext=(19,.38),
                 arrowprops=dict(arrowstyle='->',color='#7c4fa2'),color='#67428a',fontsize=10)
    bar.set(xlim=(-1,50),ylim=(-.2,.6),xlabel='Automatic route ID',yticks=[],xticks=range(0,50,5))
    bar.set_title('Cold-search coverage: routes 0–9 started; 10–49 untouched',loc='left',fontsize=11)
    for spine in bar.spines.values():spine.set_visible(False)
    handles=[Patch(facecolor='#cdd3d8',edgecolor='#626d77',label='Physical block footprint'),
             Patch(facecolor='none',edgecolor='#1672a5',ls='--',label='Selected landing surface / player center'),
             Line2D([0],[0],color='#d88724',ls='--',label='Representative guide anchors'),
             Line2D([0],[0],color='#078a67',label='Validated cold prefix (29 inputs)'),
             Line2D([0],[0],color='#7c4fa2',label='Archived T64 replay (coverage only)')]
    fig.legend(handles=handles,loc='outside lower center',ncol=2,fontsize=10)
    fig.suptitle('Stored Real11: current routing and where cold search stopped\n'
                 'Dashed guides are not movement paths. Support rectangles are finite contact domains; air volumes are shown separately.',fontsize=14)
    for suffix in ('png','svg'):fig.savefig(HERE/('routing-overview.'+suffix),dpi=170)
    plt.close(fig)

    # The large air cuboids cannot all be read on one overlaid projection.
    # Show the distinct post-B4 volumes in separate, identically cropped panels.
    route=routes[26];path=route['route_variants'][0]['regions']
    begin=next(i for i,k in enumerate(path) if regions[k]['owners']==['b4'])
    air=[];seen=set()
    for key in path[begin+1:]:
        region=regions[key]
        if region['kind']=='air_volume' and key not in seen:air.append(region);seen.add(key)
    assert len(air)==4
    fig,axes=plt.subplots(2,2,figsize=(13,10),layout='constrained')
    palette=['#dfad6d','#66abc2','#a88cc7','#78b58c']
    for j,(ax,region,color) in enumerate(zip(axes.flat,air,palette)):
        terrain(ax,request,regions,route,detail=True)
        ax.set(xlim=(727.8,736.3),ylim=(-1101.5,-1092.5))
        for box in region['cover']:rectangle(ax,box,facecolor=color,alpha=.3,edgecolor=color,lw=2,zorder=1)
        guide(ax,route)
        box=region['cover'][0]
        def value(v):return f'{v:g}' if abs(v-round(v))<1e-6 else f'{v:.1f}'
        ax.set_title(f'A{j+1}: actual air cuboid, Y ∈ [{value(box[1])}, {value(box[4])}]\n'
                     f'X ∈ [{value(box[0])}, {value(box[3])}], Z ∈ [{value(box[2])}, {value(box[5])}]',fontsize=10)
    fig.suptitle('Route 26 after B4: the actual four overlapping air cuboids (local display crop)\n'
                 'A1 → A2 → B5 landing → A2 → A3 → A4 → height-7 finish. Blue dashed rectangles are landing surfaces.',fontsize=13)
    for suffix in ('png','svg'):fig.savefig(HERE/('route26-air-cells.'+suffix),dpi=170)
    plt.close(fig)
    print(json.dumps({k:summary[k] for k in ('routes','startedRoutes','calls','solverWrapperSeconds','outsideSolverWrapperFraction','route26Started')}))


if __name__=='__main__':main()
