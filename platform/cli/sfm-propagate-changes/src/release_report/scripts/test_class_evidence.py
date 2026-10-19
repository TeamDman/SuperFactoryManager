import unittest
import contextlib, importlib.util, io, json, os, shutil, subprocess, sys, tempfile, threading
from pathlib import Path
from types import SimpleNamespace
from unittest import mock
import prepare_class_evidence as tool


def fake_runner(mode=None, entered=None, release=None):
    state={'versions':0,'batches':[]}
    lock=threading.Lock()
    def run(args, **options):
        if '-version' in args:
            with lock: state['versions']+=1
            return SimpleNamespace(returncode=0,stdout=b'25.0.3\n',stderr=b'')
        with lock:
            first=not state['batches'];state['batches'].append(list(args))
        if first and entered is not None:
            entered.set()
            if not release.wait(5): raise TimeoutError('Synthetic producer release was not signalled')
        if mode=='interrupt': raise KeyboardInterrupt('Synthetic interrupted producer')
        if mode=='nonzero': return SimpleNamespace(returncode=1,stdout=b'partial',stderr=b'synthetic failure')
        jar=Path(args[args.index('-classpath')+1]); names=args[args.index('-l')+1:]
        chunks=[]
        import zipfile
        with zipfile.ZipFile(jar) as archive:
            members={name[:-6].replace('/','.'):name for name in archive.namelist() if name.endswith('.class')}
            for name in names:
                raw=archive.read(members[name]);debug=raw[-1] if raw.startswith(b'DEBUG') else 10
                literal='same' if raw.startswith(b'DEBUG') else raw.hex()
                chunks.append(f'Compiled from "A.java"\nclass {name} {{\n  void a();\n    Code:\n       0: ldc #12 // String {literal}\n    LineNumberTable:\n      line {debug}: 0\n}}\n')
        if mode=='missing': chunks=chunks[:-1]
        if mode=='extra': chunks.append('Compiled from "Extra.java"\nclass Extra {}\n')
        return SimpleNamespace(returncode=0,stdout=''.join(chunks).encode(),stderr=b'')
    run.state=state
    return run


@contextlib.contextmanager
def cache_fixture():
    parent=Path(os.environ.get('SFM_CLASS_FIXTURE_TEMP',tempfile.gettempdir()))
    parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=parent) as tmp:
        root=Path(tmp); sdk=root/'sdk'
        for name in ('bin','lib','conf'): (sdk/name).mkdir(parents=True,exist_ok=True)
        for name,raw in {'release':b'JAVA_VERSION="25.0.3"','bin/java.exe':b'java','bin/javac.exe':b'javac','bin/javap.exe':b'javap','lib/modules':b'modules','conf/jvm.cfg':b'cfg'}.items():
            (sdk/name).write_bytes(raw)
        jar=root/'baseline.jar';write_jar(jar,{'ca/A.class':b'DEBUG1'})
        yield dict(root=root,sdk=sdk,javap=sdk/'bin/javap.exe',jar=jar,cache=root/'cache')


def write_jar(path, members):
    import zipfile
    with zipfile.ZipFile(path,'w') as archive:
        for name,raw in sorted(members.items()): archive.writestr(name,raw)


def capture(fixture, destination, runner, **options):
    with tool.BaselineDisassemblyCache(fixture['cache'],fixture['sdk'],fixture['javap'],runner=runner,**options) as cache:
        with cache.hold_archive(fixture['jar']) as archive:
            names=tool._class_names(archive)
            texts=cache.disassemble(fixture['jar'],names,destination,archive=archive)
        return texts,cache.records[0]


def child_capture(source, root, destination):
    spec=importlib.util.spec_from_file_location('selected_class_capture',source)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    root=Path(root);runner=fake_runner()
    with module.BaselineDisassemblyCache(root/'cache',root/'sdk',root/'sdk/bin/javap.exe',runner=runner) as cache:
        with cache.hold_archive(root/'baseline.jar') as archive:
            cache.disassemble(root/'baseline.jar',module._class_names(archive),Path(destination),archive=archive)
        print(json.dumps(dict(record=cache.records[0],versions=runner.state['versions'],batches=len(runner.state['batches']),batch_args=runner.state['batches'])))

from prepare_class_evidence import classify,normalized,split_views

class EvidenceTests(unittest.TestCase):
    def test_cache_requires_exact_tool_identity(self):
        import tempfile,json,hashlib
        from pathlib import Path
        import prepare_class_evidence as tool
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);jar=root/'a.jar';javap=root/'javap.exe';cache=root/'cached.json'
            jar.write_bytes(b'jar');javap.write_bytes(b'pinned tool')
            cache.write_text(json.dumps(dict(jar_sha256=hashlib.sha256(b'jar').hexdigest(),tool_sha256='wrong',classes={})))
            previous=tool.JAVAP;tool.JAVAP=javap
            try:
                with self.assertRaisesRegex(AssertionError,'pinned javap identity'):
                    tool.disassemble(jar,[],cache)
            finally:tool.JAVAP=previous
    def test_added_removed_content_metadata_and_unrepresented(self):
        self.assertEqual(classify(b'',b'x','','new','','',None,1,False,True),'added')
        self.assertEqual(classify(b'x',b'','old','','','',1,None,True,False),'deleted')
        self.assertEqual(classify(b'x',b'y','old','new','','',1,1),'representation')
        self.assertEqual(classify(b'x',b'x','same','same','','',1,2),'zip-metadata')
        self.assertEqual(classify(b'x',b'x','same','same','','',1,1),'unchanged')
        self.assertEqual(classify(b'x',b'y','same','same','a','b',1,1),'debug-view')
        self.assertEqual(classify(b'x',b'y','same','same','a','a',1,1),'unrepresented-bytes')
    def test_pool_indices_do_not_erase_string_literals_or_bootstrap_indices(self):
        self.assertEqual(normalized('  0: ldc #12 // String #123\n'),'  0: ldc #CP // String #123\n')
        self.assertIn('InvokeDynamic #0:',normalized('  4: invokedynamic #120, 0 // InvokeDynamic #0:call\n'))
    def test_debug_tables_do_not_consume_next_method(self):
        text='class A {\n  void a();\n    Code:\n       0: return\n    LineNumberTable:\n      line 10: 0\n\n  void b();\n    Code:\n       0: return\n}\n'
        main,debug=split_views(text)
        self.assertIn('void b();',main);self.assertNotIn('line 10:',main)
        self.assertIn('line 10:',debug);self.assertNotIn('void b();',debug)


    @unittest.skipUnless(os.name=='nt','Selected retained authority is Windows-only')
    def test_baseline_cold_warm_exact_raw_and_input_invalidation(self):
        with cache_fixture() as f:
            cold=fake_runner();a,first=capture(f,f['root']/'cold.raw.json',cold)
            warm=fake_runner();b,second=capture(f,f['root']/'warm.raw.json',warm)
            self.assertEqual(a,b);self.assertEqual(first['key'],second['key'])
            self.assertEqual((first['outcome'],second['outcome']),('fresh','reused'))
            self.assertEqual((len(cold.state['batches']),len(warm.state['batches'])),(1,0))
            self.assertEqual((cold.state['versions'],warm.state['versions']),(1,1))
            self.assertEqual((f['root']/'cold.raw.json').read_bytes(),(f['root']/'warm.raw.json').read_bytes())
            for index,(path,raw) in enumerate(((f['jar'],None),(f['javap'],b'changed tool'),(f['sdk']/'lib/modules',b'changed modules'),(f['sdk']/'conf/jvm.cfg',b'changed cfg'),(f['sdk']/'bin/additional.dll',b'new selected member'))):
                if raw is None: write_jar(path,{'ca/A.class':b'changed','ca/B.class':b'new'})
                else: path.write_bytes(raw)
                run=fake_runner();_,record=capture(f,f['root']/f'changed-{index}.json',run)
                self.assertEqual(record['outcome'],'fresh');self.assertEqual(len(run.state['batches']),1)

    @unittest.skipUnless(os.name=='nt','Selected retained authority is Windows-only')
    def test_corrupt_incomplete_missing_extra_and_duplicate_outputs_miss(self):
        for damage in ('head','manifest','incomplete','missing','extra','duplicate','truncated'):
            with self.subTest(damage=damage),cache_fixture() as f:
                _,record=capture(f,f['root']/'first.json',fake_runner())
                key=f['cache']/record['key'];head=key/'current.json';head_bytes=head.read_bytes()
                entry=key/json.loads(head_bytes)['entry'];payload=entry/'raw.json';manifest=entry/'manifest.json'
                original=payload.read_bytes()
                if damage=='head': head.write_bytes(b'{')
                elif damage=='manifest': manifest.unlink()
                elif damage=='incomplete':
                    m=json.loads(manifest.read_bytes());m['complete']=False;manifest.write_bytes(tool._encode(m))
                else:
                    saved=json.loads(original)
                    if damage=='missing': saved['classes'].clear()
                    if damage=='extra': saved['classes']['ca/Extra.class']='extra'
                    raw=json.dumps(saved).encode()
                    if damage=='duplicate': raw=raw.replace(b'"classes": {',b'"classes": {"ca/A.class":"extra",',1)
                    if damage=='truncated': raw=raw[:-4]
                    payload.write_bytes(raw)
                    m=json.loads(manifest.read_bytes());m['payload_bytes']=len(raw);m['payload_sha256']=tool.sha(raw);manifest.write_bytes(tool._encode(m))
                run=fake_runner();_,next_record=capture(f,f['root']/'next.json',run)
                self.assertEqual(next_record['outcome'],'fresh');self.assertEqual(len(run.state['batches']),1)
                self.assertTrue(entry.exists());self.assertEqual((key/json.loads(head.read_bytes())['entry']/'raw.json').read_bytes(),original)
                self.assertTrue(any(p.name.startswith('previous-head-') for p in key.iterdir()))

    @unittest.skipUnless(os.name=='nt','Selected retained authority is Windows-only')
    def test_failed_interrupted_and_partial_producer_cannot_publish(self):
        for mode in ('nonzero','interrupt','missing','extra'):
            with self.subTest(mode=mode),cache_fixture() as f:
                with self.assertRaises((ValueError,KeyboardInterrupt)):
                    capture(f,f['root']/'failed.json',fake_runner(mode))
                self.assertFalse((f['root']/'failed.json').exists())
                self.assertFalse(list(f['cache'].glob('*/current.json')))
                self.assertTrue(list(f['cache'].glob('*/attempt-*.json')))
                _,record=capture(f,f['root']/'recovered.json',fake_runner())
                self.assertEqual(record['outcome'],'fresh')

    def test_vm_control_presence_declines_without_values_or_producer_effects(self):
        for name in tool.JAVA_CONTROLS:
            for value in ('','private fixture value'):
                with self.subTest(name=name,value_present=bool(value)),cache_fixture() as f,mock.patch.dict(os.environ,{name:value}):
                    runner=fake_runner()
                    with self.assertRaises(tool.CacheDeclined) as error:
                        tool.BaselineDisassemblyCache(f['cache'],f['sdk'],f['javap'],runner=runner)
                    self.assertNotIn(value or 'unprintable-empty-marker',str(error.exception))
                    self.assertFalse(f['cache'].exists());self.assertEqual(runner.state['versions'],0)

    @unittest.skipUnless(os.name=='nt','Selected retained authority is Windows-only')
    def test_environment_and_actual_loaded_capture_policy_change_key(self):
        with cache_fixture() as f:
            _,first=capture(f,f['root']/'one.json',fake_runner())
            with mock.patch.dict(os.environ,{'SFM_DISASSEMBLY_FIXTURE_VALUE':'secret fixture'}):
                _,changed=capture(f,f['root']/'two.json',fake_runner())
            self.assertNotEqual(first['key'],changed['key'])
            original=tool._produce_disassembly
            def wrapper(*args,**options): return original(*args,**options)
            with mock.patch.object(tool,'_produce_disassembly',wrapper):
                _,policy=capture(f,f['root']/'three.json',fake_runner())
            self.assertNotEqual(first['key'],policy['key'])
            self.assertNotIn('secret fixture',''.join(p.read_text() for p in f['cache'].glob('*/*.json')))

    @unittest.skipUnless(os.name=='nt','Selected retained authority is Windows-only')
    def test_same_key_waiter_coalesces_and_unrelated_key_is_independent(self):
        with cache_fixture() as f:
            entered=threading.Event();release=threading.Event();waited=threading.Event()
            runner=fake_runner(entered=entered,release=release);results=[];errors=[]
            def worker(name, fixture, on_wait=None):
                try: results.append(capture(fixture,f['root']/name,runner,on_wait=on_wait)[1])
                except BaseException as error: errors.append(error)
            threads=[threading.Thread(target=worker,args=('first.json',f))]
            threads[0].start()
            try:
                self.assertTrue(entered.wait(5))
                threads.append(threading.Thread(target=worker,args=('second.json',f,waited.set)));threads[-1].start()
                self.assertTrue(waited.wait(5),'Did not observe an actual OS lock wait')
                other=dict(f);other['jar']=f['root']/'other.jar';write_jar(other['jar'],{'ca/A.class':b'other'})
                independent=threading.Thread(target=worker,args=('other.json',other));threads.append(independent);independent.start();independent.join(5)
                self.assertFalse(independent.is_alive(),'Unrelated key waited for blocked producer')
            finally:
                release.set()
                for thread in threads: thread.join(5)
            self.assertFalse(any(thread.is_alive() for thread in threads));self.assertFalse(errors,repr(errors))
            same=[r for r in results if r['key']!=next(r['key'] for r in results if r['destination'].endswith('other.json'))]
            self.assertEqual(sorted(r['outcome'] for r in same),['fresh','reused'])
            self.assertEqual(len(runner.state['batches']),2)

    @unittest.skipUnless(os.name=='nt','Selected retained authority is Windows-only')
    def test_held_inputs_block_writers_and_unsafe_aliases_fail_before_batch(self):
        with cache_fixture() as f:
            runner=fake_runner()
            with tool.BaselineDisassemblyCache(f['cache'],f['sdk'],f['javap'],runner=runner) as cache:
                with self.assertRaises(OSError): f['sdk'].joinpath('lib/modules').write_bytes(b'changed')
                with self.assertRaises(OSError): f['sdk'].joinpath('bin').rename(f['sdk']/'moved')
                with cache.hold_archive(f['jar']):
                    with self.assertRaises(OSError): f['jar'].write_bytes(b'changed')
            os.link(f['jar'],f['root']/'alias.jar')
            with self.assertRaises(tool._authority_module().AuthorityError):
                capture(f,f['root']/'unsafe.json',runner)
            self.assertEqual(len(runner.state['batches']),0)
        with cache_fixture() as f:
            runner=fake_runner()
            with self.assertRaises(tool._authority_module().AuthorityError):
                tool.BaselineDisassemblyCache(f['root']/'..'/'escape',f['sdk'],f['javap'],runner=runner)
            self.assertEqual(runner.state['versions'],0)

    @unittest.skipUnless(os.name=='nt','Selected retained authority is Windows-only')
    def test_separate_processes_reuse_and_actual_helper_policy_change_misses(self):
        with cache_fixture() as f:
            source=f['root']/'selected-source';source.mkdir()
            for name in ('prepare_class_evidence.py','baseline_cache_authority.py'):
                shutil.copy2(Path(tool.__file__).with_name(name),source/name)
            def child(destination, optimized=False):
                command=[sys.executable,'-B']+(['-O'] if optimized else [])+[__file__,'--cache-child',str(source/'prepare_class_evidence.py'),str(f['root']),str(f['root']/destination)]
                run=subprocess.run(command,capture_output=True,text=True,timeout=30,env=dict(os.environ))
                self.assertEqual(run.returncode,0,run.stderr)
                return json.loads(run.stdout)
            first=child('first.json');second=child('second.json')
            self.assertEqual(first['record']['key'],second['record']['key'])
            self.assertEqual((first['batches'],second['batches']),(1,0))
            self.assertEqual((first['versions'],second['versions']),(1,1))
            helper=source/'baseline_cache_authority.py';helper.write_text(helper.read_text()+'\nSYNTHETIC_POLICY_REVISION = 2\n')
            changed=child('changed.json',optimized=True)
            self.assertNotEqual(first['record']['key'],changed['record']['key']);self.assertEqual(changed['batches'],1)
            self.assertEqual((f['root']/'first.json').read_bytes(),(f['root']/'changed.json').read_bytes())
            capture_source=source/'prepare_class_evidence.py'
            capture_source.write_text(capture_source.read_text().replace('-J-Xmx512m','-J-Xmx513m'))
            options=child('options.json')
            self.assertNotEqual(changed['record']['key'],options['record']['key'])
            self.assertEqual(options['batches'],1);self.assertIn('-J-Xmx513m',options['batch_args'][0])
            self.assertEqual((f['root']/'first.json').read_bytes(),(f['root']/'options.json').read_bytes())

    @unittest.skipUnless(os.name=='nt','Selected retained authority is Windows-only')
    def test_two_candidates_keep_final_evidence_equal_to_fresh_baseline(self):
        with cache_fixture() as f:
            candidate=f['root']/'candidate.jar';runner=fake_runner()
            def main_capture(label, members, cache_root):
                write_jar(candidate,members)
                manifest=f['root']/(label+'-input.json');manifest.write_text(json.dumps({'jars':[{'id':'pair','baseline_path':str(f['jar']),'candidate_path':str(candidate)}]}))
                output=f['root']/label;result=f['root']/(label+'-output.json')
                with mock.patch.multiple(tool,ROOT=f['root'],INPUT=manifest,OUT=output,OUTPUT_MANIFEST=result,SDK=f['sdk'],JAVAP=f['javap'],ASSET_PREFIX='classes',BASELINE_CACHE_ROOT=cache_root,SELECTED_SDK_ROOT=f['sdk']),mock.patch.object(tool.subprocess,'run',runner),contextlib.redirect_stdout(io.StringIO()):
                    tool.main()
                return (output/'pair.json').read_bytes(),json.loads((output/'baseline-cache.json').read_bytes())
            debug,first=main_capture('one',{'ca/A.class':b'DEBUG2'},f['cache'])
            meaningful,warm=main_capture('two',{'ca/A.class':b'other','ca/New.class':b'new'},f['cache'])
            equivalent,fresh=main_capture('three',{'ca/A.class':b'other','ca/New.class':b'new'},f['root']/'fresh-cache')
            self.assertEqual(meaningful,equivalent)
            self.assertEqual(json.loads(debug)['classes'][0]['status'],'debug-view')
            self.assertEqual({row['status'] for row in json.loads(meaningful)['classes']},{'representation','added'})
            self.assertEqual([first['observations'][0]['outcome'],warm['observations'][0]['outcome'],fresh['observations'][0]['outcome']],['fresh','reused','fresh'])
            self.assertEqual(sum('candidate.jar' in str(args) for args in runner.state['batches']),3)

    @unittest.skipUnless(os.name=='nt','Selected retained authority is Windows-only')
    def test_live_membership_change_and_unsafe_cached_payload_fail_before_batch(self):
        with cache_fixture() as f:
            runner=fake_runner()
            with tool.BaselineDisassemblyCache(f['cache'],f['sdk'],f['javap'],runner=runner) as cache:
                f['sdk'].joinpath('bin/late.dll').write_bytes(b'late selected member')
                with self.assertRaises(cache.authority_module.AuthorityError):
                    cache.disassemble(f['jar'],['ca/A.class'],f['root']/'invalid.json')
            self.assertEqual(len(runner.state['batches']),0)
            self.assertFalse(list(f['cache'].glob('*/current.json')))
        with cache_fixture() as f:
            _,record=capture(f,f['root']/'first.json',fake_runner())
            key=f['cache']/record['key'];entry=key/json.loads((key/'current.json').read_bytes())['entry']
            os.link(entry/'raw.json',f['root']/'cache-payload-alias')
            runner=fake_runner()
            with self.assertRaises(tool._authority_module().AuthorityError):
                capture(f,f['root']/'invalid.json',runner)
            self.assertEqual(len(runner.state['batches']),0)
            self.assertFalse(f['root'].joinpath('invalid.json').exists())

    @unittest.skipUnless(os.name=='nt','Selected retained authority is Windows-only')
    def test_requested_membership_and_legacy_raw_are_not_new_cache_authority(self):
        with cache_fixture() as f:
            runner=fake_runner()
            with tool.BaselineDisassemblyCache(f['cache'],f['sdk'],f['javap'],runner=runner) as cache:
                with self.assertRaises(ValueError):
                    cache.disassemble(f['jar'],['ca/Missing.class'],f['root']/'missing.json')
                legacy=f['root']/'legacy.json';legacy.write_text(json.dumps(dict(jar_sha256=tool.sha(f['jar'].read_bytes()),tool_sha256=tool.sha(f['javap'].read_bytes()),classes={})))
                with self.assertRaises(FileExistsError):
                    cache.disassemble(f['jar'],['ca/A.class'],legacy)
            self.assertEqual(len(runner.state['batches']),0)
            self.assertFalse(list(f['cache'].glob('*/current.json')))

    def test_strict_json_and_fresh_only_guards_survive_optimized_mode(self):
        def actual_code(value):
            return value in {'first', 'second'}
        before=tool._encode(tool._code_policy(actual_code.__code__))
        for _ in range(100): actual_code('first')
        self.assertEqual(before,tool._encode(tool._code_policy(actual_code.__code__)))
        self.assertEqual(tool._code_constant(frozenset(('first','second'))),
                         tool._code_constant(frozenset(('second','first'))))
        with self.assertRaises(ValueError): tool._strict_json(b'{"a":1,"a":2}',100)
        with cache_fixture() as f:
            destination=f['root']/'legacy.json';destination.write_text('{}')
            with self.assertRaises(FileExistsError): tool.disassemble(f['jar'],[],destination,fresh_only=True)
            with self.assertRaises(ValueError): tool._class_map({'ca/Extra.class':'extra'},['ca/A.class'])

if __name__=='__main__':
    if len(sys.argv)>1 and sys.argv[1]=='--cache-child': child_capture(*sys.argv[2:])
    else: unittest.main()

