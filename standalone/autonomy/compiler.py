import json
import subprocess
import threading


class JavaCompiler:
    def __init__(self, config):
        command = config.get('command') if isinstance(config, dict) else None
        if not isinstance(command, list) or not command or any(not isinstance(part, str) for part in command):
            raise ValueError('Compiler config needs a nonempty command list')
        self.command = command[:]
        self.timed_out = False
        self._start()

    def _start(self):
        self.process = subprocess.Popen(self.command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=subprocess.PIPE, text=True, bufsize=1)
        self.errors = []
        self.reader = threading.Thread(target=self._errors, daemon=True)
        self.reader.start()

    def _errors(self):
        try:
            for line in self.process.stderr:
                self.errors.append(line.rstrip())
                self.errors[:] = self.errors[-20:]
        except (OSError, ValueError):
            return

    def compile(self, route, plan, seconds):
        message = {'grounded': plan['grounded'], 'jump': plan['jump'], 'inputs': route['inputs']}
        if route.get('sharedMovement'):
            message.update(sharedMovement=True, contacts=plan.get('sceneContacts', []))
            for key in ('sneak', 'ladderTicks', 'vertical'):
                if key in plan:
                    message[key] = plan[key]
        return self._exchange(message, seconds)

    def derive_footprints(self, route, seconds):
        return self._exchange({'action': 'footprints', 'blocks': route['blocks']}, seconds)['footprints']

    def derive_standing_footprints(self, route, seconds):
        return self._exchange({'action': 'standingFootprints', 'blocks': route['blocks']}, seconds)['footprints']

    def check_scene(self, scene, positions, movement, ground, seconds, contacts=None, **contract):
        if set(contract) - {'sneak', 'ladderTicks', 'velocities', 'friction'}:
            raise ValueError('Unsupported scene contract')
        return self._exchange(dict({'action': 'sceneCheck', 'scene': scene, 'positions': positions,
                               'movement': movement, 'expectedGround': ground, 'contacts': contacts or []}, **contract), seconds)

    def _exchange(self, message, seconds):
        import queue
        if self.timed_out:
            self._start()
            self.timed_out = False
        received = queue.Queue(maxsize=1)

        def exchange():
            try:
                self.process.stdin.write(json.dumps(message) + '\n')
                self.process.stdin.flush()
                received.put(self.process.stdout.readline())
            except (OSError, ValueError) as exc:
                received.put(exc)

        threading.Thread(target=exchange, daemon=True).start()
        try:
            line = received.get(timeout=seconds)
        except queue.Empty:
            self.close()
            self.timed_out = True
            raise TimeoutError('Movement coefficient compilation exceeded the budget')
        if isinstance(line, Exception) or not line:
            raise RuntimeError('Coefficient compiler failed: ' + str(line) + ' ' + '\n'.join(self.errors))
        result = json.loads(line)
        if 'error' in result:
            raise ValueError(result['error'])
        return result

    def close(self):
        self.timed_out = False
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait()
        for stream in (self.process.stdin, self.process.stdout, self.process.stderr):
            stream.close()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()
