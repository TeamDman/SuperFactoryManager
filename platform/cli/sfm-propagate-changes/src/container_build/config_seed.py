"""Pinned offline defaults; never reads personal configs or imports saved worlds."""
from pathlib import Path, PurePosixPath
import argparse, base64, hashlib, json, re, tomllib
from config_seed_data import DATA

def digest(raw):
    return hashlib.sha256(raw).hexdigest()

def identity(data):
    return digest(json.dumps(data, sort_keys=True, separators=(',', ':')).encode())

def validate(data, projection, source_lock):
    if data['schema'] != 'sfm:pinned_config_seed@1' or data['projection'] != projection or data['source_lock_sha256'] != source_lock:
        raise ValueError('No validated config seed for this projection/source lock; regenerate and review pinned defaults')
    if data['minecraft_version'] != '1.19.2' or len(data['files']) != 145:
        raise ValueError('Config seed schema/version/file set mismatch')
    result = {}
    def check_values(value, key=''):
        if isinstance(value, dict):
            for k, v in value.items(): check_values(v, key+'.'+k)
        elif isinstance(value, list):
            for v in value: check_values(v, key)
        elif isinstance(value, str):
            if re.search(r'(?i)((?<![A-Z0-9])[A-Z]:[\\/]|/home/|/Users/|/root/|\\\\|bearer\s|BEGIN.*PRIVATE KEY)', value):
                raise ValueError('Private or machine-specific config value: '+key)
            if value and re.search(r'(?i)(password|credential|api.?key|access.?token|secret.?key|username|account.?id|player.?uuid|azure|discord|tailscale)', key):
                raise ValueError('Nonempty personal/credential config field: '+key)
    for name, row in sorted(data['files'].items()):
        path = PurePosixPath(name)
        if name != str(path) or '\\' in name or path.is_absolute() or '..' in path.parts or len(path.parts)<2 or path.parts[0] not in ('config','defaultconfigs') or path.suffix!='.toml':
            raise ValueError('Unsafe config seed path: '+name)
        if row['kind'] != ('server' if path.parts[0]=='defaultconfigs' else 'client_or_common'):
            raise ValueError('Config scope mismatch: '+name)
        raw = base64.b64decode(row['base64'], validate=True)
        if len(raw)!=row['bytes'] or len(raw)>524288 or digest(raw)!=row['sha256']:
            raise ValueError('Config seed hash/size mismatch: '+name)
        check_values(tomllib.loads(raw.decode()))
        result[name] = raw
    if sum(map(len,result.values()))>4*1024**2:
        raise ValueError('Config seed exceeds byte budget')
    return result

def checked(root, relative):
    path=root/relative
    if not path.is_relative_to(root): raise ValueError('Path escaped root')
    for p in (path,*path.parents):
        if p.is_symlink(): raise ValueError('Config seed refuses symlinks')
        if p==root: break
    return path

def prepare(manifest_path, destination):
    manifest=json.loads(manifest_path.read_text())
    lock=Path('/work/platform/minecraft/projections')/manifest['projection']/'sfm-toolchain.lock.json'
    source_lock='sha256:'+digest(lock.read_bytes())
    files=validate(DATA,manifest['projection'],source_lock)
    # Bind all pinned Maven jar inputs, including the complete third-party mod graph.
    for name,expected in DATA['artifacts'].items():
        artifact=checked(Path('/work/.sfm-cache'),name)
        if 'sha256:'+digest(artifact.read_bytes())!=expected:
            raise ValueError('Pinned config dependency changed: '+name)
    game=Path(manifest['game_directory'])
    if game != Path('/work/platform/minecraft/projections')/DATA['projection']/'runGameTestPreview':
        raise ValueError('Unexpected game directory')
    destination.mkdir(exist_ok=False)
    for name,raw in files.items():
        target=checked(destination,str(game.relative_to('/work')/name))
        target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
    manifest['config_seed']={'schema':DATA['schema'],'identity':identity(DATA),'source_lock_sha256':source_lock,'modset_sha256':identity(DATA['artifacts'])}
    (destination/'runtime-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps({'config_seed':manifest['config_seed'],'files':len(files),'bytes':sum(map(len,files.values()))}))

def verify_initial(manifest, game):
    binding=manifest.get('config_seed')
    expected={'schema':DATA['schema'],'identity':identity(DATA),'source_lock_sha256':DATA['source_lock_sha256'],'modset_sha256':identity(DATA['artifacts'])}
    if binding != expected: raise ValueError('Runtime config seed provenance mismatch')
    files=validate(DATA,manifest['projection'],binding['source_lock_sha256'])
    for name,raw in files.items():
        if checked(game,name).read_bytes()!=raw: raise ValueError('Staged config changed before Java: '+name)
    return files

def inspect_after(game, files):
    rows={}; missing=[];changed=[]
    for name,raw in files.items():
        if name.startswith('defaultconfigs/'):
            candidates=list((game/'saves').glob('*/serverconfig/'+name.split('/',1)[1]))
            if len(candidates)!=1:missing.append(name);continue
            path=candidates[0]
            if path.is_symlink():raise ValueError('Server config symlink refused')
        else: path=checked(game,name)
        if not path.is_file():missing.append(name);continue
        if path.stat().st_size>524288: raise ValueError('Config exceeds receipt budget')
        actual=path.read_bytes();rows[name]={'bytes':len(actual),'sha256':digest(actual)}
        if actual!=raw:changed.append(name)
    return {'schema':'sfm:config_seed_acceptance@1','identity':identity(DATA),'files':rows,'missing':missing,'changed':changed,'byte_identical':not missing and not changed}

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('--manifest',type=Path,required=True);ap.add_argument('--destination',type=Path,required=True);args=ap.parse_args()
    prepare(args.manifest,args.destination)
