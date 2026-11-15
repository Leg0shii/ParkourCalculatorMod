"""Optional plotting of runtime turnaround observation records."""


def draw(guides, observations, destination, *, trajectory=None):
    """Export inspectable edge/margin/point geometry without changing the run."""
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib.patches import Polygon, Rectangle
    fig,ax=plt.subplots(figsize=(10,8))
    points=guides['points']
    ax.plot([p[0] for p in points],[p[2] for p in points],'--',color='gray',label='backward guides')
    for a,b in zip(points,points[1:]):
        ax.annotate('',xy=(b[0],b[2]),xytext=(a[0],a[2]),arrowprops=dict(arrowstyle='->',color='gray'))
    for boxes in guides.get('physicalFootprints',{}).values():
        for b in boxes:
            ax.add_patch(Rectangle((b[0],b[2]),b[3]-b[0],b[5]-b[2],color='gray',alpha=.2))
    for name,cover in guides['covers'].items():
        for b in cover:ax.add_patch(Rectangle((b[0],b[2]),b[3]-b[0],b[5]-b[2],fill=False,linestyle=':',alpha=.4))
    if trajectory:
        ax.plot([p[0] for p in trajectory],[p[1] for p in trajectory],color='green',linewidth=1.,label='validated trajectory')
    for turn in guides['turns']:
        n=turn['normal']; tangent=(-n[1],n[0]);c=turn['offset'];m=turn['margin']
        # Draw on a local tangent interval covering E, rather than an infinite
        # line that visually assigns unrelated route neighborhoods a mode.
        tangents=[sum(a*b for a,b in zip(tangent,p)) for p in turn['footprint']]
        lo,hi=min(tangents)-.5,max(tangents)+.5
        def at(offset,t):return [n[a]*offset+tangent[a]*t for a in (0,1)]
        for offset,style in ((c,'-'),(c-m,':')):
            a,b=at(offset,lo),at(offset,hi)
            ax.plot([a[0],b[0]],[a[1],b[1]],style,color='purple')
        poly=[at(c-m,lo),at(c-m,hi),at(c+1,hi),at(c+1,lo)]
        ax.add_patch(Polygon(poly,color='purple',alpha=.08))
    for e in observations.get('decisions',{}).values():
        x,z=e['point'];ax.scatter(x,z,c='orange' if e['isTurnaround'] else 'blue',s=18)
        for r in e['replays']:
            if r['wouldFlip']:ax.scatter(*r['point'],marker='x',c='red')
    for name,p in zip(guides['anchors'],points):ax.annotate(name,(p[0],p[2]))
    ax.set(xlabel='X',ylabel='Z',title='Purple: approach edge / dotted margin; blue: ordinary; orange: turnaround; red ×: replay flip')
    ax.legend();ax.set_aspect('equal',adjustable='datalim');fig.tight_layout();fig.savefig(destination);plt.close(fig)
