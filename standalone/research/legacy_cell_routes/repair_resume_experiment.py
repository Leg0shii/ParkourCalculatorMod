from tools.paths import RESEARCH_ROOT, DEFAULT_COMPILER
import argparse
import copy
from pathlib import Path
import subprocess
import sys
import time

from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.bridge.compiler import JavaCompiler
from research.legacy_cell_routes.checkpoint_experiment import replay_witness
from parkour_solver.search.engine import _motion_bound, evaluate_program
from parkour_solver.spatial.checkpoints import retain_unresolved
from research.legacy_cell_routes.failure_diagnostic import read
from research.legacy_cell_routes.generation_diagnostic import save
from parkour_solver.spatial.cells import decompose
from parkour_solver.validation.scoped_scene import CorridorAdapter
from parkour_solver.search.work import SearchWork
from parkour_solver.spatial.compat.corridors import corridor_for_points, landmarks


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--compiler',type=Path,default=DEFAULT_COMPILER)
    parser.add_argument('--input',type=Path,default=RESEARCH_ROOT/'failure-fixes/evidence/fix-generation.json.gz')
    args=parser.parse_args()
    source=next(r for r in read(args.input)['runs'] if r['name']=='turn-blockage')
    programs=[c for c in source['completed'] if c['ticks'] in (12,16)]
    report=dict(source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),runs=[])
    with JavaCompiler(read(args.compiler)) as compiler:
        for candidate in programs:
            for mode in ('restart','resume','single'):
                scene=prepare_scene(source['request'])
                d,gates=decompose(scene),landmarks(scene)
                adapter=CorridorAdapter(scene,corridor_for_points(d,source['spatialPoints'],gates),gates,decomposition=d)
                motion=_motion_bound(scene,compiler,2)
                work=SearchWork(12,deadline=time.monotonic()+12)
                program=copy.deepcopy(candidate['program'])
                attempts=[]
                for index in range(1 if mode=='single' else 3):
                    result=evaluate_program(scene,program,compiler,adapter,work,motion,
                        seconds=6 if mode=='single' else 2,candidate_seconds=.7,
                        solver_calls=12 if mode=='single' else 4,python_executable=sys.executable)
                    item=dict(result=result,work=work.summary())
                    if result['best']:
                        item['independentReplay']=replay_witness(scene,result['best'],compiler)
                        if not item['independentReplay']['passed']:
                            raise RuntimeError('Resumed candidate failed independent replay')
                    attempts.append(item)
                    if result['best'] or work.termination:
                        break
                    if mode=='resume':
                        program=retain_unresolved(program,result['repairState'])
                record=dict(ticks=candidate['ticks'],mode=mode,attempts=attempts,work=work.summary(),
                    status=attempts[-1]['result']['status'],request=source['request'],program=candidate['program'])
                report['runs'].append(record)
                save(args.output,report)
                print(dict(ticks=candidate['ticks'],mode=mode,status=record['status'],calls=work.counts['solverCalls'],
                    resumed=[a['result']['repairResume'] for a in attempts]),flush=True)


if __name__=='__main__':
    main()
