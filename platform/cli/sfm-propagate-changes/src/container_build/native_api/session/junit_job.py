"""Fixed offline JUnit workload. Engine admission/lifecycle belongs to the typed host request."""
import hashlib,json,os,pathlib,subprocess,sys,shutil
work=pathlib.Path('/work');receipts=work/'test-receipts';receipts.mkdir(exist_ok=True)
assert sys.argv[1:]==['sfm-dev/mc-1.19.2']
raw=(work/'container-inputs.json').read_bytes();assert len(raw)<=4*1024**2
inputs=json.loads(raw);assert 1<=len(inputs)<=10000
def verify():
    status=dict(line.split(':',1) for line in pathlib.Path('/proc/self/status').read_text().splitlines() if ':' in line)
    security={'uid':os.geteuid(),'NoNewPrivs':int(status['NoNewPrivs']),'CapEff':status['CapEff'].strip(),'Seccomp':int(status['Seccomp'])}
    (receipts/'job-security.json').write_text(json.dumps(security))
    assert security['uid']==10001 and security['NoNewPrivs']==1 and int(security['CapEff'],16)==0 and security['Seccomp']==2
    for relative,digest in inputs.items():
        path=pathlib.PurePosixPath(relative);assert not path.is_absolute() and '..' not in path.parts
        actual=work/relative;assert actual.is_file() and not actual.is_symlink()
        assert actual.stat().st_size<=16*1024**2 and 'sha256:'+hashlib.sha256(actual.read_bytes()).hexdigest()==digest,relative
    return {'files':len(inputs),'manifestSha256':'sha256:'+hashlib.sha256(raw).hexdigest(),'verified':True}
before=verify();env=dict(os.environ);env['HOME']='/work/.home-junit';pathlib.Path(env['HOME']).mkdir(exist_ok=True)
with (receipts/'junit-run.log').open('wb') as log:
    result=subprocess.run(['/opt/sfm/cargo/bin/sfm-propagate-changes','test','run','--repo-root','/work','--projection',sys.argv[1],'--java-home',os.environ['JAVA_HOME'],'--require-portable-artifacts','--no-capture'],stdout=log,stderr=subprocess.STDOUT,env=env,timeout=870)
after=verify();(receipts/'input-verification.json').write_text(json.dumps({'before':before,'after':after}))
logs=list((work/'platform/minecraft/projections/sfm-dev/mc-1.19.2/build/sfm-toolchain/native-project').glob('*/run/runTest/console.log'))
assert len(logs)==1 and logs[0].stat().st_size<=16*1024**2,logs
shutil.copyfile(logs[0],receipts/'junit-events.log')
assert (receipts/'junit-run.log').stat().st_size<=16*1024**2
(receipts/'junit-run.json').write_text(json.dumps({'exit_code':result.returncode,'projection':sys.argv[1],'network':'none','gradle':False}))
sys.exit(result.returncode)
