"""Search budgets and compiler accounting, independent of route adapters."""

from parkour_solver.bridge.counting import CountingCompiler
import collections
import hashlib
import json
import time


class SearchWork:
    def __init__(self, max_solver_calls=None, *, deadline=None):
        if max_solver_calls is not None and (type(max_solver_calls) is not int or max_solver_calls < 1):
            raise ValueError('max_solver_calls must be a positive integer')
        self.max_solver_calls = max_solver_calls
        self.deadline = deadline
        self.started = time.monotonic()
        self.termination = None
        self.counts = collections.Counter()
        self.program_keys = set()
        self.first_proposal = None
        self.first_accepted = None
        self.program_domains = collections.Counter()

    def check(self):
        if self.deadline is not None and time.monotonic() >= self.deadline:
            self.termination = 'deadline'
        if self.max_solver_calls is not None and self.counts['solverCalls'] >= self.max_solver_calls:
            self.termination = 'numerical_call_limit'
        if self.termination:
            raise TimeoutError(self.termination)

    def program(self, program):
        profile = program['profile']
        key = hashlib.sha256(json.dumps([profile, program['ladderEvents']], sort_keys=True).encode()).hexdigest()
        self.program_keys.add(key)
        self.counts['completePrograms'] += 1
        self.counts['completeProgramInputs'] += profile['airtime']
        for u in program['movementUnits']:
            self.program_domains[u['type']] += 1
        self.counts['selectedFiniteContacts'] += len(profile.get('contactEvents', []))
        self.counts['selectedSneakInputs'] += sum(profile.get('sneak', []))

    def proposal(self, problem):
        self.counts['numericalProposals'] += 1
        if self.first_proposal is None:
            self.first_proposal = time.monotonic() - self.started

    def before_solve(self, problem):
        self.check()
        self.counts['solverCalls'] += 1

    def accepted(self, best):
        self.counts['acceptedBranches'] += 1
        if self.first_accepted is None:
            self.first_accepted = time.monotonic() - self.started

    def summary(self):
        return dict(self.counts, uniqueCompletePrograms=len(self.program_keys),
            unitsByType=dict(self.program_domains), firstProposalSeconds=self.first_proposal,
            firstAcceptedSeconds=self.first_accepted, maxSolverCalls=self.max_solver_calls,
            termination=self.termination)
