"""Catalog support-agent controller. Offline, one operation at a time, no host mounts."""
import argparse
import hashlib
import json
import math
import secrets
import os
from pathlib import Path
import re
import signal
import shutil
import subprocess
import sys
import time

CLI = '/opt/sfm/cargo/bin/sfm-propagate-changes'
CATALOG = 'platform/minecraft/projections.json'
CORE = 'platform/minecraft/core-liquid-template'
CONFIGURATION = 'platform/minecraft/build-configuration'
FORGE_LINUX = {'1.19.2', '1.19.4', '1.20', '1.20.1'}
SUPPORTED_LINUX = FORGE_LINUX | {'1.20.2', '1.20.3', '1.20.4', '1.21.0', '1.21.1', '26.1.2'}


def load(path):
    return json.loads(path.read_text())


def write(path, value):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value, indent=2) + '\n')
    temporary.replace(path)


def checked_tree(root):
    for path in root.rglob('*'):
        if path.is_symlink():
            raise ValueError('source symlink refused: ' + str(path))


def command(argv, root, log, timeout=900):
    environment = dict(os.environ, HOME=str(root / '.home'),
                       GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull,
                       GIT_TERMINAL_PROMPT='0', LIBGL_ALWAYS_SOFTWARE='true',
                       GALLIUM_DRIVER='llvmpipe', LP_NUM_THREADS='2', ALSOFT_DRIVERS='null')
    started = time.monotonic()
    with log.open('wb') as output:
        child = subprocess.Popen(argv, cwd=root, env=environment, stdout=output,
                                 stderr=subprocess.STDOUT, start_new_session=True)
        try:
            code = child.wait(timeout=timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            os.killpg(child.pid, signal.SIGTERM)
            try:
                child.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid, signal.SIGKILL)
                child.wait(timeout=15)
            raise
    return {'argv': argv, 'exit_code': code,
            'seconds': time.monotonic() - started, 'log': str(log)}


def process_stamp(pid):
    try:
        fields = Path('/proc/' + str(pid) + '/stat').read_text().rpartition(')')[2].split()
        return None if fields[0] == 'Z' else fields[19]
    except (OSError, IndexError):
        return None


def process_alive(active):
    stamp = process_stamp(active['pid'])
    return stamp is not None and stamp == active['process_stamp']


def terminate(active):
    if process_alive(active):
        os.killpg(active['pid'], signal.SIGTERM)
        until = time.monotonic() + 15
        while process_alive(active) and time.monotonic() < until:
            time.sleep(.2)
        if process_alive(active):
            os.killpg(active['pid'], signal.SIGKILL)


def start_managed(argv, root, state, args, active_path):
    game = root / 'platform/minecraft/projections' / state['projection'] / 'runGameTestPreview'
    if (game / 'managed-session').exists():
        raise ValueError('managed session already ran here; choose a fresh session id')
    environment = dict(os.environ, HOME=str(root / '.home'),
                       LIBGL_ALWAYS_SOFTWARE='true', GALLIUM_DRIVER='llvmpipe',
                       LP_NUM_THREADS='2', ALSOFT_DRIVERS='null',
                       SFM_CONTAINER_SESSION_SECONDS=str(args.seconds))
    log = root / ('agent-start-' + str(time.time_ns()) + '.log')
    with log.open('xb') as output:
        child = subprocess.Popen(argv, cwd=root, env=environment, stdout=output,
                                 stderr=subprocess.STDOUT, start_new_session=True)
    active = {'schema': 'sfm:agent_active@1', 'session': args.session,
              'projection': state['projection'], 'pid': child.pid,
              'process_stamp': process_stamp(child.pid), 'game_directory': str(game),
              'log': str(log), 'seconds': args.seconds}
    write(active_path, active)
    deadline = time.monotonic() + 900
    ready = game / 'managed-session/ready.json'
    try:
        while time.monotonic() < deadline:
            if child.poll() is not None:
                raise ValueError('managed launch exited; inspect ' + str(log))
            if ready.exists():
                contract = load(ready)
                if contract.get('schema') != 'sfm.managed-session/1' or contract.get('max_requests') != 128:
                    raise ValueError('unexpected managed readiness contract')
                active['ready'] = contract
                write(active_path, active)
                return active
            if shutil.disk_usage(root).free < 2 * 1024**3:
                raise ValueError('disk reserve exhausted during startup')
            time.sleep(1)
        raise ValueError('managed startup exceeded 900 seconds')
    except BaseException:
        terminate(active)
        active_path.unlink(missing_ok=True)
        raise


def managed_request(active, args):
    directory = Path(active['game_directory']) / 'managed-session'
    operation = {'op': {'run-game-test': 'run_game_test'}.get(args.op, args.op)}
    if args.op == 'run-game-test':
        test = args.game_test.removeprefix('sfm:')
        if not re.fullmatch('[a-zA-Z0-9_]+', test):
            raise ValueError('invalid game test id')
        operation['game_test'] = test
    if args.op == 'capture':
        if not math.isfinite(args.angle) or abs(args.angle) > 2 * math.pi:
            raise ValueError('camera angle outside finite +/-2pi')
        operation['angle'] = args.angle
    for sequence in range(1, 129):
        prefix = str(sequence).zfill(6)
        request = directory / (prefix + '.request.json')
        response = directory / (prefix + '.response.json')
        if response.exists():
            previous = load(response)
            if previous.get('sequence') != sequence:
                raise ValueError('managed response sequence mismatch')
            continue
        if request.exists():
            raise ValueError('another managed operation is pending')
        operation['sequence'] = sequence
        temporary = directory / ('agent-' + secrets.token_hex(8) + '.pending')
        with temporary.open('x') as stream:
            json.dump(operation, stream, allow_nan=False)
        os.link(temporary, request)
        temporary.unlink()
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            if response.exists():
                result = load(response)
                if result.get('schema') != 'sfm.managed-session-response/1' or result.get('sequence') != sequence:
                    raise ValueError('unexpected managed response contract')
                if result.get('status') == 'error':
                    raise ValueError('managed operation failed: ' + str(result))
                if args.op == 'stop':
                    until = time.monotonic() + 30
                    while process_alive(active) and time.monotonic() < until:
                        time.sleep(.2)
                    if process_alive(active):
                        terminate(active)
                return result
            if not process_alive(active):
                raise ValueError('managed game exited before response')
            time.sleep(.2)
        raise ValueError('managed operation timed out; inspect pending request')
    raise ValueError('managed request budget exhausted')



def verify_producers(lock_path):
    """Verify reviewed producer receipts before passing artifacts to the native CLI."""
    cache = Path(os.environ.get('SFM_PROPAGATE_CHANGES_CACHE', '/work/.sfm-cache')) / 'minecraft-toolchain'
    def checked(relative):
        if not relative or relative.startswith('/') or any(c in ('', '.', '..') for c in relative.split('/')):
            raise ValueError('unsafe producer cache path')
        path = cache
        for part in relative.split('/'):
            path = path / part
            if path.is_symlink():
                raise ValueError('producer cache symlink refused')
        if not path.is_file():
            raise ValueError('missing producer input: ' + relative)
        return path
    def digest(value):
        return isinstance(value, str) and re.fullmatch('[a-f0-9]{64}', value)
    verified = []
    for artifact in load(lock_path)['artifacts']:
        coordinate = artifact.get('coordinate') or ''
        build = artifact.get('source_build') or {}
        environment = dict(build.get('environment') or {})
        pin = environment.pop('SFM_PRODUCER_RECEIPT_SHA256', None)
        if not coordinate.startswith('mekanism:Mekanism:26.1.2-') and pin is None:
            continue
        if not digest(pin):
            raise ValueError('missing or invalid digest-bound producer receipt: ' + coordinate)
        receipt_path = checked('producer-receipts/' + pin + '.json')
        if receipt_path.stat().st_size > 2 * 1024**2:
            raise ValueError('producer receipt byte budget exceeded')
        raw = receipt_path.read_bytes()
        if hashlib.sha256(raw).hexdigest() != pin:
            raise ValueError('producer receipt digest mismatch')
        receipt = json.loads(raw)
        git = artifact.get('source_git') or {}
        if (receipt.get('schema') != 'sfm:source_producer@1' or git.get('dirty') is not False
            or git.get('commit') != receipt.get('commit') or git.get('remote_url') != receipt.get('repository_url')):
            raise ValueError('producer repository or revision mismatch')
        for field in ('source_archive_sha256', 'producer_container', 'producer_image', 'java_archive_sha256',
                      'gradle_archive_sha256', 'download_inventory_sha256', 'build_log_sha256'):
            if not digest(receipt.get(field)):
                raise ValueError('invalid producer input digest: ' + field)
        if (receipt.get('exit_code') != 0 or receipt.get('task_output_cache_reused') is not False
            or receipt.get('tracked_source_unchanged') is not True or receipt.get('build_system') != 'gradle-wrapper'
            or receipt.get('tasks') != build.get('tasks') or receipt.get('environment') != environment):
            raise ValueError('producer execution recipe mismatch')
        output = receipt.get('outputs', {}).get(coordinate) or {}
        if (artifact.get('weak') is not None or output.get('content_hash') != artifact.get('hash')
            or output.get('path') != build.get('output_path', '').replace('\\', '/')):
            raise ValueError('producer output lock mismatch')
        relative = artifact['cache_path'].replace('\\', '/')
        if not relative.startswith('$sfm-cache/'):
            raise ValueError('producer output must use the portable cache root')
        path = checked(relative[len('$sfm-cache/'):])
        size = output.get('bytes')
        if not isinstance(size, int) or not 0 < size <= 64 * 1024**2 or path.stat().st_size != size:
            raise ValueError('producer output size mismatch')
        if not digest(output.get('sha256')) or hashlib.sha256(path.read_bytes()).hexdigest() != output['sha256']:
            raise ValueError('producer output bytes mismatch')
        # The native CLI independently verifies the canonical locked BLAKE3 hash.
        verified.append({'coordinate': coordinate, 'receipt_sha256': pin, 'artifact_sha256': output['sha256']})
    return verified


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=Path(os.environ.get('SFM_SUPPORT_SOURCE_ROOT', '/opt/sfm/catalog')))
    parser.add_argument('--sessions', type=Path, default=Path('/work/agent-sessions'))
    parser.add_argument('--cli', default=CLI)
    sub = parser.add_subparsers(dest='op', required=True)
    sub.add_parser('list')
    for op in ('select', 'generate', 'status', 'build', 'launch', 'start', 'capture', 'stop', 'run-game-test'):
        p = sub.add_parser(op)
        p.add_argument('--session', required=True)
        if op in ('select', 'generate'):
            p.add_argument('--projection', required=True)
        if op == 'generate':
            p.add_argument('--name', required=True)
            p.add_argument('--feature', action='append', default=[])
        if op in ('build', 'launch', 'start'):
            p.add_argument('--java-home', type=Path)
            p.add_argument('--game-test', default='sfm:move_1_stack_direct')
            p.add_argument('--heap-mib', type=int, default=4096)
        if op == 'start':
            p.add_argument('--seconds', type=int, default=1800)
        if op == 'capture':
            p.add_argument('--angle', type=float, default=0)
        if op == 'run-game-test':
            p.add_argument('--game-test', required=True)
    args = parser.parse_args()
    for key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'):
        if os.environ.get(key):
            raise ValueError('JVM environment override refused')
    catalog = load(args.source / CATALOG)
    # Canonical metadata permits trailing commas; decode only its strict target map.
    metadata = (args.source / CORE / 'project-inputs.json').read_text()
    target_start = re.search(r'"targets"\s*:\s*', metadata).end()
    targets = json.JSONDecoder().raw_decode(metadata[target_start:])[0]
    readiness_path = args.source / 'support-readiness.json'
    readiness = {r['projection']: r for r in load(readiness_path)} if readiness_path.exists() else {}
    if args.op == 'list':
        print(json.dumps({'schema': 'sfm:agent_catalog@1', 'projections': [
            {'projection': key, **entry, **targets[key.split('/mc-')[1]],
             'linux_native_adapter': key.split('/mc-')[1] in SUPPORTED_LINUX,
             'dependency_snapshot': readiness.get(key, {'cache_ready': False, 'missing': ['catalog cache snapshot not provided']}),
             'runtime_validated': False} for key, entry in sorted(catalog.items())]}, indent=2))
        return 0
    if not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}', args.session):
        raise ValueError('unsafe session id')
    args.sessions.mkdir(parents=True, exist_ok=True)
    if args.sessions.is_symlink():
        raise ValueError("session root symlink refused")
    active_path = args.sessions / '.active-game.json'
    active = load(active_path) if active_path.exists() else None
    if active and not process_alive(active):
        active_path.unlink()
        active = None
    if active and args.op in ('start', 'launch'):
        raise ValueError('one game is already active: ' + active['session'])
    operations = args.sessions / '.operations'
    operations.mkdir(exist_ok=True)
    if operations.is_symlink():
        raise ValueError('operation root symlink refused')
    lock = operations / args.session
    lock.mkdir()  # Per-session mutation safety; independent builds do not share a lock.
    game_start_lock = args.sessions / '.game-start-lock'
    game_start_reserved = False
    try:
        root = args.sessions / args.session
        if root.is_symlink():
            raise ValueError('session symlink refused')
        statepath = root / 'agent-state.json'
        if args.op in ('select', 'generate'):
            if args.projection not in catalog:
                raise ValueError('unknown catalog projection')
            if root.exists():
                raise ValueError('session already exists; use a fresh id')
            if shutil.disk_usage(args.sessions).free < 8 * 1024**3:
                raise ValueError('insufficient headroom: reserve 8 GiB')
            checked_tree(args.source)
            root.mkdir()
            (root / '.home').mkdir()
            (root / 'platform/minecraft').mkdir(parents=True)
            shutil.copytree(args.source / CORE, root / CORE)
            shutil.copytree(args.source / CONFIGURATION, root / CONFIGURATION)
            selected = args.projection
            if args.op == 'generate':
                if not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}', args.name):
                    raise ValueError('unsafe derivative name')
                target = selected.split('/mc-')[1]
                selected = 'agent-' + args.name + '/mc-' + target
                if selected in catalog:
                    raise ValueError('derivative collides with existing catalog entry')
                catalog[selected] = {**catalog[args.projection], 'environment': 'dev',
                                     'features': sorted(set(catalog[args.projection]['features'] + args.feature))}
            write(root / CATALOG, catalog)
            # Preserve canonical environment policy: only dev output roots are ignored.
            (root / '.gitignore').write_text(''.join('/platform/minecraft/projections/' + k + '/\n'
                for k, v in catalog.items() if v['environment'] == 'dev') + '/.home/\n/agent-*\n')
            command(['git', 'init', '-q', root.as_posix()], root, root / 'agent-git.log')
            target = selected.split('/mc-')[1]
            state = {'schema': 'sfm:agent_session@1', 'projection': selected,
                     'base_projection': args.projection, 'target': target,
                     'java_major': targets[target]['java_major'],
                     'tool_java_major': 17 if target in FORGE_LINUX else (25 if target == '26.1.2' else 21), 'generated': False,
                     'catalog_sha256': hashlib.sha256((args.source / CATALOG).read_bytes()).hexdigest(),
                     'built': False, 'runtime_validated': False, 'operations': []}
            write(statepath, state)
            # Canonical generator validates feature prerequisites and target support.
            receipt = command([args.cli, 'source', 'project', 'sync', '--repo-root', str(root),
                               '--projection', selected], root, root / 'agent-generate.log')
            state['operations'].append(receipt)
            state['generated'] = receipt['exit_code'] == 0
            write(statepath, state)
        else:
            state = load(statepath)
        if args.op in ('capture', 'stop', 'run-game-test'):
            if not active or active['session'] != args.session:
                raise ValueError('this managed session is not active')
            result = managed_request(active, args)
            print(json.dumps(result, indent=2))
            return 0
        if args.op == 'status':
            state['active'] = bool(active and active['session'] == args.session)
            state['dependency_snapshot'] = readiness.get(state['base_projection'])
        if args.op in ('build', 'launch', 'start'):
            if not state['generated']:
                raise ValueError('generation failed; inspect agent-generate.log')
            state['producer_receipts'] = verify_producers(root / 'platform/minecraft/projections' / state['projection'] / 'sfm-toolchain.lock.json')
            if state['target'] not in SUPPORTED_LINUX:
                raise ValueError('no Linux adapter for target ' + state['target'])
            tool_major = state.get('tool_java_major', 17 if state['target'] in FORGE_LINUX else (25 if state['target'] == '26.1.2' else 21))
            home = args.java_home or (Path('/usr/lib/jvm/java-17-openjdk-amd64') if tool_major == 17 else Path('/opt/sfm/jdks') / str(tool_major))
            if not (home / 'bin/javac').is_file():
                raise ValueError('missing reviewed Linux JDK ' + str(state['java_major']))
            version = subprocess.check_output([str(home / 'bin/javac'), '-version'], stderr=subprocess.STDOUT, timeout=15).decode()
            if not re.match(r'javac ' + str(tool_major) + r'(?:\.|\s)', version):
                raise ValueError('JDK major does not match selected target')
            if not re.fullmatch(r'sfm:[a-zA-Z0-9_]+', args.game_test) or not 512 <= args.heap_mib <= 8192:
                raise ValueError('invalid game test or heap bound')
            if shutil.disk_usage(root).free < 8 * 1024**3:
                raise ValueError('insufficient build headroom: reserve 8 GiB')
            # Existing puppet path performs canonical native generation/build and captures.
            argv = [args.cli, 'puppet', 'run', 'managed_superflat_session' if args.op == 'start' else 'game_test_orbit_capture', '--repo-root', str(root),
                    '--projection', state['projection'], '--java-home', str(home),
                    '--require-portable-artifacts', '--game-test', args.game_test,
                    '--java-max-heap-mib', str(args.heap_mib),
                    '--control-cli-source-root', '/work']
            if args.op == 'build':
                argv += ['--dry-run']
            else:
                argv = ['/usr/bin/xvfb-run', '-a'] + argv + ['--keep-open', '1s']
            if args.op in ('start', 'launch'):
                game_start_lock.mkdir()  # Only game startup/foreground launch is serialized.
                game_start_reserved = True
                fresh_active = load(active_path) if active_path.exists() else None
                if fresh_active and process_alive(fresh_active):
                    raise ValueError('one game is already active: ' + fresh_active['session'])
                if fresh_active:
                    active_path.unlink()
            if args.op == 'start':
                if not 1 <= args.seconds <= 28800:
                    raise ValueError('session lifetime must be 1..28800 seconds')
                result = start_managed(argv, root, state, args, active_path)
                state['managed'] = result
                write(statepath, state)
                print(json.dumps(result, indent=2))
                return 0
            receipt = command(argv, root, root / ('agent-' + args.op + '.log'))
            state['operations'].append(receipt)
            if args.op == 'build':
                state['built'] = receipt['exit_code'] == 0
            else:
                state['launch_completed'] = receipt['exit_code'] == 0
            # Gameplay acceptance requires external GameTest + decoded capture review.
            write(statepath, state)
        print(json.dumps(state, indent=2))
        return 0 if state['generated'] and (args.op not in ('build', 'launch', 'start') or receipt['exit_code'] == 0) else 2
    finally:
        if game_start_reserved:
            game_start_lock.rmdir()
        lock.rmdir()


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        print(json.dumps({'schema': 'sfm:agent_error@1', 'error': str(error)}), file=sys.stderr)
        sys.exit(2)
