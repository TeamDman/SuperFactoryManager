#!/usr/bin/env python3
"""Build and guard a new release installation, or initialize an exact declared scope.

Existing tools, verified SDKs and inherited filtered trust are prerequisites.
Only the explicit junit-cache mode acquires the existing pinned JUnit JARs.
Other modes never download dependencies. No mode rewrites an SDK, changes
system trust, replaces an older executable, or publishes an image.
"""
import argparse
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import cloud_environment as checks


def clean_head(repo):
    checks.require(not checks.git(repo, 'status', '--porcelain', '--untracked-files=no'), 'tracked checkout content is dirty')
    return checks.git(repo, 'rev-parse', 'HEAD')


def release_build(repo, layout, output):
    head = clean_head(repo)
    checks.validate_layout(layout)
    checks.require_drained(repo, layout)
    checks.real_path(output.parent, True)
    checks.require(not output.exists(), 'installation output must be new; preserve previous installations')
    for name in ('git', 'cargo', 'rustc', 'python3', 'openssl', 'cc'):
        checks.require(shutil.which(name), f'missing prerequisite {name}; use supported environment provisioning')
    checks.native_policy(repo)
    environment = checks.tls_environment(layout)
    for sdk in layout['sdks']:
        checks.audit_sdk(repo, sdk)
        checks.audit_java_trust(repo, sdk)
    before = checks.snapshots(layout, allow_missing_native=True)
    environment['SFM_PROPAGATE_CHANGES_INSTALL_GIT_REVISION'] = head
    environment['CARGO_BUILD_JOBS'] = '1'
    environment['CARGO_PROFILE_RELEASE_DEBUG'] = '0'
    # Deliberately retain configured CARGO_HOME/RUSTUP_HOME/target dir and proxy.
    command = ['cargo', 'build', '--offline', '--locked', '--release',
               '--features', 'reqwest/rustls-tls-native-roots', '--bin', 'sfm-propagate-changes']
    output.mkdir(parents=False)
    target = Path(environment.get('CARGO_TARGET_DIR', repo / 'platform/cli/sfm-propagate-changes/target'))
    previous = target / 'release/sfm-propagate-changes'
    if previous.exists():
        old = checks.real_path(previous)
        shutil.copyfile(old, output / 'previous-target-release')
        checks.require(checks.digest(old) == checks.digest(output / 'previous-target-release'), 'release backup differs')
    with (output / 'build.stdout').open('x') as stdout, (output / 'build.stderr').open('x') as stderr:
        subprocess.run(command, cwd=repo / 'platform/cli/sfm-propagate-changes', env=environment,
                       stdout=stdout, stderr=stderr, check=True, timeout=3600)
    checks.require(clean_head(repo) == head, 'source changed during release build')
    target = Path(environment.get('CARGO_TARGET_DIR', repo / 'platform/cli/sfm-propagate-changes/target'))
    binary = checks.real_path(target / 'release/sfm-propagate-changes')
    checks.native_binary(repo, binary, layout['denied_certificates'])
    version = subprocess.check_output([str(binary), '--version'], env=environment, text=True).strip()
    checks.require(f'rev {head}' in version, 'release does not report exact full source revision')
    for sdk in layout['sdks']:
        checks.audit_sdk(repo, sdk)
    checks.same_snapshot(before, checks.snapshots(layout, allow_missing_native=True))
    installed = output / 'sfm-propagate-changes.native'
    with binary.open('rb') as source, installed.open('xb') as destination:
        shutil.copyfileobj(source, destination)
    installed.chmod(0o500)
    checks.require(checks.digest(installed) == checks.digest(binary), 'installed binary differs')
    record = {'schema': 'sfm:cloud-release-build@1', 'source_commit': head,
              'tool_versions': {name: subprocess.check_output([name, '--version'], env=environment, text=True).strip()
                                for name in ('rustc', 'cargo', 'git')},
              'profile': 'release', 'cli': str(installed), 'cli_sha256': checks.digest(installed),
              'version': version, 'command': command, 'layout_sha256': checks.digest_payload(layout),
              'cargo_lock_sha256': checks.digest(repo / 'platform/cli/sfm-propagate-changes/Cargo.lock'),
              'qualification': 'release-build-and-input-audit-only; no offline/fresh-task/publication proof'}
    (output / 'build-receipt.json').write_text(json.dumps(record, indent=2) + '\n')
    return record


def start(repo, scope_path, cli):
    gate = checks.helper(repo, 'cloud-offline-readiness.py')
    scope = checks.load(scope_path)
    gate.validate_scope(repo, cli, scope)
    layout = scope['layout']
    checks.native_binary(repo, cli, layout['denied_certificates'])
    environment = checks.tls_environment(layout)
    for sdk in layout['sdks']:
        checks.require(checks.digest(Path(sdk['trust_store'])) == sdk['trust_sha256'], 'Java trust store changed')
    return scope, environment


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('build', 'start', 'exec', 'junit-cache'))
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--layout', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--scope', type=Path)
    parser.add_argument('--cli', type=Path)
    parser.add_argument('--build-receipt', type=Path)
    argv = sys.argv[1:]
    split = argv.index('--') if '--' in argv else len(argv)
    command_args = argv[split + 1:] if split < len(argv) else []
    args = parser.parse_args(argv[:split])
    args.command = command_args
    try:
        repo = checks.real_path(args.repo_root.absolute(), True)
        if args.mode == 'build':
            checks.require(args.layout and args.output and not args.command, 'build requires --layout and new --output')
            print(json.dumps(release_build(repo, checks.load(args.layout.absolute()), args.output.absolute()), indent=2))
        elif args.mode == 'junit-cache':
            checks.require(args.layout and args.cli and args.build_receipt and args.output and not args.command,
                           'junit-cache requires --layout, --cli, --build-receipt and new --output')
            preparation = checks.helper(repo, 'cloud_junit_cache.py')
            print(json.dumps(preparation.prepare(repo, checks.load(args.layout.absolute()),
                  checks.real_path(args.cli.absolute()), args.build_receipt.absolute(), args.output.absolute()), indent=2))
        else:
            checks.require(args.scope and args.cli, 'start/exec require --scope and guarded --cli')
            cli = checks.real_path(args.cli.absolute())
            scope, environment = start(repo, args.scope.absolute(), cli)
            if args.mode == 'exec':
                command = args.command[1:] if args.command[:1] == ['--'] else args.command
                checks.require(command, 'exec requires CLI arguments after --')
                return subprocess.run([str(cli), *command], cwd=repo, env=environment).returncode
            checks.require(not args.command, 'start takes no command')
            # Print sourceable exports only; never dump inherited credentials/proxy values.
            keys = {'SFM_PROPAGATE_CHANGES_CACHE', 'NODE_OPTIONS'}
            keys.update(k for k in environment if k.startswith('SFM_JAVA_TRUST_'))
            keys.update(('SSL_CERT_FILE', 'SSL_CERT_DIR', 'REQUESTS_CA_BUNDLE', 'CURL_CA_BUNDLE',
                         'GIT_SSL_CAINFO', 'GIT_SSL_CAPATH', 'CARGO_HTTP_CAINFO', 'CARGO_PROXY_CAINFO',
                         'NPM_CONFIG_CAFILE', 'PIP_CERT', 'NODE_EXTRA_CA_CERTS'))
            for key in sorted(keys):
                print(f'export {key}={shlex.quote(environment[key])}')
            print(f'export SFM_CLOUD_CLI={shlex.quote(str(cli))}')
            print(f'export SFM_CLOUD_SCOPE={shlex.quote(str(args.scope.absolute()))}')
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print(f'cloud preparation refused: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
