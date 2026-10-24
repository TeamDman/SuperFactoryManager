#!/usr/bin/env python3
"""Headless end-to-end evidence checks with real javac/JAR fixtures; no game UI claim."""
import argparse
import hashlib
import json
import subprocess
import sys
import zipfile
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--cli', type=Path, required=True)
p.add_argument('--java-home', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
repo = Path(__file__).resolve().parents[2]
out = a.output.resolve(); out.mkdir(exist_ok=False)
java = a.java_home.resolve()
for side in ('old', 'new'):
    root = out / side; root.mkdir()
    (root / 'A.java').write_text('class A { int n(){ return ' + ('1' if side == 'old' else '2') + '; } }\n')
    (root / 'U.java').write_text('class U { int n(){ return ' + ('3' if side == 'old' else '4') + '; } }\n')
    (root / 'Debug.java').write_text(('' if side == 'old' else '\n\n') + 'class Debug { int n(){ return 1; } }\n')
    (root / 'Same.java').write_text('class Same {}\n')
    (root / 'Signature.java').write_text(('public ' if side == 'old' else '') + 'class Signature { int n(){ return 1; } }\n')
    membership = 'Removed' if side == 'old' else 'Added'
    (root / f'{membership}.java').write_text(f'class {membership} {{}}\n')
    (root / 'mixed.txt').write_bytes(b'old text\n' if side == 'old' else b'\x00\xff')
    subprocess.run([str(java / 'bin/javac'), '-g', *map(str, root.glob('*.java'))], check=True)
    with zipfile.ZipFile(out / f'{side}.jar', 'w') as jar:
        for path in root.glob('*.class'): jar.write(path, path.name)
        jar.writestr('resource.txt', 'old\n' if side == 'old' else 'new\n')
        jar.writestr('removed.txt' if side == 'old' else 'added.txt', side)
        jar.writestr('binary.dat', b'\x00\xff' if side == 'old' else b'\x00\xfe')
        jar.writestr('mixed.dat', b'old text\n' if side == 'old' else b'\x00\xff')
manifest = out / 'jars.json'
manifest.write_text(json.dumps({'jars': [{'id': 'fixture', 'baseline_path': str(out / 'old.jar'), 'candidate_path': str(out / 'new.jar')}]}))
subprocess.run([sys.executable, str(repo / 'platform/cli/sfm-propagate-changes/src/release_report/scripts/prepare_class_evidence.py'),
                '--manifest', str(manifest), '--output', str(out / 'class-evidence'), '--output-manifest', str(out / 'class-manifest.json'),
                '--asset-prefix', 'class-evidence', '--javap', str(java / 'bin/javap')], check=True)
evidence_path = out / 'class-evidence/fixture.json'
evidence = json.loads(evidence_path.read_text())
evidence['classes'] = [row for row in evidence['classes'] if row['path'] != 'U.class']
evidence_path.write_text(json.dumps(evidence))  # deliberately incomplete evidence tests the unknown partition
policy = dict(schema_version=1, allow_generated_banner=False, normalize_crlf=False, allow_final_newline_difference=False, allow_settings_project_identity_override=False)
request = {'schema': 'sfm:explorer-inspection-request@1', 'comparisons': [
    {'Jar': dict(label='Fixture JAR', old=str(out/'old.jar'), new=str(out/'new.jar'), class_evidence=str(evidence_path))},
    {'Source': dict(label='Fixture working trees', request={'schema': 'sfm:source-comparison-request@1',
        'left': {'Directory': {'root': str(out/'old')}}, 'right': {'Directory': {'root': str(out/'new')}}, 'policy': policy})}]}
def run(request, name):
    path = out / f'{name}.request.json'; path.write_text(json.dumps(request))
    result = out / f'{name}.sfm-inspection.json'
    command = [str(a.cli.resolve()), '--output-format', 'json', 'source', 'inspect-prepare', '--request', str(path), '--output', str(result)]
    subprocess.run(command, check=True, stdout=subprocess.DEVNULL)
    return result, command, json.loads(result.read_text())
result, command, workspace = run(request, 'fixture')
assert all(root['error'] is None for root in workspace['roots']), workspace
pairs = {pair['path']: pair for pair in workspace['roots'][0]['pairs']}
expected = {'A.class': 'instruction-view-changed', 'Debug.class': 'printed-debug-only', 'Same.class': 'exact',
            'Signature.class': 'class-view-changed',
            'Added.class': 'added', 'Removed.class': 'removed',
            'U.class': 'changed', 'added.txt': 'added', 'removed.txt': 'removed', 'binary.dat': 'changed'}
for path, status in expected.items(): assert pairs[path]['classification'] == status, (path, pairs[path])
assert not any(v['filename'] == 'view.diff' for v in pairs['binary.dat']['representations'])
assert not any(v['filename'] == 'view.diff' for v in pairs['mixed.dat']['representations'])
source = {pair['path']: pair for pair in workspace['roots'][1]['pairs']}
assert not any(v['filename'] == 'view.diff' for v in source['mixed.txt']['representations'])
assert source['Debug.java']['classification'] == 'source-whitespace'
assert any(v['label'] == 'Java structured diff' and v['surface_json'] for v in source['A.java']['representations'])
old_hash = hashlib.sha256(result.read_bytes()).hexdigest()
assert subprocess.run(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0
assert hashlib.sha256(result.read_bytes()).hexdigest() == old_hash
request['comparisons'][0]['Jar']['old'] = str(out/'missing.jar')
missing, _, missing_workspace = run(request, 'missing')
assert missing_workspace['roots'][0]['error'] and not missing_workspace['roots'][0]['pairs']
assert missing_workspace['roots'][1]['error'] is None
request['comparisons'][0]['Jar']['old'] = str(out/'old.jar')
evidence['baseline_sha256'] = 'sha256:' + '0'*64; evidence_path.write_text(json.dumps(evidence))
_, _, mismatch = run(request, 'mismatch')
assert 'different JAR' in mismatch['roots'][0]['error']
print(json.dumps({'result': 'passed', 'checks': ['instruction/debug/exact/unknown partitions', 'added/removed/binary',
    'working-tree whitespace and structured source maps', 'existing output preserved', 'missing input independent roots',
    'mismatched JAR evidence rejected'], 'fixture': str(result), 'missing_fixture': str(missing)}, indent=2))
