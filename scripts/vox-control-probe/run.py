#!/usr/bin/env python3
"""Bounded real SFM Rust CLI / generated Java wire probe; not a game test."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java-home', type=Path, required=True)
parser.add_argument('--runtime-jar', type=Path, required=True)
parser.add_argument('--sfm', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=False)
jar = args.runtime_jar.resolve()
assert hashlib.sha256(jar.read_bytes()).hexdigest() == 'be441a57e11d4981d283306fe471874e625bf2f423c83a91ce8f44dde623d062'
classes = out / 'classes'
classes.mkdir()
sources = sorted((root / 'platform/minecraft/core-liquid-template/src/main/java/ca/teamdman/sfm/client/control/generated').glob('*.java'))
sources.append(Path(__file__).with_name('ControlServer.java').resolve())
subprocess.run([str(args.java_home / 'bin/javac'), '--release', '17', '-Xlint:all', '-Werror', '-cp', str(jar), '-d', str(classes), *map(str, sources)], check=True, timeout=60)
env = os.environ.copy()
env['XDG_DATA_HOME'] = str(out / 'data')
descriptor = out / 'data/teamdman/sfm/instances/fixture.json'
commands = []

def invoke(label, argv):
    command = [str(args.sfm.resolve()), *argv]
    result = subprocess.run(command, env=env, capture_output=True, text=True, timeout=20)
    (out / (label + '.stdout')).write_text(result.stdout)
    (out / (label + '.stderr')).write_text(result.stderr)
    commands.append({'label': label, 'argv': command, 'exit': result.returncode})
    return result

with (out / 'server.log').open('w') as log:
    server = subprocess.Popen([str(args.java_home / 'bin/java'), '-ea', '-cp', os.pathsep.join([str(classes), str(jar)]), 'ControlServer', str(descriptor)], stdout=log, stderr=subprocess.STDOUT, env=env)
    try:
        deadline = time.monotonic() + 10
        while not descriptor.exists():
            if server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError('Java fixture failed to become ready')
            time.sleep(0.05)
        result = invoke('discover', ['--output-format', 'json', 'instance', 'list'])
        assert result.returncode == 0 and '"live"' in result.stdout and 'not-minecraft' in result.stdout, result.stdout + result.stderr
        result = invoke('echo', ['--output-format', 'json', 'action', 'invoke', 'sfm:echo', 'cutover-π-世界'])
        assert result.returncode == 0 and 'cutover-π-世界' in result.stdout, result.stdout + result.stderr
        original = json.loads(descriptor.read_text())
        bad = dict(original, authentication_token='deliberately-wrong')
        descriptor.write_text(json.dumps(bad))
        result = invoke('reject-auth', ['--output-format', 'json', 'instance', 'list'])
        assert 'AuthenticationFailed' in result.stdout and '"live"' not in result.stdout, result.stdout + result.stderr
        bad = dict(original, protocol_version=999)
        descriptor.write_text(json.dumps(bad))
        result = invoke('reject-protocol', ['--output-format', 'json', 'instance', 'list'])
        assert '"live"' not in result.stdout and ('ProtocolMismatch' in result.stdout or 'incompatible' in result.stdout), result.stdout + result.stderr
        (out / 'receipt.json').write_text(json.dumps({'status': 'passed', 'scope': 'real loopback TCP, generated SFM service fixture, not Minecraft', 'checks': ['identity ping and describe', 'Unicode action request and result', 'authentication rejection', 'protocol rejection'], 'runtime_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'sfm_sha256': hashlib.sha256(args.sfm.read_bytes()).hexdigest(), 'commands': commands}, indent=2) + '\n')
        print('PASS: 4 generated SFM Java / Rust wire checks; not a Minecraft run')
    finally:
        server.terminate()
        try:
            server.wait(timeout=5)
        except subprocess.TimeoutExpired:
            server.kill()
            server.wait(timeout=5)
        descriptor.unlink(missing_ok=True)
