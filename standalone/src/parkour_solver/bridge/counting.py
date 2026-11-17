"""Compiler/scene accounting wrapper, independent of search traversal."""
import collections
import time


class CountingCompiler:
    def __init__(self, compiler):
        self.compiler = compiler
        self.counts = collections.Counter()
        self.seconds = collections.Counter()

    def __getattr__(self, name):
        value = getattr(self.compiler, name)
        if not callable(value) or name not in ('compile', 'derive_footprints', 'derive_standing_footprints', 'check_scene'):
            return value
        def invoke(*args, **kwargs):
            started = time.monotonic()
            self.counts[name] += 1
            try:
                result = value(*args, **kwargs)
                if name == 'check_scene' and not result.get('feasible'):
                    self.counts['sceneValidationFailures'] += 1
                return result
            finally:
                self.seconds[name] += time.monotonic() - started
        return invoke

    def summary(self):
        return dict(calls=dict(self.counts), elapsedSeconds=dict(self.seconds))
