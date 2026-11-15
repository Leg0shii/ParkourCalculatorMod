"""Packaging boundaries whose failures are easy to hide with repository cwd imports."""
import ast
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from parkour_solver.io.provenance import source_hashes
from tests.support.paths import STANDALONE_ROOT


class PackageLayoutTests(unittest.TestCase):
    def test_production_has_no_developer_imports_and_primitives_do_not_import_search(self):
        source = STANDALONE_ROOT / 'src/parkour_solver'
        for path in source.rglob('*.py'):
            for node in ast.walk(ast.parse(path.read_text())):
                names = [a.name for a in node.names] if isinstance(node, ast.Import) else (
                    [node.module or ''] if isinstance(node, ast.ImportFrom) else [])
                for name in names:
                    self.assertNotIn(name.split('.')[0], ('standalone', 'tests', 'tools', 'research'), str(path))
                    if path.parts[-2] in ('scene', 'physics') or path.name == 'schema.py':
                        self.assertFalse(name.startswith('parkour_solver.search'), (str(path), name))

    def test_source_manifest_covers_every_installed_runtime_file(self):
        from parkour_solver import __file__ as package_file
        package = Path(package_file).parent
        expected = {'parkour_solver/' + p.relative_to(package).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                    for p in package.rglob('*.py')}
        self.assertEqual(source_hashes(), expected)
        self.assertIn('parkour_solver/numerics/worker.py', expected)
        self.assertIn('parkour_solver/search/bounds/flight.py', expected)

    def test_tests_share_builders_without_importing_test_cases(self):
        for path in (STANDALONE_ROOT / 'tests').rglob('*.py'):
            for node in ast.walk(ast.parse(path.read_text())):
                names = [a.name for a in node.names] if isinstance(node, ast.Import) else (
                    [node.module or ''] if isinstance(node, ast.ImportFrom) else [])
                for name in names:
                    self.assertFalse(name.startswith('tests.') and '.test_' in name, (str(path), name))

    def test_public_api_and_entrypoints_are_installed_without_checkout_paths(self):
        script = '''
import sys
from importlib.metadata import distribution
from parkour_solver import api
from parkour_solver.search.application import run
assert api.solve_task is run
assert callable(api.find_checkpoint_route) and callable(api.solve)
assert not any(m.split('.')[0] in ('research','tools','tests','standalone') for m in sys.modules)
assert not any(m in sys.modules for m in ('casadi','numpy','scipy','matplotlib','clarabel'))
names = {e.name for e in distribution('parkour-solver').entry_points}
assert {'parkour-solve','parkour-blocks','parkour-route','parkour-ipopt','parkour-fixed-clock'} <= names
'''
        with tempfile.TemporaryDirectory() as cwd:
            result = subprocess.run([sys.executable, '-I', '-c', script], cwd=cwd, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    @unittest.skipUnless(os.environ.get('PKC_IPOPT_PYTHON'), 'Native IPOPT interpreter not configured')
    def test_real_worker_launches_through_installed_package_from_empty_cwd(self):
        script = '''
from parkour_solver.api import solve
p=dict(version=1,ticks=2,position=[0.,0.],velocity=[0.,0.],magnitude=[1.,1.],
       friction=[.9,.9],base=[0.,0.],yaw=[45.,45.],threshold=0.,
       objective=dict(axis='X',sense='MAX',tick=2),
       walls=[dict(mode='X',t1=2,cmp='GE',rhs=.4),dict(mode='X',t1=2,cmp='LE',rhs=.8)])
r=solve(p,seconds=3.)
assert r['status']=='feasible',r
assert r.get('workerLaunched'),r
'''
        with tempfile.TemporaryDirectory() as cwd:
            result = subprocess.run([sys.executable, '-I', '-c', script], cwd=cwd,
                                    capture_output=True, text=True, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)
