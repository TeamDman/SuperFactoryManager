"""Synthetic failure-boundary and pinned-data tests; no game or engine required."""
from pathlib import Path
import copy, tempfile, unittest
from config_seed import DATA, validate, verify_initial, inspect_after, identity

class ConfigSeedTests(unittest.TestCase):
    def test_exact_reviewed_seed_and_private_scan(self):
        files=validate(DATA,DATA['projection'],DATA['source_lock_sha256'])
        self.assertEqual(len(files),145)
        self.assertEqual(sum(map(len,files.values())),297390)

    def test_projection_and_lock_mismatch_fail_closed(self):
        for projection,lock in [('sfm-dev/mc-1.21.1',DATA['source_lock_sha256']),(DATA['projection'],'sha256:bad')]:
            with self.assertRaises(ValueError): validate(DATA,projection,lock)

    def test_path_scope_hash_and_secret_failures(self):
        import base64,hashlib
        for mutation in ('path','scope','hash','secret'):
            data=copy.deepcopy(DATA);name=next(iter(data['files']));row=data['files'][name]
            if mutation=='path':data['files']['config/../private.toml']=data['files'].pop(name)
            if mutation=='scope':row['kind']='server'
            if mutation=='hash':row['sha256']='0'*64
            if mutation=='secret':
                raw=b'apiKey="synthetic-secret"\n';row.update(base64=base64.b64encode(raw).decode(),bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
            with self.assertRaises(ValueError):validate(data,data['projection'],data['source_lock_sha256'])

    def test_identity_changes_for_payload_and_mod_pins(self):
        original=identity(DATA)
        for field in ('files','artifacts'):
            data=copy.deepcopy(DATA)
            if field=='files':data[field][next(iter(data[field]))]['sha256']='0'*64
            else:data[field][next(iter(data[field]))]='sha256:changed'
            self.assertNotEqual(identity(data),original)

    def test_staged_files_and_world_defaults_are_exact(self):
        with tempfile.TemporaryDirectory() as directory:
            game=Path(directory);files=validate(DATA,DATA['projection'],DATA['source_lock_sha256'])
            for name,raw in files.items():
                p=game/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
                if name.startswith('defaultconfigs/'):
                    p=game/'saves/test/serverconfig'/name.split('/',1)[1];p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
            manifest={'projection':DATA['projection'],'config_seed':{'schema':DATA['schema'],'identity':identity(DATA),'source_lock_sha256':DATA['source_lock_sha256'],'modset_sha256':identity(DATA['artifacts'])}}
            self.assertEqual(verify_initial(manifest,game),files)
            self.assertTrue(inspect_after(game,files)['byte_identical'])
            p=game/next(name for name in files if name.startswith('config/'));p.write_bytes(b'changed=true\n')
            with self.assertRaises(ValueError):verify_initial(manifest,game)
            self.assertFalse(inspect_after(game,files)['byte_identical'])

    def test_runtime_binding_rejects_stale_seed(self):
        with self.assertRaises(ValueError):verify_initial({'config_seed':{}},Path('unused'))

if __name__=='__main__':unittest.main()
