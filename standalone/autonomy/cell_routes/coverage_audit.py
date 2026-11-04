"""Audit an archived witness against unseeded automatic spatial discovery."""

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import subprocess

from ..block_search import prepare_scene
from ..compiler import JavaCompiler
from .checkpoint_experiment import replay_witness
from .geometry import decompose, diagnostic_svg as cell_svg
from .regions import annotate_normal_proposals, build_route_graph, diagnostic_svg
from .route_queue import collision_request
from .routes import enumerate_routes
from .semantic_adapter import SemanticCorridorAdapter
from .window_experiment import save


def audit(fixture,compiler,witness):
    request=fixture['request']
    goal=fixture.get('terminalGoal')
    if goal and goal['kind']=='landing_height_region':
        request=collision_request(request,compiler,5)
    scene=prepare_scene(request)
    d=decompose(scene)
    g=annotate_normal_proposals(scene,build_route_graph(scene,d,goal),compiler,seconds=5)
    routes,generation=enumerate_routes(scene,d,route_graph=g,**fixture.get('spatialSettings',{}))
    # Witness controls/positions first enter the pipeline after enumeration.
    replay=replay_witness(scene,witness,compiler)
    verdicts=[]
    for route in routes:
        adapter=SemanticCorridorAdapter(scene,route,decomposition=d)
        view=adapter.for_goal(len(adapter.goals())-1).bind_program(witness['movementProgram'])
        checked=view.candidate(witness['plan'],witness['candidate'],dict(y=witness['candidate']['y']))
        verdicts.append(dict(route=route.id,goals=adapter.goals(),accepted=checked['accepted'],
                             progress=checked['routeProgress']))
    return dict(request=request,geometry=d.to_dict(),graph=g.to_dict(),routeGeneration=generation,
                routes=[r.to_dict(include_graph=False) for r in routes],witnessReplay=replay,
                classifications=verdicts,acceptedRoutes=[v['route'] for v in verdicts if v['accepted']],
                interpretation='Unseeded spatial coverage only; no movement-search seed or numerical optimization'),d,g


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--witness',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--compiler',type=Path,required=True)
    args=parser.parse_args()
    fixture=json.loads(args.input.read_text())
    data=args.witness.read_bytes()
    archived=json.loads(gzip.decompress(data) if args.witness.suffix=='.gz' else data)
    witness=archived.get('result',archived).get('best')
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        report,d,g=audit(fixture,compiler,witness)
    report.update(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
                  fixture=str(args.input),witness=str(args.witness),witnessHash=hashlib.sha256(data).hexdigest())
    from .automatic_experiment import source_hashes
    report['sourceHashes']=source_hashes()
    save(args.output,report)
    bounds=fixture['request'].get('geometryBounds',fixture['request'].get('collisionScene',{}).get('bounds'))
    args.output.with_suffix('.cells.svg').write_text(cell_svg(d,y=6,bounds=bounds))
    args.output.with_suffix('.regions.svg').write_text(diagnostic_svg(g,y=6,bounds=bounds,support_all_heights=True))
    print(json.dumps(dict(routes=len(report['routes']),accepted=report['acceptedRoutes'],replay=report['witnessReplay']['passed'])))


if __name__=='__main__':main()
