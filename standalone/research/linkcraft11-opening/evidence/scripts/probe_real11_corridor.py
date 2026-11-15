import copy,gzip,json,sys,time
from pathlib import Path
from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.cell_routes.checkpoint_search import _motion_bound,evaluate_program
from standalone.autonomy.cell_routes.checkpoints import Checkpoint
from standalone.autonomy.cell_routes.geometry import decompose
from standalone.autonomy.cell_routes.local import CorridorAdapter,SearchWork
from standalone.autonomy.cell_routes.routes import corridor_for_points,landmarks
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.window_experiment import save
source=json.loads(gzip.open('standalone/autonomy/build/real11-clock64-event-priority.json.gz','rt').read());f=source['fixture'];scene=prepare_scene(f['request']);d,gates=decompose(scene),landmarks(scene)
corridor=corridor_for_points(d,f['spatialPoints'],gates)
w=source['result']['windows'][2]
record=next(c for c in w['candidates'] if [i for i,b in enumerate(c['program']['profile']['jump']) if b]==[9,18,27,37])
p=copy.deepcopy(record['program']);p.pop('repairState',None)
cp=w['checkpoint'];target=Checkpoint(cp['tick'],-1,tuple(cp['direction']),tuple(cp['start']),tuple(cp['end']),())
report=dict(candidate=record['id'],runs=[])
class SceneOnly:
 def candidate(self,*args,**kwargs):return dict(accepted=True,validation='shared_scalar_and_finite_scene_only')
with JavaCompiler(json.loads(Path('standalone/autonomy/build/compiler.json').read_text())) as compiler:
 motion=_motion_bound(scene,compiler,5)
 for name in ['prescribed','no_signature','scene_only']:
  route=copy.deepcopy(corridor)
  if name=='no_signature':route.signature=()
  adapter=SceneOnly() if name=='scene_only' else CorridorAdapter(scene,route,[] if name=='no_signature' else gates,decomposition=d)
  result=evaluate_program(scene,p,compiler,adapter,SearchWork(48,deadline=time.monotonic()+25),motion,
   checkpoint=target,seconds=25,candidate_seconds=2,solver_calls=48,python_executable=sys.executable)
  replay=replay_witness(scene,result['best'],compiler) if result['best'] else None
  report['runs'].append(dict(mode=name,result=result,replay=replay))
  save(Path('standalone/autonomy/build/real11-corridor-probe.json.gz'),report)
  print(json.dumps(dict(mode=name,status=result['status'],termination=result['termination'],calls=result['work'].get('solverCalls'),
   objective=result['best']['candidate']['objective'] if result['best'] else None,
   audits=[dict(filter=a.get('candidateFilter'),geometry=a.get('geometryCheck'),conflicts=a.get('conflicts')) for a in result.get('contactAttempts',[])[:3]],
   replay=replay['passed'] if replay else None)),flush=True)
