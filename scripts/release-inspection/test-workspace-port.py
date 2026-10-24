#!/usr/bin/env python3
"""Run focused workspace tests using existing native build/JUnit artifacts; never download."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess

TESTS = (
    'screen/SFMScissorStackTests.java',
    'screen/explorer/SFMExplorerPreviewPlacementTests.java',
    'action/SFMClientActionContinuationTests.java',
    'screen/workspace/SFMHeadlessWorkspaceTestSupport.java',
    'screen/explorer/SFMExplorerPanelViewportTests.java',
    'screen/explorer/SFMExplorerPanelInteractionTests.java',
    'screen/SFMTextEditorReadOnlyChromeTests.java',
    'syntax/SFMTextEditorSyntaxSessionTests.java',
)


def file_sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def class_tree_identity(root):
    files = sorted(root.rglob('*.class'))
    digest = hashlib.sha256()
    for path in files:
        digest.update(path.relative_to(root).as_posix().encode('utf-8'))
        digest.update(b'\0')
        digest.update(file_sha256(path).encode('ascii'))
        digest.update(b'\n')
    return {'files': len(files), 'path_sha256_lines_sha256': digest.hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('projection', 'native-javac-args', 'main-classes', 'java-home',
                 'junit-console', 'vanilla-client', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--inspection-workspace', type=Path)
    parser.add_argument('--syntax-worker', type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    receipt = {'started_utc': datetime.now(timezone.utc).isoformat(),
               'status': 'validating_inputs',
               'arguments': {name: str(value) if value is not None else None
                             for name, value in vars(args).items()}}

    def save_receipt():
        (args.output / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')

    def input_error(message):
        receipt.update(status='input_error', error=message)
        save_receipt()
        parser.error(message)

    save_receipt()
    if bool(args.inspection_workspace) != bool(args.syntax_worker):
        input_error('--inspection-workspace and --syntax-worker must be supplied together')
    for path in (args.projection, args.main_classes, args.java_home):
        if not path.is_dir():
            input_error('Required existing directory is missing: ' + str(path))
    if not args.native_javac_args.is_file():
        input_error('Required existing input is missing: ' + str(args.native_javac_args))
    native = args.native_javac_args.read_text().splitlines()
    if '-classpath' not in native or native.index('-classpath') + 1 >= len(native):
        input_error('Native javac arguments have no classpath')
    native_classpath = native[native.index('-classpath') + 1].strip('"')
    classpath = os.pathsep.join((str(args.main_classes), native_classpath,
                                str(args.projection / 'src/main/resources'),
                                str(args.junit_console), str(args.vanilla_client)))
    source_root = args.projection / 'src/test/java/ca/teamdman/sfm/client'
    sources = [source_root / name for name in TESTS]
    runtime_inputs = []
    if args.inspection_workspace:
        sources.append(Path(__file__).resolve().with_name('PortRuntimeSmoke.java'))
        runtime_inputs = [args.inspection_workspace, args.syntax_worker]
    for path in (*sources, args.junit_console, args.vanilla_client, *runtime_inputs):
        if not path.is_file():
            input_error('Required existing input is missing: ' + str(path))
    classes = args.output / 'classes'
    classes.mkdir(exist_ok=True)
    suffix = '.exe' if os.name == 'nt' else ''
    compile_command = [str(args.java_home / 'bin' / ('javac' + suffix)),
                       '-proc:none', '-encoding', 'UTF-8', '-cp', classpath,
                       '-d', str(classes), *map(str, sources)]
    run_command = [str(args.java_home / 'bin' / ('java' + suffix)), '-Xmx1G',
                   '-jar', str(args.junit_console), '--class-path',
                   str(classes) + os.pathsep + classpath, '--details', 'summary',
                   '--disable-ansi-colors']
    for name in TESTS:
        if name.endswith('Tests.java'):
            run_command += ['--select-class', 'ca.teamdman.sfm.client.' +
                            name[:-5].replace('/', '.')]
    receipt.update({'compile_command': compile_command, 'run_command': run_command,
               'junit_sha256': file_sha256(args.junit_console),
               'vanilla_client_sha256': file_sha256(args.vanilla_client),
               'native_javac_args_sha256': file_sha256(args.native_javac_args),
               'main_classes': class_tree_identity(args.main_classes),
               'test_source_sha256': {str(path.relative_to(source_root)) if path.is_relative_to(source_root)
                                      else path.name: file_sha256(path) for path in sources}})
    phases = [('compile', compile_command), ('tests', run_command)]
    if args.inspection_workspace:
        runtime_command = [str(args.java_home / 'bin' / ('java' + suffix)), '-Xmx256m',
                           '-cp', str(classes) + os.pathsep + classpath, 'PortRuntimeSmoke',
                           str(args.syntax_worker.resolve()), str(args.inspection_workspace.resolve()),
                           str(args.output.resolve() / 'deliberately-missing.sfm-inspection.json')]
        if Path(runtime_command[-1]).exists():
            input_error('Missing-input fixture unexpectedly exists: ' + runtime_command[-1])
        receipt['runtime_command'] = runtime_command
        receipt['inspection_workspace_sha256'] = file_sha256(args.inspection_workspace)
        receipt['syntax_worker_sha256'] = file_sha256(args.syntax_worker)
        phases.append(('runtime', runtime_command))
    receipt['phases'] = {phase: 'not_run' for phase, _ in phases}
    receipt['status'] = 'pending'
    save_receipt()
    for phase, command in phases:
        receipt.update(status='running', current_phase=phase)
        receipt['phases'][phase] = 'running'
        save_receipt()
        try:
            with (args.output / (phase + '.log')).open('w') as log:
                result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT,
                                        timeout=60 if phase == 'runtime' else 300)
        except (subprocess.TimeoutExpired, OSError) as error:
            state = 'timed_out' if isinstance(error, subprocess.TimeoutExpired) else 'start_error'
            receipt.update(status=state, error=str(error))
            receipt['phases'][phase] = state
            save_receipt()
            raise SystemExit(1) from error
        receipt[phase + '_exit'] = result.returncode
        receipt['phases'][phase] = 'passed' if result.returncode == 0 else 'failed'
        if result.returncode:
            receipt['status'] = 'failed'
            save_receipt()
            raise SystemExit(result.returncode)
        save_receipt()
    receipt['status'] = 'passed'
    receipt['completed_utc'] = datetime.now(timezone.utc).isoformat()
    save_receipt()
    print('Focused workspace tests passed; receipt:', args.output / 'receipt.json')


if __name__ == '__main__':
    main()
