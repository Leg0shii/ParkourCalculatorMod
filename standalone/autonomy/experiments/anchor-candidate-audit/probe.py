"""Enumerate raw choices from saved anchor arrivals; no numerical solves."""
import argparse
import collections
import gzip
import json
import os
from pathlib import Path
import time

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.interactions import InteractionGate
from standalone.autonomy.movement_generation import generate_choices
from standalone.autonomy.movement_composition import append_choice, boundary_state
from standalone.autonomy.movement_search import movement_settings, screen_program
from standalone.autonomy.cell_routes.anchor_adapter import AnchorAdapter
from standalone.autonomy.block_profiles import ordinary_jump_profile


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    data=json.loads(gzip.decompress(args.input.read_bytes()));scene=prepare_scene(data['request']);cfg=movement_settings(scene)
    rows=[];summary={}
    def block(box):
        return next((b['id'] for b in scene['blocks'] if scene['collisionScene']['solids'][b['solidIndex']]==box),None)
    with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
        adapter=AnchorAdapter(data['domain'],data['graph'],data['policy'],motion=data['motion'],compiler=compiler,candidate_seconds=2.)
        for label,saved in data['contexts'].items():
            parent=saved['parent'];view=adapter.for_goal(saved['request'],saved['cursor']);gate=InteractionGate(scene,compiler,seconds=2.)
            context=dict(mode='open',scene=scene,program=parent,state=boundary_state(parent),
                maxInputs=cfg['maxInputs']-parent['profile']['airtime'],maxEntryInputs=cfg['maxEntryInputs'],
                normalGap=False,threshold=0.,generationRecords=[])
            view.context(context);gate.context(context)
            started=time.monotonic();kinds=collections.Counter();reasons=collections.Counter()
            for kind in cfg['types']:
                for choice in generate_choices(kind,context):
                    profile=choice['profile'];events=profile.get('caseEvents',[])
                    name='+'.join(e['case'] for e in events) or ('jump' if any(profile['jump']) else 'hold_or_runoff')
                    kinds[name]+=1;child=append_choice(parent,choice,max_inputs=cfg['maxInputs'])
                    proof=(dict(reason='composition_rejected') if child is None else gate.required(child))
                    if child is not None and proof is None:proof=screen_program(scene,child,compiler,2.)
                    reasons[proof['reason'] if proof else 'not_ruled_out']+=1
                    rows.append(dict(span=label,parentT=parent['profile']['airtime'],generator=kind,case=name,
                        duration=profile['airtime'],support=choice.get('terminalSupport'),
                        events=[dict(case=e['case'],input=e['input'],block=block(e.get('box')),lower=block(e.get('lowerBox'))) for e in events],
                        constraints=profile['positionConstraints'],screen=proof))
            summary[label]=dict(parentT=parent['profile']['airtime'],rawChoices=dict(kinds),screenReasons=dict(reasons),seconds=time.monotonic()-started)
    names=[scene['startBlock']]+data['policy']['anchors']+[scene['goalBlock']]
    tops={b['id']:b['top'] for b in scene['blocks']}
    clocks={a+'->'+b:ordinary_jump_profile(tops[a],tops[b],max_inputs=200)['airtime'] for a,b in zip(names,names[1:])}
    args.output.write_text(json.dumps(dict(scope='Diagnostic enumeration from saved prefixes, current helpers; no IPOPT calls or new cold route search.',
        ordinaryJumpClocks=clocks,clockScope='Conditional ordinary jumps only; not a bound for arbitrary stepping/cancelled alternatives.',
        summary=summary,choices=rows),indent=2)+'\n')
    print(json.dumps(summary),flush=True)


if __name__=='__main__':main()
