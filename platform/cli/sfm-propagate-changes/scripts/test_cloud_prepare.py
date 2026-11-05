"""Preparation/activation guard regression tests; no real installation or network."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
SCRIPT = Path(__file__).with_name('cloud-prepare.py')
SPEC = importlib.util.spec_from_file_location('cloud_prepare', SCRIPT)
PREP = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREP)
C = PREP.checks


class CloudPrepareTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.output = self.repo / 'installation'
        self.cli_root = self.repo / 'platform/cli/sfm-propagate-changes'; self.cli_root.mkdir(parents=True)
        (self.cli_root / 'Cargo.lock').write_text('unchanged locked dependencies')
        self.target = self.repo / 'target'; (self.target / 'release').mkdir(parents=True)
        self.binary = self.target / 'release/sfm-propagate-changes'; self.binary.write_bytes(b'old release')
        self.layout = {'sdks': [], 'denied_certificates': [], 'test': 'fixture'}
        self.head = 'a' * 40

    def build(self, version=None, fail=False):
        def run(*args, **kwargs):
            if fail:raise subprocess.CalledProcessError(1, args[0])
            self.binary.write_bytes(b'new guarded ELF fixture')
            self.assertIn('--offline', args[0]); self.assertIn('--locked', args[0]); self.assertIn('--release', args[0])
            self.assertEqual(kwargs['env']['SFM_PROPAGATE_CHANGES_INSTALL_GIT_REVISION'], self.head)
        with patch.object(C, 'validate_layout'), patch.object(C, 'require_drained'), patch.object(PREP, 'clean_head', return_value=self.head), patch.object(PREP.shutil, 'which', return_value='/tool'), \
             patch.object(C, 'native_policy'), patch.object(C, 'tls_environment', return_value={'CARGO_TARGET_DIR': str(self.target)}), \
             patch.object(C, 'snapshots', return_value={}), patch.object(C, 'native_binary') as guard, \
             patch.object(PREP.subprocess, 'run', side_effect=run), \
             patch.object(PREP.subprocess, 'check_output', return_value=version or 'sfm rev ' + self.head):
            result = PREP.release_build(self.repo, self.layout, self.output)
        return result, guard

    def test_build_backs_up_previous_release_before_guarded_versioned_install(self):
        result, guard = self.build()
        self.assertEqual((self.output / 'previous-target-release').read_bytes(), b'old release')
        self.assertEqual((self.output / 'sfm-propagate-changes.native').read_bytes(), b'new guarded ELF fixture')
        self.assertEqual(result['source_commit'], self.head)
        self.assertEqual(result['profile'], 'release'); guard.assert_called_once()
        self.assertEqual(result['cli_sha256'], C.digest(self.output / 'sfm-propagate-changes.native'))
        self.assertEqual(json.loads((self.output / 'build-receipt.json').read_text()), result)

    def test_stale_embedded_revision_cannot_install_or_emit_success_receipt(self):
        with self.assertRaisesRegex(ValueError, 'exact full source'):self.build(version='rev deadbeef')
        self.assertFalse((self.output / 'sfm-propagate-changes.native').exists())
        self.assertFalse((self.output / 'build-receipt.json').exists())
        self.assertEqual((self.output / 'previous-target-release').read_bytes(), b'old release')

    def test_failed_build_preserves_logs_and_old_release_without_activation(self):
        with self.assertRaises(subprocess.CalledProcessError):self.build(fail=True)
        self.assertTrue((self.output / 'build.stderr').exists())
        self.assertFalse((self.output / 'build-receipt.json').exists())
        self.assertEqual(self.binary.read_bytes(), b'old release')

    def test_existing_installation_and_dirty_source_refused(self):
        self.output.mkdir()
        with patch.object(C, 'validate_layout'), patch.object(C, 'require_drained'), patch.object(PREP, 'clean_head', return_value=self.head):
            with self.assertRaisesRegex(ValueError, 'must be new'):PREP.release_build(self.repo, self.layout, self.output)
        with patch.object(C, 'git', return_value=' M unrelated'):
            with self.assertRaisesRegex(ValueError, 'dirty'):PREP.clean_head(self.repo)

    def test_missing_native_feature_or_embedded_roots_refused(self):
        manifest = self.cli_root / 'Cargo.toml'
        manifest.write_text('[dependencies.reqwest]\ndefault-features = false\nfeatures = ["rustls-tls"]\n')
        with self.assertRaisesRegex(ValueError, 'native-only TLS'):C.native_policy(self.repo)
        manifest.write_text('[dependencies.reqwest]\ndefault-features = false\nfeatures = ["rustls-tls-native-roots"]\n')
        (self.cli_root / 'Cargo.lock').write_text('[[package]]\nname = "webpki-roots"\nversion = "1"\n')
        with self.assertRaisesRegex(ValueError, 'WebPKI'):C.native_policy(self.repo)
        (self.cli_root / 'Cargo.lock').write_text('package = []\n'); C.native_policy(self.repo)

    def test_no_native_binary_or_missing_denied_witnesses_refused(self):
        with patch.object(C, 'native_policy'):
            with self.assertRaisesRegex(ValueError, 'native Linux'):C.native_binary(self.repo, self.binary, [])
            self.binary.write_bytes(b'\x7fELFfixture')
            with self.assertRaisesRegex(ValueError, 'all four'):C.native_binary(self.repo, self.binary, [])

    def test_start_rechecks_scope_native_guard_and_java_digest(self):
        scope = self.repo / 'scope.json'; scope.write_text(json.dumps({'layout': self.layout}))
        gate = types.SimpleNamespace(validate_scope=lambda *args: None)
        with patch.object(C, 'helper', return_value=gate), patch.object(C, 'native_binary') as guard, \
                patch.object(C, 'tls_environment', return_value={'SSL_CERT_FILE': '/filtered'}):
            result, environment = PREP.start(self.repo, scope, self.binary)
            guard.assert_called_once(); self.assertEqual(environment['SSL_CERT_FILE'], '/filtered')

    def test_cli_arguments_after_mode_are_parsed_and_missing_scope_is_refused(self):
        completed = subprocess.run([sys.executable, '-B', str(SCRIPT), 'start', '--repo-root', str(self.repo),
                                    '--scope', str(self.repo / 'absent'), '--cli', str(self.binary)], capture_output=True, text=True)
        self.assertEqual(completed.returncode, 1)
        self.assertIn('cloud preparation refused:', completed.stderr)
        self.assertNotIn('required: --repo-root', completed.stderr)

    def test_tls_bypass_environment_is_rejected(self):
        for variable in ('GIT_SSL_VERIFY', 'CARGO_HTTP_SSL_VERIFY', 'NPM_CONFIG_STRICT_SSL', 'NODE_TLS_REJECT_UNAUTHORIZED'):
            with self.subTest(variable=variable), patch.object(C, 'check_tls'), patch.dict(os.environ, {variable: '0'}):
                with self.assertRaisesRegex(ValueError, 'bypass'):C.tls_environment({'sdks': [], 'application_cache_root': '/cache',
                                                                                 'tls_ca_file': '/filtered', 'tls_ca_dir': '/filtered-dir'})

    def test_guard_failure_prevents_version_execution_or_installation(self):
        with patch.object(C, 'validate_layout'), patch.object(C, 'require_drained'), \
             patch.object(PREP, 'clean_head', return_value=self.head), patch.object(PREP.shutil, 'which', return_value='/tool'), \
             patch.object(C, 'native_policy'), patch.object(C, 'tls_environment', return_value={'CARGO_TARGET_DIR': str(self.target)}), \
             patch.object(C, 'snapshots', return_value={}), patch.object(PREP.subprocess, 'run'), \
             patch.object(C, 'native_binary', side_effect=ValueError('denied embedded key')), \
             patch.object(PREP.subprocess, 'check_output') as version:
            with self.assertRaisesRegex(ValueError, 'embedded key'):PREP.release_build(self.repo, self.layout, self.output)
            version.assert_not_called()
        self.assertFalse((self.output / 'sfm-propagate-changes.native').exists())
        self.assertFalse((self.output / 'build-receipt.json').exists())

    def test_absent_native_root_is_observed_without_adoption_only_during_build(self):
        native = self.repo / 'absent-native'; app = self.repo / 'application'; app.mkdir()
        sdk = self.repo / 'sdk-cache'; sdk.mkdir(); home = sdk / 'home'; home.mkdir()
        layout = {'native_cache_root': str(native), 'application_cache_root': str(app),
                  'sdks': [{'sdk_cache_root': str(sdk), 'home': str(home)}]}
        with self.assertRaises(FileNotFoundError):C.snapshots(layout)
        before = C.snapshots(layout, allow_missing_native=True)
        self.assertIsNone(before[str(native)]); self.assertFalse(native.exists())
        native.mkdir()  # Simulate a concurrent creator, never adopted by build.
        with self.assertRaisesRegex(ValueError, 'changed'):
            C.same_snapshot(before, C.snapshots(layout, allow_missing_native=True))
        self.assertFalse((native / '.sfm-native-cache-root.json').exists())

    def test_absent_native_root_cannot_overlap_or_traverse_linked_parents(self):
        app = self.repo / 'application'; app.mkdir()
        layout = {'native_cache_root': str(app / 'nested'), 'application_cache_root': str(app), 'sdks': []}
        with self.assertRaisesRegex(ValueError, 'overlap'):C.snapshots(layout, allow_missing_native=True)
        linked = self.repo / 'linked'; linked.symlink_to(app, target_is_directory=True)
        layout['native_cache_root'] = str(linked / 'absent')
        with self.assertRaisesRegex(ValueError, 'linked'):C.snapshots(layout, allow_missing_native=True)

    def test_junit_cache_mode_requires_its_explicit_build_receipt(self):
        completed = subprocess.run([sys.executable, '-B', str(SCRIPT), 'junit-cache', '--repo-root', str(self.repo),
                                    '--layout', '/unused', '--cli', str(self.binary), '--output', str(self.output)],
                                   capture_output=True, text=True)
        self.assertEqual(completed.returncode, 1)
        self.assertIn('junit-cache requires', completed.stderr); self.assertFalse(self.output.exists())



if __name__ == '__main__':
    unittest.main()
