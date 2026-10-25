#!/usr/bin/env python3
"""Read-only full-output audit against explicit per-release Git witnesses.

Uses existing local Git objects and the canonical CLI. No build, network access,
cache admission, generated-project writes, or binary-equivalence assertion.
"""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import subprocess
import tarfile


HISTORICAL = '25fec27456508a0bc2e70569e15a9bf2fc1d2d17'  # Last pre-demo 4.34 projection tree; resolved in receipt.
BEFORE = 'c969392e9e4b1eb77e70755778fad5dd43f01f06'
PREFIX = 'platform/minecraft/projections/'
SEARCH = 'src/test/java/ca/teamdman/sfml/test/SFMLTests.java'
MANAGED = [
    'src/gametest/java/ca/teamdman/sfm/gametest/puppet/action/ManagedSessionPuppetAction.java',
    'src/gametest/java/ca/teamdman/sfm/gametest/puppet/definition/ManagedSuperflatSessionGamePuppet.java',
]
DEMO = MANAGED + [
    'src/main/java/ca/teamdman/sfm/common/capability/Issue610FixtureEnergyProvider.java',
    'src/gametest/java/ca/teamdman/sfm/gametest/tests/regression/Issue610PortableControlsGameTest.java',
    'src/main/java/ca/teamdman/sfm/client/screen/explorer/SFMExplorerPanel.java',
    'src/main/java/ca/teamdman/sfm/client/action/SFMExplorerDiffAction.java',
]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def identity(files):
    """Hash canonical sorted path/hash records; includes set membership."""
    return sha(json.dumps(files, sort_keys=True, separators=(',', ':')).encode())


def git(root, *args):
    return subprocess.check_output(['git', *args], cwd=root)


def tree_hashes(root, revision, family):
    prefix = PREFIX + family + '/'
    child = subprocess.Popen(['git', 'archive', revision, prefix], cwd=root,
                             stdout=subprocess.PIPE)
    files = {}
    with tarfile.open(fileobj=child.stdout, mode='r|') as archive:
        for entry in archive:
            if entry.isdir():
                continue
            assert entry.isfile() and entry.name.startswith(prefix), entry.name
            assert entry.size <= 16 * 1024 * 1024, entry.name
            files[entry.name.removeprefix(PREFIX)] = sha(archive.extractfile(entry).read())
    assert child.wait(timeout=30) == 0
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cli', type=Path, required=True)
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = args.repo_root.resolve()
    args.output.mkdir(parents=True, exist_ok=False)
    receipt_path = args.output / 'receipt.json'
    receipt = {'status': 'running', 'source_head': git(root, 'rev-parse', 'HEAD').decode().strip(),
               'cli_sha256': sha(args.cli.read_bytes()), 'projections': {},
               'compiled': False, 'binary_equivalence_checked': False}
    receipt_path.write_text(json.dumps(receipt, indent=2) + '\n')
    try:
        historical = git(root, 'rev-parse', HISTORICAL + '^{commit}').decode().strip()
        receipt['historical_434_witness'] = historical
        receipt['preserved_435_witness'] = BEFORE
        receipt['435_exceptions'] = {'omit': MANAGED, 'restore_from_matching_434': SEARCH}
        catalog = json.loads((root / 'platform/minecraft/projections.json').read_text())
        keys = sorted(k for k in catalog if k.startswith(('sfm-4.34.0/', 'sfm-4.35.0/')))
        assert len(keys) == 20
        expected = tree_hashes(root, historical, 'sfm-4.34.0')
        expected.update(tree_hashes(root, BEFORE, 'sfm-4.35.0'))
        for key in keys:
            if key.startswith('sfm-4.35.0/'):
                for path in MANAGED:
                    del expected[key + '/' + path]
                expected[key + '/' + SEARCH] = expected[key.replace('4.35.0', '4.34.0') + '/' + SEARCH]

        def check(key):
            command = [str(args.cli), 'source', 'project', 'dry-run', '--repo-root', str(root),
                       '--projection', key]
            result = subprocess.run(command, capture_output=True, timeout=180)
            name = key.replace('/', '-')
            (args.output / (name + '.json')).write_bytes(result.stdout)
            (args.output / (name + '.stderr')).write_bytes(result.stderr)
            assert result.returncode == 0, (key, result.stderr.decode())
            report = json.loads(result.stdout)
            rendered = {p: a['output_sha256'].removeprefix('sha256:')
                        for p, a in report['artifacts'].items()}
            wanted = {p.removeprefix(key + '/'): h for p, h in expected.items()
                      if p.startswith(key + '/')}
            stored = {p.relative_to(root / PREFIX / key).as_posix(): sha(p.read_bytes())
                      for p in (root / PREFIX / key).rglob('*')
                      if p.is_file() and p.relative_to(root / PREFIX / key).parts[0] not in
                      ('build', '.gradle', 'run') and not p.name.startswith('.sfm')}
            # Compare complete authored output membership, not only changed Java.
            mismatches = [p for p in sorted(rendered.keys() | wanted.keys())
                          if rendered.get(p) != wanted.get(p)]
            disk_mismatches = [p for p in sorted(stored.keys() | wanted.keys())
                               if stored.get(p) != wanted.get(p)]
            assert not report['writes_performed']
            assert all(p in report['omitted_source_paths'] for p in DEMO), key
            record = {'selected_files': len(rendered),
                      'selected_source_files': sum(p.startswith('src/') for p in rendered),
                      'expected_tree_sha256': identity(wanted),
                      'rendered_tree_sha256': identity(rendered),
                      'stored_tree_sha256': identity(stored),
                      'rendered_mismatches': mismatches, 'stored_mismatches': disk_mismatches,
                      'demo_paths_omitted': True, 'writes_performed': False,
                      'report_sha256': sha(result.stdout)}
            print(key, 'files', len(rendered), 'rendered mismatches', len(mismatches),
                  'stored mismatches', len(disk_mismatches), flush=True)
            return key, record

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            receipt['projections'] = dict(pool.map(check, keys))
        source_passed = all(not any(p.startswith('src/') for p in
                                    x['rendered_mismatches'] + x['stored_mismatches'])
                            for x in receipt['projections'].values())
        project_passed = all(not x['rendered_mismatches'] and not x['stored_mismatches']
                            for x in receipt['projections'].values())
        receipt['source_isolation_status'] = 'passed' if source_passed else 'failed'
        receipt['full_project_status'] = 'passed' if project_passed else 'failed'
        assert source_passed, 'Source isolation differs from the explicit per-target witnesses'
        assert project_passed, 'Full project differences remain; inspect exact paths in receipt'
        receipt['status'] = 'passed'
    except BaseException as error:
        receipt['status'] = 'failed'
        receipt['error'] = repr(error)
        raise
    finally:
        receipt['authored_input_sha256'] = {str(p.relative_to(root)): sha(p.read_bytes())
            for p in [root / 'platform/minecraft/projections.json',
                      root / 'platform/minecraft/core-liquid-template/feature-definitions.json',
                      root / 'platform/minecraft/core-liquid-template/project-inputs.json',
                      root / 'platform/minecraft/core-liquid-template' / SEARCH,
                      root / 'platform/minecraft/core-liquid-template/src/main/java/ca/teamdman/sfm/client/screen/SFMScreenRenderUtils.java',
                      root / 'platform/minecraft/core-liquid-template/src/main/java/ca/teamdman/sfm/common/registry/registration/SFMGlobalBlockCapabilityProviders.java']}
        receipt_path.write_text(json.dumps(receipt, indent=2) + '\n')


if __name__ == '__main__':
    main()
