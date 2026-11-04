import argparse
import gzip
import json
from pathlib import Path
import signal
import subprocess
import sys
import threading

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from .checkpoint_experiment import brief, replay_witness
from .checkpoint_search import find_checkpoint_route


def run_fixture(fixture, compiler, *, gap=None, settings=None, python_executable=None, cancel_event=None):
    return find_checkpoint_route(fixture['request'],compiler,opportunities=fixture['opportunities'],
        spatial_points=fixture.get('spatialPoints'),objective_gap=gap,
        python_executable=python_executable,cancel_event=cancel_event,
        **dict(fixture.get('settings',{}),**(settings or {})))


def save(path, report):
    path.parent.mkdir(parents=True,exist_ok=True)
    compressed = path.suffix=='.gz'
    data = json.dumps(report,allow_nan=False,**(dict(separators=(',',':')) if compressed else dict(indent=2))).encode()
    temporary = path.with_suffix(path.suffix+'.tmp')
    temporary.write_bytes(gzip.compress(data,compresslevel=1,mtime=0) if compressed else data)
    temporary.replace(path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--compiler',type=Path,default=Path('standalone/autonomy/build/compiler.json'))
    parser.add_argument('--python',default=sys.executable)
    parser.add_argument('--repeats',type=int,default=1)
    parser.add_argument('--gaps',nargs='+',default=['fixed','0.25','0.5'])
    args = parser.parse_args()
    if args.repeats<1:
        parser.error('repeats must be positive')
    cancel = threading.Event()
    for sign in (signal.SIGINT,signal.SIGTERM):
        signal.signal(sign,lambda *_:cancel.set())
    report = dict(source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
                  runs=[],summaries=[],validation=[])
    paths = sorted(args.input.glob('*.json')) if args.input.is_dir() else [args.input]
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        for repeat in range(args.repeats):
            for path in paths:
                fixture = json.loads(path.read_text())
                scene = prepare_scene(dict(fixture['request'],maxWallHits=0))
                for label in args.gaps:
                    gap = None if label=='fixed' else float(label)
                    result = run_fixture(fixture,compiler,gap=gap,python_executable=args.python,cancel_event=cancel)
                    report['runs'].append(dict(name=path.stem,repeat=repeat,gap=gap,fixture=fixture,result=result))
                    summary = dict(name=path.stem,repeat=repeat,gap=gap,**brief(result),
                        checkpoints=[c['tick'] for c in result.get('checkpoints',[])],
                        retained=[len(w['retained']) for w in result['windows']],
                        outsideBand=sum(w['retention']['outsideBand'] for w in result['windows']),
                        scoredCapDrops=sum(w['retention']['scoredCapDrops'] for w in result['windows']))
                    report['summaries'].append(summary)
                    witnesses = [c['witness'] for w in result['windows'] for c in w['candidates'] if 'witness' in c]
                    if result['best']:
                        witnesses.append(result['best'])
                    for best in witnesses:
                        checked = replay_witness(scene,best,compiler)
                        report['validation'].append(dict(name=path.stem,repeat=repeat,gap=gap,**checked))
                        if not checked['passed']:
                            raise RuntimeError('Decision-window witness failed independent replay')
                    save(args.output,report)
                    print(json.dumps(summary),flush=True)
                    if cancel.is_set():
                        return 130
    return 0


if __name__=='__main__':
    raise SystemExit(main())
