from tools.paths import RESEARCH_ROOT, MOVEMENT_FIXTURES, DEFAULT_COMPILER
import argparse
import copy
import json
import math
from pathlib import Path
import subprocess
import sys
import time
from unittest.mock import patch

from parkour_solver.constraints.contacts import contact_conflicts, floor_checks
from parkour_solver.search.repair import solve_contact_branches; from parkour_solver.validation.vertical import validate_vertical_schedule
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.movement.program import compile_program, truncate_program
from parkour_solver.search.movement import iter_movement_programs, screen_program
from parkour_solver.movement.settings import movement_settings
from parkour_solver.validation.scene import check_geometry
from parkour_solver.scene.route import prepare_route, schedule, supports
import parkour_solver.search.ordered_route as search
from parkour_solver.numerics import prepare, validate
from parkour_solver.io.checkpoint_store import read_report
from parkour_solver.search.engine import _motion_bound
from parkour_solver.spatial.checkpoints import checkpoint, retain_prefix
from parkour_solver.search.compat.decision_windows import DecisionPolicy, plan_windows
from research.legacy_cell_routes.generation_diagnostic import KeyAudit, QueueAudit, instrumented_generator, save
from parkour_solver.spatial.cells import decompose
from parkour_solver.validation.scoped_scene import CorridorAdapter
from parkour_solver.bridge.counting import CountingCompiler; from parkour_solver.search.work import SearchWork
from parkour_solver.spatial.compat.corridors import audit_candidate, corridor_for_points, landmarks


ROOT = RESEARCH_ROOT


def read(path):
    return read_report(path)


def generation_scenes():
    for path in sorted((ROOT/'decision-windows/fixtures').glob('*.json')):
        fixture = read(path)
        yield path.stem,fixture['request'],fixture['spatialPoints'],fixture['opportunities']
    controls = ROOT/'cell-routes/controls'
    for name in ('run-up','remote-supports','grind'):
        request = read(ROOT/'cell-routes/evidence/witnesses'/(name+'-prescribed.json'))['request']
        yield 'extra-'+name,request,read(controls/(name+'-route.json')),None
    path = MOVEMENT_FIXTURES/'generic-repeat-ceiling.json'
    yield 'extra-repeat-ceiling',read(path),None,None


def run_generation(compiler, policy, canonical, *, expansions=512, queue_cap=384, proposals=12):
    results = []
    for name,request,points,opportunities in generation_scenes():
        scene = prepare_scene(dict(request,maxWallHits=0))
        cfg = movement_settings(scene)
        cfg.update(maxPrefixes=expansions,maxQueuedPrefixes=queue_cap)
        cfg['types'] = [k for k in cfg['types'] if k in ('normal','ceiling','stepping')]
        cfg['maxInputs'] = min(40,cfg['maxInputs'])
        counted = CountingCompiler(compiler)
        adapter = None
        if points is not None:
            d,gates = decompose(scene),landmarks(scene)
            adapter = CorridorAdapter(scene,corridor_for_points(d,points,gates),gates,decomposition=d)
        domain = DecisionPolicy(plan_windows(opportunities,gap=1,max_inputs=cfg['maxInputs']),scene) if opportunities is not None else None
        queue,keys = QueueAudit(scene['goalBlock'],policy),KeyAudit(canonical)
        generate = iter_movement_programs if policy == 'balanced' else instrumented_generator(queue,keys)
        report,completed = {},[]
        def screen(program):
            if program['profile']['position'][1] < scene['initial']['position'][1]-1e-7 and any(
                    abs(b['top']-scene['initial']['position'][1])<1e-7 and supports(b,*scene['initial']['position'][::2])
                    for b in scene['blocks']):
                return dict(reason='unselected_initial_floor')
            return screen_program(scene,program,counted,2)
        started = time.monotonic()
        generator = generate(scene,settings=cfg,deadline=started+30,report=report,screen=screen,
            context_adapter=adapter.context if adapter else None,threshold=0.,program_filter=domain)
        for program in generator:
            completed.append(dict(ticks=program['profile']['airtime'],expandedAtYield=report['expandedPrefixes'],
                methods=[u.get('case',u['type']) for u in program['movementUnits']],program=program))
            if len(completed)>=proposals:
                break
        generator.close()
        equivalence = []
        for a,b in keys.samples:
            aa,bb = [compile_program(scene,p,require_complete=False) for p in (a,b)]
            routes = [prepare_route(c['route']) for c in (aa,bb)]
            plans = [schedule(r,[0]) for r in routes]
            vertical = [validate_vertical_schedule(r,c['profiles'],p) for r,c,p in zip(routes,(aa,bb),plans)]
            for p,v in zip(plans,vertical):
                p['vertical'] = v['vertical']
            coefficients = [counted.compile(c['route'],p,2) for c,p in zip((aa,bb),plans)]
            rows = [[{k:v for k,v in r.items() if k!='name'} for r in p['walls']] for p in plans]
            same = coefficients[0]==coefficients[1] and sorted(rows[0],key=repr)==sorted(rows[1],key=repr)
            same &= all(plans[0].get(k)==plans[1].get(k) for k in ('contacts','sceneContacts','supportChecks','exitChecks'))
            same &= vertical[0]==vertical[1]
            equivalence.append(dict(passed=same,ticks=a['profile']['airtime']))
        results.append(dict(name=name,policy=policy,canonical=canonical,config=cfg,
            request=copy.deepcopy(request),spatialPoints=points,
            context='prescribed_corridor' if adapter else 'unconditioned_scene',opportunityDomains=opportunities,
            firstCompleteExpanded=completed[0]['expandedAtYield'] if completed else None,
            completed=completed,generation=report,queue=(dict(policy='balanced', instrumentation=False) if policy == 'balanced' else queue.summary()),metadataDuplicates=keys.metadata_duplicates,
            uniqueLegacyKeys=len(keys.legacy),uniquePhysicalKeys=len(keys.physical),equivalence=equivalence,
            compiler=counted.summary(),instrumentedSeconds=time.monotonic()-started))
        print(json.dumps({k:v for k,v in results[-1].items() if k in
            ('name','policy','canonical','firstCompleteExpanded','metadataDuplicates','instrumentedSeconds')}),flush=True)
    return results


class CandidateAudit:
    def __init__(self, scene, compiled, compiler, corridor=None, gates=(), cp=None):
        self.scene,self.compiler,self.corridor,self.gates,self.cp = scene,compiler,corridor,gates,cp
        self.route = prepare_route(compiled['route'])
        if hasattr(compiler,'derive_footprints'):
            for b,footprint in zip(self.route['blocks'],compiler.derive_footprints(self.route,2)):
                b['_derivedFootprint'] = footprint
        self.plan = schedule(self.route,[0])
        self.vertical = validate_vertical_schedule(self.route,compiled['profiles'],self.plan)
        self.checks = floor_checks(scene,[scene['startBlock'],scene['goalBlock']],compiled['profiles'])

    def __call__(self, problem, yaw):
        candidate = validate(problem,yaw)
        result = dict(scalar=candidate['feasible'],vertical=self.vertical['feasible'])
        if not all(result.values()):
            return dict(result,passed=False)
        plan = dict(self.plan,walls=problem['walls'],contacts=problem['contacts'])
        result['supports'] = all(supports(self.route['blocks'][i],*candidate['positions'][t],kind=k)
                                 for t,i,k in plan['supportChecks'])
        result['exits'] = all(not supports(self.route['blocks'][i],*candidate['positions'][t],kind='run')
                             for t,i in plan['exitChecks'])
        result['floors'] = not contact_conflicts(self.checks,plan,candidate)
        candidate.update({k:self.vertical[k] for k in ('y','vy','onGround')})
        terminal_pre = any(t==plan['ticks']-1 and k=='landing' for t,_,k in plan['supportChecks'])
        result['corridor'] = self.corridor is None or audit_candidate(self.corridor,candidate,self.gates,
            partial=not self.route.get('terminalSupportRequired',True),terminal_pre=terminal_pre)['accepted']
        result['stage'] = self.cp is None or self.cp.stage(candidate,self.gates)['accepted']
        candidate['rawMovement'] = [[(v if abs(v)>=problem['threshold'] else 0)+problem['magnitude'][t]*q
            for v,q in zip(candidate['velocity'][t],(math.cos(problem['base'][t]+math.radians(y)),
                                                    math.sin(problem['base'][t]+math.radians(y))))]
            for t,y in enumerate(yaw)]
        candidate['friction'] = problem['friction']
        result['geometry'] = check_geometry(self.scene['collisionScene'],plan,candidate,self.vertical,self.compiler,2)['feasible']
        return dict(result,passed=all(result.values()),objective=candidate['objective'])


def warm_controls():
    main = read(ROOT/'checkpoint-guidance/evidence/checkpoint-guidance-final.json.gz')
    greedy = read(ROOT/'checkpoint-guidance/evidence/checkpoint-guidance-greedy-final.json.gz')
    for name,report,arm,label in [(n,main,'guided','main') for n in
            ('open','ceiling','step-or-jump','grind-or-run','multi-support','turn-blockage')]+[
                ('ceiling',greedy,'guided','late_jump'),('step-or-jump',greedy,'guided','slab'),
                ('grind-or-run',greedy,'guided','short_chain'),('turn-blockage',greedy,'guided','alternate_turn')]:
        known = next(r['result']['best'] for r in report['runs'] if r['name']==name and r['repeat']==0 and r['result']['arm']==arm)
        fixture = read(ROOT/'checkpoint-guidance/fixtures'/f'{name}.json')
        horizon = 10 if name=='turn-blockage' else min(10,known['plan']['ticks']-1)
        yield name+'-'+label,fixture['request'],fixture['spatialPoints'],known,horizon
    for name,stem in [('run-up','run-up'),('remote-supports','remote-supports'),('normal-step','normal-step'),('grind','grind')]:
        saved = read(ROOT/'cell-routes/evidence/witnesses'/(name+'-prescribed.json'))
        known = saved['best']
        yield 'extra-'+name,saved['request'],read(ROOT/'cell-routes/controls'/(stem+'-route.json')),known,min(4,known['plan']['ticks'])


def run_warm(compiler, mode='baseline'):
    records = []
    original_solve = search.solve
    inputs = []
    for case in warm_controls():
        inputs.append((case,'known'))
        if case[0] in ('turn-blockage-main','step-or-jump-main','extra-grind','extra-run-up'):
            inputs.append((case,'perturbed'))
    for (name,request,points,known,h),seed_kind in inputs:
        scene = prepare_scene(dict(request,maxWallHits=0))
        program = truncate_program(known['movementProgram'],h)
        program['threshold'] = program['profile']['threshold'] = 0.
        program['guideSeedYaw'] = known['candidate']['yaw'][:h]
        if seed_kind=='perturbed':
            program['guideSeedYaw'] = [v+180 for v in program['guideSeedYaw']]
        compiled = compile_program(scene,program,require_complete=False)
        d,gates = decompose(scene),landmarks(scene)
        corridor = corridor_for_points(d,points,gates)
        cp = checkpoint(corridor,gates,dict(tick=h))
        for direction in ('forward','reverse'):
            route = copy.deepcopy(compiled['route'])
            objective = cp.objective
            if direction=='reverse':
                objective['customYaw'] += 180
            route['guideObjective'] = objective
            local = dict(route=route,profiles=compiled['profiles'])
            audit = CandidateAudit(scene,local,compiler,corridor,gates)
            calls,validated_seeds = [],[]
            def traced(problem,**kwargs):
                prepared = prepare(problem)
                seed = validate(prepared,prepared['yaw'])
                seed_audit = audit(prepared,seed['yaw'])
                result = original_solve(problem,**kwargs)
                output_audit = audit(prepared,result['candidate']['yaw']) if result['feasible'] else dict(passed=False)
                fallback = mode=='preserve' and seed_audit['passed'] and not output_audit['passed']
                if mode=='two_channel' and seed_audit['passed']:
                    retained = retain_prefix(program,dict(plan=dict(walls=prepared['walls']),candidate=seed))
                    accepted = compile_program(scene,retained,require_complete=False)
                    accepted['route']['guideObjective'] = objective
                    candidate = copy.deepcopy(seed)
                    candidate.update({k:audit.vertical[k] for k in ('y','vy','onGround')})
                    validated_seeds.append(dict(route=accepted['route'],profiles=accepted['profiles'],candidate=candidate,
                        plan=schedule(prepare_route(accepted['route']),[0]),cost=0,allocation=[0]))
                calls.append(dict(seedAudit=seed_audit,outputAudit=output_audit,fallback=fallback,
                    inputYaw=prepared['yaw'],outputYaw=result['candidate']['yaw'] if result['feasible'] else None,
                    objective=prepared['objective'],termination=result['termination'],problem=prepared,
                    solveSeconds=kwargs.get('seconds'),stopOnFeasible=kwargs.get('stop_on_feasible')))
                return dict(result,status='feasible',feasible=True,candidate=seed) if fallback else result
            adapter = CorridorAdapter(scene,corridor,gates,decomposition=d)
            motion = _motion_bound(scene,compiler,2)
            work = SearchWork(4,deadline=time.monotonic()+3)
            with patch('parkour_solver.search.ordered_route.solve',traced):
                result = solve_contact_branches(route,compiled['profiles'],audit.checks,compiler,seconds=3,
                    candidate_seconds=.7,retries=0,improve=True,
                    rank_equal_cost=True,optimize_goal=mode!='feasibility_first',python_executable=sys.executable,
                    geometry=scene['collisionScene'],motion_bound=motion,solid_blocks=[b['sourceBlock'] for b in scene['blocks']],
                    max_wall_hits=0,candidate_filter=lambda p,c,v:adapter.candidate(p,c,v,partial=True),work=work)
            if mode=='two_channel' and validated_seeds:
                seed_best = max(validated_seeds,key=lambda b:b['candidate']['objective'])
                if result['best'] is None or seed_best['candidate']['objective']>result['best']['candidate']['objective']:
                    result.update(status='feasible',best=seed_best,incumbentSource='full_validated_seed')
            record = dict(name=name,h=h,direction=direction,mode=mode,seedKind=seed_kind,status=result['status'],
                termination=result['termination'],calls=calls,work=work.summary(),
                firstSeedValid=calls[0]['seedAudit']['passed'] if calls else False,
                lostValidSeed=any(c['seedAudit']['passed'] and not c['outputAudit']['passed'] for c in calls),
                candidateFilter=adapter.summary(),result=result,request=request,program=program,spatialPoints=points)
            if result['best']:
                from research.legacy_cell_routes.checkpoint_experiment import replay_witness
                record['independentReplay'] = replay_witness(scene,result['best'],compiler)
                if not record['independentReplay']['passed']:
                    raise RuntimeError('Research candidate failed independent replay')
            records.append(record)
            print(json.dumps({k:v for k,v in record.items() if k in
                ('name','h','direction','mode','seedKind','status','firstSeedValid','lostValidSeed')}),flush=True)
    return records


def verify_generation(path, compiler):
    from parkour_solver.search.engine import evaluate_program
    from research.legacy_cell_routes.checkpoint_experiment import replay_witness
    records = []
    chosen = ('grind-or-run','turn-blockage','multi-support','extra-grind','extra-repeat-ceiling')
    for run in read(path)['runs']:
        if run['name'] not in chosen or run['canonical'] and run['policy']=='shortest':
            continue
        scene = prepare_scene(dict(run['request'],maxWallHits=0))
        if run['spatialPoints'] is not None:
            d,gates = decompose(scene),landmarks(scene)
            adapter = CorridorAdapter(scene,corridor_for_points(d,run['spatialPoints'],gates),gates,decomposition=d)
        else:
            class NoCorridor:
                def candidate(self,*args,**kwargs):
                    return dict(accepted=True)
            adapter = NoCorridor()
        motion = _motion_bound(scene,compiler,2)
        work = SearchWork(48,deadline=time.monotonic()+20)
        trials,best = [],None
        for candidate in sorted(run['completed'],key=lambda c:c['ticks']):
            try:
                work.check()
            except TimeoutError:
                break
            result = evaluate_program(scene,candidate['program'],compiler,adapter,work,motion,
                seconds=min(2,max(.001,work.deadline-time.monotonic())),candidate_seconds=.7,
                solver_calls=4,python_executable=sys.executable)
            trial = dict(ticks=candidate['ticks'],status=result['status'],termination=result['termination'],result=result)
            if result['best']:
                trial['independentReplay'] = replay_witness(scene,result['best'],compiler)
                if not trial['independentReplay']['passed']:
                    raise RuntimeError('Generated candidate failed replay')
                if best is None or (candidate['ticks'],-result['best']['candidate']['objective']) < (
                        best['plan']['ticks'],-best['candidate']['objective']):
                    best = result['best']
            trials.append(trial)
        record = dict(name=run['name'],policy=run['policy'],canonical=run['canonical'],trials=trials,
            best=best,work=work.summary())
        records.append(record)
        print(json.dumps(dict(name=run['name'],policy=run['policy'],
            ticks=best['plan']['ticks'] if best else None,calls=work.counts['solverCalls'])),flush=True)
    return records


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--part',choices=['generation','warm','verify'],required=True)
    parser.add_argument('--generation-input',type=Path)
    parser.add_argument('--compiler',type=Path,default=DEFAULT_COMPILER)
    args = parser.parse_args()
    report = dict(source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),part=args.part,runs=[])
    with JavaCompiler(read(args.compiler)) as compiler:
        if args.part=='generation':
            for policy,canonical in [('shortest',False),('shortest',True),('complete_first',True),('tick_fair',True)]:
                report['runs'].extend(run_generation(compiler,policy,canonical))
                save(args.output,report)
        elif args.part=='warm':
            for mode in ('baseline','preserve','feasibility_first','two_channel'):
                report['runs'].extend(run_warm(compiler,mode))
                save(args.output,report)
        else:
            if args.generation_input is None:
                parser.error('verify requires --generation-input')
            report['runs'] = verify_generation(args.generation_input,compiler)
            save(args.output,report)
    return 0


if __name__=='__main__':
    raise SystemExit(main())
