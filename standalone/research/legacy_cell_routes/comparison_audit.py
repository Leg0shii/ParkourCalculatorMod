import copy
import time

from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.search.movement import iter_movement_programs, screen_program
from parkour_solver.movement.settings import movement_settings
from parkour_solver.search.ranking import candidate_rank
from parkour_solver.schema import integer, number
from parkour_solver.spatial.cells import decompose
from parkour_solver.validation.scoped_scene import CorridorAdapter
from parkour_solver.bridge.counting import CountingCompiler; from parkour_solver.search.work import SearchWork
from parkour_solver.spatial.compat.corridors import corridor_for_points, landmarks
from parkour_solver.search.completion import generation_exhausted


def audit_pool(entries, evaluate, *, objective, incumbent=None, on_candidate=None):
    records=[]
    reference=incumbent
    for entry in entries:
        result=evaluate(entry)
        best=result.get('best')
        if best is not None and (reference is None or candidate_rank(best,objective)<candidate_rank(reference,objective)):
            reference=best
        records.append(dict(candidate=entry['id'],selection=entry.get('selection','unknown'),result=result))
        if on_candidate is not None:on_candidate(records[-1])
    def equivalent(a,b):
        if a is None or b is None:return False
        ar,br=candidate_rank(a,objective),candidate_rank(b,objective)
        return ar[0]==br[0] and abs(ar[1]-br[1])<=1e-6
    def better(a,b):
        if a is None:return False
        if b is None:return True
        ar,br=candidate_rank(a,objective),candidate_rank(b,objective)
        return ar[0]<br[0] or ar[0]==br[0] and ar[1]<br[1]-1e-6
    omitted=lambda r:r['selection'] not in ('retained','final_evaluated')
    return dict(candidates=records,best=reference,allCandidatesProcessed=len(records)==len(entries),
        continuationAuditComplete=all(r['result'].get('complete',False) for r in records),
        discardedTiesBest=[r['candidate'] for r in records if omitted(r) and equivalent(r['result'].get('best'),reference)],
        discardedTiesFastestTime=[r['candidate'] for r in records if omitted(r) and reference is not None
            and r['result'].get('best') is not None and r['result']['best']['plan']['ticks']==reference['plan']['ticks']],
        discardedBeatsHeuristic=[r['candidate'] for r in records if omitted(r) and better(r['result'].get('best'),incumbent)],
        heuristicQualityLoss=better(reference,incumbent),objectiveOptimalityProven=False)


def source_entries(result):
    entries=[]
    for index,window in enumerate(result.get('windows',[])):
        selected=set(window['retained'])
        decisions=window['retention'].get('decisions',{})
        for record in window['candidates']:
            if 'decision_program' not in record:
                raise ValueError('Regression requires candidates saved before retention')
            default='final_evaluated' if record.get('complete') else 'retained' if record['id'] in selected else 'legacy_omission'
            reason=decisions.get(record['id'],{}).get('reason',default)
            entries.append(dict(id=str(index)+':'+record['id'],selection=reason,program=record['decision_program']))
    for record in result.get('finalAttempts',[]):
        if 'decision_program' in record:
            entries.append(dict(id='final:'+record['id'],selection='final_evaluated',program=record['decision_program']))
    for record in result.get('unevaluatedCandidates',[]):
        entries.append(dict(id='unevaluated:'+str(record.get('window','unknown'))+':'+record['id'],
                            selection=record['selection'],program=record['program']))
    for index,rescue in enumerate(result.get('rescues',[])):
        for entry in source_entries(rescue['result']):
            entry['id']='rescue'+str(index)+':'+entry['id']
            entries.append(entry)
    return entries


def run_full_audit(request,result,compiler,*,seconds_per_candidate=4,candidate_seconds=.7,solver_calls=48,
                   program_limit=None,python_executable=None,cancel_event=None,on_candidate=None,candidate_entries=None):
    from parkour_solver.search.engine import evaluate_program, motion_bound
    for name,value in [('seconds_per_candidate',seconds_per_candidate),('candidate_seconds',candidate_seconds)]:
        if number(value,name)<=0:raise ValueError('Reference budgets must be positive')
    integer(solver_calls,'reference solver calls',1,200000)
    if program_limit is not None:integer(program_limit,'reference program limit',1,200000)
    started=time.monotonic()
    scene=prepare_scene(request)
    counted=CountingCompiler(compiler)
    d,gates=decompose(scene),landmarks(scene)
    corridor=corridor_for_points(d,result['corridor']['points'],gates)
    adapter=CorridorAdapter(scene,corridor,gates,decomposition=d)
    motion=motion_bound(scene,counted,2)
    cfg=movement_settings(scene)
    def evaluate(entry):
        candidate_started=time.monotonic()
        deadline=candidate_started+seconds_per_candidate
        work=SearchWork(solver_calls,deadline=deadline)
        generation,trials={},[]
        best=None
        program=copy.deepcopy(entry['program'])
        program.pop('repairState',None)
        def screen(p):return screen_program(scene,p,counted,max(.001,min(candidate_seconds,deadline-time.monotonic())))
        if program['terminalSupport']==scene['goalBlock'] and program['profile']['grounded'][-1]:
            candidates=iter([program])
            generation['termination']='exhausted'
        else:
            candidates=iter_movement_programs(scene,settings=cfg,initial_programs=[program],threshold=0.,report=generation,
                deadline=deadline,cancel_event=cancel_event,work=work,screen=screen,context_adapter=adapter.context)
        capped=False
        try:
            for p in candidates:
                if program_limit is not None and len(trials)>=program_limit:
                    capped=True
                    break
                try:work.check()
                except TimeoutError:
                    capped=True
                    break
                evaluated=evaluate_program(scene,p,counted,adapter,work,motion,seconds=max(.001,deadline-time.monotonic()),
                    candidate_seconds=candidate_seconds,solver_calls=min(12,max(1,solver_calls-work.counts['solverCalls'])),
                    python_executable=python_executable,cancel_event=cancel_event,required_landing=scene['goalBlock'])
                first=next(iter(evaluated.get('attempts',[])),{})
                contact=next(iter(evaluated.get('contactAttempts',[])),{})
                proof=(first.get('status')=='blocked' and first.get('stage')=='jump' and first.get('allocation')==[0]
                    and contact.get('constraintCount')==0 and contact.get('passageCount')==0
                    and first.get('certificate',{}).get('reason') in ('interval_reach_contradiction',
                        'contact_reach_contradiction','vertical_schedule','passage_timing'))
                trial=dict(ticks=p['profile']['airtime'],result=evaluated,provenFixedProgramImpossible=proof)
                if evaluated['best'] is not None:
                    from research.legacy_cell_routes.checkpoint_experiment import replay_witness
                    trial['independentReplay']=replay_witness(scene,evaluated['best'],counted)
                    if not trial['independentReplay']['passed']:raise RuntimeError('Reference candidate failed replay')
                    evaluated['best'].update(movementProgram=p,blockIds=[scene['startBlock'],scene['goalBlock']])
                    if best is None or candidate_rank(evaluated['best'],scene['objective'])<candidate_rank(best,scene['objective']):
                        best=evaluated['best']
                trials.append(trial)
        finally:
            if hasattr(candidates,'close'):candidates.close()
        unknown=sum((t['result']['status'] in ('error','cancelled') or t['result'].get('best') is None)
            and not t['provenFixedProgramImpossible'] for t in trials)
        complete=not capped and generation_exhausted(generation) and not unknown
        return dict(best=best,trials=trials,generation=generation,work=work.summary(),complete=complete,
            unknownPrograms=unknown,progressRetentionEnabled=False,programLimitReached=capped,
            elapsedSeconds=time.monotonic()-candidate_started,
            interpretation='all_produced_continuations_tested_without_progress_retention_when_complete')
    entries=source_entries(result) if candidate_entries is None else candidate_entries
    audit=audit_pool(entries,evaluate,objective=scene['objective'],incumbent=result.get('best'),on_candidate=on_candidate)
    audit.update(compiler=counted.summary(),sourceGenerationComplete=all(
        generation_exhausted(r) for r in result.get('movementGeneration',[])),
        elapsedSeconds=time.monotonic()-started,
        referenceSettings=dict(secondsPerCandidate=seconds_per_candidate,candidateSeconds=candidate_seconds,
            solverCallsPerCandidate=solver_calls,programLimit=program_limit,movementSettings=cfg),
        interpretation='finite_generated_pool_counterfactual_audit_not_global_optimality')
    return audit
