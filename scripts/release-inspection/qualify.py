#!/usr/bin/env python3
"""Reproduce headless inspection qualification using installed tools and fresh owned inputs."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import warnings
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--cli', type=Path, required=True)
p.add_argument('--java-home', type=Path, required=True)
p.add_argument('--gson-jar', type=Path, required=True, help='Existing Gson classpath; never downloaded')
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
repo = Path(__file__).resolve().parents[2]
out = a.output.resolve(); out.mkdir(exist_ok=False)
java = a.java_home.resolve()
scripts = repo / 'scripts/release-inspection'
checks = []


def run(argv, label, success=True):
    result = subprocess.run(list(map(str, argv)), capture_output=True, timeout=120)
    (out / f'{label}.stdout').write_bytes(result.stdout)
    (out / f'{label}.stderr').write_bytes(result.stderr)
    assert (result.returncode == 0) == success, (label, result.returncode, result.stderr[-3000:])
    return result


run([sys.executable, scripts/'verify.py', '--cli', a.cli.resolve(), '--java-home', java, '--output', out/'fixtures'], 'fixtures')
checks.append('native/JAR/source regressions including method identity and exact base64 evidence')
source = repo / 'platform/minecraft/projections/sfm-dev/mc-1.19.2/src/main/java'
classes = out / 'java-classes'; classes.mkdir()
run([java/'bin/javac', '-proc:none', '-cp', a.gson_jar.resolve(), '-sourcepath', source,
     '-d', classes, scripts/'InspectionProbe.java', scripts/'QualificationProbe.java', scripts/'NucleoAdapterProbe.java', scripts/'DecorationProbe.java'], 'compile')
classpath = str(classes) + os.pathsep + str(a.gson_jar.resolve())
run([java/'bin/java', '-Xmx256m', '-cp', classpath, 'InspectionProbe', out/'fixtures/fixture.sfm-inspection.json',
     out/'fixtures/missing.sfm-inspection.json'], 'tree')
run([java/'bin/java', '-Xmx256m', '-cp', classpath, 'QualificationProbe', out/'fixtures/fixture.sfm-inspection.json'], 'strict')
checks.append('production tree, alias sharing, cached decorations, malformed input and nonblocking mount publication')
run([java/'bin/java', '-Xmx128m', '-cp', classpath, 'ca.teamdman.sfm.client.review.release_review.DecorationProbe'], 'coordinates')
checks.append('linear coordinate scan: UTF-8/astral/CRLF boundaries and 60001 endpoints')
run([java/'bin/java', '-Xmx128m', '-cp', classpath, 'NucleoAdapterProbe'], 'nucleo-adapter')
checks.append('optional Nucleo adapter contract with fake provider only; existing matcher unchanged')

valid = out/'valid-capture'; valid.mkdir()
run([sys.executable, scripts/'classes.py', '--old', out/'fixtures/old.jar', '--new', out/'fixtures/new.jar',
     '--javap', java/'bin/javap', '--output', valid], 'admitted-javap')
checks.append('admitted real javap producer')
marker = out/'javap-ran'
fake = out/'forbidden-javap'
fake.write_text('#!/usr/bin/env python3\nfrom pathlib import Path\nPath(' + repr(str(marker)) + ').write_text("ran")\n')
fake.chmod(0o700)

for case in ('unsafe', 'duplicate', 'oversized'):
    path = out/f'{case}.jar'
    with warnings.catch_warnings():
        warnings.simplefilter('ignore', UserWarning)
        with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED) as jar:
            if case == 'unsafe': jar.writestr('../Escape.class', b'bad')
            elif case == 'duplicate':
                jar.writestr('A.class', b'one'); jar.writestr('A.class', b'two')
            else: jar.writestr('large.dat', b'x' * (4 * 1024**2 + 1))
    capture = out/f'{case}-capture'; capture.mkdir()
    run([sys.executable, scripts/'classes.py', '--old', path, '--new', out/'fixtures/new.jar',
         '--javap', fake, '--output', capture], f'{case}-admission', success=False)
    assert not marker.exists(), 'Rejected archive reached javap'
checks.append('unsafe/duplicate/oversized archive rejected before javap')

if hasattr(os, 'mkfifo'):
    special = out/'input.fifo'; os.mkfifo(special)
    run([java/'bin/java', '-Xmx128m', '-cp', classpath, 'QualificationProbe', '--reject-open', special], 'fifo')
    linked = out/'linked-workspace.json'; linked.symlink_to(out/'fixtures/fixture.sfm-inspection.json')
    run([java/'bin/java', '-Xmx128m', '-cp', classpath, 'QualificationProbe', '--reject-open', linked], 'symlink')
    checks.append('FIFO and symlink workspace rejected before blocking read')
summary = dict(result='passed', checks=checks, output=str(out), package=str(out/'replay-inputs.zip'), ui='not run; no screenshot claim')
(out/'summary.json').write_text(json.dumps(summary, indent=2))
# Keep executable inputs/receipts reusable without archiving special files or symlinks.
files = [path for path in out.rglob('*') if path.is_file() and not path.is_symlink()
         and 'java-classes' not in path.relative_to(out).parts]
with zipfile.ZipFile(out/'replay-inputs.zip', 'x', compression=zipfile.ZIP_DEFLATED) as bundle:
    for path in sorted(files): bundle.write(path, path.relative_to(out).as_posix())
print(json.dumps(summary, indent=2))
