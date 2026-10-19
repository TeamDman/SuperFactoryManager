"""Offline, hash-bound javap evidence from the ten preserved release JAR pairs."""
from pathlib import Path
import json, hashlib, zipfile, subprocess, re, shutil, datetime, difflib
import contextlib, os, platform, sys, time, types, uuid

ROOT = OUT = SDK = JAVAP = None
INPUT = OUTPUT_MANIFEST = ASSET_PREFIX = None
BASELINE_CACHE_ROOT = SELECTED_SDK_ROOT = None
_AUTHORITY = None
MAX_ARCHIVE = 512 * 1024**2
MAX_RAW = 128 * 1024**2
MAX_MANIFEST = 8 * 1024**2
JAVA_CONTROLS = ('_JAVA_OPTIONS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', 'JDK_ALTERNATE_VM')

def sha(data): return hashlib.sha256(data).hexdigest()

def normalized(text):
    # Constant-pool slot numbering is an encoding detail. Keep symbolic comments,
    # instruction offsets/jump targets, descriptors and every other printed line.
    lines=[]
    for line in text.replace('\r\n','\n').splitlines():
        if re.match(r'\s*\d+:\s+\S+',line):
            operand,sep,comment=line.partition('//')
            line=re.sub(r'#\d+','#CP',operand)+sep+comment
        lines.append(line)
    return '\n'.join(lines)+'\n'

def split_views(text):
    main, debug = [], []
    in_debug = False
    for line in normalized(text).splitlines():
        if re.match(r'\s+(LineNumberTable|LocalVariableTable|LocalVariableTypeTable):', line):
            in_debug = True
        elif in_debug and (not line.strip() or re.match(r'  \S', line) or line == '}'):
            in_debug = False
        (debug if in_debug else main).append(line)
    return '\n'.join(main).strip()+'\n', '\n'.join(debug).strip()+'\n'

def _produce_disassembly(jar, names, javap, environment, runner):
    texts, receipts = {}, []
    for start in range(0, len(names), 60):
        batch=names[start:start+60]
        args=[str(javap), '-J-Xmx512m', '-J-XX:ActiveProcessorCount=2', '-classpath',str(jar), '-c','-p','-s','-l'] + [n[:-6].replace('/','.') for n in batch]
        run=runner(args,capture_output=True,timeout=90,env=environment)
        if type(run.returncode) is not int or run.returncode != 0:
            raise ValueError('javap batch failed: '+run.stderr.decode(errors='replace'))
        if not isinstance(run.stdout, bytes) or not isinstance(run.stderr, bytes):
            raise ValueError('javap capture must contain raw stdout/stderr bytes')
        if len(run.stdout) > MAX_RAW or len(run.stderr) > MAX_RAW:
            raise ValueError('javap batch exceeded its output bound')
        text=run.stdout.decode('utf-8'); chunks=re.split(r'(?=^Compiled from ")',text,flags=re.M)
        chunks=[c for c in chunks if c.strip()]
        if len(chunks) != len(batch):
            raise ValueError('javap class chunk coverage differs from its requested batch')
        texts.update(zip(batch,chunks))
        receipts.append(dict(argv=args,requested=batch,exit_code=0,chunks=len(chunks),
                             stdout_bytes=len(run.stdout),stdout_sha256=sha(run.stdout),
                             stderr_bytes=len(run.stderr),stderr_sha256=sha(run.stderr)))
    return texts, receipts


def disassemble(jar, names, dest, *, baseline=False, archive=None, cache=None, fresh_only=False, environment=None):
    if baseline and cache is not None:
        return cache.disassemble(jar, names, dest, archive=archive)
    if fresh_only and dest.exists():
        raise FileExistsError('Fresh capture destination exists; choose a new checkpoint')
    if not fresh_only and dest.exists():
        saved=json.loads(dest.read_text())
        if saved['jar_sha256'] != sha(jar.read_bytes()):
            raise AssertionError('Cached disassembly JAR identity differs')
        if saved.get('tool_sha256') != sha(JAVAP.read_bytes()):
            raise AssertionError('Cached disassembly lacks matching pinned javap identity; choose a fresh output directory.')
        return saved['classes']
    texts, _ = _produce_disassembly(jar, names, JAVAP, environment, subprocess.run)
    if fresh_only:
        _class_map(texts,names)
    raw=json.dumps(dict(jar_sha256=sha(jar.read_bytes()),tool_sha256=sha(JAVAP.read_bytes()),classes=texts))
    if fresh_only:
        if len(raw.encode('utf-8')) > MAX_RAW:
            raise ValueError('Fresh candidate raw output exceeded its byte bound')
        _write_new(dest,raw.encode('utf-8'))
        if cache is not None and cache.authority.file(dest,MAX_RAW).digest != sha(raw.encode('utf-8')):
            raise ValueError('Fresh candidate raw output changed before retention')
    else:
        dest.write_text(raw)
    return texts


def _encode(value):
    return json.dumps(value,sort_keys=True,separators=(',', ':'),ensure_ascii=False).encode('utf-8')


def _code_constant(value):
    # marshal records interpreter reference/intern state as well as code. Use
    # explicit actual-code fields instead, independent of earlier executions.
    if value is None or value is Ellipsis:
        return [type(value).__name__]
    if type(value) in (bool, int, str):
        return [type(value).__name__, value]
    if type(value) is float:
        return ['float', value.hex()]
    if type(value) is complex:
        return ['complex', value.real.hex(), value.imag.hex()]
    if type(value) is bytes:
        return ['bytes', value.hex()]
    if type(value) is tuple:
        return ['tuple', [_code_constant(item) for item in value]]
    if type(value) is frozenset:
        return ['frozenset', sorted((_code_constant(item) for item in value), key=_encode)]
    if type(value) is types.CodeType:
        return ['code', _code_policy(value)]
    raise ValueError('Unsupported actual-code policy constant')


def _code_policy(code):
    return dict(argcount=code.co_argcount,posonlyargcount=code.co_posonlyargcount,
                kwonlyargcount=code.co_kwonlyargcount,nlocals=code.co_nlocals,
                stacksize=code.co_stacksize,flags=code.co_flags,code=code.co_code.hex(),
                constants=[_code_constant(value) for value in code.co_consts],
                names=code.co_names,varnames=code.co_varnames,freevars=code.co_freevars,
                cellvars=code.co_cellvars,filename=code.co_filename,name=code.co_name,
                qualname=code.co_qualname,firstlineno=code.co_firstlineno,
                linetable=code.co_linetable.hex(),exceptiontable=code.co_exceptiontable.hex())


def _strict_json(raw, bound):
    if len(raw) > bound:
        raise ValueError('Cache JSON exceeded its byte bound')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate cache JSON key')
            result[key] = value
        return result
    return json.loads(raw.decode('utf-8'),object_pairs_hook=unique)


def _class_names(archive):
    with zipfile.ZipFile(archive.stream) as source:
        names = sorted(name for name in source.namelist() if name.endswith('.class'))
    if len(names) > 100_000 or len(set(names)) != len(names):
        raise ValueError('Class membership is duplicate or exceeds its bound')
    for name in names:
        if (len(name) > 4096 or name.startswith('-') or '\\' in name or ':' in name or '\0' in name
                or any(part in ('', '.', '..') for part in name.split('/'))):
            raise ValueError('Unsafe class membership')
    return names


def _class_map(classes, names):
    if (type(classes) is not dict or len(classes) != len(names) or set(classes) != set(names)
            or any(type(name) is not str or type(text) is not str or not text for name,text in classes.items())):
        raise ValueError('Raw class map does not have complete exact string coverage')


def _write_new(path, raw):
    with Path(path).open('xb') as target:
        target.write(raw)
        target.flush()
        os.fsync(target.fileno())


def _authority_module():
    # Explicit sibling path works for the retained file-spec instrumentation
    # loader without importing a same-named module from the working directory.
    global _AUTHORITY
    if _AUTHORITY is None:
        path = Path(__file__).with_name('baseline_cache_authority.py')
        with path.open('rb') as source:
            raw = source.read(128*1024+1)
        if not 0 < len(raw) <= 128*1024:
            raise ValueError('Selected authority implementation exceeded its bound')
        module = types.ModuleType('_sfm_baseline_cache_authority')
        module.__file__ = str(path)
        # Hash and execute the same immutable source buffer. Never reread a
        # potentially different file to label already-loaded authority code.
        exec(compile(raw,str(path),'exec'),module.__dict__)
        module.executed_source_sha256 = sha(raw)
        _AUTHORITY = module
    return _AUTHORITY


class CacheDeclined(ValueError):
    pass


class BaselineDisassemblyCache:
    """Opt-in trusted-local Windows cache; never a native execution permit.

    The direct component API includes one selected-image admission and version
    query per context. Call disassemble with a fresh destination for each capture.
    """
    def __init__(self, root, sdk_root, javap, *, runner=None, on_wait=None):
        self.stack = contextlib.ExitStack()
        self.records = []
        self.invocation = uuid.uuid4().hex
        self.runner = runner or subprocess.run
        self.on_wait = on_wait
        self.environment = dict(os.environ)
        if any(name in self.environment for name in JAVA_CONTROLS):
            raise CacheDeclined('Baseline reuse declined inherited Java VM control presence')
        if len(self.environment) > 4096 or len(_encode(self.environment)) > 1024**2:
            raise ValueError('Baseline reuse environment exceeded its bound')
        self.environment_sha256 = sha(_encode(self.environment))
        self.authority_module = _authority_module()
        started = time.perf_counter()
        try:
            self.image = self.stack.enter_context(self.authority_module.SdkImage(Path(sdk_root), Path(javap)))
            self.javap = Path(javap)
            self.sdk_root = Path(sdk_root)
            self.sdk_admission_ms = (time.perf_counter() - started) * 1000
            # Owned cache/output directories allow child head replacement while
            # denying deletion of retained ancestry. SDK/archive owners keep1.
            self.authority = self.stack.enter_context(self.authority_module.HeldAuthority(directory_share=3))
            self.root = self.authority.directory(Path(root),create=True)
            query = self.runner([str(self.javap),'-version'],capture_output=True,timeout=30,env=self.environment)
            if (type(query.returncode) is not int or query.returncode != 0
                    or not isinstance(query.stdout, bytes) or not isinstance(query.stderr, bytes)
                    or len(query.stdout) > 65536 or len(query.stderr) > 65536):
                raise ValueError('Selected javap version query failed or exceeded its bound')
            self.version = query.stdout.decode('utf-8').strip()
            if not self.version:
                raise ValueError('Selected javap version query was empty')
            self.version_receipt = dict(stdout_sha256=sha(query.stdout),stderr_sha256=sha(query.stderr),exit_code=0)
            self._boundary()
        except self.authority_module.CapabilityUnavailable as error:
            self.stack.close()
            raise CacheDeclined('Baseline reuse requires unavailable Windows authority capability') from error
        except BaseException:
            self.stack.close()
            raise

    def __enter__(self):
        return self

    def __exit__(self, *error):
        self.stack.close()

    @contextlib.contextmanager
    def hold_archive(self, path):
        with self.authority_module.HeldAuthority() as owner:
            archive = owner.file(Path(path), MAX_ARCHIVE)
            yield archive
            archive.recheck()

    def _boundary(self, archive=None):
        if dict(os.environ) != self.environment:
            raise ValueError('Capture environment changed during retained reuse authority')
        self.image.recheck()
        self.authority.recheck()
        if archive is not None:
            archive.recheck()

    def _policy(self):
        digest = hashlib.sha256()
        digest.update(b'sfm:baseline-disassembly-policy@2')
        digest.update(sys.version.encode('utf-8'))
        digest.update(self.authority_module.executed_source_sha256.encode('ascii'))
        digest.update(_encode(dict(max_archive=MAX_ARCHIVE,max_raw=MAX_RAW,max_manifest=MAX_MANIFEST,
                                   inherited_java_controls=JAVA_CONTROLS)))
        for function in (sha, _authority_module, _encode, _code_constant, _code_policy, _write_new, _produce_disassembly,
                         _strict_json, _class_names, _class_map, normalized, split_views):
            digest.update(_encode(_code_policy(function.__code__)))
        # The helper's complete executed source buffer already binds all of its
        # methods, free functions and constants. Do not marshal fresh modules.
        for _, value in sorted(vars(type(self)).items()):
            value = getattr(value, '__wrapped__', value)
            if hasattr(value, '__code__'):
                digest.update(_encode(_code_policy(value.__code__)))
        return digest.hexdigest()

    def _descriptor(self, jar, archive, names):
        return dict(schema='sfm:baseline-disassembly-input@1',
                    baseline=dict(path=str(jar),bytes=archive.length,sha256=archive.digest),classes=names,
                    javap=dict(path=str(self.javap),sha256=self.image.javap_sha256,version=self.version,query=self.version_receipt),
                    sdk=dict(root=str(self.sdk_root),image=self.image.descriptor),
                    arguments=['-J-Xmx512m','-J-XX:ActiveProcessorCount=2','-classpath',str(jar),'-c','-p','-s','-l'],
                    batch_size=60,environment_sha256=self.environment_sha256,
                    platform=[platform.system(),platform.machine()],capture_parser_sha256=self._policy())

    def _read(self, path, bound):
        with self.authority_module.HeldAuthority() as owner:
            return owner.file(path,bound).read_bytes()

    def _lookup(self, directory, descriptor, names):
        pointer_raw = self._read(directory/'current.json',4096)
        pointer = _strict_json(pointer_raw,4096)
        if (type(pointer) is not dict or set(pointer) != {'schema','entry'}
                or pointer['schema'] != 'sfm:baseline-disassembly-head@1'
                or type(pointer['entry']) is not str or re.fullmatch(r'entry-[0-9a-f]{32}',pointer['entry']) is None):
            raise ValueError('Unsupported baseline cache head')
        entry = directory/pointer['entry']
        with self.authority_module.HeldAuthority() as owner:
            owner.directory(entry)
            raw_manifest = owner.file(entry/'manifest.json',MAX_MANIFEST).read_bytes()
            manifest = _strict_json(raw_manifest,MAX_MANIFEST)
            if (type(manifest) is not dict or set(manifest) != {'schema','descriptor','production','complete','classes','payload_bytes','payload_sha256','batches'}
                    or manifest['schema'] != 'sfm:baseline-disassembly-complete@1' or manifest['complete'] is not True
                    or manifest['descriptor'] != descriptor or raw_manifest != _encode(manifest)
                    or type(manifest['production']) is not str or re.fullmatch(r'[0-9a-f]{32}',manifest['production']) is None
                    or type(manifest['classes']) is not int or manifest['classes'] != len(names)
                    or type(manifest['payload_bytes']) is not int or not 0 < manifest['payload_bytes'] <= MAX_RAW
                    or type(manifest['payload_sha256']) is not str or re.fullmatch(r'[0-9a-f]{64}',manifest['payload_sha256']) is None):
                raise ValueError('Baseline completion manifest does not match its exact input')
            self._validate_batches(manifest['batches'],descriptor,names)
            raw = owner.file(entry/'raw.json',MAX_RAW).read_bytes()
            if len(raw) != manifest['payload_bytes'] or sha(raw) != manifest['payload_sha256']:
                raise ValueError('Baseline raw output bytes differ from completion')
            saved = _strict_json(raw,MAX_RAW)
            if (type(saved) is not dict or set(saved) != {'jar_sha256','tool_sha256','classes'}
                    or saved['jar_sha256'] != descriptor['baseline']['sha256']
                    or saved['tool_sha256'] != descriptor['javap']['sha256']):
                raise ValueError('Baseline raw output input binding differs')
            _class_map(saved['classes'],names)
            owner.recheck()
            return raw, saved['classes'], manifest

    def _validate_batches(self, receipts, descriptor, names):
        if type(receipts) is not list or len(receipts) != (len(names)+59)//60:
            raise ValueError('Incomplete baseline batch receipts')
        for index, receipt in enumerate(receipts):
            batch = names[index*60:index*60+60]
            argv = [descriptor['javap']['path']] + descriptor['arguments'] + [name[:-6].replace('/','.') for name in batch]
            if (type(receipt) is not dict or set(receipt) != {'argv','requested','exit_code','chunks','stdout_bytes','stdout_sha256','stderr_bytes','stderr_sha256'}
                    or receipt['argv'] != argv or receipt['requested'] != batch
                    or type(receipt['exit_code']) is not int or receipt['exit_code'] != 0
                    or type(receipt['chunks']) is not int or receipt['chunks'] != len(batch)):
                raise ValueError('Baseline batch receipt lost exact arguments or coverage')
            for prefix in ('stdout','stderr'):
                if (type(receipt[prefix+'_bytes']) is not int or not 0 <= receipt[prefix+'_bytes'] <= MAX_RAW
                        or type(receipt[prefix+'_sha256']) is not str or re.fullmatch(r'[0-9a-f]{64}',receipt[prefix+'_sha256']) is None):
                    raise ValueError('Invalid raw subprocess output identity')

    def _publish(self, directory, descriptor, raw, names, batches, production, archive):
        entry = self.authority.directory(directory/('entry-'+production),create=True)
        _write_new(entry/'raw.json',raw)
        manifest = dict(schema='sfm:baseline-disassembly-complete@1',descriptor=descriptor,
                        production=production,complete=True,classes=len(names),payload_bytes=len(raw),
                        payload_sha256=sha(raw),batches=batches)
        self._validate_batches(batches,descriptor,names)
        encoded_manifest = _encode(manifest)
        if len(encoded_manifest) > MAX_MANIFEST:
            raise ValueError('Baseline completion manifest exceeded its byte bound')
        _write_new(entry/'manifest.json',encoded_manifest)
        # Reopen actual immutable output bytes under held no-follow authority.
        if self._read(entry/'raw.json',MAX_RAW) != raw:
            raise ValueError('Baseline publication copy verification failed')
        current = directory/'current.json'
        try:
            previous = self._read(current,4096)  # Reject linked/aliased heads.
        except FileNotFoundError:
            pass
        else:
            _write_new(directory/('previous-head-'+uuid.uuid4().hex+'.bin'),previous)
        head = directory/('head-'+uuid.uuid4().hex+'.tmp')
        _write_new(head,_encode(dict(schema='sfm:baseline-disassembly-head@1',entry=entry.name)))
        self._boundary(archive)
        os.replace(head,current)
        return manifest

    def disassemble(self, jar, names, dest, *, archive=None):
        jar, dest = Path(jar), Path(dest)
        if archive is None:
            with self.hold_archive(jar) as held:
                return self.disassemble(jar,names,dest,archive=held)
        if jar != archive.path or names != _class_names(archive):
            raise ValueError('Requested classes differ from the actual held baseline archive')
        self._boundary(archive)
        self.authority.directory(dest.parent,create=True)
        if dest.exists():
            raise FileExistsError('Baseline destination exists; legacy raw JSON is not cache authority')
        descriptor = self._descriptor(jar,archive,names)
        encoded_descriptor = _encode(descriptor)
        if len(encoded_descriptor) > MAX_MANIFEST:
            raise ValueError('Baseline input descriptor exceeded its byte bound')
        directory = self.authority.directory(self.root/sha(encoded_descriptor),create=True)
        started = time.perf_counter()
        with self.authority.exclusive(directory/'producer.lock',on_wait=self.on_wait):
            self._boundary(archive)
            miss = None
            try:
                raw, texts, manifest = self._lookup(directory,descriptor,names)
                outcome, fresh_batches = 'reused', 0
            except (FileNotFoundError, ValueError) as error:
                miss = type(error).__name__
                production = uuid.uuid4().hex
                _write_new(directory/('attempt-'+production+'.json'),_encode(dict(
                    schema='sfm:baseline-disassembly-attempt@1',production=production,descriptor_sha256=sha(_encode(descriptor)))))
                texts, batches = _produce_disassembly(jar,names,self.javap,self.environment,self.runner)
                _class_map(texts,names)
                raw = json.dumps(dict(jar_sha256=archive.digest,tool_sha256=self.image.javap_sha256,classes=texts)).encode('utf-8')
                if len(raw) > MAX_RAW:
                    raise ValueError('Raw baseline output exceeded its bound')
                self._boundary(archive)
                manifest = self._publish(directory,descriptor,raw,names,batches,production,archive)
                outcome, fresh_batches = 'fresh', len(batches)
            self._boundary(archive)
            _write_new(dest,raw)
            if self.authority.file(dest,MAX_RAW).digest != sha(raw):
                raise ValueError('Consumed baseline raw output changed before retention')
            self._boundary(archive)
            record = dict(schema='sfm:baseline-disassembly-consumption@1',invocation=self.invocation,
                          production=manifest['production'],key=directory.name,descriptor_sha256=sha(_encode(descriptor)),
                          destination=str(dest),raw_sha256=sha(raw),raw_bytes=len(raw),classes=len(names),
                          outcome=outcome,fresh_batches=fresh_batches,miss=miss,
                          elapsed_ms=(time.perf_counter()-started)*1000)
            _write_new(directory/('consumption-'+uuid.uuid4().hex+'.json'),_encode(record))
            self.records.append(record)
            return texts

def counts(a,b):
    add=delete=0
    for op, i,j,k,l in difflib.SequenceMatcher(None,a.splitlines(),b.splitlines(),autojunk=False).get_opcodes():
        if op in ('replace','delete'):delete+=j-i
        if op in ('replace','insert'):add+=l-k
    return add,delete

def zip_metadata(info):
    return (info.date_time,info.compress_type,info.compress_size,info.create_system,
            info.create_version,info.extract_version,info.flag_bits,info.volume,
            info.internal_attr,info.external_attr,info.extra,info.comment)

def classify(ba,bb,ma,mb,ga,gb,meta_a,meta_b,has_a=True,has_b=True):
    if not has_a:return 'added'
    if not has_b:return 'deleted'
    if ba==bb:return 'unchanged' if meta_a==meta_b else 'zip-metadata'
    if ma!=mb:return 'representation'
    if ga!=gb:return 'debug-view'
    return 'unrepresented-bytes'

def main():
    if shutil.disk_usage(ROOT).free <= 12*1024**3:
        raise ValueError('Insufficient capture workspace space')
    if OUTPUT_MANIFEST.exists():
        raise FileExistsError('Output manifest exists; choose a new checkpoint')
    with INPUT.open('rb') as source:
        document=source.read(32*1024**2+1)
    doc=_strict_json(document,32*1024**2)
    OUT.mkdir(exist_ok=BASELINE_CACHE_ROOT is None)
    environment=dict(os.environ)
    with contextlib.ExitStack() as lifetime:
        cache=None
        declined=None
        if BASELINE_CACHE_ROOT is not None:
            try:
                cache=lifetime.enter_context(BaselineDisassemblyCache(BASELINE_CACHE_ROOT,SELECTED_SDK_ROOT,JAVAP))
            except CacheDeclined as error:
                declined=type(error).__name__
        version=cache.version if cache else subprocess.check_output([str(JAVAP),'-version'],text=True,env=environment).strip()
        tool=dict(name='javap',version=version,
                  executable_sha256=cache.image.javap_sha256 if cache else sha(JAVAP.read_bytes()),
                  sdk_release_sha256=cache.image.release_sha256 if cache else sha((SDK/'release').read_bytes()),
                  options='-c -p -s -l; -J-Xmx512m -J-XX:ActiveProcessorCount=2',
                  normalization='CRLF to LF; constant-pool #numbers in instruction operands only replaced with #CP. Comments/string literals, bootstrap indices and offsets retained. Debug tables separated.',
                  metric='Representation-line insertions/deletions, not semantic impact. Python SequenceMatcher(auto-junk disabled) preparation counts; rendered Histogram counts are authoritative.')
        summary=[]
        for pair in doc['jars']:
            if BASELINE_CACHE_ROOT is not None and (type(pair['id']) is not str or re.fullmatch(r'[A-Za-z0-9_-]{1,128}',pair['id']) is None):
                raise ValueError('Unsafe selected pair ID')
            a,b=Path(pair['baseline_path']),Path(pair['candidate_path'])
            with contextlib.ExitStack() as pair_lifetime:
                held_a=pair_lifetime.enter_context(cache.hold_archive(a)) if cache else None
                held_b=pair_lifetime.enter_context(cache.hold_archive(b)) if cache else None
                za=pair_lifetime.enter_context(zipfile.ZipFile(held_a.stream if held_a else a))
                zb=pair_lifetime.enter_context(zipfile.ZipFile(held_b.stream if held_b else b))
                namesa=sorted(n for n in za.namelist() if n.endswith('.class'));namesb=sorted(n for n in zb.namelist() if n.endswith('.class'))
                da=disassemble(a,namesa,OUT/(pair['id']+'-before.raw.json'),baseline=True,archive=held_a,cache=cache,fresh_only=BASELINE_CACHE_ROOT is not None,environment=environment)
                db=disassemble(b,namesb,OUT/(pair['id']+'-after.raw.json'),archive=held_b,cache=cache,fresh_only=BASELINE_CACHE_ROOT is not None,environment=environment)
                rows=[]
                for name in sorted(set(namesa)|set(namesb)):
                    ba=za.read(name) if name in da else b'';bb=zb.read(name) if name in db else b''
                    ma,ga=split_views(da.get(name,''));mb,gb=split_views(db.get(name,''))
                    state=classify(ba,bb,ma,mb,ga,gb,zip_metadata(za.getinfo(name)) if name in da else None,zip_metadata(zb.getinfo(name)) if name in db else None,name in da,name in db)
                    add,delete=counts(ma,mb)
                    row=dict(path=name,status=state,added=add,removed=delete,before=ma if ma!=mb else '',after=mb if ma!=mb else '',
                             debug_before=ga if ga!=gb else '',debug_after=gb if ga!=gb else '',
                             before_sha256=sha(ba) if ba else '',after_sha256=sha(bb) if bb else '')
                    rows.append(row)
                if cache:
                    cache._boundary(held_a);cache._boundary(held_b)
                evidence=dict(schema='sfm:jar-class-evidence@1',projection=pair['id'],baseline_sha256='sha256:'+(held_a.digest if held_a else sha(a.read_bytes())),candidate_sha256='sha256:'+(held_b.digest if held_b else sha(b.read_bytes())),tool=json.dumps(tool),classes=rows)
                path=OUT/(pair['id']+'.json')
                if BASELINE_CACHE_ROOT is not None:
                    encoded_evidence=json.dumps(evidence).encode('utf-8')
                    if len(encoded_evidence) > MAX_RAW:
                        raise ValueError('Class evidence exceeded its byte bound')
                    _write_new(path,encoded_evidence)
                    if cache:
                        cache.authority.file(path,MAX_RAW)
                else:
                    path.write_text(json.dumps(evidence))
                pair['class_evidence_path']=ASSET_PREFIX.rstrip('/')+'/'+path.name
                pair['class_evidence_sha256']='sha256:'+sha(path.read_bytes())
                tallies={s:sum(r['status']==s for r in rows) for s in sorted(set(r['status'] for r in rows))}
                reps=[{k:r[k] for k in ('path','status','added','removed')} for r in rows if 'OutputStatement.class' in r['path'] or 'SFMMod.class' in r['path']]
                line=dict(projection=pair['id'],counts=tallies,representative=reps,evidence_bytes=path.stat().st_size)
                summary.append(line);print(json.dumps(line),flush=True)
        doc['generated_utc']=datetime.datetime.now(datetime.timezone.utc).isoformat()
        if BASELINE_CACHE_ROOT is not None:
            _write_new(OUT/'summary.json',json.dumps(dict(tool=tool,projections=summary),indent=2).encode('utf-8'))
        else:
            OUTPUT_MANIFEST.write_text(json.dumps(doc,indent=2))
            (OUT/'summary.json').write_text(json.dumps(dict(tool=tool,projections=summary),indent=2))
        if BASELINE_CACHE_ROOT is not None:
            _write_new(OUT/'baseline-cache.json',_encode(dict(schema='sfm:baseline-disassembly-capture@1',
                       declined=declined,sdk_admission_ms=cache.sdk_admission_ms if cache else None,
                       observations=cache.records if cache else [])))
            if cache:
                cache._boundary()
            _write_new(OUTPUT_MANIFEST,json.dumps(doc,indent=2).encode('utf-8'))

if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--output-manifest',type=Path,required=True)
    p.add_argument('--asset-prefix',required=True)
    p.add_argument('--javap',type=Path,required=True)
    p.add_argument('--baseline-cache-root',type=Path)
    p.add_argument('--sdk-root',type=Path)
    opts=p.parse_args()
    if (opts.baseline_cache_root is None) != (opts.sdk_root is None):
        p.error('--baseline-cache-root and --sdk-root must be supplied together')
    INPUT=opts.manifest;ROOT=INPUT.parent;OUT=opts.output;OUTPUT_MANIFEST=opts.output_manifest
    ASSET_PREFIX=opts.asset_prefix;JAVAP=opts.javap;SDK=JAVAP.parent.parent
    BASELINE_CACHE_ROOT=opts.baseline_cache_root;SELECTED_SDK_ROOT=opts.sdk_root
    if INPUT.stat().st_size > 32*1024**2:
        raise ValueError('Input manifest exceeded its byte bound')
    if OUTPUT_MANIFEST.exists():
        raise FileExistsError('Output manifest already exists; choose a fresh checkpoint.')
    main()
