import sys
import time

from parkour_solver.constraints.encounters import landing_segments
from parkour_solver.constraints.variants import ConstraintVariants
from parkour_solver.reachability.intervals import reachable_envelopes
from parkour_solver.reachability.service import InteractionGate


def run(ticks, segments, solids, table):
    y = [1.]*(ticks+1)
    grounded = [t % (ticks//segments) == 0 for t in range(ticks+1)]
    model = dict(ticks=ticks, position=[0., 0.], velocity=[0., 0.], magnitude=[.1]*ticks,
                 friction=[.9]*ticks, walls=[], threshold=0.)
    boxes = [[0., 0., 0., 1., 1., 1.]]+[[1000.+3*i, 0., 1000., 1001.+3*i, 3., 1001.] for i in range(solids)]
    scene = dict(collisionScene=dict(solids=boxes), blocks=[dict(id='start', sourceBlock='start', solidIndex=0, top=1.)]+[
        dict(id='far%d' % i, sourceBlock='far%d' % i, solidIndex=i+1, top=3.) for i in range(solids)])
    profile = dict(position=y, grounded=grounded)
    started = time.perf_counter()
    reachable = None
    if table:
        entry = InteractionGate(scene, None).reachable_blocks('start')
        reachable = {begin: sorted(entry['solids']) for begin, end in landing_segments(profile, ticks)
                     if end-begin <= entry['ticks']}
    owner = ConstraintVariants(dict(supportClearance=0.), profile, dict(events=[dict(jump=0, runTicks=0)]),
        dict(solids=boxes), reachable_envelopes(model, y), dict(through=ticks, solidIndices=list(range(len(boxes)))),
        segment_solids=reachable)
    variants = list(owner)
    metrics = owner.state['metrics']
    return dict(items=sum('clusterId' in i for i in owner.items), prepared=metrics.get('preparedEncounters', 0),
                phaseChecks=metrics.get('phaseChecks', 0), variants=len(variants), seconds=time.perf_counter()-started)


if __name__ == '__main__':
    ticks, segments, modes = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3]
    for solids in map(int, sys.argv[4:]):
        for table in (False, True) if modes == 'both' else (True,):
            r = run(ticks, segments, solids, table)
            print(f"solids {solids:5d} table {str(table):5s} items {r['items']:6d} prepared {r['prepared']:6d} "
                  f"phaseChecks {r['phaseChecks']:8d} variants {r['variants']} seconds {r['seconds']:.3f}", flush=True)
