"""Use only the existing qualified production classpath; no downloads."""
import json
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parent
record = Path('/workspace/scratch/release-inspection/accepted-focused-junit/command.json')
previous = json.loads(record.read_text())
classpath = previous[previous.index('--class-path') + 1]
# Exclude prior test directories so the reproduction resolves production classes.
classpath = ':'.join(p for p in classpath.split(':') if not p.startswith('/workspace/scratch/'))
command = ['/workspace/.sfm-env/jdk17/bin/java', '-Xmx256m', '--class-path',
           classpath, str(root / 'TrailingNewlineRepro.java')]
(root / 'repro-command.json').write_text(json.dumps(command, indent=2) + '\n')
result = subprocess.run(command, capture_output=True, text=True, cwd=root)
(root / 'repro.stdout').write_text(result.stdout)
(root / 'repro.stderr').write_text(result.stderr)
print(result.stdout, end='')
print('exit:', result.returncode)
raise SystemExit(result.returncode)
