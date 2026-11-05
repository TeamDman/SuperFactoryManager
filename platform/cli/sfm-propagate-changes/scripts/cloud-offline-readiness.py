"""Exact-scope cloud gate: isolated offline execution is not fresh-task proof.

Preparation uses `draft`; `check` is read-only. `verify` must run in a disposable
candidate with enforced network isolation and no host cache/archive mounts.
"""
import argparse
import fnmatch
import json
import os
from pathlib import Path
import socket
import subprocess
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import cloud_environment as checks

SCHEMA = 'sfm:cloud-offline-scope@2'
STAGES = ('check', 'compile', 'jar')
UNTESTED = ('JUnit', 'GameTests', 'GUI', 'Gradle', 'fresh-Codex-task', 'G-unavailable')
RECIPE_CONFIGS = (
    ('platform/minecraft/build-configuration/released-native.json', 'sfm:released_native_build_configuration@1'),
    ('platform/minecraft/build-configuration/released-native-historical-2612.json', 'sfm:released_native_historical_consumer@1'),
)


def project_lock(repo, key):
    return repo / 'platform/minecraft/projections' / key / 'sfm-toolchain.lock.json'


def catalog_at(repo):
    path = repo / 'platform/minecraft/projections.json'
    return path, checks.load(path)


def source_identity(repo):
    return {name: checks.digest(repo / name) for name in (
        'platform/cli/sfm-propagate-changes/Cargo.lock', *(path for path, _ in RECIPE_CONFIGS))}


def recipe(repo, key):
    if not key.startswith(('sfm-4.34.0/mc-', 'sfm-4.35.0/mc-')):
        return None
    target = key.split('/mc-', 1)[1]
    emitted_sha = checks.digest(project_lock(repo, key))
    matches = []
    for path, schema in RECIPE_CONFIGS:
        document = checks.load(repo / path)
        checks.require(document['schema'] == schema, 'unknown production recipe authority schema')
        matches.extend(r for r in document['targets'] if r['target'] == target
                       and r['source_lock']['sha256'].lower() == emitted_sha)
    checks.require(len(matches) == 1, f'exact historical recipe authority missing or ambiguous: {key}')
    row = matches[0]
    source = checks.real_path(repo / row['source_lock']['path'])
    checks.require(source.resolve().is_relative_to(repo), 'historical authority escapes repository')
    checks.require(checks.digest(source) == emitted_sha, f'historical source lock changed: {key}')
    return row['recipe_id']


def draft(repo, cli, patterns, layout, build_receipt):
    checks.validate_layout(layout)
    catalog_path, catalog = catalog_at(repo)
    keys = sorted(k for k in catalog if any(fnmatch.fnmatchcase(k, p) for p in patterns))
    checks.require(keys, 'no catalog targets match the requested patterns')
    targets = []
    for key in keys:
        row = {'projection': key, 'lock_sha256': checks.digest(project_lock(repo, key)),
               'stages': list(STAGES), 'java_major': layout['target_java'][key]}
        exact = recipe(repo, key)
        if exact:
            row['released_recipe'] = exact
        targets.append(row)
    audit_inputs(repo, cli, layout)
    cache_identity = checks.digest_payload(input_snapshot(layout))
    return {'cache_inventory_sha256': cache_identity, 'schema': SCHEMA, 'state': 'draft', 'source_commit': checks.git(repo, 'rev-parse', 'HEAD'),
            'catalog_sha256': checks.digest(catalog_path), 'cli_sha256': checks.digest(cli),
            'source_inputs': source_identity(repo), 'build_receipt': str(build_receipt),
            'build_receipt_sha256': checks.digest(build_receipt), 'layout': layout,
            'registered_target_count': len(catalog), 'targets': targets,
            'excluded_targets': sorted(set(catalog) - set(keys)), 'not_attested': list(UNTESTED)}


def require_network_none():
    checks.require(sys.platform == 'linux', 'publication gate requires isolated Linux networking')
    interfaces = {name for _, name in socket.if_nameindex()}
    checks.require(interfaces == {'lo'}, f'expected only loopback interface, found {sorted(interfaces)}')
    for name in ('/proc/net/route', '/proc/net/ipv6_route'):
        path = Path(name)
        checks.require(path.exists(), f'cannot verify network isolation: {name} absent')
        lines = path.read_text(encoding='ascii').splitlines()
        if name.endswith('ipv6_route'):
            # Retain the strict existing default-route refusal, including kernel sentinels.
            checks.require(not any(line.split() and line.split()[0] == '0' * 32
                                   and line.split()[1] == '00' for line in lines), 'IPv6 default route is present')
        else:
            checks.require(not any(line.split()[1:2] == ['00000000'] for line in lines[1:]),
                           'IPv4 default route is present')


def validate_scope(repo, cli, scope):
    checks.require(scope.get('schema') == SCHEMA and scope.get('state') == 'declared',
                   'scope must be reviewed and marked declared at the current schema')
    checks.require(not checks.git(repo, 'status', '--porcelain', '--untracked-files=no'), 'tracked checkout content is dirty')
    checks.require(scope['source_commit'] == checks.git(repo, 'rev-parse', 'HEAD'), 'source commit changed')
    path, catalog = catalog_at(repo)
    checks.require(scope['catalog_sha256'] == checks.digest(path), 'projection catalog changed')
    checks.require(scope['cli_sha256'] == checks.digest(cli), 'CLI binary changed')
    checks.require(scope['source_inputs'] == source_identity(repo), 'source authority changed')
    checks.require(checks.digest(Path(scope['build_receipt'])) == scope['build_receipt_sha256'], 'build receipt changed')
    build = checks.load(scope['build_receipt'])
    checks.require(build['schema'] == 'sfm:cloud-release-build@1' and build['source_commit'] == scope['source_commit']
                   and build['cli_sha256'] == scope['cli_sha256'] and build['profile'] == 'release'
                   and build['layout_sha256'] == checks.digest_payload(scope['layout'])
                   and build['cargo_lock_sha256'] == scope['source_inputs']['platform/cli/sfm-propagate-changes/Cargo.lock']
                   and f'rev {scope["source_commit"]}' in build['version'],
                   'release source/binary provenance mismatch')
    targets = scope['targets']
    checks.require(isinstance(targets, list) and targets, 'declared scope must contain at least one target')
    seen = set()
    checks.validate_layout(scope['layout'])
    sdks = scope['layout']['sdks']
    majors = [s['major'] for s in sdks]
    checks.require(len(majors) == len(set(majors)), 'duplicate SDK major')
    checks.require(scope['not_attested'] == list(UNTESTED), 'undeclared stage claims')
    for row in targets:
        key = row['projection']
        checks.require(key in catalog and key not in seen, f'unknown or duplicate catalog target: {key}')
        seen.add(key)
        pins = checks.load(project_lock(repo, key)).get('jdk_pins')
        selected = [s for s in sdks if s['major'] == row['java_major']]
        checks.require(len(selected) == 1, f'explicit SDK selection missing for {key}')
        if pins is not None:
            expected = [a['sha512'].lower() for pin in pins if pin['major'] == row['java_major']
                        for a in pin['artifacts'] if a['platform'] == 'linux-x64']
            checks.require(expected == [selected[0]['sha512'].lower()], f'SDK differs from target pin: {key}')
        checks.require(row['lock_sha256'] == checks.digest(project_lock(repo, key)), f'lock changed for {key}')
        checks.require(row['stages'] == list(STAGES), f'{key} must freshly check, compile and package')
        checks.require(row.get('released_recipe') == recipe(repo, key), f'exact historical recipe changed for {key}')
        checks.require(row['java_major'] in majors and scope['layout']['target_java'][key] == row['java_major'],
                       f'explicit SDK selection missing for {key}')
    checks.require(scope['registered_target_count'] == len(catalog)
                   and scope['excluded_targets'] == sorted(set(catalog) - seen), 'full catalog coverage accounting changed')
    return targets


def audit_inputs(repo, cli, layout):
    checks.native_binary(repo, cli, layout['denied_certificates'])
    environment = checks.tls_environment(layout)
    for sdk in layout['sdks']:
        home = checks.audit_sdk(repo, sdk)
        checks.audit_java_trust(repo, sdk)
        native = checks.real_path(layout['native_cache_root'], True)
        checks.require(not home.is_relative_to(native) and not native.is_relative_to(home),
                       'explicit SDK overlaps native build root')
    return environment


def input_snapshot(layout):
    result = checks.snapshots(layout)
    for sdk in layout['sdks']:
        result['sdk:' + sdk['home']] = checks.snapshot_root(sdk['home'], [sdk['home']])
        for name in ('archive', 'trust_store', 'trust_receipt'):
            result[name + ':' + sdk[name]] = checks.digest(Path(sdk[name]))
    result['tls_bundle'] = checks.digest(Path(layout['tls_ca_file']))
    result['tls'] = checks.snapshot_root(layout['tls_ca_dir']) if not any(
        p.is_symlink() for p in Path(layout['tls_ca_dir']).iterdir()) else {
            str(p): [os.readlink(p) if p.is_symlink() else None, checks.digest(p.resolve(strict=True))]
            for p in Path(layout['tls_ca_dir']).iterdir() if p.is_file()}
    return result


def stage_command(repo, cli, layout, row, stage):
    key = row['projection']
    command = [str(cli), '--output-format', 'json', 'source', 'project', stage, '--repo-root', str(repo), '--projection', key]
    if stage != 'check':
        sdk = next(s for s in layout['sdks'] if s['major'] == row['java_major'])
        command += ['--cache-root', layout['native_cache_root'], '--java-home', sdk['home']]
        if row.get('released_recipe'):
            command += ['--released-recipe', row['released_recipe']]
        else:
            command += ['--dependency-profile', 'rust-toolchain']
        # Native default-output selection creates a fresh libs directory safely.
        # Existing outputs are never replaced by this gate.
        if stage == 'jar' and (repo / 'platform/minecraft/projections' / key / 'build/libs').exists():
            raise ValueError(f'candidate contains prior JAR output directory: {key}')
    return command


def stage_result(repo, row, stage, stdout):
    report = json.loads(stdout.read_text())
    key = row['projection']
    if stage == 'check':
        checks.require(report.get('schema') == 'sfm:core_project_generation@1'
                       and report.get('projection_key') == key and report.get('operation') == 'check'
                       and report.get('writes_performed') is False, 'wrong or mutating source check receipt')
    else:
        named = bool(row.get('released_recipe'))
        expected = ('sfm:named_native_compile@1' if stage == 'compile' else 'sfm:named_native_jar@1') if named else 'sfm:development_native_build@1'
        preparation = report.get('preparation', {})
        owner = preparation.get('ownership', {}) if named else preparation
        checks.require(report.get('schema') == expected and owner.get('projection_key') == key,
                       'wrong native stage schema or target')
        checks.require(report.get('immutable_source_lock') is True and report.get('compilation') in
                       ('completed', 'completed_or_valid_compile_cache'), 'native compilation did not complete')
        checks.require(report.get('tool_jvm_major') == row['java_major'], 'native SDK major differs from scope')
        if stage == 'jar':
            checks.require(report.get('jar_packaging') == ('completed_this_invocation_no_unreceipted_jar_cache_reuse' if named else 'completed'),
                           'JAR was not packaged this invocation')
            output = checks.real_path(report['output_jar'])
            checks.require(output.parent == repo / 'platform/minecraft/projections' / key / 'build/libs',
                           'JAR output escapes selected projection')
            return {'output_jar': str(output), 'output_sha256': checks.digest(output)}
    return {}


def verify(repo, cli, scope, evidence):
    require_network_none()
    targets = validate_scope(repo, cli, scope)
    checks.require(not evidence.exists(), 'gate evidence directory must be new')
    receipts = repo / 'sfm-toolchain-data/check-everything-native/receipts'
    checks.require(not receipts.exists() or not any(receipts.rglob('*.pass.json')), 'candidate image contains matrix pass receipts')
    layout = scope['layout']
    checks.validate_layout(layout)
    checks.require_drained(repo, layout)
    checks.real_path(evidence.parent, True)
    environment = audit_inputs(repo, cli, layout)
    before = input_snapshot(layout)
    checks.require(checks.digest_payload(before) == scope['cache_inventory_sha256'], 'declared cache inputs changed')
    evidence.mkdir(parents=False)
    results = []
    try:
        for row in targets:
            for stage in STAGES:
                command = stage_command(repo, cli, layout, row, stage)
                prefix = evidence / (row['projection'].replace('/', '__') + '-' + stage)
                # Retain evidence on failure, interruption and timeout. No pass reuse.
                with Path(str(prefix) + '.stdout').open('x') as stdout, Path(str(prefix) + '.stderr').open('x') as stderr:
                    done = subprocess.run(command, cwd=repo, env=environment, stdout=stdout, stderr=stderr,
                                          timeout=scope['layout'].get('stage_timeout_seconds', 1800))
                record = {'projection': row['projection'], 'stage': stage, 'command': command,
                          'exit': done.returncode, 'status': 'passed' if done.returncode == 0 else 'failed'}
                Path(str(prefix) + '.json').write_text(json.dumps(record, indent=2) + '\n')
                results.append(record)
                checks.require(done.returncode == 0, f'{row["projection"]}: {stage} failed; see {prefix}')
                try:
                    record.update(stage_result(repo, row, stage, Path(str(prefix) + '.stdout')))
                except (ValueError, OSError, KeyError):
                    record['status'] = 'failed'
                    Path(str(prefix) + '.json').write_text(json.dumps(record, indent=2) + '\n')
                    raise
                Path(str(prefix) + '.json').write_text(json.dumps(record, indent=2) + '\n')
        validate_scope(repo, cli, scope)
        audit_inputs(repo, cli, layout)
        checks.same_snapshot(before, input_snapshot(layout))
    except BaseException as error:
        (evidence / 'result.json').write_text(json.dumps({'status': 'failed', 'reason': str(error), 'rows': results}, indent=2) + '\n')
        raise
    result = {'schema': 'sfm:cloud-offline-readiness@2', 'status': 'passed', 'source_commit': scope['source_commit'],
              'cli_sha256': scope['cli_sha256'], 'scope_sha256': checks.digest_payload(scope),
              'proof': 'isolated-offline-native-stages-only', 'not_attested': list(UNTESTED),
              'registered_target_count': scope['registered_target_count'], 'excluded_targets': scope['excluded_targets'], 'rows': results}
    (evidence / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('draft', 'check', 'verify'))
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--cli', type=Path, required=True)
    parser.add_argument('--scope', type=Path, required=True)
    parser.add_argument('--projection', action='append', default=[])
    parser.add_argument('--layout', type=Path)
    parser.add_argument('--build-receipt', type=Path)
    parser.add_argument('--evidence', type=Path)
    args = parser.parse_args()
    try:
        repo, cli = checks.real_path(args.repo_root.absolute(), True), checks.real_path(args.cli.absolute())
        if args.mode == 'draft':
            checks.require(args.projection and args.layout and args.build_receipt, 'draft requires projections, layout and build receipt')
            content = draft(repo, cli, args.projection, checks.load(args.layout.absolute()), args.build_receipt.absolute())
            with args.scope.open('x') as output:
                json.dump(content, output, indent=2)
            print('Draft only; review every target, stage, input and exclusion before declaring')
        else:
            checks.require(not args.projection and not args.layout and not args.build_receipt, 'use only declared scope inputs')
            scope = checks.load(args.scope.absolute())
            if args.mode == 'check':
                print(json.dumps({'status': 'current', 'targets': len(validate_scope(repo, cli, scope)),
                                  'proof': 'identity-check-only', 'not_attested': list(UNTESTED)}))
            else:
                checks.require(args.evidence, 'verify requires a new --evidence directory')
                print(json.dumps(verify(repo, cli, scope, args.evidence.absolute()), indent=2))
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print(f'cloud offline readiness failed: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
