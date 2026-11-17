import copy,gzip,json,math,sys,time
from pathlib import Path
from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.movement_composition import append_choice,boundary_state
from standalone.autonomy.open_movement import normal_choices
from standalone.autonomy.movement_search import iter_movement_programs,movement_settings,screen_program
from standalone.autonomy.cell_routes.checkpoints import Checkpoint
from standalone.autonomy.cell_routes.checkpoint_search import evaluate_program,_motion_bound
from standalone.autonomy.cell_routes.geometry import decompose
from standalone.autonomy.cell_routes.local import CorridorAdapter,SearchWork
from standalone.autonomy.cell_routes.routes import corridor_for_points,landmarks
from standalone.autonomy.cell_routes.support_order import SupportOrder
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.window_experiment import save
fixture=json.loads(Path('standalone/autonomy/experiments/linkcraft11-opening/fixtures/clock64-event-priority.json').read_text())
scene=prepare_scene(fixture['request']);d,gates=decompose(scene),landmarks(scene);corridor=corridor_for_points(d,fixture['spatialPoints'],gates)
adapter=CorridorAdapter(scene,corridor,gates,decomposition=d);policy=SupportOrder(scene,fixture['requiredSupports'],64)
probes=json.loads(gzip.open('standalone/autonomy/build/real11-next-support-probe.json.gz','rt').read())
report=dict(fixture=fixture,runs=[])
def choice(program,target,jump):
 context=dict(scene=scene,state=boundary_state(program),maxInputs=64-program['profile']['airtime'],threshold=0.)
 return next((c for c in normal_choices(context) if c['terminalSupport']==target and c['profile']['jump'][0]==jump),None)
with JavaCompiler(json.loads(Path('standalone/autonomy/build/compiler.json').read_text())) as compiler:
 motion=_motion_bound(scene,compiler,5)
 for seed in probes['runs']:
  if seed['jumps'] not in [[0],[8],[9]] or seed['spatial']['best'] is None:continue
  p=copy.deepcopy(seed['spatial']['program']);p['guideSeedYaw']=seed['spatial']['best']['candidate']['yaw'][:];p.pop('repairState',None)
  history=dict(initialTakeoff=seed['jumps'],stages=[],finals=[]);report['runs'].append(history)
  current='b2';failed=False
  for target,next_target in [('b3','b4'),('b4','b5'),('b5','b7')]:
   base=copy.deepcopy(p);accepted=False
   for delay in range(4):
    q=copy.deepcopy(base)
    for _ in range(delay):
     c=choice(q,current,False)
     if c is None:break
     q=append_choice(q,c,max_inputs=64)
    c=choice(q,target,True)
    if c is None:continue
    q=append_choice(q,c,max_inputs=64)
    b=next(b for b in scene['blocks'] if b['id']==next_target);last=next(b for b in scene['blocks'] if b['id']==target)
    dx=(b['bounds'][0]+b['bounds'][1]-last['bounds'][0]-last['bounds'][1])/2;dz=(b['bounds'][2]+b['bounds'][3]-last['bounds'][2]-last['bounds'][3])/2
    length=math.hypot(dx,dz)
    cp=Checkpoint(q['profile']['airtime'],-1,(dx/length,dz/length),tuple(scene['initial']['position']),tuple(corridor.points[-1]),())
    result=evaluate_program(scene,q,compiler,adapter,SearchWork(24,deadline=time.monotonic()+20),motion,
     checkpoint=cp,seconds=20,candidate_seconds=3,solver_calls=24,python_executable=sys.executable)
    checked=replay_witness(scene,result['best'],compiler) if result['best'] else None
    history['stages'].append(dict(target=target,delay=delay,tick=q['profile']['airtime'],result=result,program=q,replay=checked))
    save(Path('standalone/autonomy/build/real11-constructive-probe.json.gz'),report)
    print(json.dumps(dict(seed=seed['jumps'],target=target,delay=delay,tick=q['profile']['airtime'],status=result['status'],calls=result['work'].get('solverCalls'),replay=checked['passed'] if checked else None)),flush=True)
    if result['best']:
     p=q;p['guideSeedYaw']=result['best']['candidate']['yaw'][:];accepted=True;current=target;break
   if not accepted:failed=True;break
  if failed:continue
  generation={};deadline=time.monotonic()+90;work=SearchWork(128,deadline=deadline)
  def screen(p):return screen_program(scene,p,compiler,2)
  gen=iter_movement_programs(scene,initial_programs=[p],threshold=0.,checkpoint_tick=64,stop_on_goal=False,
   program_filter=policy,program_priority=policy.priority,settings=movement_settings(scene),report=generation,deadline=deadline,screen=screen)
  try:
   for q in gen:
    cp=Checkpoint(64,-1,(0.,1.),tuple(scene['initial']['position']),tuple(corridor.points[-1]),corridor.signature)
    result=evaluate_program(scene,q,compiler,adapter,work,motion,checkpoint=cp,seconds=max(.01,deadline-time.monotonic()),
     candidate_seconds=3,solver_calls=32,python_executable=sys.executable)
    checked=replay_witness(scene,result['best'],compiler) if result['best'] else None
    history['finals'].append(dict(result=result,program=q,replay=checked));save(Path('standalone/autonomy/build/real11-constructive-probe.json.gz'),report)
    print(json.dumps(dict(seed=seed['jumps'],terminal=64,status=result['status'],calls=result['work'].get('solverCalls'),z=result['best']['candidate']['positions'][-1][1] if result['best'] else None,replay=checked['passed'] if checked else None)),flush=True)
    if result['best'] or len(history['finals'])>=8:break
  finally:gen.close()
  history['generation']=generation
  save(Path('standalone/autonomy/build/real11-constructive-probe.json.gz'),report)
