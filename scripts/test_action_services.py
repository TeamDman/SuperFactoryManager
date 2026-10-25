"""Headless action-service checks. Requires existing JDK 17+, never downloads or listens.

Run after generating the extended matrix with the native source-project CLI.
These are policy/source checks, not Minecraft compilation or runtime acceptance.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / 'platform/minecraft/core-liquid-template'
CATALOG = json.loads((ROOT / 'platform/minecraft/projections.json').read_text())
FEATURES = json.loads((CORE / 'feature-definitions.json').read_text())
INPUTS = json.loads(re.sub(r',\s*([}\]])', r'\1', (CORE / 'project-inputs.json').read_text()))
BASE = 'e2ba833b2b235835d69475b641e24b22bfd9551a'


class ActionServicesTests(unittest.TestCase):
    def test_real_policy_and_scheduler(self):
        java_home = Path(os.environ['JAVA_HOME'])
        self.assertTrue((java_home / 'bin/javac').is_file(), 'An existing JDK is required; do not install one automatically')
        names = ['SFMActionServiceSettings', 'SFMActionArbiter', 'SFMClientThreadGate', 'SFMActionWorkTracker']
        sources = [CORE / f'src/main/java/ca/teamdman/sfm/client/control/{n}.java' for n in names]
        sources.append(ROOT / 'scripts/action-services/SFMActionServicesContractTest.java')
        with tempfile.TemporaryDirectory(prefix='sfm-action-policy-') as output:
            subprocess.run([str(java_home / 'bin/javac'), '-d', output, *map(str, sources)], check=True)
            subprocess.run([str(java_home / 'bin/java'), '-cp', output, 'ca.teamdman.sfm.client.control.SFMActionServicesContractTest'], check=True)

    def test_all_target_extended_contract(self):
        for target in INPUTS['targets']:
            for release in ['4.34.0', '4.35.0']:
                key = f'sfm-{release}-extended/mc-{target}'
                with self.subTest(projection=key):
                    config = CATALOG[key]
                    self.assertEqual('dev', config['environment'])
                    self.assertTrue({'client_action_services', 'extended_release_regressions'} <= set(config['features']))
                    for feature in config['features']:
                        self.assertIn(target, FEATURES[feature]['supported_targets'])
                        self.assertTrue(set(FEATURES[feature]['requires']) <= set(config['features']), feature)
                    project = ROOT / 'platform/minecraft/projections' / key
                    control = project / 'src/main/java/ca/teamdman/sfm/client/control'
                    for name in ['SFMClientActionRuntime', 'SFMClientControlLifecycle', 'SFMClientControlServer', 'SFMClientControlFileBridge', 'SFMClientCaptureService']:
                        source = (control / (name + '.java')).read_text()
                        self.assertNotIn('{%', source, name)
                    self.assertTrue((project / 'src/gametest/java/ca/teamdman/sfm/gametest/tests/regression/ReleaseEnergyExtractionProgressGameTest.java').is_file())

    def test_legacy_feature_dependencies(self):
        for name in ['client_control_cli', 'client_control_file_actions']:
            self.assertIn('client_action_services', FEATURES[name]['requires'])
        self.assertEqual(set(INPUTS['targets']), set(FEATURES['structured_action_results']['supported_targets']))

    def test_plain_historical_tree(self):
        prefix = 'platform/minecraft/projections/sfm-4.34.0/'
        output = subprocess.check_output(['git', 'ls-tree', '-r', '--full-tree', BASE, '--', prefix], cwd=ROOT, text=True)
        expected = {}
        for line in output.splitlines():
            metadata, path = line.split('\t', 1)
            expected[path] = metadata.split()[2]
        for path in expected:
            self.assertTrue((ROOT / path).is_file(), path)
        hashes = subprocess.check_output(['git', 'hash-object', '--no-filters', '--stdin-paths'],
                                         input=''.join(str(ROOT / path) + '\n' for path in expected),
                                         cwd=ROOT, text=True).splitlines()
        self.assertEqual(len(expected), len(hashes))
        for (path, blob), actual in zip(expected.items(), hashes):
            self.assertEqual(blob, actual, 'Historical release byte drift: ' + path)
        actual_paths = set()
        for target in INPUTS['targets']:
            project = ROOT / prefix / ('mc-' + target)
            for folder, dirs, files in os.walk(project):
                if Path(folder) == project:
                    dirs[:] = [d for d in dirs if d not in ['build', 'run', '.gradle']]
                for file in files:
                    path = Path(folder) / file
                    if path.name == '.sfm-core-project.json':
                        continue  # Native generated ownership receipt, not release source.
                    actual_paths.add(path.relative_to(ROOT).as_posix())
        self.assertEqual(set(expected), actual_paths, 'Historical file membership drift')


    def test_fixed_release_changes_only_fixture_membership(self):
        prefix = 'platform/minecraft/projections/sfm-4.35.0/'
        # Reviewed literal allowlist; feature metadata cannot enlarge this exception.
        moved_names = ['src/test/java/ca/teamdman/sfm/client/Issue493LabelGunChatTests.java', 'src/test/java/ca/teamdman/sfm/test/Issue583BufferLootTests.java', 'src/test/java/ca/teamdman/sfm/test/Issue621RecipeNamespaceTests.java', 'src/gametest/java/ca/teamdman/sfm/gametest/tests/regression/ReleaseEnergyExtractionProgressGameTest.java', 'src/gametest/java/ca/teamdman/sfm/gametest/tests/cable/tunnelled/TunnelledCapabilityForwardingGameTestGenerator.java', 'src/gametest/java/ca/teamdman/sfm/gametest/tests/cable/tunnelled/TunnelledCapabilityCacheRecoveryGameTestGenerator.java', 'src/gametest/java/ca/teamdman/sfm/gametest/tests/cable/tunnelled/TunnelledCapabilityRelayCycleGameTest.java', 'src/gametest/java/ca/teamdman/sfm/gametest/puppet/action/ManagedSessionPuppetAction.java', 'src/gametest/java/ca/teamdman/sfm/gametest/puppet/definition/ManagedSuperflatSessionGamePuppet.java']
        fixtures = {prefix + "mc-" + target + "/" + name
                    for target in INPUTS["targets"] for name in moved_names}
        tree = subprocess.check_output(['git', 'ls-tree', '-r', BASE, '--', prefix], cwd=ROOT, text=True)
        expected = {}
        for line in tree.splitlines():
            metadata, path = line.split('\t', 1)
            if path in fixtures:
                self.assertFalse((ROOT / path).exists(), 'Regression fixture remains in plain release: ' + path)
            else:
                expected[path] = metadata.split()[2]
                self.assertTrue((ROOT / path).is_file(), path)
        hashes = subprocess.check_output(['git', 'hash-object', '--no-filters', '--stdin-paths'],
                                         input=''.join(str(ROOT / path) + '\n' for path in expected),
                                         cwd=ROOT, text=True).splitlines()
        self.assertEqual(len(expected), len(hashes))
        for (path, blob), actual in zip(expected.items(), hashes):
            self.assertEqual(blob, actual, 'Unrelated fixed-release change: ' + path)
        actual_paths = set()
        for target in INPUTS['targets']:
            project = ROOT / prefix / ('mc-' + target)
            for folder, dirs, files in os.walk(project):
                if Path(folder) == project:
                    dirs[:] = [d for d in dirs if d not in ['build', 'run', '.gradle']]
                actual_paths.update((Path(folder) / f).relative_to(ROOT).as_posix()
                                    for f in files if f != '.sfm-core-project.json')
        self.assertEqual(set(expected), actual_paths)


if __name__ == '__main__':
    unittest.main()
