"""Replay saved B6 catalogues and log every emitted option and prefix check.

Research-only: uses the captured context, current production generation/screening,
and native coefficients. Does not launch angle-solver workers or fabricate their
outcomes. Output directories must be new.
"""
import argparse
import collections
import copy
from datetime import datetime, timezone
import gzip
import hashlib
import json
from pathlib import Path
import sys
import time
from unittest.mock import patch

import scipy.optimize

from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.constraints.variants import ConstraintVariants
from parkour_solver.io.checkpoint_store import read_report
from parkour_solver.io.provenance import source_hashes, source_revision
from parkour_solver.reachability import linear
from parkour_solver.reachability.intervals import interaction_reachability
from parkour_solver.reachability.service import InteractionGate
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.search.identity import digest, solve_scope


def write(path, value):
    path.write_text(json.dumps(value, indent=2, allow_nan=False)+'\n')


def ranges(values):
    groups=[]
    for value in sorted(set(values)):
        if groups and value==groups[-1][-1]+1:groups[-1].append(value)
        else:groups.append([value])
    return ','.join(str(g[0]) if len(g)==1 else f'{g[0]}–{g[-1]}' for g in groups)


def explain(proof):
    reason=proof['reason']
    if not proof['ruledOut']:
        return 'Retained by B6 pre-check; no angle solve in this replay.'
    if reason=='norm_reach_contradiction':
        return (f"States {proof['previousState']}→{proof['state']}: minimum travel "
                f"{proof['gap']:.9f} exceeds upper bound {proof['travelUpperBound']:.9f} blocks.")
    if reason=='interval_reach_contradiction':
        axis=proof['axis']
        if isinstance(axis,int):axis=('X','Z')[axis]
        return f"Propagated {axis} position range is empty at state {proof['state']}."
    if reason=='linear_control_relaxation_infeasible':
        return 'Optimistic linear movement model is infeasible (recorded LP status 2).'
    if reason=='interaction_row_unreachable':
        return f"State {proof['tick']}: required {proof['axis']} bound lies outside the saved reachable interval."
    return reason


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report',type=Path,required=True)
    parser.add_argument('--compiler',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--document',type=Path,required=True)
    args=parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=False)
    before=source_hashes()
    report=read_report(args.report)
    if report['provenance']['sourceHashes']!=before:
        raise RuntimeError('Captured run and current production source differ; use its recorded source revision')
    result=report['result'];scene=prepare_scene(result['effectiveRequest'])
    found={};no_choices={}
    def visit(value):
        if isinstance(value,dict):
            p=value.get('program')
            if isinstance(p,dict) and p.get('profile',{}).get('jump'):
                jumps=[i for i,j in enumerate(p['profile']['jump']) if j]
                repair=p.get('repairState') or {};g=repair.get('constraintGeneration') or {}
                item=next((x for x in g.get('items',[]) if x.get('solidIndex')==5),None)
                if jumps and jumps[0]==8 and item and item.get('rows')==[[]]:
                    proofs=[x for x in g['exclusions'] if x.get('solidIndex')==5]
                    no_choices[g['context']]=dict(through=p['profile']['airtime'],jumpInputs=jumps,
                        reason=proofs[0]['reason'] if proofs else 'no added B6 rows in saved state')
            if isinstance(p,dict) and p.get('profile',{}).get('airtime') in (57,58,67):
                jumps=[i for i,j in enumerate(p['profile']['jump']) if j]
                repair=p.get('repairState') or {};g=repair.get('constraintGeneration') or {}
                if jumps and jumps[0]==8 and g.get('context'):
                    index=next((i for i,x in enumerate(g['items']) if x.get('solidIndex')==5),None)
                    nodes=[n for n in g['pending'] if n['item']==index]
                    if index is not None:
                        node=nodes[0] if nodes else None
                        count=len(node.get('cursor',{}).get('seenOptions',[])) if node else 0
                        old=found.get(g['context'])
                        if old is None or count>old['observedCount']:
                            found[g['context']]=dict(program=p,repair=repair,item=g['items'][index],
                                parent=node,observedCount=count,index=index)
            for child in value.values():visit(child)
        elif isinstance(value,list):
            for child in value:visit(child)
    visit(result)
    if len(found)!=4:raise RuntimeError(f'Expected four B6 catalogue contexts, found {len(found)}')
    enumeration=json.loads(args.report.with_name('enumeration.json').read_text())
    aliases={};all_options=[];summaries=[]
    linear._check.cache_clear()
    actual_check=linear._check;actual_linprog=scipy.optimize.linprog
    active=[];backend_cache={}
    def linprog(*a,**k):
        answer=actual_linprog(*a,**k)
        active[-1]['backend']=dict(status=int(answer.status),message=str(answer.message))
        return answer
    def checked(encoded):
        event={};active.append(event)
        try:
            answer=actual_check(encoded)
            if 'backend' in event:
                backend_cache[encoded]=event['backend'];event['cacheHit']=False
            else:
                event['backend']=backend_cache.get(encoded);event['cacheHit']=True
            return answer
        finally:
            active.pop()
            current_lp.append(event)
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler, \
            patch.object(linear,'_check',side_effect=checked), \
            patch.object(scipy.optimize,'linprog',side_effect=linprog), \
            (args.output/'events.jsonl').open('w') as log:
        ordered=sorted(found.items(),key=lambda x:(0 if x[1]['program']['profile']['airtime']==67 else 1,
            x[1]['program']['profile']['airtime'],x[0]))
        for history,(context,data) in enumerate(ordered,1):
            label=chr(64+history)
            current_lp=[]
            count=sum(row['exits']['returned'] for row in enumeration['rows']
                      if row['context']==context and row['solidIndex']==5)
            if data['parent'] is not None:assert count==data['observedCount'],(count,data['observedCount'])
            target_scene=dict(scene,goalBlock=data['program']['terminalSupport'])
            service=InteractionGate(target_scene,compiler,seconds=10)
            prepared=service.prepare(data['program'],require_complete=True)
            assert prepared['profiles']==data['repair']['context']['profiles']
            assert prepared['route']['constraints']==data['repair']['context']['constraints']
            analysis=service.analyze_model(prepared['route'],prepared['plan'],prepared['vertical'],
                scope=solve_scope(target_scene,data['program']))
            owner=ConstraintVariants.__new__(ConstraintVariants)
            owner.route=prepared['route'];owner.profile=prepared['profiles'][0]
            owner.through=data['program']['profile']['airtime']
            owner.events=[dict(prepared['plan']['events'][0],landing=owner.through)]
            owner.envelopes=analysis['envelopes'];owner.deadline=time.monotonic()+90
            owner.route_guards=None
            owner.state=dict(context=context,items=copy.deepcopy(data['repair']['constraintGeneration']['items']),
                pending=[dict(item=0,rows=[],passages=[])],seen=[],partialSeen=[],exclusions=[],screenExclusions=[],
                emitted=0,exhausted=False,duplicateVariants=0,
                timings=dict(preparationSeconds=0.,variantGenerationSeconds=0.))
            owner.items=owner.state['items'];owner.seen=set();owner.partial_seen=set();owner.exclusions=[]
            owner.reference=data['repair']['constraintGeneration']['reference'];owner.proof_context=context
            original_parent=data['parent']
            if original_parent is None:
                class ParentCaptured(Exception):pass
                option=owner.option;captured=[]
                def capture(item,cursor):
                    if item is owner.items[data['index']]:
                        captured.append(copy.deepcopy(owner.state['pending'][-1]));raise ParentCaptured()
                    return option(item,cursor)
                with patch.object(owner,'option',side_effect=capture):
                    try:next(owner)
                    except ParentCaptured:pass
                if len(captured)!=1:raise RuntimeError('Could not recover pre-B6 constraint prefix')
                data['parent']=captured[0]
            item=copy.deepcopy(data['item']);parent_rows=data['parent']['rows']
            for f,mask in zip(item['faces'],item['masks']):
                if f['axis']=='Y':continue
                for t in item['required'][f['axis']]:
                    possible=not interaction_reachability(owner.envelopes,[owner.face_row(f,t)])['ruledOut']
                    assert possible==mask[t],(label,f,t)
            compressed=gzip.compress(json.dumps(dict(scene=result['effectiveRequest'],
                program={k:v for k,v in data['program'].items() if k!='repairState'},item=item,
                parent=data['parent'],context=context,prepared=prepared),allow_nan=False).encode(),mtime=0)
            (args.output/f'history-{label}-input.json.gz').write_bytes(compressed)
            cursor={};options=[];observed_matched=False
            proof_by_hash=collections.defaultdict(list)
            for rejection in data['repair']['constraintGeneration']['screenExclusions']:
                proof_by_hash[rejection['rowsHash']].append(rejection['proof'])
            while True:
                value=owner.option(item,cursor)
                if value is None:break
                number=len(options)+1;option_id=f'{label}{number:03d}'
                passage=value['passage'];rows=value['rows']
                log.write(json.dumps(dict(event='generated',id=option_id,passage=passage,rows=rows),allow_nan=False)+'\n');log.flush()
                current_lp=[]
                proof=owner._screen(parent_rows+rows)
                status='rejected' if proof['ruledOut'] else 'not_ruled_out'
                key=digest([data['parent']['item']+1,owner.effective_key(parent_rows+rows),
                    [(p['solidIndex'],p['blockageCounts']) for p in data['parent']['passages']+[passage]]])
                historical='not reached in the recorded 300-second run'
                if number<=count:
                    if key in data['repair']['constraintGeneration']['partialSeen']:
                        historical='recorded prefix accepted for downstream generation'
                    elif digest(parent_rows+rows) in proof_by_hash:
                        historical='recorded rejection: '+', '.join(sorted({p['reason'] for p in proof_by_hash[digest(parent_rows+rows)]}))
                    else:historical='returned by generator; per-option historical decision unavailable'
                row_key=digest(sorted({(r['mode'],r['t1'],r['cmp'],r['rhs']) for r in rows}))
                previous=aliases.setdefault((label,row_key),option_id)
                record=dict(id=option_id,history=label,ordinal=number,context=context,
                    emittedInOriginalRun=number<=count,originalDecision=historical,
                    faces=[f['axis']+('+' if f['direction']>0 else '-') for f in passage['faces']],
                    gates=passage['gates'],rows=rows,counts=passage['blockageCounts'],
                    result=status,proof=proof,linearEvents=current_lp,
                    sameRowsAs=previous if previous!=option_id else None,explanation=explain(proof))
                if not proof['ruledOut'] and any(e.get('backend',{}).get('status') not in (0,2) for e in current_lp):
                    statuses=sorted({e.get('backend',{}).get('status') for e in current_lp})
                    record['explanation']=f'Linear check inconclusive (status {statuses}); retained, not proved feasible.'
                historical_match=None
                if historical.startswith('recorded prefix accepted'):
                    historical_match=not proof['ruledOut']
                elif historical.startswith('recorded rejection: '):
                    historical_match=proof['ruledOut'] and proof['reason'] in historical.split(': ',1)[1].split(', ')
                record['historicalDecisionMatchesReplay']=historical_match
                if historical_match is False:raise RuntimeError(f'Historical/replay disagreement: {option_id}')
                log.write(json.dumps(dict(event='checked',**record),allow_nan=False)+'\n');log.flush()
                options.append(record)
                if number==count and original_parent is not None:
                    assert cursor['seenOptions']==original_parent['cursor']['seenOptions']
                    observed_matched=True
            if original_parent is not None:
                saved_nodes=original_parent['cursor']['seenNodes']
                assert saved_nodes==cursor['seenNodes'][:len(saved_nodes)]
                assert observed_matched
            else:assert count==len(options),'Exhausted original catalogue count differs'
            assert cursor['chain']==len(item['chains']) and not cursor['frontier']
            write(args.output/f'history-{label}-options.json',options)
            summaries.append(dict(history=label,context=context,options=len(options),through=owner.through,
                jumpInputs=[i for i,j in enumerate(data['program']['profile']['jump']) if j],parentRows=parent_rows,
                originalEmitted=count,originalOrderVerified=original_parent is not None,
                originalVisitedNodesVerified=original_parent is not None,
                complete=True,chains=len(item['chains']),statuses=dict(collections.Counter(o['result'] for o in options)),
                reasons=dict(collections.Counter(o['proof']['reason'] for o in options)),
                historicalDecisionsVerified=sum(o['historicalDecisionMatchesReplay'] is True for o in options),
                originalDecisions=dict(collections.Counter(o['originalDecision'] for o in options))))
            all_options.extend(options)
            print(json.dumps(summaries[-1]),flush=True)
    assert before==source_hashes()
    write(args.output/'summary.json',dict(histories=summaries,sourceRevision=source_revision(),sourceHashes=before,
        pythonExecutable=sys.executable,generatedAtUTC=datetime.now(timezone.utc).isoformat(),
        driverSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        originalManifestSHA256=hashlib.sha256(args.report.read_bytes()).hexdigest(),
        noB6ChoiceContexts=no_choices,
        compilerConfigSHA256=hashlib.sha256(args.compiler.read_bytes()).hexdigest(),
        originalReport=str(args.report.resolve()),originalSourceMatched=True,
        allCataloguesComplete=True,angleSolverWorkersLaunched=0))
    lines=['# Every generated B6 option: logged replay','',
        'This is a fresh replay on production revision `'+source_revision()+'` of **all four B6 catalogue contexts** captured in the latest continuous-search run.',
        'Histories A/B are the final 67-tick journeys (jumps **8, 19, 30, 43, 58**). C/D are the earlier partial journeys ending at 57/58. B6 box: `[730, 0, -1095, 731, 20, -1094]`.',
        '', '**Scope:** every option returned by the B6 option generator, after its internal masks and duplicate suppression. All four catalogues were run to exhaustion. Internal discarded DFS states are not called options here.',
        '**Results are freshly logged B6-prefix pre-check decisions.** The B7 alternatives and angle solver were not run in this replay. “Retained” means not ruled out, not a proven feasible path.',
        'The recorded-run column distinguishes original logged decisions from options reached only in this complete replay. Every recorded decision was checked against the fresh result. Order and visited-node prefixes were also checked for A/B/D; C’s completed cursor was already removed, so its original order cannot be compared.',
        '', '## Reading the entries','',
        'Face labels: `X−` is the winning side (`X ≤ 729.6999989880791`); `X+` is the opposite side (`X ≥ 731.3000010119209`); `Z−` is behind B6 (`Z ≤ −1095.300001011921`); `Z+` is past its far end (`Z ≥ −1093.699998988079`). `Y−` / `Y+` are below / above labels.',
        'Switch tuples are the generator’s recorded gates, in order. The next face starts at the same state or the next state according to collision-axis order. The requirements column is authoritative about which states actually receive constraints.',
        'Early labelled phases can have no active constraints. Equal constraints can remain separate options because their nominal blockage counts differ. “Same rows” identifies that relationship within a history.',
        'Requirement symbols below use the face labels above; all numeric row values and LP backend statuses are preserved in the JSON evidence. A missing historical decision is labelled unavailable rather than inferred.',
        '', '## Summary','', '| History | Complete options | Emitted in old run | Rejected now | Retained now |','| --- | ---: | ---: | ---: | ---: |']
    for s in summaries:lines.append(f"| {s['history']} | {s['options']} | {s['originalEmitted']} | {s['statuses'].get('rejected',0)} | {s['statuses'].get('not_ruled_out',0)} |")
    lines+=['',f"**Total: {len(all_options)} logged options; {sum(s['originalEmitted'] for s in summaries)} were emitted in the original run.**",
        f"All {sum(s['historicalDecisionsVerified'] for s in summaries)} available historical decisions match the fresh replay.",
        '', '**Inconclusive checks:** A228 and A233 were retained because the LP returned status 4 (unknown/numerical issue). They are not labelled feasible. The exact backend messages are in the evidence.',
        '', '## Recorded contexts with no B6 passage choices','',
        '| Horizon | Jump inputs | Saved reason |','| --- | --- | --- |']
    for x in sorted(no_choices.values(),key=lambda x:x['through']):
        lines.append(f"| {x['through']} | {x['jumpInputs']} | `{x['reason']}` |")
    for summary in summaries:
        label=summary['history'];lines+=['',f'## History {label}','',f"Context: `{summary['context']}`."]
        lines+=[f"Horizon: **{summary['through']}**. Jump inputs: **{summary['jumpInputs']}**."]
        if label=='B':lines+=['This history includes the additional earlier approach constraint `X ≥ 731.3000010119209` at state 56.']
        if summary['parentRows']:
            lines+=['The check also includes these previously selected required-contact rows: '+
                '; '.join(f"{r['mode']} {r['cmp']} {r['rhs']} at state {r.get('t1',r.get('offset'))}" for r in summary['parentRows'])+'.']
        lines+=['','| ID | Recorded 300s run | Face sequence | Switches | Active added requirements | Fresh result | Reason | Same rows |',
            '| --- | --- | --- | --- | --- | --- | --- | --- |']
        for o in (o for o in all_options if o['history']==label):
            groups=collections.defaultdict(list)
            for row in o['rows']:groups[(row['mode']+('+' if row['cmp']=='GE' else '−'),row['rhs'])].append(row['t1'])
            requirements='; '.join(f'{face}@{ranges(ticks)}' for (face,_),ticks in sorted(groups.items())) or 'No added position rows'
            faces=' → '.join(o['faces']).replace('-', '−')
            switches=', '.join(map(str,o['gates'])) or '—'
            historical=('retained' if o['originalDecision'].startswith('recorded prefix accepted') else
                'rejected' if o['originalDecision'].startswith('recorded rejection:') else
                'not reached' if not o['emittedInOriginalRun'] else 'decision unavailable')
            lines.append(f"| {o['id']} | {historical} | {faces} | {switches} | {requirements} | {'REJECTED' if o['result']=='rejected' else 'RETAINED'} | {o['explanation']} | {o['sameRowsAs'] or '—'} |")
    args.document.write_text('\n'.join(lines)+'\n')
    print('Document:',args.document,flush=True)


if __name__=='__main__':main()
