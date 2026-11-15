"""Render recorded decisions with every declared obstacle footprint present."""

import argparse
import copy
import gzip
import json
from pathlib import Path

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.cell_routes.turn_observability import draw


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--input',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path,action='append')
    args=parser.parse_args()
    if any(p.exists() for p in args.output):parser.error('Output already exists; preserve recorded diagrams')
    data=args.input.read_bytes()
    report=json.loads(gzip.decompress(data) if args.input.suffix=='.gz' else data)
    result=report['result'];scene=prepare_scene(result['effectiveRequest'])
    guides=copy.deepcopy(result['searchState']['guidePlan'])
    guides['physicalFootprints']={b['id']:[scene['collisionScene']['solids'][b['solidIndex']]] for b in scene['blocks']}
    best=result['best']
    for output in args.output:
        draw(guides,result['searchState']['dfs']['observations'],output,
             trajectory=best['candidate']['positions'] if best else None)


if __name__=='__main__':main()
