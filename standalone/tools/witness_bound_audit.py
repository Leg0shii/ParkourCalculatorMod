"""Check current optimistic bounds against every prefix of archived valid finishes.

This is a retained-witness control, never a discovery seed or a global proof.
"""
import argparse
import hashlib
import json
from pathlib import Path

from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.io.checkpoint_store import read_report
from parkour_solver.search.anchors.policy import visits
from parkour_solver.search.bounds.remaining import remaining_bound
from parkour_solver.search.bounds.flight import transfer_bounds
from parkour_solver.reachability.service import InteractionGate
from parkour_solver.movement.program import truncate_program


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reports',type=Path,nargs='+',required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    if args.output.exists():raise ValueError('Use a new evidence path')
    records=[]
    for path in args.reports:
        result=read_report(path)['result'];state=result['searchState']
        scene=prepare_scene(result['effectiveRequest']);guides=state['guidePlan'];motion=state['motion']
        program=result['best']['movementProgram'];finish=program['profile']['airtime']
        table=transfer_bounds(scene,guides,motion,InteractionGate(scene,object()))
        contacts=visits(scene,program);anchors=guides['anchors']
        times=[next(t for t,name in contacts if name==a) for a in anchors]
        checks=[]
        for tick in range(1,finish+1):
            span=next((i for i,t in enumerate(times[1:]) if tick<=t),len(anchors)-2)
            p=truncate_program(program,tick)
            owner=next((name for t,name in reversed(contacts) if t<=tick),anchors[0])
            p['terminalSupport']=owner if p['profile']['grounded'][-1] else None
            proof=remaining_bound(guides,span,p,motion,transfers=table,hard_latches=table)
            optimistic=tick+proof['remainingTicks']
            assert optimistic<=finish,(str(path),tick,optimistic,finish)
            checks.append(dict(tick=tick,span=span,optimisticFinish=optimistic,
                horizontal=proof['horizontalTicks'],vertical=proof['verticalTicks']))
        records.append(dict(report=str(path),manifestSHA256=hashlib.sha256(path.read_bytes()).hexdigest(),
            finish=finish,prefixes=checks,passed=True))
    args.output.write_text(json.dumps(dict(scope='Archived accepted-witness prefix control; not seeded into search.',
        assumption='hard_transfer_without_stepping',records=records),indent=2,allow_nan=False)+'\n')
    print(json.dumps(dict(finishes=[r['finish'] for r in records],prefixes=sum(len(r['prefixes']) for r in records),passed=True)))


if __name__=='__main__':main()
