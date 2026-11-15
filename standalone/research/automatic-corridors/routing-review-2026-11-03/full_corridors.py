"""Show every saved region/transition, with local and full spatial extents."""

import hashlib
import json
import math
from pathlib import Path

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
from matplotlib.patches import Rectangle, Patch
from matplotlib.lines import Line2D

from inspect_routing import AUTO, HERE, read, rectangle


VIEW=(727.5,748.5,-1106.5,-1092.)
AIR_COLORS=['#e0a343','#5eb0c6','#a489d4','#db837b','#83b777','#b3a54e','#709dcc','#c98cad']


def value(x):
    return f'{x:.0f}' if abs(x-round(x))<1e-6 else f'{x:.1f}'


def span(boxes,axis):
    lo=min(b[axis] for b in boxes);hi=max(b[axis+3] for b in boxes)
    return value(lo) if abs(lo-hi)<1e-8 else f'[{value(lo)}, {value(hi)}]'


def background(ax,request,points):
    for block in request['blocks']:
        b=block['boxes'][0];tall=block['id']=='b6'
        rectangle(ax,b,facecolor='#e7bfba' if tall else '#d7dce0',
                  edgecolor='#ad645b' if tall else '#939ea5',lw=.65,zorder=2,
                  hatch='///' if tall else None)
        ax.text((b[0]+b[3])/2,(b[2]+b[5])/2,block['id'].upper(),fontsize=7,
                color='#3e474d',ha='center',va='center',zorder=6)
    ax.plot([p[0] for p in points],[p[2] for p in points],color='#9c9c9c',ls='--',lw=.85,zorder=3)
    ax.set(xlim=VIEW[:2],ylim=VIEW[2:]);ax.set_aspect('equal');ax.grid(alpha=.12)
    ax.tick_params(labelsize=7)
    ax.ticklabel_format(useOffset=False,style='plain')


def main():
    saved=read(AUTO);state=saved['result']['searchState'];request=saved['fixture']['request']
    regions={r['key']:r for r in state['graph']['regions']}
    transitions={t['key']:t for t in state['graph']['transitions']}
    plt.rcParams.update({'font.size':9,'axes.spines.top':False,'axes.spines.right':False})
    audit=dict(input=str(AUTO),inputHash=hashlib.sha256(AUTO.read_bytes()).hexdigest(),routes=[])
    text=['# Full saved corridors: every occurrence and portal',
          '', 'The previous cell diagram deliberately showed only the post-B4 approach. These atlases include every region from B1 to the terminal predicate. Occurrence numbers express order, not input ticks.',
          '', 'Local panels use the same full-course viewport. No region occurrence is omitted. Exact full bounds and transition covers are retained in [full-corridors.json](full-corridors.json); the separate extent figure removes the local viewport crop.',
          '', 'Grey: physical footprints. Colored fill: current region projected onto X/Z. Green dashed: incoming portal/contact. Magenta dotted: outgoing portal/contact. Air-region projections may overlap blocks at different heights; the Y range identifies the vertical layer. Multiple rectangles in a support panel form a union.', '']
    figures=[]
    for route_id in (0,26):
        route=state['routes'][route_id];variant=route['route_variants'][0]
        assert len(variant['transitions'])==len(variant['regions'])-1
        names={};air_index=0
        for key in variant['regions']:
            r=regions[key]
            if key in names:continue
            if r['kind']=='air_volume':
                names[key]=f'A{air_index+1}';air_index+=1
            elif r['kind']=='support_surface':names[key]='/'.join(r['owners']).upper()
            else:names[key]='FINISH'
        palette={k:AIR_COLORS[(int(name[1:])-1)%len(AIR_COLORS)]
                 for k,name in names.items() if name.startswith('A')}
        n=len(variant['regions']);nrows=math.ceil(n/4)
        fig,axes=plt.subplots(nrows,4,figsize=(17.6,nrows*3.65+1.0),layout='constrained')
        occurrences=[]
        text.extend([f'## Route {route_id}', '',f'![Full route {route_id}](full-route{route_id}.png)', '',
                     ' → '.join(f'{i}:{names[k]}' for i,k in enumerate(variant['regions'])), '',
                     '| Occurrence | Region | X bounds | Y bounds | Z bounds | Transition to next |',
                     '| --- | --- | --- | --- | --- | --- |'])
        for i,key in enumerate(variant['regions']):
            ax=axes.flat[i];r=regions[key];label=names[key];boxes=r['cover']
            before=transitions[variant['transitions'][i-1]] if i else None
            after=transitions[variant['transitions'][i]] if i<n-1 else None
            background(ax,request,route['points'])
            if boxes:
                color=palette[key] if r['kind']=='air_volume' else '#4898c2'
                for box in boxes:rectangle(ax,box,facecolor=color,edgecolor=color,alpha=.32,lw=1.4,zorder=1 if r['kind']=='air_volume' else 4)
                # Redraw exact outlines so a zero-width support fragment remains visible.
                for box in boxes:rectangle(ax,box,fill=False,edgecolor=color,lw=1.1,zorder=5)
                bounds=f'X {span(boxes,0)}; Z {span(boxes,2)}'
                y='Y '+span(boxes,1)
                if len(boxes)>1:bounds+=f' ({len(boxes)} patches)'
            else:
                rectangle(ax,[VIEW[0],0,-1095.3,729.7,0,VIEW[3]],facecolor='#85b968',edgecolor='#46753b',alpha=.5,zorder=4)
                bounds='X < 729.7; Z ≥ −1095.3 (unbounded)'
                y='descending height-7 clock'
            for edge,color,ls in [(before,'#008567','--'),(after,'#b13988',':')]:
                if edge:
                    for box in edge['cover']:rectangle(ax,box,fill=False,edgecolor=color,ls=ls,lw=1.6,zorder=7)
            kind=('START ' if i==0 else 'LAND ' if r['kind']=='support_surface' else '')
            exit_label=f"→ {i+1}: {after['kind']}" if after else 'END: task predicate'
            ax.set_title(f'{i}. {kind}{label} · {y}\n{bounds}\n{exit_label}',fontsize=8.3,pad=6)
            if i//4==nrows-1:ax.set_xlabel('X',fontsize=8)
            if i%4==0:ax.set_ylabel('Z',fontsize=8)
            occurrences.append(dict(occurrence=i,label=label,region=r,incoming=before,outgoing=after))
            text.append(f"| {i} | {kind}{label} | {span(boxes,0) if boxes else '<729.7'} | {span(boxes,1) if boxes else 'height7 clock'} | {span(boxes,2) if boxes else '≥−1095.3'} | {after['kind'] if after else 'END'} |")
        for ax in list(axes.flat)[n:]:ax.set_visible(False)
        sequence=' → '.join(names[k] for k in variant['regions'])
        fig.suptitle(f'Full corridor {route_id}: B1 to finish — all {n} region occurrences\n{sequence}\n'
                     'Every panel shows the entire local course. Full saved bounds are in the panel titles; Y identifies the layer.',fontsize=12)
        handles=[Patch(facecolor='#e0a343',alpha=.4,label='Current air region (X/Z projection)'),
                 Patch(facecolor='#4898c2',alpha=.5,label='Current finite support region'),
                 Line2D([0],[0],color='#008567',ls='--',label='Incoming portal/contact'),
                 Line2D([0],[0],color='#b13988',ls=':',label='Outgoing portal/contact')]
        fig.legend(handles=handles,loc='outside lower center',ncol=2,fontsize=9)
        for suffix in ('png','svg'):fig.savefig(HERE/f'full-route{route_id}.{suffix}',dpi=160)
        figures.append(fig)
        audit['routes'].append(dict(id=route_id,occurrences=occurrences))
        text.extend(['',f'[Zoomable SVG](full-route{route_id}.svg)', ''])

    fig,axes=plt.subplots(1,2,figsize=(14.5,7.9),layout='constrained')
    for ax,route_id in zip(axes,(0,26)):
        route=state['routes'][route_id];path=route['route_variants'][0]['regions'];seen=set();color_index=0
        all_boxes=[]
        for key in path:
            r=regions[key]
            if r['kind']!='air_volume' or key in seen:continue
            seen.add(key);all_boxes.extend(r['cover']);color=AIR_COLORS[color_index%len(AIR_COLORS)];color_index+=1
            for box in r['cover']:rectangle(ax,box,facecolor=color,edgecolor=color,lw=1.7,alpha=.18)
        for block in request['blocks']:
            for box in block['boxes']:rectangle(ax,box,facecolor='black',edgecolor='black',lw=.5,zorder=5)
        rectangle(ax,[VIEW[0],0,VIEW[2],VIEW[1],0,VIEW[3]],fill=False,edgecolor='#be3030',lw=1.6,zorder=6)
        x0=min(b[0] for b in all_boxes);x1=max(b[3] for b in all_boxes)
        z0=min(b[2] for b in all_boxes);z1=max(b[5] for b in all_boxes)
        ax.set(xlim=(x0-25,x1+25),ylim=(z0-25,z1+25),xlabel='X',ylabel='Z')
        ax.set_aspect('equal');ax.grid(alpha=.15)
        ax.set_title(f'Route {route_id}: all {len(seen)} distinct air cuboids\nFull X/Z extents, no local course crop',fontsize=11)
        ax.annotate('Entire B1–B7 course / local panel viewport',((VIEW[0]+VIEW[1])/2,(VIEW[2]+VIEW[3])/2),
                    xytext=(x0+75,z1-180),arrowprops=dict(arrowstyle='->',color='#be3030'),fontsize=9,color='#9d2929')
    fig.suptitle('Full spatial extent of the saved air corridors\n'
                 'The finite horizon-derived query envelope makes some cells huge. The terminal X/Z predicate remains unbounded.',fontsize=13)
    for suffix in ('png','svg'):fig.savefig(HERE/f'full-corridor-extents.{suffix}',dpi=160)
    figures.append(fig)
    with PdfPages(HERE/'full-corridors.pdf') as pdf:
        for figure in figures:pdf.savefig(figure)
    for figure in figures:plt.close(figure)
    (HERE/'full-corridors.json').write_text(json.dumps(audit,indent=2)+'\n')
    text.extend(['## Full spatial extents', '', '![Full saved spatial extents](full-corridor-extents.png)', '',
                 '[All pages as vector PDF](full-corridors.pdf). Exact input hashes and full-precision region/portal covers are in the JSON file above. No solve, route generation or runtime change was performed.'])
    (HERE/'FULL_CORRIDORS.md').write_text('\n'.join(text)+'\n')
    print(json.dumps(dict(occurrences={r['id']:len(r['occurrences']) for r in audit['routes']},
                          portals={r['id']:sum(o['outgoing'] is not None for o in r['occurrences']) for r in audit['routes']})))


if __name__=='__main__':main()
