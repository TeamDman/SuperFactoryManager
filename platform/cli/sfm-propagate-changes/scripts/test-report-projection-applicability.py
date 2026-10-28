"""Focused candidate-ledger safety checks; no build or network access."""

import importlib.util
from pathlib import Path
import unittest


SCRIPT = Path(__file__).with_name("report-projection-applicability.py")
SPEC = importlib.util.spec_from_file_location("sfm_applicability", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ApplicabilityTests(unittest.TestCase):
    def test_missing_portable_lock_blocks_required_routes(self):
        key = "sfm-4.34.0-issue610-portable/mc-1.19.2"
        family = MODULE.family_for(key)
        rows = {row["operation"]: row for row in MODULE.classify(
            key, family, {"issue_610_portable_fixture"}, False)}
        for operation in ("frozen_lock", "native_compile", "native_jar",
                          "portable_fe_gametest"):
            self.assertEqual(rows[operation]["status"], "blocked")
            self.assertTrue(rows[operation]["required_when_unblocked"])
        self.assertEqual(rows["source_render"]["status"], "required")
        self.assertEqual(rows["infrastructure_guard_gametest"]["status"], "N/A")

    def test_plain_release_excludes_added_fixture_and_keeps_historical_route(self):
        key = "sfm-4.34.0/mc-1.21.0"
        rows = {row["operation"]: row for row in MODULE.classify(
            key, MODULE.family_for(key), set(), True)}
        self.assertEqual(rows["named_historical_jar"]["status"], "required")
        self.assertEqual(rows["portable_fe_gametest"]["status"], "N/A")
        self.assertEqual(rows["infrastructure_guard_gametest"]["status"], "N/A")
        self.assertEqual(MODULE.family_for("sfm-4.35.0/mc-26.1.2"), "plain-4.35")
        with self.assertRaises(ValueError):
            MODULE.family_for("sfm-dev/../outside")


if __name__ == "__main__":
    unittest.main()
