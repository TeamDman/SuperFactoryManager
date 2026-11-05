"""Explicit Linux preparation of the existing flat JUnit JAR closure only.

The guarded CLI owns pin selection and BLAKE3 verification. No POM acquisition,
manifest update, native-root adoption, SDK mutation or Java launch occurs here.
"""
import contextlib
import ctypes
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import ssl
import stat
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
import uuid

import cloud_environment as checks

SOURCE = 'platform/minecraft/core-liquid-template/build/lockfiles/1.19.2/schema-4.test-dependency-authority.json'
PROOF = 'platform/minecraft/core-liquid-template/build/lockfiles/1.19.2/schema-4.test-dependency-provenance.json'
CONFIG = 'platform/minecraft/build-configuration/released-native.json'
MAX_FILE = 64 * 1024 * 1024
MAX_TOTAL = 512 * 1024 * 1024
MAX_JSON = 8 * 1024 * 1024
MISSING = {'test_artifact_missing_or_corrupt', 'production_base_artifact_missing_or_corrupt'}
FLAGS = os.O_NOFOLLOW | os.O_CLOEXEC


def cli_json(cli, arguments, environment, allowed=(0,)):
    result = subprocess.run([str(cli), '--output-format', 'json', *arguments], env=environment,
                            capture_output=True, timeout=60, check=False)
    checks.require(result.returncode in allowed, f'CLI command refused: {arguments[0]} (exit {result.returncode})')
    checks.require(len(result.stdout) <= MAX_JSON, 'CLI JSON exceeds its bound')
    return json.loads(result.stdout)


def preflight(repo, cli, environment, root):
    env = dict(environment, SFM_PROPAGATE_CHANGES_CACHE=str(root))
    report = cli_json(cli, ['test', 'preflight', '--repo-root', str(repo)], env, (0, 1))
    checks.require(report['schema'] == 'sfm:junit_dependency_preflight@1'
                   and not any(report[k] for k in ('writes_performed', 'java_launched', 'live_pom_discovery')),
                   'unexpected JUnit preflight contract')
    checks.require(all(issue['code'] in MISSING for issue in report['issues']),
                   'JUnit authority/roles are invalid; cache preparation cannot repair source')
    return report


def tracked_json(repo, relative, expected):
    path = checks.real_path(repo / relative)
    checks.require(path.is_relative_to(repo), 'JUnit authority escapes repository')
    checks.git(repo, 'ls-files', '--error-unmatch', str(path.relative_to(repo)))
    checks.require(checks.digest(path) == expected.removeprefix('sha256:'), 'JUnit authority changed')
    return checks.load(path)


def maven_path(coordinate):
    parts = coordinate.split(':')
    checks.require(len(parts) == 3 and all(re.fullmatch(r'[A-Za-z0-9_.-]+', p) and '..' not in p
                                        and p not in ('.', '..') for p in parts), 'unsafe JUnit coordinate')
    group, artifact, version = parts
    checks.require(all(group.split('.')), 'empty Maven group component')
    return Path(group.replace('.', '/')) / artifact / version / f'{artifact}-{version}.jar'


def pins_from_report(repo, report):
    source = tracked_json(repo, SOURCE, report['test_source_lock_sha256'])
    tracked_json(repo, PROOF, report['test_provenance_sha256'])
    config = tracked_json(repo, CONFIG, report['configuration_sha256'])
    checks.require(len(report['targets']) == 10 and len({t['target_id'] for t in report['targets']}) == 10,
                   'expected all ten reviewed JUnit targets')
    selected, origins = {}, list(source['artifacts'])
    for target in report['targets']:
        recipes = [r for r in config['targets'] if r['target'] == target['target_id']
                   and r['recipe_id'] == target['released_recipe_id']
                   and r['source_lock']['sha256'] == target['raw_source_lock_sha256']]
        checks.require(len(recipes) == 1, 'JUnit recipe authority differs from preflight')
        ref = recipes[0]['source_lock']
        origins.extend(tracked_json(repo, ref['path'], ref['sha256'])['artifacts'])
        for pin in target['selected_pins'] + target['production_base_witnesses']:
            previous = selected.setdefault(pin['coordinate'], pin)
            checks.require(previous == pin, 'contradictory JUnit pin')
    checks.require(len(selected) == 26, 'expected exactly 25 test artifacts and one production witness')
    result = []
    for coordinate, pin in sorted(selected.items()):
        checks.require(re.fullmatch(r'blake3:[0-9a-f]{40}', pin['content_hash']), 'unsupported JUnit hash')
        relative = maven_path(coordinate)
        rows = [a for a in origins if a.get('coordinate') == coordinate and a.get('hash') == pin['content_hash']]
        checks.require(rows, f'no tracked JAR origin: {coordinate}')
        # The immutable original authority has precedence; no URL is guessed.
        url = rows[0]['url']
        parsed = urllib.parse.urlsplit(url)
        checks.require(parsed.scheme == 'https' and parsed.hostname and not parsed.username and not parsed.password
                       and not parsed.query and not parsed.fragment and parsed.port is None,
                       'JUnit origin must be exact credential-free HTTPS')
        checks.require(parsed.path.endswith('/' + relative.as_posix()), 'JUnit origin differs from Maven coordinate')
        if pin['full_sha256'] is not None:
            checks.require(re.fullmatch(r'[0-9a-f]{64}', pin['full_sha256']), 'invalid full artifact digest')
        checks.require(pin['bytes'] is None or 0 < pin['bytes'] <= MAX_FILE, 'invalid artifact length')
        result.append(dict(pin, url=url, relative=relative.as_posix()))
    return result


def read_regular(fd, limit):
    before = os.fstat(fd)
    checks.require(stat.S_ISREG(before.st_mode) and before.st_nlink == 1 and before.st_size <= limit,
                   'artifact/marker/lock must be bounded, regular and single-linked')
    os.lseek(fd, 0, os.SEEK_SET)
    chunks, size = [], 0
    while block := os.read(fd, min(65536, limit + 1 - size)):
        chunks.append(block); size += len(block)
        checks.require(size <= limit, 'file grew beyond its bound')
    after = os.fstat(fd)
    checks.require((before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns)
                   == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns), 'file changed while reading')
    return b''.join(chunks)


class CacheRoot:
    def __init__(self, path, native=False):
        self.path = Path(path)
        checks.require(self.path.is_absolute() and self.path.parent != self.path
                       and '..' not in self.path.parts, 'cache root must be an existing absolute normal path')
        self.handles = contextlib.ExitStack()
        self.marker = None
        try:
            fd = os.open('/', os.O_RDONLY | os.O_DIRECTORY | FLAGS)
            self.handles.callback(os.close, fd)
            for part in self.path.parts[1:]:
                fd = os.open(part, os.O_RDONLY | os.O_DIRECTORY | FLAGS, dir_fd=fd)
                self.handles.callback(os.close, fd)
            self.fd = fd
            self.recheck()
            if native or os.path.lexists(self.path / '.sfm-native-cache-root.json'):
                self.marker = os.open('.sfm-native-cache-root.json', os.O_RDONLY | FLAGS, dir_fd=fd)
                self.handles.callback(os.close, self.marker)
                self.recheck()
        except BaseException:
            self.close(); raise

    def close(self):
        self.handles.close()

    def recheck(self):
        checks.real_path(self.path, True)
        current, held = self.path.stat(), os.fstat(self.fd)
        checks.require((current.st_dev, current.st_ino) == (held.st_dev, held.st_ino), 'cache root was replaced')
        if self.marker is not None:
            expected = json.dumps(['sfm:native_cache_root@1', str(self.path)], ensure_ascii=False,
                                  separators=(',', ':')).encode()
            checks.require(read_regular(self.marker, 64 * 1024) == expected,
                           'native root requires its existing canonical CLI-owned marker; do not mkdir/adopt it')
            opened = os.stat('.sfm-native-cache-root.json', dir_fd=self.fd, follow_symlinks=False)
            held = os.fstat(self.marker)
            checks.require((opened.st_dev, opened.st_ino) == (held.st_dev, held.st_ino),
                           'native ownership marker was replaced')

    def check_parent(self, relative, fd):
        self.recheck()
        path = checks.real_path(self.path / relative.parent, True)
        current, held = path.stat(), os.fstat(fd)
        checks.require((current.st_dev, current.st_ino) == (held.st_dev, held.st_ino),
                       'artifact parent was replaced')

    @contextlib.contextmanager
    def parent(self, relative, create):
        checks.require(not relative.is_absolute() and '..' not in relative.parts, 'cache path escapes owner')
        with contextlib.ExitStack() as stack:
            fd = self.fd
            for part in relative.parent.parts:
                if create:
                    try: os.mkdir(part, 0o755, dir_fd=fd)
                    except FileExistsError: pass
                fd = os.open(part, os.O_RDONLY | os.O_DIRECTORY | FLAGS, dir_fd=fd)
                stack.callback(os.close, fd)
            self.check_parent(relative, fd)
            yield fd
            self.check_parent(relative, fd)


@contextlib.contextmanager
def artifact_lock(parent, name, timeout=10):
    fd = os.open(name + '.lock', os.O_RDWR | os.O_CREAT | FLAGS, 0o600, dir_fd=parent)
    try:
        info = os.fstat(fd)
        checks.require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1, 'unsafe artifact lock')
        deadline = time.monotonic() + timeout
        while True:
            try: fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB); break
            except BlockingIOError:
                checks.require(time.monotonic() < deadline, 'artifact lock busy; drain the owner, do not remove its lock')
                time.sleep(0.05)
        read_regular(fd, 64 * 1024)
        yield
    finally: os.close(fd)


def verify(path, fd, pin, cli, environment):
    data = read_regular(fd, MAX_FILE)
    digest = hashlib.sha256(data).hexdigest()
    checks.require(pin['bytes'] is None or len(data) == pin['bytes'], 'artifact length mismatch')
    checks.require(pin['full_sha256'] is None or digest == pin['full_sha256'], 'artifact full SHA-256 mismatch')
    # Hash a private snapshot of the bytes read from the held descriptor. The
    # CLI deliberately rejects /proc descriptor links; reopening the shared
    # cache pathname could instead hash a temporarily substituted inode.
    with tempfile.TemporaryDirectory(prefix='sfm-junit-hash-') as folder:
        snapshot = Path(folder) / 'artifact.jar'
        with snapshot.open('xb') as output: output.write(data)
        report = cli_json(cli, ['hash', 'file', '--algorithm', 'blake3', '--length', '20', str(snapshot)], environment)
    checks.require(report['schema'] == 'sfm:file_hash@1' and report['algorithm'] == 'blake3'
                   and report['digest_bytes'] == 20 and report['identity'] == pin['content_hash']
                   and report['bytes_hashed'] == len(data) and not report['writes_performed'], 'artifact canonical BLAKE3 mismatch')
    checks.require(read_regular(fd, MAX_FILE) == data, 'artifact changed after hashing')
    opened = path.stat(follow_symlinks=False)
    held = os.fstat(fd)
    checks.require((opened.st_dev, opened.st_ino) == (held.st_dev, held.st_ino), 'artifact path replaced during hashing')
    return data, digest


def artifact_relative(pin):
    relative = maven_path(pin['coordinate'])
    checks.require(relative.as_posix() == pin['relative'], 'artifact path differs from pinned JAR coordinate')
    return Path('minecraft-toolchain/maven') / relative


def existing(root, pin, cli, environment):
    relative = artifact_relative(pin)
    try:
        with root.parent(relative, False) as parent:
            fd = os.open(relative.name, os.O_RDONLY | FLAGS, dir_fd=parent)
            try: return verify(root.path / relative, fd, pin, cli, environment)
            finally: os.close(fd)
    except FileNotFoundError:
        return None


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError('JUnit download redirect refused; no alternate source or policy bypass permitted')


def download(pin, layout):
    # ProxyHandler retains the session's inherited proxy/credential routing.
    context = ssl.create_default_context(cafile=layout['tls_ca_file'], capath=layout['tls_ca_dir'])
    checks.require(context.verify_mode == ssl.CERT_REQUIRED and context.check_hostname, 'verified TLS required')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler(),
                                        urllib.request.HTTPSHandler(context=context), NoRedirect())
    deadline, chunks, size = time.monotonic() + 30, [], 0
    with opener.open(pin['url'], timeout=5) as response:
        checks.require(response.status == 200 and response.url == pin['url'], 'unexpected JUnit download response')
        while True:
            checks.require(time.monotonic() < deadline, 'JUnit download exceeded bounded duration')
            block = response.read(65536)
            if not block: break
            size += len(block); checks.require(size <= MAX_FILE, 'JUnit download exceeds byte bound')
            chunks.append(block)
    return b''.join(chunks)


def rename_new(parent, source, destination):
    # Linux renameat2(RENAME_NOREPLACE): atomic visibility, single link, no
    # overwrite window and no cross-filesystem staging. Unsupported fails closed.
    libc = ctypes.CDLL(None, use_errno=True)
    function = getattr(libc, 'renameat2', None)
    checks.require(function is not None, 'Linux renameat2 is required; no replace fallback is allowed')
    function.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint]
    function.restype = ctypes.c_int
    if function(parent, os.fsencode(source), parent, os.fsencode(destination), 1):
        code = ctypes.get_errno()
        raise OSError(code, os.strerror(code), destination)


def publish(root, pin, data, cli, environment):
    relative = artifact_relative(pin)
    checks.require(len(data) <= MAX_FILE, 'artifact exceeds byte bound')
    with root.parent(relative, True) as parent, artifact_lock(parent, relative.name):
        if found := existing(root, pin, cli, environment): return 'reused', found[1]
        name = '.sfm-junit-' + uuid.uuid4().hex + '.part'
        fd = os.open(name, os.O_RDWR | os.O_CREAT | os.O_EXCL | FLAGS, 0o600, dir_fd=parent)
        identity = os.fstat(fd)
        try:
            with os.fdopen(os.dup(fd), 'wb') as output:
                output.write(data); output.flush(); os.fsync(output.fileno())
            _, digest = verify(root.path / relative.parent / name, fd, pin, cli, environment)
            root.check_parent(relative, parent)
            rename_new(parent, name, relative.name)
            os.fsync(parent)
            return 'created', digest
        finally:
            os.close(fd)
            # Only this invocation's private staging inode may be removed.
            try:
                current = os.stat(name, dir_fd=parent, follow_symlinks=False)
                if (current.st_dev, current.st_ino) == (identity.st_dev, identity.st_ino): os.unlink(name, dir_fd=parent)
            except FileNotFoundError: pass


def selected_java_trust(layout):
    identities = {}
    for sdk in layout['sdks']:
        path = checks.real_path(sdk['trust_store'])
        identities[str(path)] = checks.digest(path)
        checks.require(identities[str(path)] == sdk['trust_sha256'], 'selected Java trust store changed')
    return identities


def prepare(repo, layout, cli, build_receipt, output):
    checks.require(sys.platform == 'linux', 'JUnit cache preparation requires Linux guarded file operations')
    checks.require(not checks.git(repo, 'status', '--porcelain', '--untracked-files=no'), 'tracked checkout is dirty')
    head = checks.git(repo, 'rev-parse', 'HEAD')
    checks.validate_layout(layout); checks.require_drained(repo, layout)
    environment = checks.tls_environment(layout)
    checks.native_binary(repo, cli, layout['denied_certificates'])
    build = checks.load(build_receipt)
    checks.require(build['schema'] == 'sfm:cloud-release-build@1' and build['source_commit'] == head
                   and build['cli_sha256'] == checks.digest(cli) and Path(build['cli']) == cli
                   and build['layout_sha256'] == checks.digest_payload(layout)
                   and build['cargo_lock_sha256'] == checks.digest(repo / 'platform/cli/sfm-propagate-changes/Cargo.lock'),
                   'guarded CLI build receipt differs from source/layout/binary')
    version = subprocess.check_output([str(cli), '--version'], env=environment, text=True, timeout=10).strip()
    checks.require(re.search(r'\brev ' + re.escape(head) + r'(?:[,\s)]|$)', version),
                   'CLI does not report the exact source head')
    paths = sorted({Path(layout['native_cache_root']), Path(layout['application_cache_root'])})
    project = repo / 'platform/minecraft'
    checks.require(all(p.is_absolute() and not repo.is_relative_to(p) and not p.is_relative_to(project)
                       and not project.is_relative_to(p) for p in paths), 'cache root overlaps protected source owner')
    checks.require(all(not p.is_relative_to(repo) or p == repo / 'sfm-toolchain-data' for p in paths),
                   'cache inside repository must be its conventional sfm-toolchain-data root')
    checks.require(not any(a != b and a.is_relative_to(b) for a in paths for b in paths), 'managed cache roots overlap')
    for sdk in layout['sdks']:
        home, native = Path(sdk['home']), Path(layout['native_cache_root'])
        checks.require(not home.is_relative_to(native) and not native.is_relative_to(home),
                       'explicit SDK overlaps native build root')
        checks.require(not any((p / 'minecraft-toolchain/maven').is_relative_to(Path(sdk['home']))
                               or Path(sdk['home']).is_relative_to(p / 'minecraft-toolchain/maven') for p in paths),
                       'Maven cache overlaps SDK home')
    checks.real_path(output.parent, True)
    checks.require(output.is_absolute() and '..' not in output.parts, 'receipt output must be an absolute normal path')
    managed = set(paths) | {Path(s['sdk_cache_root']) for s in layout['sdks']}
    checks.require(not any(a != b and a.is_relative_to(b) for a in managed for b in managed),
                   'managed cache/SDK roots overlap')
    checks.require(not output.exists() and not output.is_symlink() and not output.is_relative_to(repo)
                   and not any(output.is_relative_to(p) for p in managed), 'receipt output must be new and outside source/caches')
    java_trust = selected_java_trust(layout)
    with contextlib.ExitStack() as stack:
        roots = []
        for path in paths:
            root = CacheRoot(path, path == Path(layout['native_cache_root']))
            stack.callback(root.close); roots.append(root)
        before = preflight(repo, cli, environment, Path(layout['application_cache_root']))
        pins = pins_from_report(repo, before)
        # Refuse every conflicting existing byte/link before the first download.
        for root in roots:
            for pin in pins: existing(root, pin, cli, environment)
        output.mkdir()
        (output / 'preflight.before.json').write_text(json.dumps(before, indent=2) + '\n')
        changes, total = [], 0
        for index, pin in enumerate(pins):
            data = None
            for root in roots:
                if cached := existing(root, pin, cli, environment): data = cached[0]; break
            if data is None: data = download(pin, layout)
            total += len(data) * (len(roots) + 1)  # Both caches and retained staging.
            checks.require(total <= MAX_TOTAL, 'JUnit preparation exceeds total byte budget')
            staged = output / f'{index:02d}.jar'
            with staged.open('xb') as destination: destination.write(data)
            with staged.open('rb') as source: _, digest = verify(staged, source.fileno(), pin, cli, environment)
            for root in roots:
                action, actual = publish(root, pin, data, cli, environment)
                checks.require(actual == digest, 'published bytes differ from verified staging')
                changes.append({'root': str(root.path), 'coordinate': pin['coordinate'], 'url': pin['url'],
                                'content_hash': pin['content_hash'], 'sha256': digest, 'bytes': len(data), 'action': action})
            print(f'JUnit cache prepared {index + 1}/{len(pins)}: {pin["coordinate"]}', file=sys.stderr, flush=True)
        for index, root in enumerate(roots):
            after = preflight(repo, cli, environment, root.path)
            checks.require(after['closure_complete'] and not after['issues'], 'prepared JUnit closure is incomplete')
            checks.require(pins_from_report(repo, after) == pins, 'JUnit authority changed during preparation')
            (output / f'preflight.after.{index}.json').write_text(json.dumps(after, indent=2) + '\n')
            root.recheck()
        checks.require(checks.git(repo, 'rev-parse', 'HEAD') == head
                       and not checks.git(repo, 'status', '--porcelain', '--untracked-files=no'), 'source changed during preparation')
        checks.require(checks.digest(cli) == build['cli_sha256'], 'CLI changed during preparation')
        checks.tls_environment(layout)
        checks.require(selected_java_trust(layout) == java_trust, 'selected Java trust changed during preparation')
        record = {'schema': 'sfm:cloud-junit-cache-preparation@1', 'source_commit': head,
                  'cli_sha256': build['cli_sha256'], 'layout_sha256': checks.digest_payload(layout),
                  'authority_sha256': before['test_source_lock_sha256'], 'provenance_sha256': before['test_provenance_sha256'],
                  'configuration_sha256': before['configuration_sha256'], 'artifact_count': len(pins),
                  'java_trust_sha256': java_trust,
                  'cache_roots': [str(r.path) for r in roots], 'artifacts': changes,
                  'qualification': 'pinned-JAR-preparation-only; no Java, full JUnit, offline-image or fresh-task proof'}
        with (output / 'success.json').open('x') as destination: destination.write(json.dumps(record, indent=2) + '\n')
        return record
