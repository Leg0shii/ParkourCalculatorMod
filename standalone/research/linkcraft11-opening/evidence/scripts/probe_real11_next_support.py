import gzip,json,time
from pathlib import Path
from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.cell_routes.checkpoint_search import _motion_bound
from standalone.autonomy.cell_routes.comparison_goals import probe_spatial
from standalone.autonomy.cell_routes.geometry import decompose
from standalone.autonomy.cell_routes.local import CorridorAdapter,SearchWork
from standalone.autonomy.cell_routes.routes import corridor_for_points,landmarks
from standalone.autonomy.cell_routes.support_order import SupportOrder
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.window_experiment import save
import sys
source=json.loads(gzip.open('standalone/autonomy/build/real11-clock64-first.json.gz','rt').read())
f=source['fixture'];scene=prepare_scene(f['request']);d,gates=decompose(scene),landmarks(scene)
c=corridor_for_points(d,f['spatialPoints'],gates);adapter=CorridorAdapter(scene,c,gates,decomposition=d)
policy=SupportOrder(scene,f['requiredSupports'],64)
report=dict(sourceSearch='real11-clock64-first.json.gz',goal='b2',runs=[])
with JavaCompiler(json.loads(Path('standalone/autonomy/build/compiler.json').read_text())) as compiler:
 motion=_motion_bound(scene,compiler,5)
 for record in source['result']['windows'][0]['candidates']:
  p=record['program'];j=[i for i,b in enumerate(p['profile']['jump']) if b]
  if j not in [[0],[3],[6],[8],[9]]:continue
  result=probe_spatial(scene,p,compiler,adapter,SearchWork(24,deadline=time.monotonic()+20),motion,
   spec=dict(kind='landing',block='b2'),seconds=20,candidate_seconds=2,solver_calls=24,max_candidates=8,
   max_prefixes=256,python_executable=sys.executable,program_filter=policy)
  replay=replay_witness(scene,result['best'],compiler) if result['best'] else None
  report['runs'].append(dict(jumps=j,spatial=result,replay=replay))
  save(Path('standalone/autonomy/build/real11-next-support-probe.json.gz'),report)
  print(json.dumps(dict(jumps=j,ticks=result['measurement']['ticks'],work=result['measurement']['work'],attempts=result['measurement']['attempts'])),flush=True)
