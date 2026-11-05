"""Pinned JAR preparation: real filesystem guards, no external acquisition."""
import contextlib
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import ssl
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import cloud_junit_cache as J
C = J.checks


def pin(number=0):
    data = f'locked artifact {number}'.encode()
    coordinate = f'example.group:artifact-{number}:1.0'
    digest = hashlib.sha256(data).hexdigest()
    return {'coordinate': coordinate, 'content_hash': 'blake3:' + digest[:40],
            'full_sha256': digest, 'bytes': len(data), 'authority': 'fixture',
            'relative': J.maven_path(coordinate).as_posix(),
            'url': 'https://repo.maven.apache.org/maven2/' + J.maven_path(coordinate).as_posix()}, data


def hash_cli(cli, arguments, environment, allowed=(0,)):
    # A deterministic stand-in for the separately guarded native BLAKE3 CLI.
    # Filesystem/atomicity tests use actual descriptors, locks and renameat2.
    if arguments[:2] != ['hash', 'file']:
        raise AssertionError(arguments)
    data = Path(arguments[-1]).read_bytes()
    return {'schema': 'sfm:file_hash@1', 'algorithm': 'blake3', 'digest_bytes': 20,
            'identity': 'blake3:' + hashlib.sha256(data).hexdigest()[:40],
            'bytes_hashed': len(data), 'writes_performed': False}


def native_fixture(path):
    """Test-only marker fixture; production never creates/adopts this marker."""
    path.mkdir()
    (path / '.sfm-native-cache-root.json').write_text(json.dumps(
        ['sfm:native_cache_root@1', str(path)], separators=(',', ':')))


class CacheFilesystemTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name)
        self.root = J.CacheRoot(self.path); self.addCleanup(self.root.close)
        self.pin, self.data = pin()
        self.hasher = patch.object(J, 'cli_json', side_effect=hash_cli)
        self.hasher.start(); self.addCleanup(self.hasher.stop)

    def test_create_only_reuse_never_rewrites_matching_bytes_or_metadata(self):
        metadata = self.path / 'maven-metadata-pins.json'; metadata.write_bytes(b'unchanged metadata')
        self.assertEqual(J.publish(self.root, self.pin, self.data, '/cli', {})[0], 'created')
        artifact = self.path / J.artifact_relative(self.pin)
        identity = artifact.stat(); self.assertEqual(identity.st_nlink, 1)
        self.assertEqual(J.publish(self.root, self.pin, self.data, '/cli', {})[0], 'reused')
        self.assertEqual((artifact.stat().st_ino, artifact.stat().st_mtime_ns),
                         (identity.st_ino, identity.st_mtime_ns))
        self.assertEqual(metadata.read_bytes(), b'unchanged metadata')
        self.assertFalse(list(artifact.parent.glob('*.part')))
        artifact.write_bytes(b'corrupt existing bytes')
        with self.assertRaisesRegex(ValueError, 'length|SHA-256'):
            J.publish(self.root, self.pin, self.data, '/cli', {})
        self.assertEqual(artifact.read_bytes(), b'corrupt existing bytes')

    def test_wrong_download_never_becomes_visible(self):
        bad = dict(self.pin, bytes=None, full_sha256=None)
        with self.assertRaisesRegex(ValueError, 'BLAKE3'):
            J.publish(self.root, bad, b'not the locked JAR', '/cli', {})
        artifact = self.path / J.artifact_relative(self.pin)
        self.assertFalse(artifact.exists()); self.assertFalse(list(artifact.parent.glob('*.part')))

    def test_path_swap_cannot_substitute_good_bytes_for_the_held_bad_descriptor(self):
        artifact = self.path / 'shared.jar'
        artifact.write_bytes(b'wrong artifact bytes')
        retained = self.path / 'retained.jar'
        expected = dict(self.pin, full_sha256=None, bytes=None)
        def swapped(cli, arguments, environment, allowed=(0,)):
            artifact.rename(retained); artifact.write_bytes(self.data)
            try: return hash_cli(cli, arguments, environment, allowed)
            finally:
                artifact.unlink(); retained.rename(artifact)
        with artifact.open('rb') as source, patch.object(J, 'cli_json', side_effect=swapped):
            with self.assertRaisesRegex(ValueError, 'BLAKE3'):
                J.verify(artifact, source.fileno(), expected, '/cli', {})
        self.assertEqual(artifact.read_bytes(), b'wrong artifact bytes')

    def test_atomic_no_replace_preserves_an_uncooperative_writer(self):
        actual = J.rename_new
        def race(parent, source, destination):
            fd = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600, dir_fd=parent)
            os.write(fd, b'concurrent bytes'); os.close(fd)
            return actual(parent, source, destination)
        with patch.object(J, 'rename_new', side_effect=race):
            with self.assertRaises(FileExistsError):J.publish(self.root, self.pin, self.data, '/cli', {})
        self.assertEqual((self.path / J.artifact_relative(self.pin)).read_bytes(), b'concurrent bytes')

    def test_leaf_hardlinks_symlinks_and_linked_directories_are_refused(self):
        relative = J.artifact_relative(self.pin); artifact = self.path / relative
        artifact.parent.mkdir(parents=True)
        outside = self.path / 'outside'; outside.write_bytes(self.data)
        artifact.symlink_to(outside)
        with self.assertRaises(OSError):J.existing(self.root, self.pin, '/cli', {})
        artifact.unlink(); os.link(outside, artifact)
        with self.assertRaisesRegex(ValueError, 'single-linked'):J.existing(self.root, self.pin, '/cli', {})
        artifact.unlink()
        linked = self.path / 'linked'; linked.symlink_to(artifact.parent, target_is_directory=True)
        with self.assertRaises(OSError):
            with self.root.parent(Path('linked/another.jar'), True):pass

    def test_parent_replacement_is_detected(self):
        relative = Path('original/file.jar'); (self.path / 'original').mkdir()
        with self.assertRaisesRegex(ValueError, 'parent was replaced'):
            with self.root.parent(relative, False):
                (self.path / 'original').rename(self.path / 'retained-original')
                (self.path / 'original').mkdir()

    def test_marker_missing_wrong_or_replaced_is_not_adopted(self):
        missing = self.path / 'missing'; missing.mkdir()
        with self.assertRaises(FileNotFoundError):J.CacheRoot(missing, native=True)
        self.assertEqual(list(missing.iterdir()), [])
        owned = self.path / 'owned'; native_fixture(owned)
        with contextlib.closing(J.CacheRoot(owned, native=True)) as root:
            root.recheck()
            marker = owned / '.sfm-native-cache-root.json'
            marker.rename(owned / 'retained-marker')
            marker.write_bytes((owned / 'retained-marker').read_bytes())
            with self.assertRaisesRegex(ValueError, 'marker was replaced'):root.recheck()
        marker.write_text('["sfm:native_cache_root@1","/wrong/root"]')
        with self.assertRaisesRegex(ValueError, 'canonical CLI-owned marker'):J.CacheRoot(owned, native=True)

    def test_busy_or_linked_lock_refuses_without_removing_owner_lock(self):
        with self.root.parent(Path('artifact.jar'), False) as parent:
            with J.artifact_lock(parent, 'artifact.jar'):
                with self.assertRaisesRegex(ValueError, 'busy'):
                    with J.artifact_lock(parent, 'artifact.jar', timeout=0):pass
        lock = self.path / 'artifact.jar.lock'; self.assertTrue(lock.is_file())
        lock.unlink(); lock.symlink_to(self.path / 'outside-lock')
        with self.root.parent(Path('artifact.jar'), False) as parent:
            with self.assertRaises(OSError):
                with J.artifact_lock(parent, 'artifact.jar'):pass

    def test_wrong_destination_and_byte_bound_fail_before_artifact_write(self):
        bad = dict(self.pin, relative='../maven-metadata-pins.json')
        with self.assertRaisesRegex(ValueError, 'path differs'):J.publish(self.root, bad, self.data, '/cli', {})
        with patch.object(J, 'MAX_FILE', 1):
            with self.assertRaisesRegex(ValueError, 'byte bound'):J.publish(self.root, self.pin, self.data, '/cli', {})
        self.assertEqual(list(self.path.iterdir()), [])


class AuthorityAndTransportTests(unittest.TestCase):
    def authority(self):
        pins = [pin(i)[0] for i in range(26)]
        origins = [{'coordinate': p['coordinate'], 'hash': p['content_hash'], 'url': p['url']} for p in pins]
        report = {'test_source_lock_sha256': 'a' * 64, 'test_provenance_sha256': 'b' * 64,
                  'configuration_sha256': 'c' * 64, 'targets': []}
        config = {'targets': []}
        for i in range(10):
            target = {'target_id': str(i), 'released_recipe_id': f'recipe-{i}',
                      'raw_source_lock_sha256': 'd' * 64,
                      'selected_pins': [p for p in pins[:-1]], 'production_base_witnesses': [pins[-1]]}
            report['targets'].append(target)
            config['targets'].append({'target': str(i), 'recipe_id': f'recipe-{i}',
                                      'source_lock': {'path': 'source-lock.json', 'sha256': 'd' * 64}})
        def load(repo, relative, expected):
            if relative == J.CONFIG:return config
            return {'artifacts': origins}
        return report, load, origins

    def test_union_uses_all_ten_exact_recipes_and_tracked_origins(self):
        report, loader, _ = self.authority()
        with patch.object(J, 'tracked_json', side_effect=loader):
            selected = J.pins_from_report(Path('/repo'), report)
            self.assertEqual(len(selected), 26)
            report['targets'][0]['released_recipe_id'] = 'wrong-recipe'
            with self.assertRaisesRegex(ValueError, 'recipe authority'):J.pins_from_report(Path('/repo'), report)

    def test_missing_contradictory_and_unsafe_origins_never_guess_urls(self):
        for damage in ('missing', 'conflict', 'http', 'credentials', 'path', 'size', 'hash'):
            report, loader, origins = self.authority()
            if damage == 'missing':origins.pop(0)
            if damage == 'conflict':report['targets'][1] = copy.deepcopy(report['targets'][1]); report['targets'][1]['selected_pins'][0]['bytes'] += 1
            if damage == 'http':origins[0]['url'] = origins[0]['url'].replace('https:', 'http:')
            if damage == 'credentials':origins[0]['url'] = origins[0]['url'].replace('https://', 'https://user:secret@')
            if damage == 'path':origins[0]['url'] += '?different=source'
            if damage == 'size':report['targets'][0]['selected_pins'][0]['bytes'] = J.MAX_FILE + 1
            if damage == 'hash':report['targets'][0]['selected_pins'][0]['content_hash'] = 'sha1:invalid'
            with self.subTest(damage=damage), patch.object(J, 'tracked_json', side_effect=loader):
                with self.assertRaises(ValueError):J.pins_from_report(Path('/repo'), report)

    def test_authority_read_checks_tracking_and_full_digest(self):
        with tempfile.TemporaryDirectory() as folder:
            repo = Path(folder); path = repo / 'authority.json'; path.write_text('{"artifacts": []}')
            with patch.object(C, 'git', return_value='tracked'):
                self.assertEqual(J.tracked_json(repo, path.name, C.digest(path)), {'artifacts': []})
                with self.assertRaisesRegex(ValueError, 'authority changed'):J.tracked_json(repo, path.name, '0' * 64)
            with patch.object(C, 'git', side_effect=ValueError('not tracked')):
                with self.assertRaisesRegex(ValueError, 'not tracked'):J.tracked_json(repo, path.name, C.digest(path))

    def test_tls_proxy_handler_and_no_redirect_or_oversize_fallback(self):
        p, data = pin()
        class Response(io.BytesIO):
            status = 200; url = p['url']
        opener = types.SimpleNamespace(open=lambda *args, **kwargs: Response(data))
        context = ssl.create_default_context()
        with patch.object(J.ssl, 'create_default_context', return_value=context), \
             patch.object(J.urllib.request, 'build_opener', return_value=opener) as build:
            self.assertEqual(J.download(p, {'tls_ca_file': '/filtered', 'tls_ca_dir': '/filtered-dir'}), data)
            self.assertIsInstance(build.call_args.args[0], J.urllib.request.ProxyHandler)
            self.assertIsInstance(build.call_args.args[-1], J.NoRedirect)
            with patch.object(J, 'MAX_FILE', 1):
                with self.assertRaisesRegex(ValueError, 'byte bound'):J.download(p, {'tls_ca_file': '/filtered', 'tls_ca_dir': '/filtered-dir'})
        with self.assertRaisesRegex(ValueError, 'redirect refused'):J.NoRedirect().redirect_request()


class PreparationFlowTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.parent = Path(self.temp.name); self.repo = self.parent / 'repo'; self.repo.mkdir()
        self.native = self.parent / 'native'; native_fixture(self.native)
        self.app = self.parent / 'app'; self.app.mkdir()
        self.cli = self.parent / 'cli'; self.cli.write_bytes(b'ELF fixture')
        lock = self.repo / 'platform/cli/sfm-propagate-changes/Cargo.lock'; lock.parent.mkdir(parents=True); lock.write_bytes(b'frozen lock')
        self.layout = {'native_cache_root': str(self.native), 'application_cache_root': str(self.app), 'sdks': [], 'denied_certificates': []}
        self.head = 'a' * 40; self.output = self.parent / 'new-evidence'
        self.receipt = self.parent / 'build.json'
        self.receipt.write_text(json.dumps({'schema': 'sfm:cloud-release-build@1', 'source_commit': self.head,
            'cli': str(self.cli), 'cli_sha256': C.digest(self.cli), 'layout_sha256': C.digest_payload(self.layout),
            'cargo_lock_sha256': C.digest(lock)}))
        self.pins = [pin(i)[0] for i in range(26)]
        self.protected = self.app / 'minecraft-toolchain/maven/maven-metadata-pins.json'
        self.protected.parent.mkdir(parents=True); self.protected.write_bytes(b'never expand this manifest')

    def run_preparation(self, downloader=None, version=None):
        def git(repo, *args):return self.head if args[:2] == ('rev-parse', 'HEAD') else ''
        def preflight(repo, cli, environment, root):
            missing = [p for p in self.pins if not (root / J.artifact_relative(p)).is_file()]
            return {'schema': 'sfm:junit_dependency_preflight@1', 'issues': [] if not missing else [{'code': 'test_artifact_missing_or_corrupt'}],
                    'closure_complete': not missing, 'test_source_lock_sha256': 'b' * 64,
                    'test_provenance_sha256': 'c' * 64, 'configuration_sha256': 'd' * 64}
        with patch.object(C, 'git', side_effect=git), patch.object(C, 'validate_layout'), \
             patch.object(C, 'require_drained'), patch.object(C, 'tls_environment', return_value={}), \
             patch.object(C, 'native_binary'), patch.object(J.subprocess, 'check_output', return_value=version or 'sfm rev ' + self.head), \
             patch.object(J, 'preflight', side_effect=preflight), patch.object(J, 'pins_from_report', return_value=self.pins), \
             patch.object(J, 'cli_json', side_effect=hash_cli), \
             patch.object(J, 'download', side_effect=downloader or (lambda p, _: ('locked artifact ' + p['coordinate'].split(':')[1].split('-')[-1]).encode())) as network, \
             contextlib.redirect_stderr(io.StringIO()):
            result = J.prepare(self.repo, self.layout, self.cli, self.receipt, self.output)
        return result, network

    def test_unattended_preparation_covers_both_roots_and_reuses_without_network(self):
        result, network = self.run_preparation()
        self.assertEqual(network.call_count, 26); self.assertEqual(result['artifact_count'], 26)
        self.assertEqual(len(result['artifacts']), 52)
        self.assertTrue((self.output / 'success.json').is_file())
        for root in (self.app, self.native):
            for p in self.pins:self.assertTrue((root / J.artifact_relative(p)).is_file())
        self.assertEqual(self.protected.read_bytes(), b'never expand this manifest')
        self.output = self.parent / 'repeat-evidence'
        result, network = self.run_preparation(lambda *args: self.fail('network must not run on complete cache'))
        network.assert_not_called(); self.assertEqual({x['action'] for x in result['artifacts']}, {'reused'})

    def test_conflicting_existing_entry_precedes_download_and_any_other_admission(self):
        artifact = self.app / J.artifact_relative(self.pins[-1]); artifact.parent.mkdir(parents=True); artifact.write_bytes(b'conflict')
        with self.assertRaises(ValueError):self.run_preparation(lambda *args: self.fail('must scan every existing artifact first'))
        self.assertFalse(self.output.exists()); self.assertEqual(artifact.read_bytes(), b'conflict')
        self.assertEqual([x.name for x in self.native.iterdir()], ['.sfm-native-cache-root.json'])

    def test_verified_bytes_in_one_root_populate_the_other_without_network(self):
        with contextlib.closing(J.CacheRoot(self.native, native=True)) as root, patch.object(J, 'cli_json', side_effect=hash_cli):
            for i, p in enumerate(self.pins):J.publish(root, p, pin(i)[1], '/cli', {})
        result, network = self.run_preparation(lambda *args: self.fail('verified declared cache must be reused'))
        network.assert_not_called()
        self.assertEqual(sum(x['action'] == 'created' for x in result['artifacts']), 26)

    def test_bad_download_retains_evidence_but_never_emits_success_or_admits_bytes(self):
        with self.assertRaises(ValueError):self.run_preparation(lambda *args: b'wrong bytes')
        self.assertTrue((self.output / 'preflight.before.json').is_file())
        self.assertFalse((self.output / 'success.json').exists())
        self.assertFalse(list(self.app.rglob('*.jar')))
        self.assertEqual(self.protected.read_bytes(), b'never expand this manifest')

    def test_total_byte_budget_cannot_emit_success_or_admit_oversize_work(self):
        with patch.object(J, 'MAX_TOTAL', 1):
            with self.assertRaisesRegex(ValueError, 'total byte budget'):self.run_preparation()
        self.assertFalse((self.output / 'success.json').exists()); self.assertFalse(list(self.app.rglob('*.jar')))

    def test_identical_application_and_native_roots_are_populated_once(self):
        self.layout['application_cache_root'] = str(self.native)
        receipt = json.loads(self.receipt.read_text()); receipt['layout_sha256'] = C.digest_payload(self.layout)
        self.receipt.write_text(json.dumps(receipt))
        result, network = self.run_preparation()
        self.assertEqual(len(result['cache_roots']), 1); self.assertEqual(len(result['artifacts']), 26)
        self.assertEqual(network.call_count, 26)

    def test_source_and_sdk_overlap_or_wrong_revision_are_not_repaired(self):
        with self.assertRaisesRegex(ValueError, 'exact source head'):
            self.run_preparation(version='sfm rev ' + self.head + '0')
        source_cache = self.repo / 'platform/cli/cache'; source_cache.mkdir(parents=True)
        self.layout['application_cache_root'] = str(source_cache)
        receipt = json.loads(self.receipt.read_text()); receipt['layout_sha256'] = C.digest_payload(self.layout)
        self.receipt.write_text(json.dumps(receipt))
        with self.assertRaisesRegex(ValueError, 'conventional sfm-toolchain-data'):self.run_preparation()
        self.assertFalse(self.output.exists())
        self.layout['application_cache_root'] = str(self.app)
        self.layout['sdks'] = [{'home': str(self.native / 'sdk'), 'sdk_cache_root': str(self.native)}]
        receipt['layout_sha256'] = C.digest_payload(self.layout); self.receipt.write_text(json.dumps(receipt))
        with self.assertRaisesRegex(ValueError, 'SDK overlaps'):self.run_preparation()

    def test_stale_build_and_unmarked_native_root_fail_closed(self):
        data = json.loads(self.receipt.read_text()); data['source_commit'] = '0' * 40; self.receipt.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, 'build receipt'):self.run_preparation()
        data['source_commit'] = self.head; self.receipt.write_text(json.dumps(data))
        marker = self.native / '.sfm-native-cache-root.json'; marker.unlink()
        with self.assertRaises(FileNotFoundError):self.run_preparation()
        self.assertEqual(list(self.native.iterdir()), []); self.assertFalse(self.output.exists())

    def select_test_store(self):
        sdk_root = self.parent / 'sdk-cache'; (sdk_root / 'home').mkdir(parents=True)
        store = self.parent / 'filtered-cacerts'; store.write_bytes(b'verified store bytes')
        self.layout['sdks'] = [{'home': str(sdk_root / 'home'), 'sdk_cache_root': str(sdk_root),
                               'trust_store': str(store), 'trust_sha256': C.digest(store)}]
        receipt = json.loads(self.receipt.read_text()); receipt['layout_sha256'] = C.digest_payload(self.layout)
        self.receipt.write_text(json.dumps(receipt))
        return store

    def test_changed_selected_java_store_prevents_acquisition(self):
        store = self.select_test_store(); store.write_bytes(b'changed policy')
        with self.assertRaisesRegex(ValueError, 'trust store changed'):
            self.run_preparation(lambda *args: self.fail('changed trust must stop acquisition'))
        self.assertFalse(self.output.exists())

    def test_trust_change_during_preparation_cannot_emit_success(self):
        store = self.select_test_store()
        def changed(p, layout):
            store.write_bytes(b'changed during acquisition')
            return ('locked artifact ' + p['coordinate'].split(':')[1].split('-')[-1]).encode()
        with self.assertRaisesRegex(ValueError, 'trust store changed'):self.run_preparation(changed)
        self.assertFalse((self.output / 'success.json').exists())
        self.assertEqual(self.protected.read_bytes(), b'never expand this manifest')


@unittest.skipUnless(os.environ.get('SFM_CLOUD_TEST_CLI'), 'Set SFM_CLOUD_TEST_CLI for the real read-only CLI/catalog check')
class ActualCatalogTests(unittest.TestCase):
    def test_real_preflight_selects_exact_26_pins_and_existing_tracked_origins(self):
        repo = Path(__file__).resolve().parents[4]
        cli = Path(os.environ['SFM_CLOUD_TEST_CLI'])
        with tempfile.TemporaryDirectory() as folder:
            report = J.preflight(repo, cli, os.environ, Path(folder))
            pins = J.pins_from_report(repo, report)
            self.assertEqual(len(pins), 26)
            self.assertEqual(sum(p['full_sha256'] is not None for p in pins), 12)
            self.assertEqual(sum(p['coordinate'] == 'com.google.code.findbugs:jsr305:3.0.2' for p in pins), 1)
            self.assertTrue(all(p['url'].startswith('https://') for p in pins))
            self.assertEqual(list(Path(folder).iterdir()), [])

    def test_real_cli_hash_is_used_before_atomic_create_only_admission(self):
        cli = Path(os.environ['SFM_CLOUD_TEST_CLI'])
        data = b'abc'; p, _ = pin()
        p.update(content_hash='blake3:6437b3ac38465133ffb63b75273a8db548c55846',
                 full_sha256=hashlib.sha256(data).hexdigest(), bytes=3)
        with tempfile.TemporaryDirectory() as folder, contextlib.closing(J.CacheRoot(Path(folder))) as root:
            self.assertEqual(J.publish(root, p, data, cli, os.environ)[0], 'created')
            self.assertEqual(J.publish(root, p, data, cli, os.environ)[0], 'reused')
            self.assertEqual((root.path / J.artifact_relative(p)).read_bytes(), data)


if __name__ == '__main__':unittest.main()
