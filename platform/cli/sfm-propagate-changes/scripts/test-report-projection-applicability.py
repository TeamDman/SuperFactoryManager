"""Focused candidate-ledger safety checks; no build or network access."""

import importlib.util
from pathlib import Path
import unittest


SCRIPT = Path(__file__).with_name("report-projection-applicability.py")
SPEC = importlib.util.spec_from_file_location("sfm_applicability", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ApplicabilityTests(unittest.TestCase):
    def test_real_catalog_scope_has_complete_evidence_fields(self):
        repo = Path(__file__).resolve().parents[4]
        report = MODULE.report(repo)
        self.assertEqual(report["catalog_keys"], 74)
        self.assertEqual(len(report["rows"]), 1540)
        self.assertEqual(report["materialized_locks"], 56)
        self.assertEqual(sum(key["applicability_profiles"] == ("unmaterialized",)
                             for key in report["keys"]), 18)
        for row in report["rows"]:
            self.assertIn(row["status"], {"required", "N/A", "external", "blocked"})
            self.assertTrue(row["expected_proof"])
            self.assertTrue(row["source_basis"])
            if row["status"] == "blocked":
                self.assertTrue(row["required_when_unblocked"])

    def test_missing_portable_lock_blocks_required_routes(self):
        key = "sfm-4.34.0-issue610-portable/mc-1.19.4"
        family = MODULE.family_for(key)
        rows = {row["operation"]: row for row in MODULE.classify(
            key, family, {"issue_610_portable_fixture"}, False, "unmaterialized")}
        for operation in ("frozen_lock", "native_compile", "native_jar",
                          "gradle_compile", "portable_fe_gametest"):
            self.assertEqual(rows[operation]["status"], "blocked")
            self.assertTrue(rows[operation]["required_when_unblocked"])
        self.assertEqual(rows["source_render"]["status"], "required")
        self.assertEqual(rows["infrastructure_guard_gametest"]["status"], "N/A")

    def test_plain_release_excludes_added_fixture_and_keeps_historical_route(self):
        key = "sfm-4.34.0/mc-1.21.0"
        rows = {row["operation"]: row for row in MODULE.classify(
            key, MODULE.family_for(key), set(), True, "legacy-schema-v2")}
        self.assertEqual(rows["named_historical_jar"]["status"], "required")
        self.assertEqual(rows["ordinary_plain_client"]["status"], "required")
        self.assertEqual(rows["gradle_compile"]["status"], "external")
        self.assertEqual(rows["portable_fe_gametest"]["status"], "N/A")
        self.assertEqual(rows["infrastructure_guard_gametest"]["status"], "N/A")
        self.assertEqual(MODULE.family_for("sfm-4.35.0/mc-26.1.2"), "plain-4.35")
        with self.assertRaises(ValueError):
            MODULE.family_for("sfm-dev/../outside")

    def test_historical_locks_are_not_misread_as_missing_profiles(self):
        self.assertEqual(MODULE.applicability_profiles({"schema_version": 2}),
                         ("legacy-schema-v2",))
        self.assertEqual(MODULE.applicability_profiles(None), ("unmaterialized",))
        with self.assertRaises(ValueError):
            MODULE.applicability_profiles({"schema_version": 3})
        with self.assertRaises(ValueError):
            MODULE.applicability_profiles({"profiles": [{"id": "gradle"},
                                                        {"id": "gradle"}]})
        with self.assertRaises(ValueError):
            MODULE.applicability_profiles({"profiles": [{"id": "unknown"}]})

    def test_gradle_route_does_not_claim_native_game_test_proof(self):
        key = "sfm-4.35.0-extended/mc-1.21.0"
        features = {"issue_610_portable_fixture"}
        gradle = {row["operation"]: row for row in MODULE.classify(
            key, MODULE.family_for(key), features, True, "gradle")}
        native = {row["operation"]: row for row in MODULE.classify(
            key, MODULE.family_for(key), features, True, "rust-toolchain")}
        self.assertEqual(gradle["gradle_compile"]["status"], "external")
        self.assertEqual(gradle["portable_fe_gametest"]["status"], "external")
        self.assertEqual(gradle["native_compile"]["status"], "N/A")
        self.assertEqual(native["native_compile"]["status"], "required")
        self.assertEqual(native["portable_fe_gametest"]["status"], "required")
        self.assertEqual(native["portable_fe_gametest"]["selector"],
                         "sfm:issue_610_portable_controls")
        self.assertEqual(native["native_gametest_server"]["status"], "required")

    def test_guard_counts_and_zero_match_remain_explicit(self):
        features = {"gametest_infrastructure_invariants"}
        old = {row["operation"]: row for row in MODULE.classify(
            "sfm-dev/mc-1.19.2", "dev", features, True, "rust-toolchain")}
        new = {row["operation"]: row for row in MODULE.classify(
            "sfm-dev/mc-26.1.2", "dev", features, True, "rust-toolchain")}
        self.assertEqual(old["infrastructure_negative_witnesses"]["minimum_selected"], 5)
        self.assertEqual(new["infrastructure_negative_witnesses"]["minimum_selected"], 4)
        self.assertEqual(new["dev26_ae2_inscribers"]["status"], "blocked")
        self.assertEqual(new["dev26_ae2_inscribers"]["minimum_selected"], 1)
        self.assertEqual(old["dev26_ae2_inscribers"]["status"], "N/A")

    def test_plain_gradle_and_representative_extended_client_routes(self):
        plain = {row["operation"]: row for row in MODULE.classify(
            "sfm-4.35.0/mc-1.21.1", "plain-4.35", set(), True,
            "legacy-schema-v2")}
        extended = {row["operation"]: row for row in MODULE.classify(
            "sfm-4.35.0-extended/mc-1.21.1", "extended",
            {"issue_610_portable_fixture"}, True, "rust-toolchain")}
        self.assertEqual(plain["ordinary_plain_client"]["status"], "external")
        self.assertEqual(extended["client_visual"]["status"], "required")
        self.assertIsNone(extended["client_visual"]["selector"])


if __name__ == "__main__":
    unittest.main()
