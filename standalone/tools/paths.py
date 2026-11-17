"""Checkout data locations for developer/research tools, not solver runtime."""
import os
from pathlib import Path

STANDALONE_ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = STANDALONE_ROOT.parent
RESEARCH_ROOT = STANDALONE_ROOT / 'research'
MOVEMENT_FIXTURES = STANDALONE_ROOT / 'tests/fixtures/movement'
NUMERICAL_FIXTURES = STANDALONE_ROOT / 'tests/fixtures/numerics'
REGRESSION_FIXTURES = STANDALONE_ROOT / 'tests/fixtures/regression'
DEFAULT_COMPILER = Path(os.environ.get('PKC_RUN_TICKS_COMPILER', STANDALONE_ROOT / 'build/native/compiler.json'))
