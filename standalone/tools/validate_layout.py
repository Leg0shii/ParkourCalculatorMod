"""Verify production boundaries and byte-preserved relocated evidence."""
import argparse
import ast
import hashlib
import json
from pathlib import Path

from tools.paths import STANDALONE_ROOT


def verify():
    source=STANDALONE_ROOT/'src/parkour_solver'
    checked=0
    for path in source.rglob('*.py'):
        tree=ast.parse(path.read_text(),filename=str(path))
        for node in ast.walk(tree):
            names=[a.name for a in node.names] if isinstance(node,ast.Import) else [node.module or ''] if isinstance(node,ast.ImportFrom) else []
            for name in names:
                assert name.split('.')[0] not in ('standalone','research','tools','tests'),(str(path),node.lineno,name)
        checked+=1
    manifest=json.loads((STANDALONE_ROOT/'tools/layout-migration.json').read_text())
    preserved=0
    for row in manifest['files']:
        if row['immutable']:
            p=STANDALONE_ROOT/row['new']
            assert hashlib.sha256(p.read_bytes()).hexdigest()==row['originalSha256'],row
            preserved+=1
    return dict(runtimeModules=checked,forbiddenImports=0,preservedEvidenceAndFixtures=preserved,passed=True)


def main():
    print(json.dumps(verify(),indent=2))


if __name__=='__main__':main()
