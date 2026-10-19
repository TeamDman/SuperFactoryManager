"""Export only reviewed OS runtime files and paths used by the prepared Java launch."""
from pathlib import Path
import hashlib,json,os,re,shlex,shutil,subprocess

work=Path('/work');out=Path('/work/.runtime-root')
assert not out.exists();out.mkdir()
copied={}
def copy(path):
    path=Path(path)
    assert path.is_absolute() and '..' not in path.parts
    if path.is_dir():
        for p in sorted(path.rglob('*')):
            if p.is_file():copy(p)
        return
    assert path.is_file(),str(path)
    dest=out/str(path).lstrip('/')
    if dest.exists():return
    dest.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(path,dest,follow_symlinks=True)
    copied[str(path)]={'bytes':dest.stat().st_size,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest()}

# Trusted OS paths only; no home, credentials, user cache, build targets or sources.
bins=['xkbcomp','mcookie','stty','fmt','sh','python3','Xvfb','xauth','xvfb-run','getopt','sed','awk','grep','mktemp','cat','kill','rm','env','head','tail','df','sleep','setpriv','id','glxinfo','pgrep','mkdir','chmod','touch']
for name in bins:
    path=shutil.which(name);assert path,name;copy(path)
    if name=='sh':copy('/bin/sh')
for root in ['/usr/lib/x86_64-linux-gnu','/lib/x86_64-linux-gnu']:
    for p in sorted(Path(root).glob('*.so*')):
        if p.is_file():copy(p)
    for sub in ['dri','pulseaudio','alsa-lib']:
        if (Path(root)/sub).is_dir():copy(Path(root)/sub)
for p in ['/lib64/ld-linux-x86-64.so.2','/usr/lib/python3.11','/etc/fonts','/usr/share/fonts','/usr/share/fontconfig','/usr/share/X11','/etc/ssl/certs','/etc/java-17-openjdk']:
    if Path(p).exists():copy(p)
jdk=Path('/usr/lib/jvm/java-17-openjdk-amd64')
for sub in ['lib','conf','legal']:
    copy(jdk/sub)
for name in ['java','jcmd','jps']:copy(jdk/'bin'/name)
for path in ['/etc/passwd','/etc/group','/etc/nsswitch.conf','/etc/hosts','/etc/resolv.conf','/etc/localtime']:
    if Path(path).is_file():copy(path)
for p in [out/'tmp',out/'work',out/'work/.home']:
    p.mkdir(parents=True,exist_ok=True);p.chmod(0o777 if p.name=='tmp' else 0o755)

# Xvfb expects a root-owned sticky socket directory (final OS COPY sets UID0).
x11=out/'tmp/.X11-unix';x11.mkdir();x11.chmod(0o1777)

args=list(work.glob('platform/minecraft/projections/sfm-dev/*/build/sfm-toolchain/native-project/*/run/runGameTestPreview/launch.java.args'))
assert len(args)==1,args
argfile=args[0];tokens=shlex.split(argfile.read_text())
environment_path=argfile.parent/'launch.environment.json'
assert environment_path.stat().st_size<=1024*1024
environment=json.loads(environment_path.read_text())
assert set(environment)<= {'MOD_CLASSES','MCP_MAPPINGS'},'unreviewed launch environment'
assert all(isinstance(v,str) and len(v)<=1024*1024 for v in environment.values())
tokens.extend(environment.values())
source_base=str(argfile.parent)
for token in tokens:
    if token.startswith('-Dsfm.controlCliExecutable='):
        assert token=='-Dsfm.controlCliExecutable=/work/platform/cli/sfm/target/debug/sfm','unreviewed control executable'
        assert hashlib.sha256(Path(token.split('=',1)[1]).read_bytes()).digest()==hashlib.sha256(Path('/opt/sfm/cargo/bin/sfm').read_bytes()).digest(),'builder control alias differs'
        # runtime_launch.py maps this exact native alias to the packaged ELF.
        continue
    # Java class/module paths are colon-delimited; other path-valued arguments are single paths.
    for segment in token.split(':'):
        pos=segment.find('/work/')
        if pos<0:continue
        raw=segment[pos:];p=Path(raw)
        assert '..' not in p.parts and p.is_relative_to(work)
        allowed=[work/'.sfm-cache/minecraft-toolchain',work/'platform/minecraft/projections/sfm-dev'/argfile.parts[argfile.parts.index('sfm-dev')+1]/'build/sfm-toolchain/native-project',work/'platform/minecraft/projections/sfm-dev'/argfile.parts[argfile.parts.index('sfm-dev')+1]/'runGameTestPreview']
        assert any(p.is_relative_to(base) for base in allowed),'unreviewed runtime path: '+str(p)
        assert not any(part in {'world','worlds','saves','screenshots','.runtime-root'} for part in p.parts),'private or generated runtime path: '+str(p)
        if p.exists():copy(p)
        else:(out/str(p).lstrip('/')).mkdir(parents=True,exist_ok=True)
copy(argfile)
copy(environment_path)
copy('/opt/sfm/cargo/bin/sfm')
copy('/opt/sfm/runtime_launch.py')
assets=work/'.sfm-cache/minecraft-toolchain/minecraft/assets';copy(assets)
# Required runtime-generated Forge inputs are path-valued properties in the launch file.
projection=argfile.parts[argfile.parts.index('sfm-dev')+1]
manifest={'schema':'sfm:prebuilt_runtime@1','projection':'sfm-dev/'+projection,'argfile':str(argfile),'game_directory':str(work/'platform/minecraft/projections/sfm-dev'/projection/'runGameTestPreview'),'files':copied,'bytes':sum(p['bytes'] for p in copied.values()),'compilation_at_launch':False,'launch_environment':environment}
(out/'work/runtime-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
receipt=work/'test-receipts'
if receipt.exists():copy(receipt)
# Separate COPY ownership without duplicating a full game layer.
# OS files stay root-owned; only disposable game inputs/output paths belong to UID10001.
runtime_game=work/'.runtime-game'
assert not runtime_game.exists()
(out/'work').rename(runtime_game)
print(json.dumps({'runtime_files':len(copied),'runtime_bytes':manifest['bytes'],'argfile':str(argfile)}))
