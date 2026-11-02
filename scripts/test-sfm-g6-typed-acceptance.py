#!/usr/bin/env python3
"""Source-only contract tests for sfm-g6-typed-acceptance.py."""

from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

MODULE = Path(__file__).with_name("sfm-g6-typed-acceptance.py")
SPEC = importlib.util.spec_from_file_location("sfm_g6_typed_acceptance", MODULE)
assert SPEC and SPEC.loader
g6 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(g6)


def h(byte: str) -> str:
    return byte * 64


class TypedAcceptanceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="sfm-g6-typed-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.row = {
            "projection": "sfm-4.35.0/mc-1.20.2", "profile": "legacy-schema-v2",
            "operation": "gradle_compile", "status": "required", "lock_sha256": h("d"),
            "minimum_selected": None, "witness_outcomes": None,
        }
        self.scope = {"schema": 3, "rows": [self.row]}
        self.scope_hash = self.write("scope.json", self.scope)["sha256"]
        self.prior_hash = self.write("prior.json", {"schema": "legacy", "green": 466})["sha256"]
        self.raw = self.write_text("run.log", "BUILD SUCCESSFUL\n")
        self.raw_pin = self.write_text("selected-lock.json", "checked official hash\n")
        self.pin_source = self.write("pin-source.json", {
            "schema": g6.PIN_SOURCE_SCHEMA, "domain": "tracked-supplement",
            "coordinate": "a:b:1", "algorithm": "sha256", "digest": h("1"),
            "source_document_sha256": self.raw_pin["sha256"],
            "raw_evidence": [self.raw_pin],
        })
        producer = {
            "schema": g6.PRODUCER_SCHEMA, "recipe_id": "neoforge:20.2.86",
            "tool_sha256": h("2"), "relevant_inputs_sha256": h("3"),
            "output_sha256": h("f"), "output_bytes": 200,
            "exit_code": 0, "inputs_stable": True, "reader_safe": True,
            "raw_evidence": [self.raw],
        }
        self.producer_ref = self.write("producer.json", producer)
        self.claim = {
            "row": {key: self.row[key] for key in ("projection", "profile", "operation")},
            "lock_sha256": h("d"), "source_sha256": h("a"),
            "tool_sha256": h("b"), "jdk_sha256": h("c"),
        }
        consumer = {
            "schema": g6.CONSUMER_SCHEMA, "row": self.claim["row"],
            "lock_sha256": h("d"), "source_sha256": h("a"),
            "tool_sha256": h("b"), "jdk_sha256": h("c"),
            "ordered_classpath": [
                {"index": 0, "kind": "upstream", "coordinate": "a:b:1",
                 "path": "cache/a-b-1.jar", "bytes": 100, "sha256": h("1"),
                 "pin": {"algorithm": "sha256", "digest": h("1"), "source": self.pin_source}},
                {"index": 1, "kind": "derived", "coordinate": "neo:raw:1",
                 "path": "build/raw.jar", "bytes": 200, "sha256": h("f"),
                 "producer_ref": self.producer_ref,
                 "recipe_id": "neoforge:20.2.86", "tool_sha256": h("2"),
                 "relevant_inputs_sha256": h("3")},
            ],
        }
        self.consumer = consumer
        self.trace = self.make_trace(consumer)
        self.consumer["trace_ref"] = self.write("captured-classpath.json", self.trace)
        self.claim["consumer_ref"] = self.write("consumer.json", consumer)
        self.run = self.result(task_executed=True)
        self.repeat = self.result(task_executed=False)
        self.claim["result_ref"] = self.write("run.json", self.run)
        self.claim["repeat_ref"] = self.write("repeat.json", self.repeat)

    def write_text(self, name: str, value: str) -> dict[str, str]:
        path = self.root / name
        path.write_text(value, encoding="utf-8")
        return {"path": name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}

    def write(self, name: str, value: object) -> dict[str, str]:
        return self.write_text(name, json.dumps(value, sort_keys=True) + "\n")

    def result(self, task_executed: bool) -> dict[str, object]:
        return {
            "schema": g6.RESULT_SCHEMA, "row": self.claim["row"],
            "lock_sha256": h("d"), "source_sha256": h("a"),
            "tool_sha256": h("b"), "jdk_sha256": h("c"),
            "input_before_sha256": h("4"), "input_after_sha256": h("4"),
            "writer_excluded": True, "children_exited": True,
            "outcome": "pass", "exit_code": 0, "task_executed": task_executed,
            "rust_invoked": False, "offline": True, "network_downloads": 0,
            "reuse_proven": not task_executed, "raw_evidence": [self.raw],
        }

    def make_trace(self, consumer: dict[str, object]) -> dict[str, object]:
        fields = ("index", "path", "coordinate", "bytes", "sha256")
        return {
            "schema": g6.TRACE_SCHEMA, "row": consumer["row"],
            "lock_sha256": h("d"), "source_sha256": h("a"),
            "tool_sha256": h("b"), "jdk_sha256": h("c"),
            "complete": True, "configured_count": len(consumer["ordered_classpath"]),
            "capture_command": "Gradle compileJava.classpath ordered capture",
            "writer_excluded": True, "input_before_sha256": h("4"),
            "input_after_sha256": h("4"), "raw_evidence": [self.raw],
            "ordered_classpath": [
                {field: entry[field] for field in fields}
                for entry in consumer["ordered_classpath"]
            ],
        }

    def check(self, claims: list[dict] | None = None, prior_hash: str | None = None):
        batch = {"schema": g6.SCHEMA, "scope_sha256": self.scope_hash,
                 "prior_ledger_sha256": prior_hash or self.prior_hash,
                 "claims": claims if claims is not None else [self.claim]}
        return g6.validate_batch(self.scope, self.scope_hash, self.prior_hash, batch, self.root)

    def assert_rejected(self, fragment: str, claims: list[dict] | None = None) -> None:
        result = self.check(claims)
        self.assertEqual([], result["accepted"])
        self.assertIn(fragment, result["rejected"][0]["reason"])

    def replace_consumer(self) -> None:
        self.claim["consumer_ref"] = self.write("consumer-mutated.json", self.consumer)

    def replace_run(self) -> None:
        self.claim["result_ref"] = self.write("run-mutated.json", self.run)

    def test_complete_gradle_row_passes_without_fresh_producer_per_consumer(self):
        second = copy.deepcopy(self.row)
        second["projection"] = "sfm-4.34.0/mc-1.20.2"
        self.scope["rows"].append(second)
        self.scope_hash = self.write("scope-two.json", self.scope)["sha256"]
        second_claim = copy.deepcopy(self.claim)
        second_claim["row"] = {key: second[key] for key in ("projection", "profile", "operation")}
        second_consumer = copy.deepcopy(self.consumer)
        second_consumer["row"] = second_claim["row"]
        second_consumer["trace_ref"] = self.write("captured-classpath-two.json",
                                                  self.make_trace(second_consumer))
        second_claim["consumer_ref"] = self.write("consumer-two.json", second_consumer)
        for name, task in (("run-two.json", True), ("repeat-two.json", False)):
            result = self.result(task)
            result["row"] = second_claim["row"]
            second_claim["result_ref" if task else "repeat_ref"] = self.write(name, result)
        result = self.check([self.claim, second_claim])
        self.assertEqual(2, len(result["accepted"]))
        self.assertFalse(result["cumulative_ledger_changed"])

    def test_rejects_stale_prior_ledger(self):
        with self.assertRaisesRegex(ValueError, "stale scope or prior"):
            self.check(prior_hash=h("9"))

    def test_authoritative_head_rejects_old_ledger(self):
        head = {"schema": g6.HEAD_SCHEMA, "scope_sha256": self.scope_hash,
                "current_ledger_sha256": self.prior_hash, "revision": 3}
        g6.verify_ledger_head(head, self.scope_hash, self.prior_hash)
        with self.assertRaisesRegex(ValueError, "authoritative ledger head"):
            g6.verify_ledger_head(head, self.scope_hash, h("9"))

    def test_cli_writes_only_validated_claim_delta(self):
        batch = {"schema": g6.SCHEMA, "scope_sha256": self.scope_hash,
                 "prior_ledger_sha256": self.prior_hash, "claims": [self.claim]}
        self.write("claims.json", batch)
        self.write("head.json", {"schema": g6.HEAD_SCHEMA,
                                 "scope_sha256": self.scope_hash,
                                 "current_ledger_sha256": self.prior_hash,
                                 "revision": 0})
        output = self.root / "accepted.json"
        argv = ["sfm-g6-typed-acceptance.py", "--scope", str(self.root / "scope.json"),
                "--scope-sha256", self.scope_hash, "--prior-ledger",
                str(self.root / "prior.json"), "--prior-sha256", self.prior_hash,
                "--ledger-head", str(self.root / "head.json"), "--claims",
                str(self.root / "claims.json"), "--evidence-root", str(self.root),
                "--out", str(output)]
        with mock.patch.object(sys, "argv", argv):
            self.assertEqual(0, g6.main())
        self.assertEqual(1, len(json.loads(output.read_text())["accepted"]))
        self.assertFalse(json.loads(output.read_text())["cumulative_ledger_changed"])
        with mock.patch.object(sys, "argv", argv):
            with self.assertRaises(FileExistsError):
                g6.main()

    def test_expected_historical_red_needs_matching_review(self):
        self.row["operation"] = "native_gametest_server"
        self.row["minimum_selected"] = 1
        self.scope_hash = self.write("red-scope.json", self.scope)["sha256"]
        self.claim["row"]["operation"] = "native_gametest_server"
        self.claim.pop("consumer_ref")
        self.claim.pop("repeat_ref")
        red_result = self.result(task_executed=True)
        red_result["row"] = self.claim["row"]
        red_result.update(outcome="expected_red", exit_code=1, selected_count=1,
                          witness_outcomes={"baseline": "expected failure"})
        self.claim["result_ref"] = self.write("red-result.json", red_result)
        spec = {"schema": g6.RED_SCHEMA, "row": self.claim["row"],
                "scope_sha256": self.scope_hash, "expected_exit_code": 1,
                "witness_outcomes": {"baseline": "expected failure"},
                "review_evidence": [self.raw_pin]}
        self.claim["expected_red_ref"] = self.write("red-spec.json", spec)
        self.assertEqual("historical_red", self.check()["accepted"][0]["state"])
        self.claim.pop("expected_red_ref")
        self.assert_rejected("evidence reference needs")

    def test_rejects_unknown_and_duplicate_rows(self):
        duplicate = self.check([self.claim, copy.deepcopy(self.claim)])
        self.assertEqual(0, len(duplicate["accepted"]))
        self.assertEqual(1, len(duplicate["checked_but_not_joinable"]))
        self.assertIn("duplicate row", duplicate["rejected"][0]["reason"])
        mutant = copy.deepcopy(self.claim)
        mutant["row"]["projection"] = "absent/mc-1.20.2"
        self.assert_rejected("absent or not required", [mutant])

    def test_rejects_missing_or_reordered_classpath(self):
        self.consumer["ordered_classpath"][1]["index"] = 3
        self.replace_consumer()
        self.assert_rejected("reordered")

    def test_rejects_omitted_last_derived_jar_with_contiguous_indices(self):
        self.consumer["ordered_classpath"].pop()
        self.replace_consumer()
        self.assert_rejected("consumer trace is incomplete")

    def test_rejects_upstream_pin_mismatch(self):
        self.consumer["ordered_classpath"][0]["pin"]["digest"] = h("9")
        self.replace_consumer()
        self.assert_rejected("pin source does not declare")

    def test_rejects_pin_source_with_wrong_coordinate(self):
        source = {"schema": g6.PIN_SOURCE_SCHEMA, "domain": "selected-lock",
                  "coordinate": "other:artifact:1", "algorithm": "sha256", "digest": h("1"),
                  "source_document_sha256": self.raw_pin["sha256"],
                  "raw_evidence": [self.raw_pin]}
        self.consumer["ordered_classpath"][0]["pin"]["source"] = self.write(
            "wrong-pin-source.json", source)
        self.replace_consumer()
        self.assert_rejected("pin source does not declare")

    def test_rejects_pin_claiming_another_selected_lock(self):
        source = {"schema": g6.PIN_SOURCE_SCHEMA, "domain": "selected-lock",
                  "coordinate": "a:b:1", "algorithm": "sha256", "digest": h("1"),
                  "source_document_sha256": self.raw_pin["sha256"],
                  "raw_evidence": [self.raw_pin]}
        self.consumer["ordered_classpath"][0]["pin"]["source"] = self.write(
            "other-lock-pin.json", source)
        self.replace_consumer()
        self.assert_rejected("another selected lock")

    def test_rejects_evidence_path_escape(self):
        self.run["raw_evidence"] = [{"path": "../outside.log", "sha256": h("1")}]
        self.replace_run()
        self.assert_rejected("evidence path escapes root")

    def test_rejects_repeat_with_different_input_inventory(self):
        self.repeat["input_before_sha256"] = h("5")
        self.repeat["input_after_sha256"] = h("5")
        self.claim["repeat_ref"] = self.write("repeat-other-input.json", self.repeat)
        self.assert_rejected("different input inventory")

    def test_rejects_online_gradle_resolution(self):
        self.run["offline"] = False
        self.replace_run()
        self.assert_rejected("offline resolution")

    def test_rejects_derived_producer_mismatch(self):
        self.consumer["ordered_classpath"][1]["tool_sha256"] = h("9")
        self.replace_consumer()
        self.assert_rejected("producer tool_sha256 differs")

    def test_rejects_source_drift_and_missing_writer_exclusion(self):
        self.run["input_after_sha256"] = h("9")
        self.replace_run()
        self.assert_rejected("identity drifted")
        self.run["input_after_sha256"] = h("4")
        self.run["writer_excluded"] = False
        self.replace_run()
        self.assert_rejected("writer exclusion")

    def test_rejects_opaque_gradle_or_up_to_date_only_first_result(self):
        self.run["rust_invoked"] = True
        self.replace_run()
        self.assert_rejected("Rust independence")
        self.run["rust_invoked"] = False
        self.run["task_executed"] = False
        self.replace_run()
        self.assert_rejected("did not execute")

    def test_rejects_changed_evidence_file(self):
        (self.root / "run.log").write_text("changed\n", encoding="utf-8")
        self.assert_rejected("evidence SHA-256 differs")

    def test_normalizes_sha1_of_ascii_inner_hash(self):
        inner = "80b4dc3bf85a97116e13694a47eeb22f498a2bfe"
        outer = hashlib.sha1(inner.encode("ascii")).hexdigest()
        joined = {"schema": g6.DIGEST_JOIN_SCHEMA, "artifact_sha256": h("1"),
                  "algorithm": "sha1", "expected": outer, "observed": outer,
                  "semantics": "sha1_of_lower_hex_sha1", "inner_sha1": inner}
        entry = self.consumer["ordered_classpath"][0]
        entry["pin"]["algorithm"] = "sha1"
        entry["pin"]["digest"] = outer
        entry["pin"]["source"] = self.write("pin-source-sha1.json", {
            "schema": g6.PIN_SOURCE_SCHEMA, "domain": "tracked-supplement",
            "coordinate": "a:b:1", "algorithm": "sha1", "digest": outer,
            "source_document_sha256": self.raw_pin["sha256"],
            "raw_evidence": [self.raw_pin],
        })
        entry["digest_join_ref"] = self.write("join.json", joined)
        self.replace_consumer()
        self.assertEqual(1, len(self.check()["accepted"]))
        joined["inner_sha1"] = "0" * 40
        entry["digest_join_ref"] = self.write("join-wrong.json", joined)
        self.replace_consumer()
        self.assert_rejected("inconsistent digest-join semantics")


if __name__ == "__main__":
    unittest.main()
