#!/usr/bin/env python3
"""Compare affected Dev/extension renders to the pre-isolation checkpoint."""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import subprocess


BEFORE = 'c969392e9e4b1eb77e70755778fad5dd43f01f06'
CORE = 'platform/minecraft/core-liquid-template/'
FILES = [
    'src/test/java/ca/teamdman/sfml/test/SFMLTests.java',
    'src/gametest/java/ca/teamdman/sfm/gametest/puppet/action/ManagedSessionPuppetAction.java',
    'src/gametest/java/ca/teamdman/sfm/gametest/puppet/definition/ManagedSuperflatSessionGamePuppet.java',
    'src/main/java/ca/teamdman/sfm/client/screen/SFMScreenRenderUtils.java',
    'src/main/java/ca/teamdman/sfm/common/registry/registration/SFMGlobalBlockCapabilityProviders.java',
    'src/main/java/ca/teamdman/sfml/ast/OutputStatement.java',
]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def removed_one_blank_line(old, new):
    lines = old.splitlines(keepends=True)
    return any(not line.strip() and b''.join(lines[:i] + lines[i + 1:]) == new
               for i, line in enumerate(lines))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cli', type=Path, required=True)
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    baseline = args.output / 'baseline-render-inputs'
    for path in ['platform/minecraft/projections.json', CORE + 'feature-definitions.json'] + [CORE + p for p in FILES]:
        destination = baseline / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(subprocess.check_output(['git', 'show', BEFORE + ':' + path], cwd=args.repo_root))
    catalog = json.loads((args.repo_root / 'platform/minecraft/projections.json').read_text())
    keys = sorted(k for k, value in catalog.items() if value['environment'] == 'dev')
    assert len(keys) == 34
    receipt = {'status': 'running', 'baseline': BEFORE,
               'cli_sha256': sha(args.cli.read_bytes()), 'compiled': False,
               'scope': 'canonical Java render bytes; actual file membership is covered by the Rust catalog test',
               'renders': {}}
    target = args.output / 'receipt.json'
    target.write_text(json.dumps(receipt, indent=2) + '\n')

    def render(root, key, path):
        data = subprocess.check_output([str(args.cli), 'source', 'render', '--repo-root', str(root),
                                        '--projection', key, '--file', path], timeout=30)
        return json.loads(data)['rendered_content'].encode()

    def check(pair):
        key, path = pair
        old = render(baseline, key, path)
        new = render(args.repo_root, key, path)
        features = set(catalog[key]['features'])
        exact = old == new
        inactive = ((path.endswith('/SFMScreenRenderUtils.java') and 'inspection_workspace' not in features)
                    or (path.endswith('/SFMGlobalBlockCapabilityProviders.java') and 'issue_610_portable_fixture' not in features))
        if inactive:
            assert exact or removed_one_blank_line(old, new), (key, path)
        else:
            assert exact, (key, path)
        if path.endswith('/SFMLTests.java'):
            assert b'while (cwd != null) {' in new
        return key + '/' + path, {'before_sha256': sha(old), 'after_sha256': sha(new),
                                  'exact': exact, 'only_inactive_blank_line_removed': not exact}

    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            receipt['renders'] = dict(pool.map(check, ((k, p) for k in keys for p in FILES)))
        receipt['status'] = 'passed'
        receipt['render_pairs'] = len(receipt['renders'])
        print('Passed', len(keys), 'Dev/extension contexts and', receipt['render_pairs'], 'render pairs.')
    except BaseException as error:
        receipt['status'] = 'failed'
        receipt['error'] = repr(error)
        raise
    finally:
        target.write_text(json.dumps(receipt, indent=2) + '\n')


if __name__ == '__main__':
    main()
