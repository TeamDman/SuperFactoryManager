import importlib.util
import json
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name("cloud-offline-readiness.py")
SPEC = importlib.util.spec_from_file_location("cloud_offline_readiness", SCRIPT)
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class CloudOfflineReadinessTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.key = "sfm-dev/mc-1.21.1"
        catalog = self.repo / "platform/minecraft/projections.json"
        catalog.parent.mkdir(parents=True)
        catalog.write_text(json.dumps({self.key: {"minecraft_version": "1.21.1"}}))
        lock = GATE.project_lock(self.repo, self.key)
        lock.parent.mkdir(parents=True)
        lock.write_bytes(b"exact lock")
        self.cli = self.repo / "cli"
        self.cli.write_bytes(b"exact CLI")
        self.git = patch.object(GATE, "git", side_effect=lambda _repo, *args: "abc" if args[-1] == "HEAD" else "")
        self.git.start()
        self.addCleanup(self.git.stop)

    def scope(self):
        result = GATE.draft(self.repo, self.cli, ["sfm-dev/*"])
        result["state"] = "declared"
        return result

    def test_scope_is_exact_and_lock_change_fails_closed(self):
        scope = self.scope()
        self.assertEqual(GATE.validate_scope(self.repo, self.cli, scope)[0]["projection"], self.key)
        GATE.project_lock(self.repo, self.key).write_bytes(b"changed lock")
        with self.assertRaisesRegex(ValueError, "lock changed"):
            GATE.validate_scope(self.repo, self.cli, scope)

    def test_unreviewed_or_empty_scope_is_rejected(self):
        scope = self.scope()
        scope["state"] = "draft"
        with self.assertRaisesRegex(ValueError, "reviewed"):
            GATE.validate_scope(self.repo, self.cli, scope)
        scope["state"] = "declared"
        scope["targets"] = []
        with self.assertRaisesRegex(ValueError, "at least one"):
            GATE.validate_scope(self.repo, self.cli, scope)

    def test_fresh_pass_and_managed_cache_growth(self):
        scope = self.scope()
        rows = [{"projection": self.key, "stage": stage, "status": "passed"} for stage in GATE.STAGES]
        result = types.SimpleNamespace(returncode=0, stdout=json.dumps({"rows": rows, "manifest": {"fresh": True}}), stderr="")
        with patch.object(GATE, "require_network_none"), patch.object(GATE.subprocess, "run", return_value=result) as run:
            self.assertEqual(GATE.verify(self.repo, self.cli, scope)["status"], "passed")
            self.assertIn("--fresh", run.call_args.args[0])
        cache = self.repo / "sfm-toolchain-data/minecraft-toolchain"
        cache.mkdir(parents=True)

        def acquired(*_args, **_kwargs):
            (cache / "new.jar").write_bytes(b"unexpected acquisition")
            return result

        with patch.object(GATE, "require_network_none"), patch.object(GATE.subprocess, "run", side_effect=acquired):
            with self.assertRaisesRegex(ValueError, "cache grew"):
                GATE.verify(self.repo, self.cli, scope)

    def test_existing_pass_receipt_cannot_mask_missing_cache(self):
        scope = self.scope()
        receipt = self.repo / "sfm-toolchain-data/check-everything-native/receipts/x/check/prior.pass.json"
        receipt.parent.mkdir(parents=True)
        receipt.write_text("{}")
        with patch.object(GATE, "require_network_none"):
            with self.assertRaisesRegex(ValueError, "matrix pass receipts"):
                GATE.verify(self.repo, self.cli, scope)

    def test_publication_requires_loopback_only_network(self):
        with patch.object(GATE.sys, "platform", "linux"), patch.object(
            GATE.socket, "if_nameindex", return_value=[(1, "lo"), (2, "eth0")]
        ):
            with self.assertRaisesRegex(ValueError, "only loopback"):
                GATE.require_network_none()
        with patch.object(GATE.sys, "platform", "linux"), patch.object(
            GATE.socket, "if_nameindex", return_value=[(1, "lo")]
        ), patch.object(GATE.Path, "exists", return_value=True), patch.object(
            GATE.Path, "read_text", side_effect=["Iface Destination\neth0 00000000\n", ""]
        ):
            with self.assertRaisesRegex(ValueError, "IPv4 default route"):
                GATE.require_network_none()


if __name__ == "__main__":
    unittest.main()
