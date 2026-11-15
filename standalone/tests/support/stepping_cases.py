"""Shared test builders extracted from tests.unit.movement.test_stepping_cases."""
from tests.support.paths import AUTONOMY_DATA

import json

FIXTURES = AUTONOMY_DATA / 'fixtures/stepping'

def fixture(name):
    return json.loads((FIXTURES / (name + '.json')).read_text())
