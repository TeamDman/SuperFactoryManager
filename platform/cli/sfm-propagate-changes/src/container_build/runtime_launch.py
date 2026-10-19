"""Bounded prebuilt client lifecycle; no compilers or downloads."""
from pathlib import Path
import argparse,hashlib,json,os,re,signal,subprocess,sys,time

ap=argparse.ArgumentParser();ap.add_argument('projection');ap.add_argument('game_test');ap.add_argument('--java-max-heap-mib',type=int,default=4096);ap.add_argument('--session-seconds',type=int,default=0);ap.add_argument('--startup-seconds',type=int,default=720);a=ap.parse_args()
assert 512<=a.java_max_heap_mib<=8192 and 0<=a.session_seconds<=8*3600 and 30<=a.startup_seconds<=900
assert re.fullmatch(r'sfm:[a-zA-Z0-9_]+',a.game_test)
for key in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']:
    assert not os.environ.get(key),'JVM environment overrides are refused'
work=Path('/work');manifest=json.loads((work/'runtime-manifest.json').read_text());assert a.projection==manifest['projection']
# Validate ELF/launch provenance cheaply; builder receipt binds the complete immutable inputs.
for name in [manifest['argfile'],'/opt/sfm/cargo/bin/sfm']:
    assert hashlib.sha256(Path(name).read_bytes()).hexdigest()==manifest['files'][name]['sha256'],name
out=work/'output';out.mkdir(exist_ok=False)
game=Path(manifest['game_directory']);game.mkdir(parents=True,exist_ok=True)
from config_seed import verify_initial, inspect_after
seed_files=verify_initial(manifest,game)
assert set(manifest['launch_environment'])<= {'MOD_CLASSES','MCP_MAPPINGS'}
os.environ.update(manifest['launch_environment'])
os.environ.update(HOME='/work/.home',LIBGL_ALWAYS_SOFTWARE='true',GALLIUM_DRIVER='llvmpipe',LP_NUM_THREADS='2',ALSOFT_DRIVERS='null')
args=Path(manifest['argfile']).read_text()
if a.session_seconds:
    args=args.replace('game_test_orbit_capture','managed_superflat_session')
args=re.sub(r'^-Xmx\S+\s*$', '',args,flags=re.M)
args=re.sub(r'^-Dsfm\.gamePuppet\.gameTest=.*$', '-Dsfm.gamePuppet.gameTest='+a.game_test.removeprefix('sfm:'),args,flags=re.M)
args=re.sub(r'^-Dsfm\.controlCliExecutable=.*$', '-Dsfm.controlCliExecutable=/opt/sfm/cargo/bin/sfm',args,flags=re.M)
args=re.sub(r'^-Dsfm\.controlCliSourceRoot=.*\n?', '',args,flags=re.M)
args=re.sub(r'^-Dsfm\.clientRun\.keepOpenSeconds=.*\n?', '',args,flags=re.M)
prepared=out/'launch.java.args';prepared.write_text(f'-Xmx{a.java_max_heap_mib}m\n-Dsfm.managedSession.seconds={max(1,a.session_seconds)}\n-Dsfm.clientRun.keepOpenSeconds=0\n'+args)
started=time.monotonic();logpath=out/'console.log'
with logpath.open('wb') as log:
    child=subprocess.Popen(['/usr/bin/xvfb-run','-a','/usr/lib/jvm/java-17-openjdk-amd64/bin/java','@'+str(prepared)],cwd=game,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
    stopping=False
    def stop(*unused):
        if child.poll() is None:os.killpg(child.pid,signal.SIGTERM)
    signal.signal(signal.SIGTERM,stop);signal.signal(signal.SIGINT,stop)
    complete_at=None;peak=0;first_capture=None;ready_written=False
    deadline=started+a.startup_seconds+a.session_seconds+30
    while child.poll() is None:
        now=time.monotonic();text=logpath.read_text(errors='replace')[-4_000_000:]
        try:
            peak=max(peak,int(Path('/sys/fs/cgroup/memory.peak').read_text()))
            stat={k:int(v) for k,v in (l.split() for l in Path('/sys/fs/cgroup/memory.stat').read_text().splitlines())}
            sample={'seconds':now-started,'peak':peak,'anon':stat.get('anon'),'file':stat.get('file'),'memory_events':Path('/sys/fs/cgroup/memory.events').read_text()}
            (out/'resource-sample.json').write_text(json.dumps(sample))
            with (out/'resource-history.jsonl').open('a') as history:history.write(json.dumps(sample)+'\n')
        except OSError:pass
        if first_capture is None and 'SFM_GAME_PUPPET_CAPTURE_MESSAGE' in text and 'Saved screenshot as' in text:first_capture=now-started
        if ('SFM_GAME_PUPPET_COMPLETE failed=0 total=1' in text or 'SFM_MANAGED_SESSION_READY' in text) and complete_at is None:complete_at=now
        if complete_at is not None and not ready_written:
            (out/'ready.json').write_text(json.dumps({'schema':'sfm:live_session@1','pid':child.pid,'projection':a.projection,'game_test':a.game_test,'first_capture_seconds':first_capture,'session_seconds':a.session_seconds}))
            print('SFM_PREBUILT_READY',flush=True);ready_written=True
        if now>deadline or (complete_at is None and now-started>a.startup_seconds):
            stop();break
        if Path('/work/output/stop.request').exists():stop();break
        if int(subprocess.check_output(['/usr/bin/df','-Pk','/']).decode().splitlines()[-1].split()[3])*1024<2*1024**3:
            print('SFM_DISK_RESERVE_EXHAUSTED',flush=True);stop();break
        time.sleep(1)
    try:code=child.wait(timeout=20)
    except subprocess.TimeoutExpired:
        os.killpg(child.pid,signal.SIGKILL);code=child.wait(timeout=10)
text=logpath.read_text(errors='replace')
config_receipt=inspect_after(game,seed_files)
config_receipt['incorrect_key_corrections']=text.count('Incorrect key ')
config_receipt['incorrect_config_warnings']=len(re.findall(r'Configuration file .*? is not correct',text))
(out/'config-seed-receipt.json').write_text(json.dumps(config_receipt,indent=2)+'\n')
success=('SFM_GAME_PUPPET_COMPLETE failed=0 total=1' in text or 'SFM_MANAGED_SESSION_READY' in text) and 'SFM_GAME_PUPPET_FAILED' not in text
receipt={'schema':'sfm:prebuilt_launch_receipt@1','game_test':a.game_test,'success':success,'exit_code':code,'first_capture_seconds':first_capture,'duration_seconds':time.monotonic()-started,'observed_cgroup_peak':peak,'compiled_at_launch':False,'session_seconds':a.session_seconds}
(out/'launch-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt),flush=True)
sys.exit(0 if success and code==0 else 1)
