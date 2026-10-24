"""Restore exact locked test JARs and inspect POMs; never admit metadata."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from urllib.parse import urlparse
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = Path('/workspace/SuperFactoryManager')
CACHE = Path('/workspace/.sfm-env/cache/minecraft-toolchain')
CLI = '/workspace/.sfm-env/target/debug/sfm-propagate-changes'
LOCK = REPO / 'platform/minecraft/projections/sfm-dev/mc-1.19.2/sfm-toolchain.lock.json'
PROPOSAL = ROOT.parent / 'metadata-admission-20261024/proposed-bundle'
lock = json.loads(LOCK.read_text())
catalog = CACHE / 'maven/maven-metadata-pins.json'
catalog_before = catalog.read_bytes()
lock_before = LOCK.read_bytes()
roots = []
for dep in lock['dependencies']:
    for component in dep.get('components', []):
        if any(scope in ('test-compile', 'test-runtime', 'test-compile-only')
               for scope in component.get('declaration', {}).get('scopes', [])):
            roots.append(component['derived_checks']['resolved_coordinate'])
own = [row['coordinate'].removesuffix('@pom')
       for row in json.loads((PROPOSAL / 'receipts.json').read_text())
       if row['isOwnPomForExistingLockedArtifact']]
targets = sorted(set(roots + own + ['com.github.javaparser:javaparser-core:3.26.4']))
(ROOT / 'test-roots.json').write_text(json.dumps(roots, indent=2) + '\n')
artifacts = {a['coordinate']: a for a in lock['artifacts'] if a.get('coordinate')}
search = subprocess.run(['rg', '--files', '--hidden', '-g', '*.jar',
                         str(CACHE), '/workspace/scratch/release-inspection',
                         str(REPO / 'platform/minecraft/projections/sfm-dev/mc-1.19.2/build')],
                        capture_output=True, text=True, check=True)
existing = [Path(p) for p in search.stdout.splitlines()]
records = []

def verified(path, expected):
    report = json.loads(subprocess.check_output(
        [CLI, 'hash', 'file', str(path), '--algorithm', 'blake3', '--length', '20'], text=True))
    assert report['writes_performed'] is False
    return report['identity'] == expected

def publish(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    assert destination.parent.resolve().is_relative_to(CACHE.resolve())
    assert not os.path.lexists(destination), 'Refusing to overwrite intervening cache file'
    fd, temp = tempfile.mkstemp(prefix='.sfm-locked-restore-', dir=destination.parent)
    try:
        with os.fdopen(fd, 'wb') as out, source.open('rb') as inp:
            shutil.copyfileobj(inp, out)
            out.flush()
            os.fsync(out.fileno())
        assert Path(temp).read_bytes() == source.read_bytes()
        os.link(temp, destination)
    finally:
        os.unlink(temp)

for coord in targets:
    a = artifacts[coord]
    assert a['hash'].startswith('blake3:') and len(a['hash'].split(':')[1]) == 40
    group, artifact, version = coord.split(':')
    relative = f'{group.replace(".", "/")}/{artifact}/{version}/{artifact}-{version}.jar'
    destination = CACHE / 'maven' / relative
    declared = Path(a['cache_path'].replace('$sfm-cache', str(CACHE)).replace('\\', '/'))
    assert declared == destination
    row = {'coordinate': coord, 'lockedHash': a['hash'], 'lockedUrl': a['url'],
           'cachePath': str(destination)}
    if destination.exists():
        assert verified(destination, a['hash']), f'Existing cache hash mismatch: {coord}'
        row['restoration'] = 'already present; locked hash verified'
    else:
        candidates = [p for p in existing if p.name == destination.name and p != destination]
        source = next((p for p in candidates if verified(p, a['hash'])), None)
        if source:
            row['restoration'] = 'copied existing hash-verified artifact'
            row['existingSource'] = str(source)
        else:
            parsed = urlparse(a['url'])
            assert parsed.scheme == 'https' and parsed.hostname in ('repo1.maven.org', 'repo.maven.apache.org')
            assert not parsed.query and not parsed.fragment and not parsed.username
            source = ROOT / 'downloads' / relative
            source.parent.mkdir(parents=True, exist_ok=True)
            assert not source.exists()
            result = subprocess.run(['curl', '--fail', '--silent', '--show-error',
                                     '--max-time', '60', '--max-filesize', '67108864',
                                     '--proto', '=https', '--output', str(source),
                                     '--write-out', '%{http_code}', a['url']],
                                    capture_output=True, text=True)
            row['httpStatus'] = result.stdout
            if result.returncode or result.stdout != '200':
                row.update(restoration='blocked; no retry', error=result.stderr)
                records.append(row)
                (ROOT / 'artifacts.json').write_text(json.dumps(records, indent=2) + '\n')
                raise SystemExit(f'Locked download blocked: {coord}: {result.stdout}: {result.stderr}')
            assert verified(source, a['hash']), f'Locked download hash mismatch: {coord}'
            row['restoration'] = 'downloaded exact locked URL; hash verified before publication'
        publish(source, destination)
        assert verified(destination, a['hash'])
    raw = destination.read_bytes()
    row.update(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    member = f'META-INF/maven/{group}/{artifact}/pom.xml'
    with zipfile.ZipFile(destination) as archive:
        row['hasEmbeddedPom'] = member in archive.namelist()
        if row['hasEmbeddedPom']:
            assert archive.getinfo(member).file_size <= 2 * 1024 * 1024
            pom = archive.read(member)
            pom_relative = relative[:-4] + '.pom'
            output = ROOT / 'embedded' / pom_relative
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(pom)
            staged = PROPOSAL / pom_relative
            row.update(embeddedPomPath=str(output), embeddedPomSha256=hashlib.sha256(pom).hexdigest(),
                       embeddedPomBytes=len(pom),
                       stagedPomIdentical=staged.read_bytes() == pom if staged.exists() else None)
    records.append(row)
    (ROOT / 'artifacts.json').write_text(json.dumps(records, indent=2) + '\n')
    print(coord, row['restoration'], 'embedded=' + str(row['hasEmbeddedPom']), flush=True)

assert catalog.read_bytes() == catalog_before and LOCK.read_bytes() == lock_before
(ROOT / 'restoration-summary.json').write_text(json.dumps({
    'artifactsVerified': len(records), 'declaredTestRoots': len(roots),
    'downloaded': sum('downloaded' in x['restoration'] for x in records),
    'copiedExisting': sum('copied' in x['restoration'] for x in records),
    'catalogSha256Unchanged': hashlib.sha256(catalog_before).hexdigest(),
    'artifactLockSha256Unchanged': hashlib.sha256(lock_before).hexdigest(),
    'metadataAdmitted': 0,
}, indent=2) + '\n')
