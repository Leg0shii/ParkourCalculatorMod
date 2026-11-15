import collections
import gzip
import json
from pathlib import Path

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.movement_composition import boundary_state, program_physics_key
from standalone.autonomy.movement_generation import generate_choices


ROOT = Path(__file__).resolve().parent


def read(name):
    with gzip.open(ROOT / 'evidence' / (name + '.json.gz'), 'rt') as handle:
        return json.load(handle)


def main():
    names = ['real11-clock64-first', 'real11-clock64-near-goals',
             'real11-clock64-event-priority', 'real11-clock64-resumed']
    original = {name: read(name) for name in names}
    source = original[names[2]]
    scene = prepare_scene(source['fixture']['request'])
    owners = {tuple(b): row['id'] for row in source['fixture']['request']['blocks'] for b in row['boxes']}
    def contacts(p):
        return [dict(state=e['offset']+1, block=owners[tuple(e['box'])])
                for e in p['profile']['contactEvents'] if e['axis']=='Y' and e['direction']==-1]
    def signature(p):
        q = p['profile']
        return dict(jumps=[i for i,j in enumerate(q['jump']) if j],
                    y=q['position'][-1], onGround=q['grounded'][-1], contacts=contacts(p))
    report = dict(originalRuns=[], followups=[])
    for name in names:
        r = original[name]['result']
        item = dict(name=name, status=r['status'], termination=r['termination'],
                    calls=r['work']['solverCalls'], elapsedSeconds=r['elapsedSeconds'], windows=[],
                    finalAttempts=len(r['finalAttempts']), finalTerminationCounts=dict(collections.Counter(c['termination'] for c in r['finalAttempts'])),
                    finalGeneration={k:v for k,v in r['movementGeneration'][-1].items() if k!='parents'})
        probe64 = []
        def collect_probes(branch):
            for window in branch['windows']:
                for candidate in window['candidates']:
                    for attempt in candidate.get('spatial_probe',{}).get('measurement',{}).get('attempts',[]):
                        if attempt['ticks']==64:
                            probe64.append(dict(window=window['checkpoint']['tick'],candidate=candidate['id'],attempt=attempt))
            for rescue in branch['rescues']:
                collect_probes(rescue['result'])
        collect_probes(r)
        item['spatialLandingProbesAt64'] = probe64
        for w in r['windows']:
            by_id = {c['id']:c for c in w['candidates']}
            item['windows'].append(dict(tick=w['checkpoint']['tick'], candidates=len(w['candidates']),
                accepted=w['scored'], unknown=w['unknown'], retained=[dict(id=key,status=by_id[key]['status'],
                    **signature(by_id[key]['decision_program'])) for key in w['retained']],
                terminations=dict(collections.Counter(c['termination'] for c in w['candidates'])),
                generation={k:v for k,v in w['generation'].items() if k!='parents'}))
        report['originalRuns'].append(item)

    a = source['result']['finalAttempts']
    b = original[names[3]]['result']['finalAttempts']
    repeated = [c for c in b if c['id'] in {x['id'] for x in a}]
    report['resumeOverlap'] = dict(originalFinalCandidates=len(a), resumedFinalCandidates=len(b),
        repeatedCandidates=len(repeated), repeatedCandidateCalls=sum(c['work'].get('solverCalls',0) for c in repeated),
        newCandidates=len(b)-len(repeated), resumedTotalCalls=original[names[3]]['result']['work']['solverCalls'],
        exactOriginalSequenceRepeated=[c['id'] for c in b[:len(a)]]==[c['id'] for c in a],
        originalUniquePhysicalPrograms=len({program_physics_key(c['decision_program']) for c in a}),
        resumedUniquePhysicalPrograms=len({program_physics_key(c['decision_program']) for c in b}))
    assert report['resumeOverlap']['repeatedCandidates']==18
    assert report['resumeOverlap']['repeatedCandidateCalls']==335

    dead = []
    near = original[names[1]]['result']
    w = near['windows'][2]
    dead.append(('near_H44_only_retained', next(c for c in w['candidates'] if c['id']==w['retained'][0])))
    rescue = original[names[0]]['result']['rescues'][0]['result']['windows'][0]
    dead.extend(('distant_rescue_H28_physically_accepted_dead_end_'+str(i),c)
                for i,c in enumerate(rescue['candidates'])
                if c['status']=='feasible' and c['decision_program']['profile']['position'][-1]<4.4)
    report['deadEndCatalogueChecks'] = []
    for label,c in dead:
        p = c['decision_program']
        context = dict(mode='open',scene=scene,program=p,state=boundary_state(p),
            maxInputs=64-p['profile']['airtime'],maxEntryInputs=14,normalGap=True,
            normalGapInputs=64-p['profile']['airtime'],threshold=0.)
        generated = {kind:list(generate_choices(kind,context)) for kind in ('normal','stepping')}
        checks = {kind:dict(choices=len(choices),finiteEventChoices=sum(bool(x['profile']['contactEvents'] or x['profile']['caseEvents']) for x in choices))
                  for kind,choices in generated.items()}
        assert all(x['finiteEventChoices']==0 for x in checks.values())
        report['deadEndCatalogueChecks'].append(dict(label=label,oldStatus=c['status'],id=c['id'],
            state=signature(p),choiceCounts=checks,scope='existing normal/stepping catalogue, user geometry, no ladders/wall hits; only unlanded falling continuation remains'))

    generation = read('real11-failure-generation')
    corrected = [x for x in generation['runs'] if x['jumps'][:3]==[9,18,30]]
    report['generationFollowup'] = dict(first20ExactlyReproduced=generation['first20MatchOriginal'],
        originalEvaluatedCap=20, firstThreeRunB3Correction=corrected[0]['index'],
        exactKnownSuccessfulContinuation=generation['target']['index'],
        expandedPrefixes=generation['generation']['expandedPrefixes'], noNumericalSolves=True)
    assert report['generationFollowup']['firstThreeRunB3Correction']==125
    for mode in ('numeric','fresh','priority'):
        data = read('real11-failure-'+mode)
        for row in data['runs']:
            r = row['result']
            report['followups'].append(dict(label=row['label'],status=r['status'],termination=r['termination'],
                calls=r['work'].get('solverCalls',0), firstAcceptedSeconds=r['work'].get('firstAcceptedSeconds'),
                repairResume=r.get('repairResume'),
                rawStatuses=dict(collections.Counter(x['status'] for x in r['attempts'])),
                outerTerminations=dict(collections.Counter(x['termination'] for x in r['contactAttempts'])),
                pending=len(r['repairState']['pending']), unresolved=len(r['repairState']['unresolved']),
                objective=r['best']['candidate']['objective'] if r['best'] else None,
                replay=row['replay']))
        if mode=='numeric':
            report['sceneOnlyAlsoPassesOriginalCorridor']=data['sceneOnlyOriginalCorridorVerdict']
            report['sceneOnlyAlsoPassesOriginalTerminal']=data['sceneOnlyOriginalTerminalVerdict']
    assert report['sceneOnlyAlsoPassesOriginalCorridor']['accepted']
    assert report['sceneOnlyAlsoPassesOriginalTerminal']['accepted']
    assert all(row['replay']['passed'] for row in report['followups'] if row['replay'])
    incoming = a[1]['decision_program']['repairState']
    priority = read('real11-failure-priority')['runs'][0]
    from standalone.autonomy.repair_state import branch_key
    def branch_set(state):
        pool = state['pending']+state['unresolved']+([state['incumbent']] if state['incumbent'] else [])
        return {branch_key(b['rows'],b['passages'],b['hits']) for b in pool}
    assert branch_set(incoming)==branch_set(priority['program']['repairState'])
    assert program_physics_key(a[1]['decision_program'])==program_physics_key(priority['program'])
    report['inheritedRepairPool'] = dict(pending=len(incoming['pending']),unresolved=len(incoming['unresolved']),
        hasIncumbent=incoming['incumbent'] is not None, referenceSeedLength=len(incoming['referenceWarmYaw']),
        latestCandidateSeedLength=len(a[1]['decision_program']['guideSeedYaw']),
        incumbentPassages=incoming['incumbent']['passages'],
        baseBranchScores=[b['score'] for b in incoming['pending'] if not b['rows'] and not b['passages'] and not b['hits']],
        priorityControlPreservesUniqueBranchPool=True,priorityControlPreservesFixedMovement=True)
    (ROOT / 'audit/other-failures-summary.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(dict(resumeOverlap=report['resumeOverlap'],generation=report['generationFollowup'],
                          deadEnds=report['deadEndCatalogueChecks'],followupCalls=sum(r['calls'] for r in report['followups']))))


if __name__=='__main__':
    main()
