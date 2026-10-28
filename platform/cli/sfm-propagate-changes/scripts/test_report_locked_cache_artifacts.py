"""Small direct-lock inventory witnesses; no cache data or network required."""

import json
from pathlib import Path
import runpy
import tempfile
import unittest


MODULE = runpy.run_path(str(Path(__file__).with_name("report-locked-cache-artifacts.py")),
                        run_name="cache_claims_test")


class DirectClaimTests(unittest.TestCase):
    def test_shared_path_and_conflicting_pin_are_reported_separately(self):
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            catalog = repo / "platform/minecraft/projections.json"
            catalog.parent.mkdir(parents=True)
            catalog.write_text(json.dumps({"red/mc-1.19.2": {}, "green/mc-1.19.2": {}}),
                               encoding="utf-8")
            for projection, digest in (("red/mc-1.19.2", "sha256:a"),
                                       ("green/mc-1.19.2", "sha256:a")):
                lock = repo / "platform/minecraft/projections" / projection / "sfm-toolchain.lock.json"
                lock.parent.mkdir(parents=True)
                lock.write_text(json.dumps({"artifacts": [{"id": projection,
                    "cache_path": "$sfm-cache\\maven\\same.jar", "hash": digest}]}),
                    encoding="utf-8")
            first = MODULE["report"](repo)
            self.assertEqual((first["catalog_keys"], first["materialized_locks"],
                              first["artifact_claims"], first["unique_relative_paths"]),
                             (2, 2, 2, 1))
            self.assertEqual(first["same_path_hash_conflicts"], [])
            lock = repo / "platform/minecraft/projections/green/mc-1.19.2/sfm-toolchain.lock.json"
            lock.write_text(lock.read_text(encoding="utf-8").replace("sha256:a", "sha256:b"),
                            encoding="utf-8")
            changed = MODULE["report"](repo)
            self.assertEqual(len(changed["same_path_hash_conflicts"]), 1)

    def test_unsafe_cache_and_catalog_paths_fail_closed(self):
        for path in ("$sfm-cache/../private", "$sfm-cache//empty", "$sfm-cache/C:drive"):
            with self.assertRaises(ValueError):
                MODULE["portable_cache_relative"](path)
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            catalog = repo / "platform/minecraft/projections.json"
            catalog.parent.mkdir(parents=True)
            catalog.write_text(json.dumps({"../outside": {}}), encoding="utf-8")
            with self.assertRaises(ValueError):
                MODULE["report"](repo)


if __name__ == "__main__":
    unittest.main()
