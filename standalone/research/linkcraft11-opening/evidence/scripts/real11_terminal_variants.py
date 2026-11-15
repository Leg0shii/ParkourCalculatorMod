import copy,gzip,json,sys,time
from pathlib import Path
from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.movement_composition import append_choice,boundary_state
from standalone.autonomy.open_movement import event_choice
from standalone.autonomy.cell_routes.checkpoints import Checkpoint
from standalone.autonomy.cell_routes.checkpoint_search import evaluate_program,_motion_bound
from standalone.autonomy.cell_routes.geometry import decompose
from standalone.autonomy.cell_routes.local import CorridorAdapter,SearchWork
from standalone.autonomy.cell_routes.routes import corridor_for_points,landmarks
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.window_experiment import save
source=json.loads(gzip.open('standalone/autonomy/build/real11-constructive-probe.json.gz','rt').read());scene=prepare_scene(source['fixture']['request']);d,gates=decompose(scene),landmarks(scene)
corridor=corridor_for_points(d,source['fixture']['spatialPoints'],gates);adapter=CorridorAdapter(scene,corridor,gates,decomposition=d)
cp=Checkpoint(64,-1,(0.,1.),tuple(scene['initial']['position']),tuple(corridor.points[-1]),corridor.signature)
report=dict(fixture=source['fixture'],runs=[])
with JavaCompiler(json.loads(Path('standalone/autonomy/build/compiler.json').read_text())) as compiler:
 motion=_motion_bound(scene,compiler,5)
 for row in source['runs']:
  stage=next((s for s in row['stages'] if s['target']=='b5' and s['result']['best']),None)
  if stage is None:continue
  p=copy.deepcopy(stage['program']);p['guideSeedYaw']=stage['result']['best']['candidate']['yaw'][:]
  full=next((r for r in row['finals'] if r['result']['best']),None)
  for jump in [True,False]:
   choice=event_choice(dict(threshold=0.),'normal',boundary_state(p),64-p['profile']['airtime'],jumping=jump)
   q=append_choice(p,choice,max_inputs=64)
   if full:q['guideSeedYaw']=full['result']['best']['candidate']['yaw'][:]
   result=evaluate_program(scene,q,compiler,adapter,SearchWork(96,deadline=time.monotonic()+45),motion,
    checkpoint=cp,seconds=45,candidate_seconds=4,solver_calls=96,python_executable=sys.executable)
   replay=replay_witness(scene,result['best'],compiler) if result['best'] else None
   report['runs'].append(dict(initialTakeoff=row['initialTakeoff'],jumpAfterB5=jump,program=q,result=result,replay=replay))
   save(Path('standalone/autonomy/build/real11-terminal-variants.json.gz'),report)
   print(json.dumps(dict(seed=row['initialTakeoff'],jump=jump,status=result['status'],termination=result['termination'],calls=result['work'].get('solverCalls'),
    z=result['best']['candidate']['positions'][-1][1] if result['best'] else None,replay=replay['passed'] if replay else None)),flush=True)
