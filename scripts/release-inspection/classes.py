#!/usr/bin/env python3
"""Capture one JAR pair using the existing release-report javap classifier."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import hashlib
import stat
import zipfile


def admit(path):
    """Validate the complete selected archive before javap sees its class path."""
    path = path.absolute()
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_size > 128 * 1024**2:
        raise ValueError('JAR must be a regular file within 128 MiB')
    names, total = set(), 0
    with path.open('rb') as stream:
        hasher = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024**2), b''): hasher.update(chunk)
        digest = hasher.hexdigest()
        stream.seek(0)
        with zipfile.ZipFile(stream) as archive:
            if len(archive.infolist()) > 20_000:
                raise ValueError('JAR entry budget exceeded')
            for entry in archive.infolist():
                name = entry.filename
                parts = name.rstrip('/').split('/')
                if (name in names or len(name) > 4096 or any(p in ('', '.', '..') for p in parts)
                        or any(c in name for c in ('\\', ':', '\0')) or entry.flag_bits & 1
                        or stat.S_ISLNK(entry.external_attr >> 16)):
                    raise ValueError('Unsafe, duplicate, linked or encrypted JAR member: ' + name)
                names.add(name)
                total += entry.file_size
                if entry.file_size > 4 * 1024**2 or total > 32 * 1024**2:
                    raise ValueError('JAR member/expanded budget exceeded')
                if name.endswith('.class') and any(p.startswith('-') or any(c.isspace() for c in p) for p in parts):
                    raise ValueError('Unsafe javap class name: ' + name)
                with archive.open(entry) as member:
                    raw = member.read(4 * 1024**2 + 1)
                if len(raw) != entry.file_size:
                    raise ValueError('JAR member size mismatch')
    after = path.lstat()
    if any(getattr(after, field) != getattr(info, field) for field in ('st_dev', 'st_ino', 'st_mode', 'st_size', 'st_mtime_ns', 'st_ctime_ns')):
        raise ValueError('JAR changed during admission')
    return digest

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--old', type=Path, required=True)
p.add_argument('--new', type=Path, required=True)
p.add_argument('--javap', type=Path, required=True)
p.add_argument('--output', type=Path, required=True, help='Existing caller-owned empty directory')
a = p.parse_args()
identities = (admit(a.old), admit(a.new))
root = Path(__file__).resolve().parents[2]
manifest = a.output / 'input.json'
with manifest.open('x', encoding='utf-8') as f:
    json.dump({'jars': [dict(id='pair', baseline_path=str(a.old.resolve()), candidate_path=str(a.new.resolve()))]}, f)
producer = root / 'platform/cli/sfm-propagate-changes/src/release_report/scripts/prepare_class_evidence.py'
subprocess.run([sys.executable, str(producer), '--manifest', str(manifest), '--output', str(a.output/'classes'),
                '--output-manifest', str(a.output/'manifest.json'), '--asset-prefix', 'classes', '--javap', str(a.javap.resolve())], check=True)
if (admit(a.old), admit(a.new)) != identities:
    raise ValueError('JAR changed during class evidence production')
