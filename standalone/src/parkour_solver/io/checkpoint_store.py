"""Atomic, lossless checkpoints with shared data stored once in a companion DB.

The storage format is independent of the search-state schema. Decoding returns
ordinary JSON values, including independent copies of repeated mutable objects.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import sqlite3
import uuid
import zlib


FORMAT = 'pkc-checkpoint-store'
VERSION = 1
CHUNK_BYTES = 2048


class StoredValue:
    def __init__(self, identity, key):
        self.identity, self.key = identity, key


def _json(value):
    return json.dumps(value, separators=(',', ':'), allow_nan=False).encode()


def _atomic_json(path, value):
    data = _json(value)
    temporary = path.with_name(path.name+'.tmp')
    temporary.write_bytes(gzip.compress(data, mtime=0) if path.suffix == '.gz' else data)
    temporary.replace(path)


class CheckpointStore:
    """Commit new content before publishing its small atomic root manifest.

    Containers are tagged so even user data resembling a reference round-trips.
    Every save visits current data; only unchanged *content* is reused. Mutable
    object identities are never cached across saves.
    """
    def __init__(self, path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.database = self.path.with_name(self.path.name+'.store.sqlite3')
        self.connection = sqlite3.connect(self.database)
        with self.connection:
            self.connection.execute('CREATE TABLE IF NOT EXISTS metadata (version INTEGER, identity TEXT)')
            self.connection.execute('CREATE TABLE IF NOT EXISTS objects (key TEXT PRIMARY KEY, data BLOB NOT NULL)')
            row = self.connection.execute('SELECT version, identity FROM metadata').fetchone()
            if row is None:
                row = VERSION, uuid.uuid4().hex
                self.connection.execute('INSERT INTO metadata VALUES (?, ?)', row)
        if row[0] != VERSION:
            self.close()
            raise ValueError('Unsupported checkpoint store version')
        self.identity = row[1]
        self.known = {r[0] for r in self.connection.execute('SELECT key FROM objects')}

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()

    def close(self):
        self.connection.close()

    def save(self, report):
        reference, statistics = self.archive(report)
        self.publish(reference)
        return statistics

    def archive(self, report):
        """Store a value once and return a handle usable in subsequent saves."""
        pending, memo = {}, {}

        def remember(data):
            key = hashlib.sha256(data).hexdigest()
            if key not in self.known:
                pending[key] = data
            return key

        def encode(value):
            if isinstance(value, StoredValue):
                if value.identity != self.identity:
                    raise ValueError('Reference belongs to another checkpoint store')
                return _json(['r', value.key])
            container = isinstance(value, (dict, list, tuple))
            shared = container or isinstance(value, str) and len(value) >= CHUNK_BYTES
            identity = id(value)
            if shared and identity in memo:
                return memo[identity][1]
            def flat(item):
                if isinstance(item, StoredValue):
                    return False
                if isinstance(item, dict):
                    children = item.values()
                elif isinstance(item, (list, tuple)):
                    children = item
                else:
                    return not isinstance(item, str) or len(item) < CHUNK_BYTES
                return len(item) <= 16 and all(not isinstance(v, (dict, list, tuple, StoredValue)) and
                    (not isinstance(v, str) or len(v) < CHUNK_BYTES) for v in children)

            if container and all(flat(v) for v in (value.values() if isinstance(value, dict) else value)):
                # Let the C JSON encoder handle dense numerical arrays and small
                # constraint rows together; Python-level scalar traversal costs
                # more than writing them.
                data = b'["j",'+_json(value)+b']'
            elif isinstance(value, dict):
                if any(not isinstance(k, str) for k in value):
                    raise TypeError('Checkpoint dictionaries require string keys')
                pairs = (b'['+_json(k)+b','+encode(v)+b']' for k, v in value.items())
                data = b'["d",['+b','.join(pairs)+b']]'
            elif isinstance(value, (list, tuple)):
                data = b'["l",['+b','.join(encode(v) for v in value)+b']]'
            else:
                data = _json(value)
            if len(data) >= CHUNK_BYTES:
                data = _json(['r', remember(data)])
            if shared:
                # Keep a strong reference: temporary objects cannot reuse IDs.
                memo[identity] = value, data
            return data

        root = remember(encode(report))
        # A failed transaction or manifest replacement leaves the previous root
        # readable. Extra committed objects from a failed publication are safe.
        with self.connection:
            self.connection.executemany('INSERT INTO objects VALUES (?, ?)',
                ((k, zlib.compress(v, level=1)) for k,v in pending.items()))
        self.known.update(pending)
        return StoredValue(self.identity, root), dict(newObjects=len(pending), newBytes=sum(map(len, pending.values())))

    def publish(self, reference):
        if reference.identity != self.identity:
            raise ValueError('Reference belongs to another checkpoint store')
        manifest = dict(format=FORMAT, version=VERSION, store=self.database.name,
                        storeId=self.identity, root=reference.key)
        _atomic_json(self.path, manifest)

    def patch(self, base, changes):
        """Publish replacements against a frozen root without visiting its tree."""
        if base.identity != self.identity:
            raise ValueError('Reference belongs to another checkpoint store')
        changes, statistics = self.archive(changes)
        data = _json(['p', [base.key, changes.key]])
        key = hashlib.sha256(data).hexdigest()
        if key not in self.known:
            with self.connection:
                self.connection.execute('INSERT INTO objects VALUES (?, ?)', (key,zlib.compress(data,level=1)))
            self.known.add(key)
        self.publish(StoredValue(self.identity, key))
        return statistics

    def compact(self):
        """Drop superseded roots after the final report has been published."""
        manifest = json.loads(gzip.decompress(self.path.read_bytes()) if self.path.suffix == '.gz' else self.path.read_bytes())
        reachable = set()

        def visit(key):
            if key in reachable:
                return
            reachable.add(key)
            data = self.connection.execute('SELECT data FROM objects WHERE key=?',(key,)).fetchone()[0]
            walk(json.loads(zlib.decompress(data)))

        def walk(node):
            if not isinstance(node,list):
                return
            tag, content = node
            if tag == 'r':
                visit(content)
            elif tag == 'p':
                for key in content:
                    visit(key)
            elif tag == 'd':
                for _, value in content:
                    walk(value)
            elif tag == 'l':
                for value in content:
                    walk(value)

        visit(manifest['root'])
        with self.connection:
            self.connection.executemany('DELETE FROM objects WHERE key=?',((k,) for k in self.known-reachable))
        self.connection.execute('VACUUM')
        self.known = reachable


def read_report(path):
    """Read a new manifest/store pair or a legacy plain/gzipped JSON report."""
    path = Path(path)
    raw = path.read_bytes()
    value = json.loads(gzip.decompress(raw) if path.suffix == '.gz' else raw)
    if not isinstance(value, dict) or value.get('format') != FORMAT:
        return value
    if value.get('version') != VERSION:
        raise ValueError('Unsupported checkpoint manifest version')
    database = path.parent/value['store']
    if not database.is_file():
        raise ValueError('Missing checkpoint companion database: '+str(database))
    connection = sqlite3.connect(database.resolve().as_uri()+'?mode=ro', uri=True)
    try:
        metadata = connection.execute('SELECT version, identity FROM metadata').fetchone()
        if metadata != (VERSION, value['storeId']):
            raise ValueError('Checkpoint companion database does not match manifest')
        encoded = {}

        def resolve(key):
            if key not in encoded:
                row = connection.execute('SELECT data FROM objects WHERE key=?', (key,)).fetchone()
                data = zlib.decompress(row[0]) if row is not None else b''
                if row is None or hashlib.sha256(data).hexdigest() != key:
                    raise ValueError('Missing or corrupt checkpoint object: '+key)
                encoded[key] = json.loads(data)
            return decode(encoded[key])

        def decode(node):
            if not isinstance(node, list):
                return node
            tag, content = node
            if tag == 'r':
                return resolve(content)
            if tag == 'd':
                return {k:decode(v) for k,v in content}
            if tag == 'l':
                return [decode(v) for v in content]
            if tag == 'j':
                # Do not share mutable decoded objects between references.
                return json.loads(json.dumps(content))
            if tag == 'p':
                base, changes = map(resolve, content)
                for path, replacement in changes:
                    parent = base
                    for part in path[:-1]:
                        parent = parent[part]
                    parent[path[-1]] = replacement
                return base
            raise ValueError('Invalid checkpoint object tag')

        return resolve(value['root'])
    finally:
        connection.close()


def main():
    parser = argparse.ArgumentParser(description='Export a stored checkpoint as ordinary JSON for external audit tools')
    parser.add_argument('input', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.input.resolve() == args.output.resolve():
        parser.error('Export to a different path to preserve the checkpoint')
    report = read_report(args.input)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    _atomic_json(args.output, report)


if __name__ == '__main__':
    main()
