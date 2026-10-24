#!/usr/bin/env python3
"""Prepare the two in-game release comparison roots. Never build, install, publish or render HTML."""
import argparse
import json
from pathlib import Path
import subprocess
import sys


def prepare(args):
    repo = Path(__file__).resolve().parents[2]
    catalog = json.loads((repo / 'platform/minecraft/projections.json').read_text(encoding='utf-8'))
    old_projection, new_projection = f'sfm-4.34.0/mc-{args.minecraft}', f'sfm-4.35.0/mc-{args.minecraft}'
    for key in (old_projection, new_projection, 'sfm-dev/mc-1.19.2'):
        if key not in catalog:
            raise ValueError(f'Projection not declared in catalog: {key}')
    output = args.output.resolve()
    output.mkdir(parents=False, exist_ok=False)
    evidence = str(args.class_evidence.resolve()) if args.class_evidence else None
    if args.javap:
        if not args.old_jar or not args.new_jar:
            raise ValueError('--javap requires both explicit JAR paths')
        manifest = output / 'jars.json'
        manifest.write_text(json.dumps({'jars': [{'id': args.minecraft, 'baseline_path': str(args.old_jar.resolve()),
                                                'candidate_path': str(args.new_jar.resolve())}]}))
        producer = repo / 'platform/cli/sfm-propagate-changes/src/release_report/scripts/prepare_class_evidence.py'
        subprocess.run([sys.executable, str(producer), '--manifest', str(manifest), '--output', str(output / 'classes'),
                        '--output-manifest', str(output / 'class-manifest.json'), '--asset-prefix', 'classes',
                        '--javap', str(args.javap.resolve())], check=True)
        evidence = str(output / 'classes' / f'{args.minecraft}.json')
    policy = dict(schema_version=1, allow_generated_banner=False, normalize_crlf=False,
                  allow_final_newline_difference=False, allow_settings_project_identity_override=False)
    request = {'schema': 'sfm:explorer-inspection-request@1', 'comparisons': [
        {'Jar': dict(label=f'Release JAR · 4.34.0 → 4.35.0 · MC {args.minecraft}',
                     old=str(args.old_jar.resolve()) if args.old_jar else '',
                     new=str(args.new_jar.resolve()) if args.new_jar else '', class_evidence=evidence)},
        {'Source': dict(label=f'Release projection · 4.34.0 → 4.35.0 · MC {args.minecraft}',
                        request={'schema': 'sfm:source-comparison-request@1', 'policy': policy,
                                 'left': {'Projection': dict(repository=str(repo), projection=old_projection)},
                                 'right': {'Projection': dict(repository=str(repo), projection=new_projection)}})}]}
    request_path = output / 'request.json'
    request_path.write_text(json.dumps(request, indent=2), encoding='utf-8')
    workspace = output / 'release.sfm-inspection.json'
    subprocess.run([str(args.cli), '--output-format', 'json', 'source', 'inspect-prepare', '--request',
                    str(request_path), '--output', str(workspace)], check=True)
    result = json.loads(workspace.read_text(encoding='utf-8'))
    print(json.dumps({'workspace': str(workspace), 'roots': [dict(label=r['label'], error=r['error'], pairs=len(r['pairs'])) for r in result['roots']],
                      'launch_argv': [str(args.cli), 'run', 'client', '--repo-root', str(repo), '--projection',
                                      'sfm-dev/mc-1.19.2', '--inspection-workspace', str(workspace)]}, indent=2))
    return 2 if any(r['error'] for r in result['roots']) else 0


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--cli', type=Path, required=True, help='Freshly built native SFM CLI')
    p.add_argument('--output', type=Path, required=True, help='Fresh directory beneath an existing parent')
    p.add_argument('--minecraft', default='1.19.2')
    p.add_argument('--old-jar', type=Path)
    p.add_argument('--new-jar', type=Path)
    evidence = p.add_mutually_exclusive_group()
    evidence.add_argument('--class-evidence', type=Path, help='Existing hash-bound report producer evidence')
    evidence.add_argument('--javap', type=Path, help='Installed javap: run the existing report class-evidence producer')
    raise SystemExit(prepare(p.parse_args()))
