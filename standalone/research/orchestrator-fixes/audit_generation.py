import collections
import cProfile
import gzip
import io
import json
from pathlib import Path
import pstats
import time

from standalone.autonomy.block_contacts import floor_checks, validate_vertical_schedule
from standalone.autonomy.cell_routes.routes import landmarks
from standalone.autonomy.constraint_variants import ConstraintVariants
from standalone.autonomy.reachability import reachable_envelopes
from standalone.autonomy.route import schedule
from standalone.autonomy.solve_spec import digest


ROOT=Path(__file__).resolve().parent


def effective_rows(rows,envelopes):
    result=set()
    for r in rows:
        t=r.get('t1',r.get('offset'));lo,hi=envelopes['states'][t][r['mode']]
        if r['cmp']=='GE' and lo>=r['rhs'] or r['cmp']=='LE' and hi<=r['rhs']:
            continue
        result.add((r['mode'],t,r['cmp'],r['rhs']))
    return tuple(sorted(result))


def source_for(audit,p,*,restore=True):
    route=p['compiledRoute'];program=p['record']['program'];plan=schedule(route,[0])
    profile=route['verticalProfiles'][0]
    vertical=validate_vertical_schedule(route,[profile],plan)
    if not vertical['feasible']:
        raise ValueError('Saved scalar profile failed replay')
    coefficients=dict(p['coefficients'],vertical=dict(vertical['vertical'],referencePosition=vertical['y'],referenceVelocity=vertical['vy']))
    model=dict(coefficients,version=1,ticks=p['ticks'],walls=plan['walls'],contacts=plan['contacts'],
        position=audit['scene']['initial']['position'][::2],velocity=audit['scene']['initial']['velocity'][::2],
        threshold=0.,objective=dict(p['objective'],tick=p['ticks']))
    envelopes=reachable_envelopes(model,vertical['y']);envelopes['modelKey']=digest(coefficients)
    checks=[c for c in floor_checks(audit['scene'],[audit['scene']['startBlock'],audit['scene']['goalBlock']],
        [profile]) if c['offset']<p['ticks']]
    saved=program['repairState']['constraintGeneration']
    source=ConstraintVariants(route,profile,plan,audit['scene']['collisionScene'],envelopes,p['scope'],
        checks=checks,gates=landmarks(audit['scene']),signature=(),state=saved if restore else None,reference=saved['reference'])
    if restore and source.state['context']!=saved['context']:
        raise ValueError('Constraint source context does not exactly match the saved cursor')
    return source


def run():
    with gzip.open(ROOT/'audit/candidate-ledger.json.gz','rt') as handle:
        audit=json.load(handle)
    report=dict(kind='offline exact-context generator audit; no IPOPT or Java calls',programs=[])
    classes=collections.defaultdict(list)
    for p in audit['programs']:
        classes[p['physicalClass']].append(p)
    inclusion=[]
    for group in classes.values():
        for broad in group:
            a={(r['mode'],r['t1'],r['cmp'],r['rhs']) for r in broad['basePlan']['walls']}
            for narrow in group:
                if narrow is broad:
                    continue
                b={(r['mode'],r['t1'],r['cmp'],r['rhs']) for r in narrow['basePlan']['walls']}
                if a<b:
                    inclusion.append(dict(broader=broad['label'],narrower=narrow['label'],
                        sameCompiledCoefficients=broad['coefficients']==narrow['coefficients'],
                        sameFiniteContacts=broad['basePlan']['sceneContacts']==narrow['basePlan']['sceneContacts'],
                        additionalRows=sorted(b-a)))
    report['programInclusion']=inclusion
    model_sets=collections.defaultdict(set)
    handled=collections.Counter()
    for branch in audit['branches']:
        bounds={}
        for row in branch['plan']['walls']:
            key=(row['mode'],row['t1'],row['cmp'])
            if row['cmp']=='GE':
                bounds[key]=max(bounds.get(key,-float('inf')),row['rhs'])
            else:
                bounds[key]=min(bounds.get(key,float('inf')),row['rhs'])
        model_sets[branch['program']].add(tuple(sorted(bounds.items())))
        handled[branch['program']]+=1
    report['handledModels']={k:dict(handled=handled[k],distinctDirectBoundSets=len(v)) for k,v in model_sets.items()}
    for p in audit['programs']:
        if p['label'] not in ('P06','P07'):
            continue
        source=source_for(audit,p);item_records=[]
        for item in source.items:
            if 'rows' in item:
                item_records.append(dict(kind='floor_avoid_disjunction',options=len(item['rows'])))
                continue
            cursor={};seen={};examples=[];raw=0;exhausted=False;source.deadline=time.monotonic()+3
            start=time.monotonic()
            try:
                while True:
                    option=source.option(item,cursor)
                    if option is None:
                        exhausted=True;break
                    raw+=1;key=effective_rows(option['rows'],source.envelopes)
                    if key in seen and len(examples)<3:
                        examples.append(dict(first=seen[key],duplicate=option,effectiveRows=key))
                    else:
                        seen.setdefault(key,option)
            except TimeoutError:
                pass
            item_records.append(dict(kind='obstacle_passages',solidIndex=item['solidIndex'],
                block=audit['scene']['blocks'][item['solidIndex']]['id'],rawOptions=raw,
                distinctEffectiveRowSets=len(seen),duplicateOptions=raw-len(seen),exhausted=exhausted,
                elapsedSeconds=time.monotonic()-start,examples=examples,
                requiredStates={k:sorted(v) for k,v in item['required'].items()}))
        item_records=list(item_records)
        profile=None;probe={}
        if p['label']=='P07':
            source=source_for(audit,p);start=time.monotonic();source.deadline=start+2
            before=source.state.get('duplicateVariants',0);profile=cProfile.Profile();profile.enable()
            try:
                value=next(source);probe['newVariantEmitted']=True
                probe['newVariantRows']=value['rows']
            except TimeoutError:
                probe['newVariantEmitted']=False
            except StopIteration:
                probe['exhausted']=True
            finally:
                profile.disable()
            probe.update(seconds=time.monotonic()-start,additionalDuplicates=source.state.get('duplicateVariants',0)-before)
            stream=io.StringIO();pstats.Stats(profile,stream=stream).strip_dirs().sort_stats('cumulative').print_stats(18)
            probe['profile']=stream.getvalue()
        report['programs'].append(dict(program=p['label'],exactContext=True,items=item_records,nextVariantProbe=probe))
    (ROOT/'audit/generation-detail.json').write_text(json.dumps(report,indent=2,allow_nan=False)+'\n')
    print(json.dumps({p['program']:[{k:v for k,v in i.items() if k not in ('examples','requiredStates')}
                                  for i in p['items']] for p in report['programs']},indent=2))
    for p in report['programs']:
        if p['nextVariantProbe']:
            print(json.dumps({k:v for k,v in p['nextVariantProbe'].items() if k!='profile'}))
            print(p['nextVariantProbe']['profile'])


if __name__=='__main__':
    run()
