"""Run a bounded Java probe against a task-owned real Rust TCP server."""
import argparse, hashlib, json, os, re, subprocess, time
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--server',type=Path,required=True)
p.add_argument('--classes',type=Path,required=True)
p.add_argument('--classpath-file',type=Path,required=True)
p.add_argument('--main',required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args(); out=a.output; out.mkdir(exist_ok=False)
env=os.environ.copy()
for name in ['XDG_DATA_HOME','XDG_CONFIG_HOME','XDG_CACHE_HOME']:
    env[name]=str(out/name.lower())
logpath=out/'server.log'
with logpath.open('w') as log:
    server=subprocess.Popen([str(a.server),'--listen','127.0.0.1:0'],stdout=log,stderr=subprocess.STDOUT,env=env)
    try:
        deadline=time.monotonic()+15
        while True:
            match=re.search(r'VOX_TERMINAL_LISTEN_ADDR=127\.0\.0\.1:(\d+)',logpath.read_text())
            if match: break
            if server.poll() is not None or time.monotonic()>deadline: raise RuntimeError('server did not bind')
            time.sleep(.05)
        cp=str(a.classes)+os.pathsep+a.classpath_file.read_text().strip()
        r=subprocess.run(['/workspace/.sfm-env/jdk17/bin/java','-ea','-cp',cp,a.main,match.group(1)],
            capture_output=True,text=True,env=env,timeout=45)
        (out/'client.stdout').write_text(r.stdout);(out/'client.stderr').write_text(r.stderr)
        receipt={'main':a.main,'server':str(a.server),'server_sha256':hashlib.sha256(a.server.read_bytes()).hexdigest(),
            'pid':server.pid,'client_exit':r.returncode,'server_alive_after_probe':server.poll() is None,
            'scope':'real loopback Java/Rust TCP; no Minecraft or GUI; probe-specific limits apply'}
    finally:
        server.terminate()
        try: server.wait(timeout=5)
        except subprocess.TimeoutExpired:
            server.kill();server.wait(timeout=5)
    receipt['server_stopped']=server.poll() is not None
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps(receipt));print(r.stdout)
    if r.returncode:print(r.stderr[-1600:])
    raise SystemExit(r.returncode)
