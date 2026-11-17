import argparse
import gzip
import json
from pathlib import Path

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.compiler import JavaCompiler


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--solution', type=Path, default=Path(__file__).resolve().parent / 'solution.json.gz')
    parser.add_argument('--compiler', type=Path, default=Path('standalone/autonomy/build/compiler.json'))
    args = parser.parse_args()
    with gzip.open(args.solution, 'rt') as handle:
        data = json.load(handle)
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        replay = replay_witness(prepare_scene(data['fixture']['request']), data['best'], compiler)
    print(json.dumps(replay, indent=2))
    if not replay['passed']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
