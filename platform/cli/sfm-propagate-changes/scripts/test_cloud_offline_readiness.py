"""Focused positive/negative gate tests; fixtures never stand for VM qualification."""
import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import types
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
SCRIPT = Path(__file__).with_name('cloud-offline-readiness.py')
SPEC = importlib.util.spec_from_file_location('cloud_offline_readiness', SCRIPT)
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)
C = GATE.checks
REPO = SCRIPT.resolve().parents[4]


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value))


class CloudOfflineReadinessTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.key = 'sfm-dev/mc-1.21.1'
        self.catalog = self.repo / 'platform/minecraft/projections.json'
        write_json(self.catalog, {self.key: {}})
        self.lock = GATE.project_lock(self.repo, self.key)
        write_json(self.lock, {})
        self.cargo = self.repo / 'platform/cli/sfm-propagate-changes/Cargo.lock'
        self.cargo.parent.mkdir(parents=True, exist_ok=True)
        self.cargo.write_text('exact cargo lock')
        write_json(self.repo / GATE.REVIEW, {'targets': []})
        self.cli = self.repo / 'cli'
        self.cli.write_bytes(b'exact CLI')
        self.native, self.application = self.repo / 'native', self.repo / 'application'
        self.native.mkdir(); self.application.mkdir()
        self.sdk = self.repo / 'sdk'; self.sdk.mkdir()
        self.layout = {'schema': 'sfm:cloud-preparation-layout@1', 'proxy_ca_sha256': 'proxy', 'native_cache_root': str(self.native), 'application_cache_root': str(self.application),
                       'sdks': [{'major': 21, 'home': str(self.sdk), 'sdk_cache_root': str(self.application), 'proxy_ca_sha256': 'proxy', 'sha512': 'a' * 128}],
                       'target_java': {self.key: 21}}
        self.receipt = self.repo / 'build.json'
        self.git = patch.object(C, 'git', side_effect=lambda _repo, *args: 'a' * 40 if args[-1] == 'HEAD' else '')
        self.git.start(); self.addCleanup(self.git.stop)

    def scope(self):
        write_json(self.receipt, {'schema': 'sfm:cloud-release-build@1', 'source_commit': 'a' * 40,
                                 'cli_sha256': C.digest(self.cli), 'profile': 'release',
                                 'layout_sha256': C.digest_payload(self.layout),
                                 'cargo_lock_sha256': C.digest(self.cargo), 'version': 'rev ' + 'a' * 40})
        with patch.object(GATE, 'audit_inputs'), patch.object(GATE, 'input_snapshot', side_effect=lambda _: C.snapshots(self.layout)):
            result = GATE.draft(self.repo, self.cli, ['*'], self.layout, self.receipt)
        result['state'] = 'declared'
        return result

    def test_exact_scope_and_uncovered_catalog_accounting(self):
        scope = self.scope()
        self.assertEqual(len(GATE.validate_scope(self.repo, self.cli, scope)), 1)
        self.assertEqual(scope['not_attested'], list(GATE.UNTESTED))
        scope['excluded_targets'] = ['invented']
        with self.assertRaisesRegex(ValueError, 'coverage accounting'):
            GATE.validate_scope(self.repo, self.cli, scope)

    def test_changed_source_binary_catalog_lock_or_stage_fails(self):
        for field, value, reason in [('source_commit', 'b' * 40, 'source commit'),
                                     ('cli_sha256', '0' * 64, 'CLI binary'),
                                     ('catalog_sha256', '0' * 64, 'catalog'),
                                     ('state', 'draft', 'reviewed')]:
            with self.subTest(field=field):
                scope = self.scope(); scope[field] = value
                with self.assertRaisesRegex(ValueError, reason):GATE.validate_scope(self.repo, self.cli, scope)
        scope = self.scope(); scope['targets'][0]['stages'] = ['check']
        with self.assertRaisesRegex(ValueError, 'freshly'):GATE.validate_scope(self.repo, self.cli, scope)
        scope = self.scope(); write_json(self.lock, {'changed': True})
        with self.assertRaisesRegex(ValueError, 'lock changed'):GATE.validate_scope(self.repo, self.cli, scope)

    def test_duplicate_empty_and_forged_stage_claims_rejected(self):
        scope = self.scope(); scope['targets'] *= 2
        with self.assertRaisesRegex(ValueError, 'duplicate'):GATE.validate_scope(self.repo, self.cli, scope)
        scope = self.scope(); scope['targets'] = []
        with self.assertRaisesRegex(ValueError, 'at least one'):GATE.validate_scope(self.repo, self.cli, scope)
        scope = self.scope(); scope['not_attested'] = []
        with self.assertRaisesRegex(ValueError, 'stage claims'):GATE.validate_scope(self.repo, self.cli, scope)

    def test_stale_build_and_changed_layout_rejected(self):
        scope = self.scope(); scope['layout'] = copy.deepcopy(scope['layout'])
        scope['layout']['native_cache_root'] = '/different'
        with self.assertRaisesRegex(ValueError, 'provenance'):GATE.validate_scope(self.repo, self.cli, scope)
        scope = self.scope(); self.receipt.write_text('{}')
        with self.assertRaisesRegex(ValueError, 'receipt changed'):GATE.validate_scope(self.repo, self.cli, scope)

    def test_exact_historical_recipe_and_source_lock_preserved(self):
        key = 'sfm-4.34.0/mc-1.20.2'
        write_json(self.catalog, {key: {}}); write_json(GATE.project_lock(self.repo, key), {})
        self.layout['target_java'] = {key: 21}
        original = self.repo / 'historical-lock'; original.write_bytes(b'frozen')
        write_json(self.repo / GATE.REVIEW, {'targets': [{'target': '1.20.2', 'recipe_id': 'exact@1',
                   'source_lock': {'path': 'historical-lock', 'sha256': C.digest(original)}}]})
        scope = self.scope(); self.assertEqual(scope['targets'][0]['released_recipe'], 'exact@1')
        scope['targets'][0]['released_recipe'] = 'nearby@1'
        with self.assertRaisesRegex(ValueError, 'recipe changed'):GATE.validate_scope(self.repo, self.cli, scope)
        original.write_bytes(b'different')
        with self.assertRaisesRegex(ValueError, 'historical source lock'):GATE.recipe(self.repo, key)

    def test_target_pin_must_match_explicit_sdk(self):
        write_json(self.lock, {'jdk_pins': [{'major': 21, 'artifacts': [{'platform': 'linux-x64', 'sha512': 'b' * 128}]}]})
        with self.assertRaisesRegex(ValueError, 'differs from target pin'):GATE.validate_scope(self.repo, self.cli, self.scope())

    def test_both_roots_detect_equal_size_byte_edits_deletions_and_additions(self):
        for root in (self.native, self.application):
            with self.subTest(root=root):
                entry = root / 'artifact'; entry.write_bytes(b'old')
                before = C.snapshots(self.layout); entry.write_bytes(b'new')
                with self.assertRaisesRegex(ValueError, 'bytes'):C.same_snapshot(before, C.snapshots(self.layout))
                before = C.snapshots(self.layout); entry.unlink()
                with self.assertRaisesRegex(ValueError, 'bytes'):C.same_snapshot(before, C.snapshots(self.layout))
                before = C.snapshots(self.layout); entry.write_bytes(b'new')
                with self.assertRaisesRegex(ValueError, 'bytes'):C.same_snapshot(before, C.snapshots(self.layout))

    def test_mode_change_and_unowned_link_rejected(self):
        entry = self.native / 'artifact'; entry.write_bytes(b'bytes')
        before = C.snapshots(self.layout); entry.chmod(0o700)
        with self.assertRaisesRegex(ValueError, 'modes'):C.same_snapshot(before, C.snapshots(self.layout))
        (self.application / 'link').symlink_to(entry)
        with self.assertRaisesRegex(ValueError, 'lacks verified SDK authority'):C.snapshots(self.layout)

    def test_mutable_outputs_explicit_but_root_and_mutable_links_refused(self):
        (self.native / 'projects').mkdir()
        before = C.snapshots(self.layout); (self.native / 'projects/output').write_bytes(b'generated')
        C.same_snapshot(before, C.snapshots(self.layout))
        (self.native / 'nfrt').symlink_to(self.sdk)
        with self.assertRaisesRegex(ValueError, 'linked mutable'):C.snapshots(self.layout)

    def test_overlap_roots_rejected(self):
        nested = self.native / 'nested'; nested.mkdir()
        self.layout['application_cache_root'] = str(nested)
        with self.assertRaisesRegex(ValueError, 'overlap'):C.snapshots(self.layout)

    def test_launch_contract_has_both_roots_sdk_recipe_and_no_ambient_jvm_flags(self):
        scope = self.scope(); row = scope['targets'][0]; row['released_recipe'] = 'exact@1'
        command = GATE.stage_command(self.repo, self.cli, self.layout, row, 'compile')
        self.assertIn(str(self.native), command); self.assertIn(str(self.sdk), command); self.assertIn('exact@1', command)
        with patch.dict(os.environ, {'JAVA_TOOL_OPTIONS': '-Dbypass=true', 'SFM_JAVA_TRUST_STORE': '/stale'}):
            self.layout['sdks'][0].update(trust_store='/verified', trust_sha256='abc')
            env = C.java_environment(self.layout)
            self.assertNotIn('JAVA_TOOL_OPTIONS', env); self.assertNotIn('SFM_JAVA_TRUST_STORE', env)
            self.assertEqual(env['SFM_PROPAGATE_CHANGES_CACHE'], str(self.application))
            self.assertEqual(env['SFM_JAVA_TRUST_STORE_21'], '/verified')

    def test_prior_jar_outputs_never_overwritten(self):
        libs = self.repo / 'platform/minecraft/projections' / self.key / 'build/libs'; libs.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError, 'prior JAR'):GATE.stage_command(self.repo, self.cli, self.layout, self.scope()['targets'][0], 'jar')

    def test_loopback_and_both_default_routes(self):
        with patch.object(GATE.socket, 'if_nameindex', return_value=[(1, 'lo'), (2, 'eth0')]):
            with self.assertRaisesRegex(ValueError, 'only loopback'):GATE.require_network_none()
        with patch.object(GATE.socket, 'if_nameindex', return_value=[(1, 'lo')]), patch.object(GATE.Path, 'exists', return_value=True):
            with patch.object(GATE.Path, 'read_text', side_effect=['Iface Destination\n', '']):GATE.require_network_none()
            with patch.object(GATE.Path, 'read_text', return_value='Iface Destination\neth0 00000000\n'):
                with self.assertRaisesRegex(ValueError, 'IPv4'):GATE.require_network_none()
            with patch.object(GATE.Path, 'read_text', side_effect=['Iface Destination\n', '0' * 32 + ' 00']):
                with self.assertRaisesRegex(ValueError, 'IPv6'):GATE.require_network_none()

    def test_existing_pass_receipts_rejected(self):
        p = self.repo / 'sfm-toolchain-data/check-everything-native/receipts/prior.pass.json'; write_json(p, {})
        with patch.object(GATE, 'require_network_none'):
            with self.assertRaisesRegex(ValueError, 'pass receipts'):GATE.verify(self.repo, self.cli, self.scope(), self.repo / 'gate')

    def run_gate(self, side_effect=None, code=0):
        evidence = self.repo / 'gate'
        def execute(*args, **kwargs):
            stage = args[0][5]
            report = {'schema': 'sfm:core_project_generation@1', 'projection_key': self.key,
                      'operation': 'check', 'writes_performed': False}
            if stage != 'check':
                report = {'schema': 'sfm:development_native_build@1', 'preparation': {'projection_key': self.key},
                          'immutable_source_lock': True, 'compilation': 'completed', 'tool_jvm_major': 21,
                          'jar_packaging': 'completed'}
                if stage == 'jar':
                    output = self.repo / 'platform/minecraft/projections' / self.key / 'build/libs/new.jar'
                    output.parent.mkdir(parents=True); output.write_bytes(b'packaged fixture')
                    report['output_jar'] = str(output)
            kwargs['stdout'].write(json.dumps(report))
            if side_effect:side_effect()
            return types.SimpleNamespace(returncode=code)
        with patch.object(GATE, 'require_network_none'), patch.object(GATE, 'audit_inputs', return_value={}), \
                patch.object(GATE, 'input_snapshot', side_effect=lambda _: C.snapshots(self.layout)), \
                patch.object(GATE.subprocess, 'run', side_effect=execute) as run:
            result = GATE.verify(self.repo, self.cli, self.scope(), evidence)
        return result, run

    def test_fresh_commands_and_receipts_without_pass_reuse(self):
        result, run = self.run_gate()
        self.assertEqual([x['stage'] for x in result['rows']], list(GATE.STAGES))
        self.assertEqual(run.call_count, 3)
        self.assertEqual(result['proof'], 'isolated-offline-native-stages-only')
        self.assertIn('JUnit', result['not_attested'])
        self.assertEqual(len(list((self.repo / 'gate').glob('*.stdout'))), 3)

    def test_nonzero_stage_retains_failure_evidence(self):
        with self.assertRaisesRegex(ValueError, 'check failed'):self.run_gate(code=1)
        result = C.load(self.repo / 'gate/result.json'); self.assertEqual(result['status'], 'failed')
        self.assertEqual(len(result['rows']), 1)

    def test_cache_mutation_even_with_zero_exit_refuses(self):
        artifact = self.application / 'artifact'; artifact.write_bytes(b'old')
        with self.assertRaisesRegex(ValueError, 'bytes'):self.run_gate(side_effect=lambda: artifact.write_bytes(b'new'))
        self.assertEqual(C.load(self.repo / 'gate/result.json')['status'], 'failed')

    def test_zero_exit_cannot_mask_wrong_schema_target_or_stage(self):
        row = self.scope()['targets'][0]
        stdout = self.repo / 'stage.stdout'
        report = {'schema': 'sfm:core_project_generation@1', 'projection_key': self.key,
                  'operation': 'check', 'writes_performed': False}
        for field, value in [('schema', 'wrong'), ('projection_key', 'other'),
                             ('operation', 'sync'), ('writes_performed', True)]:
            changed = dict(report); changed[field] = value; write_json(stdout, changed)
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'check receipt'):
                GATE.stage_result(self.repo, row, 'check', stdout)
        write_json(stdout, {'schema': 'sfm:development_native_build@1',
                           'preparation': {'projection_key': self.key}, 'immutable_source_lock': True,
                           'compilation': 'not_performed', 'tool_jvm_major': 21})
        with self.assertRaisesRegex(ValueError, 'did not complete'):GATE.stage_result(self.repo, row, 'compile', stdout)

    def test_layout_schema_and_timeout_are_bounded(self):
        for field, value in [('schema', 'unknown'), ('stage_timeout_seconds', 0), ('stage_timeout_seconds', 999999)]:
            changed = dict(self.layout); changed[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):C.validate_layout(changed)

    def test_timeout_retains_failure_result_and_no_pass(self):
        scope = self.scope(); evidence = self.repo / 'timeout-gate'
        with patch.object(GATE, 'require_network_none'), patch.object(GATE, 'audit_inputs', return_value={}), \
                patch.object(GATE, 'input_snapshot', side_effect=lambda _: C.snapshots(self.layout)), \
                patch.object(GATE.subprocess, 'run', side_effect=subprocess.TimeoutExpired('owned-command', 1)):
            with self.assertRaises(subprocess.TimeoutExpired):GATE.verify(self.repo, self.cli, scope, evidence)
        self.assertEqual(C.load(evidence / 'result.json')['status'], 'failed')
        self.assertTrue(list(evidence.glob('*.stderr')))



class SdkAuthorityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        helper = self.repo / 'platform/cli/sfm-propagate-changes/src/jdk_tar.py'; helper.parent.mkdir(parents=True)
        shutil.copyfile(REPO / 'platform/cli/sfm-propagate-changes/src/jdk_tar.py', helper)
        self.helper = helper
        self.home = self.repo / 'minecraft-toolchain/jbrsdk/installs/sdk'; self.home.mkdir(parents=True)
        self.archive = self.repo / 'minecraft-toolchain/jbrsdk/archives/sdk.tar.gz'
        self.archive.parent.mkdir()
        with tarfile.open(self.archive, 'w:gz') as archive:
            for name, data in [('bin/java', b'java'), ('bin/javac', b'javac'), ('lib/modules', b'modules')]:
                member = tarfile.TarInfo('jdk/' + name); member.size = len(data); member.mode = 0o755
                archive.addfile(member, io.BytesIO(data))
            member = tarfile.TarInfo('jdk/lib/java-link'); member.type = tarfile.SYMTYPE; member.linkname = '../bin/java'; archive.addfile(member)
        sha = C.digest(self.archive, 'sha512')
        pin = {'major': 21, 'vendor': 'JetBrains', 'version': '21.exact', 'build': 'b1', 'flavor': 'jbrsdk'}
        artifact = {'platform': 'linux-x64', 'url': 'https://official/sdk.tar.gz', 'sha512': sha}
        pin['artifacts'] = [artifact]
        lock = self.repo / 'authored.json'; write_json(lock, {'jdk_pins': [pin]})
        self.sdk = {'major': 21, 'home': str(self.home), 'sdk_cache_root': str(self.repo), 'archive': str(self.archive), 'sha512': sha,
                    'authority_lock': 'authored.json', 'authority_lock_sha256': C.digest(lock)}
        with self.archive.open('rb') as source:
            subprocess.run([sys.executable, '-B', str(helper), 'extract', str(self.home), sha], stdin=source, check=True)
        (self.home / '.sfm-jbrsdk-receipt').write_text(C.receipt(pin, artifact))
        mock = patch.object(C, 'git', return_value='authored.json'); mock.start(); self.addCleanup(mock.stop)

    def test_verified_legitimate_links_and_installed_modes_pass(self):
        C.audit_sdk(self.repo, self.sdk)
        self.assertIn('lib/java-link', C.snapshot_root(self.home, [self.home]))

    def test_modified_link_or_installed_file_rejected_by_actual_tar_helper(self):
        link = self.home / 'lib/java-link'; link.unlink(); link.symlink_to('../bin/javac')
        with self.assertRaises(subprocess.CalledProcessError):C.audit_sdk(self.repo, self.sdk)
        link.unlink(); link.symlink_to('../bin/java'); (self.home / 'bin/java').write_bytes(b'evil')
        with self.assertRaises(subprocess.CalledProcessError):C.audit_sdk(self.repo, self.sdk)

    def test_changed_archive_receipt_or_authority_rejected(self):
        self.archive.write_bytes(b'corrupt')
        with self.assertRaisesRegex(ValueError, 'SHA-512'):C.audit_sdk(self.repo, self.sdk)
        self.sdk['authority_lock_sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'authority lock'):C.audit_sdk(self.repo, self.sdk)

    def test_unreceipted_install_and_escape_link_rejected(self):
        (self.home / '.sfm-jbrsdk-receipt').write_text('untrusted')
        with self.assertRaisesRegex(ValueError, 'receipt'):C.audit_sdk(self.repo, self.sdk)
        link = self.home / 'lib/java-link'; link.unlink(); link.symlink_to(self.archive)
        with self.assertRaisesRegex(ValueError, 'escapes'):C.snapshot_root(self.home, [self.home])


if __name__ == '__main__':
    unittest.main()
