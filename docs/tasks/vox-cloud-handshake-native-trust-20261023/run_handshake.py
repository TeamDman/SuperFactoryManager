"""Bounded real Minecraft launch, Rust RPC proof, and owned-process cleanup."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import time

root = Path('/workspace/sfm-handshake-20261023')
out = root / 'continuation'
launcher = root / 'tools/sfm-propagate-changes-native'
control = root / 'tools/sfm'
env = dict(os.environ)
assert not env.get('JAVA_TOOL_OPTIONS')
assert not env.get('_JAVA_OPTIONS')
assert not env.get('JDK_JAVA_OPTIONS')
for variable, subdir in [('XDG_DATA_HOME','xdg-data'), ('XDG_CACHE_HOME','xdg-cache'),
                         ('XDG_CONFIG_HOME','xdg-config'), ('XDG_RUNTIME_DIR','xdg-runtime'),
                         ('MESA_SHADER_CACHE_DIR','mesa-cache')]:
    path = out / subdir
    path.mkdir(mode=0o700, exist_ok=True)
    env[variable] = str(path)
env.update({'DISPLAY': ':93', 'XAUTHORITY': str(out/'display.Xauthority'),
            'LIBGL_ALWAYS_SOFTWARE': '1', 'LP_NUM_THREADS': '4', 'RAYON_NUM_THREADS': '1'})
descriptor_dir = Path(env['XDG_DATA_HOME']) / 'teamdman/sfm/instances'
assert not descriptor_dir.exists() or not list(descriptor_dir.glob('*.json'))
command = [str(launcher), '--output-format', 'json', '--log-filter', 'info',
           '--log-file', str(out/'game.ndjson'), 'run', 'client',
           '--repo-root', '/workspace/SuperFactoryManager', '--projection', 'sfm-dev/mc-1.19.2',
           '--java-home', '/workspace/.sfm-env/jdk17', '--require-portable-artifacts']
record = {'command': command, 'started_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'source_commit': subprocess.check_output(['git','rev-parse','HEAD'], cwd='/workspace/SuperFactoryManager', text=True).strip(),
          'launcher_sha256': hashlib.file_digest(launcher.open('rb'), 'sha256').hexdigest(),
          'control_sha256': hashlib.file_digest(control.open('rb'), 'sha256').hexdigest(),
          'timeout_seconds': 1200, 'container_memory_bytes': 17179869184, 'container_cpu_quota': 4,
          'display': ':93', 'descriptor_directory': str(descriptor_dir),
          'result': 'pending', 'cleanup': []}

def save():
    (out/'handshake-result.json').write_text(json.dumps(record, indent=2)+'\n')

def process_rows():
    text = subprocess.check_output(['ps','-eo','pid=,ppid=,pgid=,comm=,args='],text=True)
    return [dict(zip(['pid','ppid','pgid','comm','args'], line.strip().split(None,4)))
            for line in text.splitlines() if len(line.strip().split(None,4)) == 5]

def owned_java(pid, group):
    return next((r for r in process_rows() if int(r['pid']) == pid
                 and int(r['pgid']) == group and r['comm'] == 'java'), None)

game_pid = None
with (out/'game.json').open('w') as stdout, (out/'game.stderr').open('w') as stderr:
    process = subprocess.Popen(command, env=env, cwd='/workspace/SuperFactoryManager',
                               stdout=stdout, stderr=stderr, start_new_session=True)
    record.update({'launcher_pid': process.pid, 'owned_process_group': process.pid})
    save()
    deadline = time.monotonic() + record['timeout_seconds']
    try:
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError('Native launch exited before a live Minecraft control endpoint: '+str(process.returncode))
            if descriptor_dir.exists() and list(descriptor_dir.glob('*.json')):
                listing = subprocess.run([str(control),'--output-format','json','instance','list'],
                                         env=env,capture_output=True,text=True,timeout=15)
                if listing.returncode == 0:
                    parsed = json.loads(listing.stdout)
                    if parsed.get('live_count') == 1:
                        instance = next(i for i in parsed['instances'] if i['health'] == 'live')
                        game_pid = int(instance['process_id'])
                        assert instance['minecraft_version'] == '1.19.2', instance
                        ownership = owned_java(game_pid, process.pid)
                        assert ownership, 'Live Java process is outside the task-owned launch group'
                        (out/'live-instance.json').write_text(listing.stdout)
                        record['game_process'] = ownership
                        record['instance_id'] = instance['instance_id']
                        nonce = 'cutover-check-' + str(time.time_ns())
                        echo_command = [str(control),'--output-format','json','action','invoke',
                                        '--instance-id',instance['instance_id'],'sfm:echo',nonce]
                        echo = subprocess.run(echo_command,env=env,capture_output=True,text=True,timeout=30)
                        (out/'echo.json').write_text(echo.stdout)
                        (out/'echo.stderr').write_text(echo.stderr)
                        record.update({'echo_command':echo_command,'echo_exit':echo.returncode})
                        assert echo.returncode == 0, 'Rust echo command failed'
                        response = json.loads(echo.stdout)
                        assert response['result_code'] == 1, response
                        assert response['process_id'] == game_pid, response
                        assert response['instance_id'] == instance['instance_id'], response
                        assert nonce in json.dumps(response.get('feedback')), response
                        record['result'] = 'PASS: Rust sfm:echo against actual Minecraft 1.19.2 Java service'
                        record['echo_request_id'] = response['request_id']
                        save()
                        break
            time.sleep(2)
        else:
            raise TimeoutError('No live Minecraft control endpoint within bounded launch window')
    except Exception as exc:
        record.update({'result':'BLOCKED_OR_FAILED','error':str(exc)})
    finally:
        if game_pid is not None and owned_java(game_pid, process.pid):
            os.kill(game_pid, signal.SIGTERM)
            record['cleanup'].append({'target_pid':game_pid,'signal':'SIGTERM','reason':'graceful JVM shutdown after RPC attempt'})
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                pass
        if process.poll() is None:
            process.send_signal(signal.SIGINT)
            record['cleanup'].append({'target_pid':process.pid,'signal':'SIGINT','reason':'cancel owned native launch'})
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid,signal.SIGTERM)
                record['cleanup'].append({'target_group':process.pid,'signal':'SIGTERM'})
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid,signal.SIGKILL)
                    record['cleanup'].append({'target_group':process.pid,'signal':'SIGKILL'})
                    process.wait(timeout=10)
        record['launcher_exit'] = process.returncode
        remaining = [r for r in process_rows() if int(r['pgid']) == process.pid]
        if remaining:
            os.killpg(process.pid, signal.SIGTERM)
            record['cleanup'].append({'target_group':process.pid,'signal':'SIGTERM',
                                      'reason':'stop remaining members of the task-created process group',
                                      'members':remaining})
            cleanup_deadline = time.monotonic() + 10
            while time.monotonic() < cleanup_deadline:
                remaining = [r for r in process_rows() if int(r['pgid']) == process.pid]
                if not remaining:
                    break
                time.sleep(0.5)
            if remaining:
                os.killpg(process.pid, signal.SIGKILL)
                record['cleanup'].append({'target_group':process.pid,'signal':'SIGKILL','members':remaining})
                time.sleep(1)
        record['remaining_owned_processes'] = [r for r in process_rows() if int(r['pgid']) == process.pid]
        record['finished_utc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        save()
print(json.dumps(record,indent=2))
