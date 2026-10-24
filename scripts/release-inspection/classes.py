#!/usr/bin/env python3
"""Capture one JAR pair using the existing release-report javap classifier."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--old', type=Path, required=True)
p.add_argument('--new', type=Path, required=True)
p.add_argument('--javap', type=Path, required=True)
p.add_argument('--output', type=Path, required=True, help='Existing caller-owned empty directory')
a = p.parse_args()
root = Path(__file__).resolve().parents[2]
manifest = a.output / 'input.json'
with manifest.open('x', encoding='utf-8') as f:
    json.dump({'jars': [dict(id='pair', baseline_path=str(a.old.resolve()), candidate_path=str(a.new.resolve()))]}, f)
producer = root / 'platform/cli/sfm-propagate-changes/src/release_report/scripts/prepare_class_evidence.py'
subprocess.run([sys.executable, str(producer), '--manifest', str(manifest), '--output', str(a.output/'classes'),
                '--output-manifest', str(a.output/'manifest.json'), '--asset-prefix', 'classes', '--javap', str(a.javap.resolve())], check=True)
