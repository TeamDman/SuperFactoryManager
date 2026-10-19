"""Narrow local bootstrap: existing locks/index entries, checked archives, exact local git objects.
No downloads, builds, global Cargo config, credentials, host mounts or game inputs.
"""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,tomllib

BASE='localhost/sfm-graphics@sha256:9f7f1c4b8cbb7452c3fa5bb30822ee112f6ea446e2f391e7e45ee20b13217b84'
ROOT=None
CARGO=None
HERE=Path(__file__).parent
ap=argparse.ArgumentParser();ap.add_argument('--context',type=Path,required=True);ap.add_argument('--base',default=BASE);ap.add_argument('--repo-root',type=Path,required=True);ap.add_argument('--cargo-home',type=Path,required=True);a=ap.parse_args();out=a.context.absolute();ROOT=a.repo_root.resolve();CARGO=a.cargo_home.resolve();BASE=a.base
assert out.parent.resolve()==HERE.resolve() and not out.exists(),'Use a new directory directly under this task workspace'
receipt={};total=0
def digest(data):return 'sha256:'+hashlib.sha256(data).hexdigest()
def checked(path):
    for parent in [path,*path.parents]:
        st=parent.stat(follow_symlinks=False)
        if parent.is_symlink() or getattr(st,'st_file_attributes',0)&0x400:raise RuntimeError('Reparse input refused')
    return path.read_bytes()
def write(relative,data,parent):
    global total
    p=out/relative;assert not p.exists() and '\\' not in relative and '..' not in Path(relative).parts
    total+=len(data);assert total<=512*1024*1024,'Context byte budget exceeded'
    p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    receipt[relative]={'sha256':digest(data),'bytes':len(data),'parent':parent}
def cli_inputs(cli):
    folder=ROOT/'platform/cli'/cli
    paths=[folder/n for n in ['Cargo.toml','Cargo.lock','build.rs'] if (folder/n).is_file()]
    paths += sorted(p for p in (folder/'src').rglob('*') if p.is_file() and p.suffix in ['.rs','.java','.py','.js','.css','.liquid','.json'])
    return {str(p.relative_to(ROOT)).replace('\\','/'):checked(p) for p in paths}
sources={};packages={};locks={};git_repos={}
for cli,label in [('sfm-propagate-changes','propagate'),('sfm','control')]:
    sources.update(cli_inputs(cli))
    lock=tomllib.loads(checked(ROOT/'platform/cli'/cli/'Cargo.lock').decode())
    locks.update({(p['name'],p['version'],p.get('source')):p for p in lock['package']})
    meta=json.loads((HERE/f'cargo-linux-{label}.json').read_text())
    nodes={n['id']:n for n in meta['resolve']['nodes']};todo=[meta['resolve']['root']];reached=set()
    while todo:
        ident=todo.pop()
        if ident in reached:continue
        reached.add(ident);todo.extend(d['pkg'] for d in nodes[ident]['deps'])
    packages.update({(p['name'],p['version'],p.get('source')):p for p in meta['packages'] if p['id'] in reached})
    for p in packages.values():
        if (p.get('source') or '').startswith('git+'):
            repo=next(parent for parent in Path(p['manifest_path']).parents if (parent/'.git').exists())
            git_repos[p['source'].rsplit('#',1)[-1]]=repo
out.mkdir()
os.environ["GIT_CONFIG_NOSYSTEM"]="1"
os.environ["GIT_CONFIG_GLOBAL"]=os.devnull
os.environ["GIT_TERMINAL_PROMPT"]="0"
try:
    for rel,data in sources.items():write('source/'+rel,data,'current-authored-source')
    for name in ['app.rc','app.manifest','main.ico']:
        p=ROOT/'platform/cli/sfm-propagate-changes/resources'/name
        write('source/'+str(p.relative_to(ROOT)).replace('\\','/'),checked(p),'fixed-build-resource')
    index=CARGO/'registry/index/index.crates.io-1949cf8c6b5b557f'
    config=json.loads(checked(index/'config.json'))
    assert config=={'dl':'https://static.crates.io/crates','api':'https://crates.io'},'Unexpected registry configuration'
    write('cargo/registry/index/index.crates.io-1949cf8c6b5b557f/config.json',json.dumps(config).encode(),'public-crates-io-config')
    names=sorted({p['name'].lower() for p in locks.values() if (p.get('source') or '').startswith('registry+')})
    for name in names:
        rel=name if len(name)==1 else '2/'+name if len(name)==2 else '3/'+name[0]+'/'+name if len(name)==3 else name[:2]+'/'+name[2:4]+'/'+name
        write('cargo/registry/index/index.crates.io-1949cf8c6b5b557f/.cache/'+rel,checked(index/'.cache'/rel),'locked-registry-package-metadata')
    for key,p in packages.items():
        if not (p.get('source') or '').startswith('registry+'):continue
        filename=f'{p["name"]}-{p["version"]}.crate'
        path=CARGO/'registry/cache/index.crates.io-1949cf8c6b5b557f'/filename
        data=checked(path);assert hashlib.sha256(data).hexdigest()==locks[key]['checksum'],'Locked crate mismatch'
        write('cargo/registry/cache/index.crates.io-1949cf8c6b5b557f/'+filename,data,'Cargo.lock:'+locks[key]['checksum'])
    # Fresh bare object store: exact commits only, no inherited config/hooks/credentials.
    template=out/'empty-git-template';template.mkdir()
    db=out/'cargo/git/db/facet-7d985fba805ab53e';db.parent.mkdir(parents=True)
    subprocess.run(['git','init','--bare','--template='+str(template),str(db)],check=True,capture_output=True)
    for commit,repo in git_repos.items():
        subprocess.run(['git','-C',str(db),'-c','protocol.file.allow=always','fetch','--no-tags','--depth=1',str(repo),commit],check=True,capture_output=True)
        subprocess.run(['git','-C',str(db),'update-ref','refs/heads/locked-'+commit,commit],check=True,capture_output=True)
    subprocess.run(['git','-C',str(db),'config','remote.origin.url','https://github.com/TeamDman/facet.git'],check=True)
    # Normalize transport packing: semantic commits must produce a stable COPY key.
    subprocess.run(['git','-C',str(db),'-c','gc.writeCommitGraph=false','-c','repack.writeBitmaps=false','repack','-a','-d','-f','-F','--threads=1','--window=0','--depth=0'],check=True,capture_output=True)
    template.rmdir()
    (db/'FETCH_HEAD').unlink(missing_ok=True)
    for path in sorted(p for p in db.rglob('*') if p.is_file()):
        data=checked(path);rel=str(path.relative_to(out)).replace('\\','/');total+=len(data)
        assert total<=512*1024*1024
        receipt[rel]={'sha256':digest(data),'bytes':len(data),'parent':'Cargo.lock:exact-git-commits'}
    cli_identity=digest(json.dumps({rel:digest(data) for rel,data in sorted(sources.items()) if rel.startswith('platform/cli/sfm-propagate-changes/')},separators=(',',':')).encode())
    # Dependency-only source skeletons keep authored Rust out of these cache keys.
    for cli in ['sfm-propagate-changes','sfm']:
        folder=ROOT/'platform/cli'/cli
        manifest=tomllib.loads((folder/'Cargo.toml').read_text())
        for name in ['Cargo.toml','Cargo.lock']:
            write('deps/'+cli+'/'+name,checked(folder/name),'locked-dependency-declaration')
        write('deps/'+cli+'/build.rs',b'fn main() {}\n','dependency-skeleton')
        paths={'src/main.rs'}
        if (folder/'src/lib.rs').is_file() or 'lib' in manifest:paths.add('src/lib.rs')
        for kind in ['bin','test','bench','example']:
            paths.update(t['path'] for t in manifest.get(kind,[]) if 'path' in t)
        for path in sorted(paths):
            write('deps/'+cli+'/'+path,b'fn main() {}\n' if path!='src/lib.rs' else b'','dependency-skeleton')
    stages=[]
    for cli,label in [('sfm-propagate-changes','propagate'),('sfm','control')]:
        stages.append(f"""FROM {BASE} AS {label}-deps
SHELL ["/usr/bin/setpriv", "--no-new-privs", "/bin/sh", "-c"]
USER root
COPY --chown=10001:10001 cargo/ /opt/sfm/cargo/
COPY --chown=10001:10001 deps/{cli}/ /tooling-source/platform/cli/{cli}/
ENV CARGO_HOME=/opt/sfm/cargo CARGO_NET_OFFLINE=true CARGO_BUILD_JOBS=1 CARGO_TARGET_DIR=/tmp/{label}-target
USER 10001:10001
WORKDIR /tooling-source/platform/cli/{cli}
RUN test $(df -Pk / | tail -1 | awk '{{print $4}}') -gt 8388608 && cargo build --offline --locked --release --jobs 1 --bin {cli}
FROM {label}-deps AS {label}-binary
SHELL ["/usr/bin/setpriv", "--no-new-privs", "/bin/sh", "-c"]
COPY --chown=10001:10001 source/platform/cli/{cli}/ /tooling-source/platform/cli/{cli}/
RUN set -eu; grep -q '^NoNewPrivs:[[:space:]]*1$' /proc/self/status; find src -type f -exec touch {{}} +; touch build.rs; start=$(date +%s); cargo build --offline --locked --release --jobs 1 --bin {cli}; echo SFM_{label.upper()}_SECONDS=$(( $(date +%s) - start )); cat /sys/fs/cgroup/memory.peak /sys/fs/cgroup/memory.events
""")
    dockerfile=''.join(stages)+f"""FROM {BASE} AS tooling-runtime
COPY --from=propagate-binary /tmp/propagate-target/release/sfm-propagate-changes /opt/sfm/cargo/bin/sfm-propagate-changes
COPY --from=control-binary /tmp/control-target/release/sfm /opt/sfm/cargo/bin/sfm
ENV PATH=/opt/sfm/cargo/bin:/usr/local/cargo/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
USER 10001:10001
WORKDIR /tmp
ENTRYPOINT ["/bin/sh"]
"""
    write('Dockerfile',dockerfile.encode(),'digest-pinned-local-base')
    current={};[current.update(cli_inputs(cli)) for cli in ['sfm-propagate-changes','sfm']]
    assert current==sources,'Sources changed while preparing; keep this incomplete context unused'
    manifest={'base_image':BASE,'cli_source_identity':cli_identity,'source_files':{rel:digest(data) for rel,data in sorted(sources.items())},'bytes':total,'files':receipt,'context_prepared':True,'image_built':False,'gameplay_validated':False}
    (out/'tooling-context.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print(json.dumps({'context':str(out),'bytes':total,'files':len(receipt),'cli_source_identity':cli_identity,'base_image':BASE},indent=2))
except Exception:
    # Never erase partial output; no completion manifest means it cannot be admitted.
    raise
