"""Stable fixture locations, including paths embedded in historical evidence.

Recorded evidence retains original relative fixture names. Resolve that metadata
here without copying archives into the package or rewriting their provenance.
"""
from pathlib import PurePosixPath
from tools.paths import (STANDALONE_ROOT, RESEARCH_ROOT, MOVEMENT_FIXTURES,
                         NUMERICAL_FIXTURES, REGRESSION_FIXTURES)


class RecordedData:
    def __init__(self, fixtures):
        self.fixtures = fixtures

    def __truediv__(self, relative):
        parts = PurePosixPath(str(relative)).parts
        if parts and parts[0] == 'fixtures':
            return self.fixtures.joinpath(*parts[1:])
        if parts and parts[0] == 'experiments':
            return RESEARCH_ROOT.joinpath(*parts[1:])
        raise ValueError('Unrecognized recorded fixture location: '+str(relative))


AUTONOMY_DATA = RecordedData(MOVEMENT_FIXTURES)
NUMERICAL_DATA = RecordedData(NUMERICAL_FIXTURES)
