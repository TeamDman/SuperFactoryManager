"""Focused invariants for the G6-joined lock-side artifact inventory."""

import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("report-projection-locked-artifacts.py")
spec = importlib.util.spec_from_file_location("sfm_locked_artifact_report", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class LockedArtifactInventoryTests(unittest.TestCase):
    def test_lock_digest_drift_is_rejected_before_inventory(self):
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            path = repo / "platform/minecraft/projections/demo/mc-1.19.2/sfm-toolchain.lock.json"
            path.parent.mkdir(parents=True)
            data = json.dumps({"schema_version": 2, "artifacts": [{"hash": "blake3:x"}]}).encode()
            path.write_bytes(data)
            key = {"key": "demo/mc-1.19.2", "family": "demo",
                   "minecraft_version": "1.19.2", "lock_sha256": "0" * 64}
            with self.assertRaisesRegex(ValueError, "G6 lock digest drift"):
                module.lock_inventory(repo, key, [])
            key["lock_sha256"] = hashlib.sha256(data).hexdigest()
            result = module.lock_inventory(repo, key, [])
            self.assertEqual(result["lock_schema"], 2)
            self.assertEqual(result["artifact_count"], 1)
            self.assertTrue(result["planner_classpath_status"].startswith("unverified"))

    def test_provider_variant_must_be_unambiguous(self):
        lock = {"dependencies": [{"id": "d", "components": [{"id": "c",
            "source_providers": [{"git": {"id": "one"}, "decompile": {"id": "two"}}]}]}]}
        with self.assertRaisesRegex(ValueError, "one tagged variant"):
            module.provider_rows(lock)


if __name__ == "__main__":
    unittest.main()