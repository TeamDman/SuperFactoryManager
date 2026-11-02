#!/usr/bin/env python3
"""Validate typed G6 evidence against the frozen projection/operation scope.

This produces validated claim deltas, not a new cumulative green count. Older
heterogeneous receipts must first be normalized and reviewed. The coordinator
alone joins accepted claims to the cumulative ledger at a frozen checkpoint.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import ntpath
import os
import re
from pathlib import Path
from typing import Any

SCHEMA = "sfm:g6_typed_claim_batch@1"
RESULT_SCHEMA = "sfm:g6_typed_result@1"
CONSUMER_SCHEMA = "sfm:g6_typed_consumer@1"
TRACE_SCHEMA = "sfm:g6_ordered_consumer_trace@1"
PRODUCER_SCHEMA = "sfm:g6_typed_producer@1"
DIGEST_JOIN_SCHEMA = "sfm:g6_typed_digest_join@1"
PIN_SOURCE_SCHEMA = "sfm:g6_typed_pin_source@1"
HEAD_SCHEMA = "sfm:g6_typed_ledger_head@1"
RED_SCHEMA = "sfm:g6_typed_expected_red@1"
COMPILE_OPERATIONS = {"native_compile", "gradle_compile"}
TEST_OPERATIONS = {
    "native_gametest_server", "portable_fe_gametest",
    "infrastructure_guard_gametest", "infrastructure_negative_witnesses",
    "dev26_ae2_inscribers", "progress_gametest",
}
HEX = re.compile(r"^[0-9a-fA-F]+$")


def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=unique_object)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def digest(value: Any, algorithm: str = "sha256") -> str:
    lengths = {"sha256": 64, "sha1": 40, "blake3-20": 40, "sha512": 128}
    if algorithm not in lengths or not isinstance(value, str):
        raise ValueError(f"invalid {algorithm} digest")
    prefix = "blake3:" if algorithm == "blake3-20" else f"{algorithm}:"
    value = value.removeprefix(prefix)
    if len(value) != lengths[algorithm] or not HEX.fullmatch(value):
        raise ValueError(f"invalid {algorithm} digest: {value!r}")
    return value.lower()


def row_id(row: dict[str, Any]) -> tuple[str, str, str]:
    return tuple(row[name] for name in ("projection", "profile", "operation"))


def stable_inventory_hash(value: dict[str, Any]) -> str:
    """Hash the recorded source and exact preflight files, excluding free space."""
    files = value.get("files")
    if not isinstance(files, list) or not files:
        raise ValueError("trace preflight lacks an input file inventory")
    normalized = []
    for item in files:
        if not isinstance(item, dict) or not isinstance(item.get("path"), str) \
                or not ntpath.isabs(item["path"]):
            raise ValueError("trace preflight has a nonabsolute input path")
        normalized.append({"path": item["path"], "sha256": digest(item.get("sha256"))})
    if len({item["path"].lower() for item in normalized}) != len(normalized):
        raise ValueError("trace preflight repeats an input path")
    source = digest(value.get("source"))
    head = value.get("head")
    if not isinstance(head, str) or not re.fullmatch(r"[0-9a-fA-F]{40}", head):
        raise ValueError("trace preflight lacks a Git head")
    encoded = json.dumps({"head": head.lower(), "source": source, "files": normalized},
                         separators=(",", ":"), sort_keys=True).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def adapt_stable_gradle_trace(receipt: dict[str, Any], stdout: str,
                              row: dict[str, Any], receipt_ref: dict[str, str],
                              stdout_ref: dict[str, str]) -> dict[str, Any]:
    """Adapt an existing stable Gradle trace; never infer a missing classpath item.

    The original receipt and Gradle stdout must both be SHA-checked by Evidence.
    The adapter returns only a consumer trace, not upstream pin or producer proof.
    """
    if receipt.get("schema") != "sfm:supported_historical1202_ordered_compile_classpath@1" \
            or receipt.get("projection") != row.get("projection") \
            or row.get("operation") != "gradle_compile" \
            or receipt.get("stable") is not True \
            or receipt.get("exit_code") != 0 \
            or receipt.get("build_successful") is not True:
        raise ValueError("receipt is not a successful stable Gradle compile trace")
    if digest(receipt.get("stdout_sha256")) != digest(stdout_ref.get("sha256")):
        raise ValueError("trace stdout SHA-256 differs from receipt")
    before = receipt.get("before")
    after = receipt.get("after")
    if not isinstance(before, dict) or not isinstance(after, dict):
        raise ValueError("trace lacks before/after input inventory")
    before_hash = stable_inventory_hash(before)
    after_hash = stable_inventory_hash(after)
    if before_hash != after_hash:
        raise ValueError("trace input inventory changed")
    files = before["files"]
    lock = [item for item in files if item["path"].lower().endswith("sfm-toolchain.lock.json")]
    jdk = [item for item in files if item["path"].lower().endswith(r"\bin\java.exe")]
    gradle = [item for item in files if item["path"].lower().endswith(r"\bin\gradle.bat")]
    if len(lock) != 1 or len(jdk) != 1 or len(gradle) != 1 \
            or digest(lock[0]["sha256"]) != digest(row.get("lock_sha256")):
        raise ValueError("trace lacks exact selected lock, Gradle, or JDK executable")
    if "> Task :sfmTraceCompileClasspath" not in stdout:
        raise ValueError("trace task execution is absent from stdout")
    captured = []
    totals = []
    for line in stdout.splitlines():
        if line.startswith("SFM_TRACE_COMPILE\t"):
            parts = line.split("\t", 4)
            if len(parts) != 5:
                raise ValueError("malformed ordered classpath entry")
            _, index, size, sha, path = parts
            if not index.isdecimal() or not size.isdecimal() or not ntpath.isabs(path):
                raise ValueError("invalid ordered classpath index, size, or path")
            captured.append({"index": int(index), "path": path, "bytes": int(size),
                             "sha256": digest(sha)})
        elif line.startswith("SFM_TRACE_TOTAL\t"):
            totals.append(line)
    if not captured or len(totals) != 1 or receipt.get("ordered_classpath") != [
        f'SFM_TRACE_COMPILE\t{entry["index"]}\t{entry["bytes"]}\t{entry["sha256"]}\t{entry["path"]}'
        for entry in captured
    ] or receipt.get("total") != totals:
        raise ValueError("receipt differs from raw ordered classpath capture")
    for index, entry in enumerate(captured):
        if entry["index"] != index or entry["bytes"] <= 0:
            raise ValueError("ordered classpath is incomplete or reordered")
    total = totals[0].split("\t")
    if len(total) != 3 or not total[1].isdecimal() or not total[2].isdecimal() \
            or int(total[1]) != len(captured) \
            or int(total[2]) != sum(entry["bytes"] for entry in captured):
        raise ValueError("ordered classpath total count or bytes differ")
    tools = [item for item in files if item is not lock[0] and item is not jdk[0]]
    tool_identity = hashlib.sha256(json.dumps(tools, sort_keys=True,
                                               separators=(",", ":")).encode("utf-8")).hexdigest()
    return {"schema": TRACE_SCHEMA,
            "row": {key: row[key] for key in ("projection", "profile", "operation")},
            "lock_sha256": digest(lock[0]["sha256"]),
            "source_sha256": digest(before["source"]),
            "tool_sha256": tool_identity,
            "tool_identity_basis": "recorded-preflight-file-inventory",
            "jdk_sha256": digest(jdk[0]["sha256"]),
            "complete": True, "configured_count": len(captured),
            "capture_command": "Gradle sfmTraceCompileClasspath",
            # A stable receipt is not proof that all other writers were excluded.
            # A coordinator-owned source-freeze receipt must establish that separately.
            "writer_excluded": False,
            "input_before_sha256": before_hash, "input_after_sha256": after_hash,
            "ordered_classpath": captured,
            "raw_evidence": [receipt_ref, stdout_ref]}


def pinned_digest(value: Any) -> tuple[str, str]:
    """Preserve the pin's algorithm; never equate SHA-1 or BLAKE3 with SHA-256."""
    if not isinstance(value, str):
        raise ValueError("pin has no digest")
    if value.startswith("blake3:"):
        algorithm = "blake3-20"
    elif value.startswith("sha1:"):
        algorithm = "sha1"
    elif value.startswith("sha256:") or len(value) == 64:
        algorithm = "sha256"
    else:
        raise ValueError("unknown pin digest algorithm")
    return algorithm, digest(value, algorithm)


def normalize_classified_upstream(classified: dict[str, Any],
                                  captured: dict[str, Any],
                                  document: dict[str, Any],
                                  document_sha256: str,
                                  selected_lock_sha256: str) -> dict[str, Any]:
    """Join one captured JAR to its exact lock, version or supplement record.

    `classified` is an earlier measured-digest witness. This does not recompute
    BLAKE3 from a live JAR or upgrade a SHA-1 pin to SHA-256 provenance.
    """
    if classified.get("status") != "exact" or any(
        classified.get(field) != captured.get(field)
        for field in ("index", "path", "bytes")
    ) or digest(classified.get("sha256")) != digest(captured.get("sha256")):
        raise ValueError("classified artifact differs from captured classpath")
    coordinate = classified.get("coordinate")
    if not isinstance(coordinate, str) or not coordinate:
        raise ValueError("upstream artifact lacks a coordinate")
    kind = classified.get("kind")
    if kind == "selected-historical-lock":
        if digest(document_sha256) != digest(selected_lock_sha256):
            raise ValueError("selected lock document hash differs")
        records = [item for item in document.get("artifacts", [])
                   if item.get("coordinate") == coordinate]
        if len(records) != 1 or records[0].get("source") != "remote-maven":
            raise ValueError("selected lock has no unique remote Maven artifact")
        record = records[0]
        recorded_pin = record.get("hash")
        domain = "selected-lock"
        source_url = record.get("url")
    elif kind == "frozen-minecraft-version-library":
        records = [item for item in document.get("libraries", [])
                   if item.get("name") == coordinate]
        if len(records) != 1:
            raise ValueError("version snapshot has no unique library")
        record = records[0].get("downloads", {}).get("artifact", {})
        if record.get("size") != captured["bytes"]:
            raise ValueError("version snapshot library size differs")
        recorded_pin = "sha1:" + str(record.get("sha1", ""))
        domain = "minecraft-version-snapshot"
        source_url = record.get("url")
    elif kind == "checked-antlr-supplement":
        if digest(document.get("selected_lock_sha256")) != digest(selected_lock_sha256):
            raise ValueError("supplement selects another lock")
        records = [item for item in document.get("files", [])
                   if item.get("coordinate") == coordinate and item.get("kind") == "jar"]
        if len(records) != 1 or records[0].get("bytes") != captured["bytes"]:
            raise ValueError("supplement has no exact unique JAR")
        record = records[0]
        recorded_pin = record.get("sha256")
        domain = "tracked-supplement"
        source_url = record.get("url")
    else:
        raise ValueError(f"unknown upstream pin source: {kind}")
    algorithm, expected = pinned_digest(recorded_pin)
    classified_algorithm, classified_expected = pinned_digest(classified.get("expected"))
    observed_algorithm, observed = pinned_digest(classified.get("actual"))
    if (algorithm, expected) != (classified_algorithm, classified_expected) \
            or (algorithm, expected) != (observed_algorithm, observed):
        raise ValueError("recorded pin or measured digest differs")
    if not isinstance(source_url, str) or not source_url.startswith("https://"):
        raise ValueError("upstream pin lacks an HTTPS source URL")
    return {"index": captured["index"], "kind": "upstream",
            "path": captured["path"], "coordinate": coordinate,
            "bytes": captured["bytes"], "sha256": digest(captured["sha256"]),
            "pin": {"algorithm": algorithm, "digest": expected,
                    "domain": domain, "source_document_sha256": digest(document_sha256),
                    "source_url": source_url},
            "digest_witness": "classified-measurement"}


def normalize_recorded_upstream(classification: dict[str, Any],
                                trace: dict[str, Any],
                                documents: dict[str, tuple[str, bytes]]) -> list[dict[str, Any]]:
    """Normalize all upstream entries; reject missing or changed source documents."""
    if classification.get("projection") != trace.get("row", {}).get("projection"):
        raise ValueError("classification and trace name different projections")
    entries = classification.get("entries")
    captured = trace.get("ordered_classpath")
    if not isinstance(entries, list) or not isinstance(captured, list) \
            or len(entries) != len(captured):
        raise ValueError("classification does not cover the complete classpath")
    recorded = {}
    for side in ("before", "after"):
        files = classification.get(side)
        if not isinstance(files, list):
            raise ValueError("classification lacks source-document inventory")
        recorded[side] = {item["path"].lower(): digest(item["sha256"])
                          for item in files}
        if len(recorded[side]) != len(files):
            raise ValueError("classification repeats a source document")
    if recorded["before"] != recorded["after"]:
        raise ValueError("classification source documents changed")
    selected_lock_sha256 = trace.get("lock_sha256")
    source_documents = {}
    for kind in ("selected-historical-lock", "frozen-minecraft-version-library",
                 "checked-antlr-supplement"):
        path, data = documents[kind]
        sha = hashlib.sha256(data).hexdigest()
        if recorded["before"].get(path.lower()) != sha:
            raise ValueError(f"recorded source document differs: {kind}")
        source_documents[kind] = (read_json_bytes(data), sha)
    result = []
    for index, classified in enumerate(entries):
        current = captured[index]
        if classified.get("index") != index or current.get("index") != index:
            raise ValueError("classification order differs from captured trace")
        if classified.get("kind") == "derived-neogradle":
            continue
        document, sha = source_documents[classified["kind"]]
        result.append(normalize_classified_upstream(
            classified, current, document, sha, selected_lock_sha256))
    if len(result) + sum(item.get("kind") == "derived-neogradle" for item in entries) \
            != len(captured):
        raise ValueError("unclassified classpath input")
    return result


def read_json_bytes(data: bytes) -> Any:
    return json.loads(data.decode("utf-8-sig"), object_pairs_hook=unique_object)


def normalize_recorded_derived(classification: dict[str, Any],
                               trace: dict[str, Any],
                               adjudication_bytes: bytes,
                               comparison_bytes: bytes,
                               join: dict[str, Any]) -> list[dict[str, Any]]:
    """Join rebuilt outputs to producer evidence without inventing recipe keys.

    One adjudication can support several consumers only when their later typed
    recipe, tool, relevant-input and output identities all match. This legacy
    adjudication proves output handoff and replay, but lacks those complete keys.
    """
    adjudication = read_json_bytes(adjudication_bytes)
    comparison = read_json_bytes(comparison_bytes)
    if adjudication.get("schema") != "sfm:historical1202_fresh_producer_adjudication@1" \
            or comparison.get("schema") != "sfm:historical1202_rebuilt_jar_entry_comparison@1" \
            or join.get("schema") != "sfm:historical1202_rebuilt_classpath_join@1" \
            or digest(adjudication.get("source_sha256")) != digest(trace.get("source_sha256")) \
            or digest(adjudication.get("archive_comparison_sha256")) \
            != hashlib.sha256(comparison_bytes).hexdigest() \
            or digest(join.get("producer_adjudication_sha256")) \
            != hashlib.sha256(adjudication_bytes).hexdigest() \
            or digest(join.get("archive_comparison_sha256")) \
            != hashlib.sha256(comparison_bytes).hexdigest() \
            or digest(join.get("new_trace_sha256")) \
            != digest(trace.get("raw_evidence", [{}])[0].get("sha256")):
        raise ValueError("derived producer evidence identity differs")
    if adjudication.get("handoffs_equal") is not True:
        raise ValueError("derived producer output handoff differs")
    tasks = adjudication.get("task_results")
    if not isinstance(tasks, list) or not tasks or any(
        item.get("executed") is not True or item.get("count") != 1
        for item in tasks
    ):
        raise ValueError("derived producer tasks did not all execute once")
    outputs = adjudication.get("published_jars")
    pairs = comparison.get("pairs")
    changed = join.get("derived_changed")
    entries = classification.get("entries")
    captured = trace.get("ordered_classpath")
    if not all(isinstance(value, list) for value in (outputs, pairs, changed, entries, captured)) \
            or len(entries) != len(captured):
        raise ValueError("derived producer inventory is incomplete")
    derived = []
    for index, classified in enumerate(entries):
        if classified.get("kind") != "derived-neogradle":
            continue
        entry = captured[index]
        if classified.get("index") != index or entry.get("index") != index \
                or classified.get("path") != entry.get("path") \
                or classified.get("bytes") != entry.get("bytes"):
            raise ValueError("derived output changed position or path")
        matches = [item for item in outputs if item.get("path") == entry["path"]
                   and item.get("bytes") == entry["bytes"]
                   and digest(item.get("sha256")) == digest(entry["sha256"])]
        archive_matches = [item for item in pairs
                           if digest(item.get("after_sha256")) == digest(entry["sha256"])
                           and item.get("after_bytes") == entry["bytes"]]
        join_matches = [item for item in changed if item.get("index") == index
                        and item.get("path") == entry["path"]
                        and item.get("bytes") == entry["bytes"]
                        and digest(item.get("after_sha256")) == digest(entry["sha256"])]
        if len(matches) != 1 or len(archive_matches) != 1 or len(join_matches) != 1:
            raise ValueError("derived output lacks exact publication/archive join")
        archive = archive_matches[0]
        if any(archive.get(field) != 0 for field in (
            "missing_or_added", "payload_mismatches", "attribute_changes",
            "compressed_length_changes", "order_changes",
        )):
            raise ValueError("derived archive payload or structure differs")
        derived.append({"index": index, "kind": "derived", "path": entry["path"],
                        "bytes": entry["bytes"], "sha256": digest(entry["sha256"]),
                        "adjudication_sha256": hashlib.sha256(adjudication_bytes).hexdigest(),
                        "comparison_sha256": hashlib.sha256(comparison_bytes).hexdigest(),
                        "producer_task_set": [item["task"] for item in tasks],
                        "input_log_sha256": digest(adjudication.get("input_log_sha256")),
                        "tool_sha256": None, "recipe_id": None,
                        "relevant_inputs_sha256": None,
                        "claim_ready": False})
    if len(derived) != len(changed) or not derived:
        raise ValueError("derived producer classification is incomplete")
    return derived


def normalize_recorded_consumer(trace: dict[str, Any],
                                classification: dict[str, Any],
                                documents: dict[str, tuple[str, bytes]],
                                adjudication_bytes: bytes,
                                comparison_bytes: bytes,
                                join: dict[str, Any]) -> dict[str, Any]:
    """Produce a complete ordered evidence inventory, never a credited G6 row."""
    upstream = normalize_recorded_upstream(classification, trace, documents)
    derived = normalize_recorded_derived(classification, trace, adjudication_bytes,
                                        comparison_bytes, join)
    ordered = sorted(upstream + derived, key=lambda item: item["index"])
    if len(ordered) != trace.get("configured_count") or any(
        item["index"] != index for index, item in enumerate(ordered)
    ):
        raise ValueError("normalized records do not cover the ordered classpath")
    return {"schema": "sfm:g6_recorded_consumer_normalization@1",
            "row": trace["row"], "configured_count": len(ordered),
            "upstream_count": len(upstream), "derived_count": len(derived),
            "ordered_inputs": ordered, "claim_ready": False,
            "remaining_proofs": [
                "coordinator writer exclusion and active-reader clearance",
                "typed producer recipe/tool/relevant-input identity for derived outputs",
                "source-stable executed consumer task and unchanged repeat",
                "authoritative current ledger head",
            ]}


class Evidence:
    def __init__(self, root: Path):
        self.root = root.resolve(strict=True)
        self.cache: dict[tuple[str, str], Any] = {}
        self.checked: dict[Path, str] = {}

    def checked_ref(self, ref: Any) -> tuple[Path, str]:
        if not isinstance(ref, dict) or set(ref) != {"path", "sha256"}:
            raise ValueError("evidence reference needs path and sha256")
        name = ref["path"]
        if not isinstance(name, str) or not name or Path(name).is_absolute():
            raise ValueError("evidence path must be nonempty and relative")
        if ".." in Path(name).parts:
            raise ValueError("evidence path escapes root")
        path = (self.root / name).resolve(strict=True)
        if not path.is_relative_to(self.root) or not path.is_file():
            raise ValueError(f"evidence path escapes root or is not a file: {name}")
        expected = digest(ref["sha256"])
        if sha256_file(path) != expected:
            raise ValueError(f"evidence SHA-256 differs: {name}")
        self.checked[path] = expected
        return path, expected

    def recheck(self) -> None:
        for path, expected in self.checked.items():
            if sha256_file(path) != expected:
                raise ValueError(f"evidence changed during validation: {path.name}")

    def load(self, ref: Any, schema: str) -> dict[str, Any]:
        path, checked = self.checked_ref(ref)
        key = (str(path), checked)
        if key not in self.cache:
            self.cache[key] = read_json(path)
        value = self.cache[key]
        if not isinstance(value, dict) or value.get("schema") != schema:
            raise ValueError(f"wrong evidence schema: {path.name}")
        return value


def verify_digest_join(entry: dict[str, Any], evidence: Evidence,
                       selected_lock_sha256: str) -> None:
    pin = entry.get("pin")
    if not isinstance(pin, dict) or set(pin) != {"algorithm", "digest", "source"}:
        raise ValueError("upstream input needs an exact pin with source")
    algorithm = pin["algorithm"]
    expected = digest(pin["digest"], algorithm)
    source = evidence.load(pin["source"], PIN_SOURCE_SCHEMA)
    if source.get("coordinate") != entry.get("coordinate") \
            or source.get("algorithm") != algorithm \
            or digest(source.get("digest"), algorithm) != expected \
            or source.get("domain") not in {
                "selected-lock", "minecraft-version-snapshot", "tracked-supplement",
                "official-publisher",
            }:
        raise ValueError("pin source does not declare selected artifact identity")
    raw = source.get("raw_evidence")
    if not isinstance(raw, list) or not raw:
        raise ValueError("pin source lacks underlying lock or publisher evidence")
    document_hash = digest(source.get("source_document_sha256"))
    if source["domain"] == "selected-lock" \
            and document_hash != digest(selected_lock_sha256):
        raise ValueError("pin source names another selected lock")
    witnessed = False
    for ref in raw:
        _, checked = evidence.checked_ref(ref)
        witnessed |= checked == document_hash
    if not witnessed:
        raise ValueError("pin source document hash is not in raw evidence")
    observed_sha = digest(entry.get("sha256"))
    if algorithm == "sha256":
        if observed_sha != expected:
            raise ValueError("upstream SHA-256 pin differs")
        return
    joined = evidence.load(entry.get("digest_join_ref"), DIGEST_JOIN_SCHEMA)
    if digest(joined.get("artifact_sha256")) != observed_sha:
        raise ValueError("digest join names another artifact")
    if joined.get("algorithm") != algorithm or digest(joined.get("expected"), algorithm) != expected:
        raise ValueError("digest join names another pin")
    if digest(joined.get("observed"), algorithm) != expected:
        raise ValueError("observed upstream digest differs")
    semantics = joined.get("semantics")
    if semantics == "direct":
        return
    if semantics == "sha1_of_lower_hex_sha1" and algorithm == "sha1":
        inner = digest(joined.get("inner_sha1"), "sha1")
        if hashlib.sha1(inner.encode("ascii")).hexdigest() == expected:
            return
    raise ValueError("unknown or inconsistent digest-join semantics")


def verify_producer(entry: dict[str, Any], evidence: Evidence) -> None:
    producer = evidence.load(entry.get("producer_ref"), PRODUCER_SCHEMA)
    for field in ("recipe_id", "tool_sha256", "relevant_inputs_sha256"):
        if field not in entry or field not in producer or entry[field] != producer[field]:
            raise ValueError(f"derived producer {field} differs")
    for field in ("tool_sha256", "relevant_inputs_sha256", "output_sha256"):
        digest(producer[field])
    if digest(entry.get("sha256")) != digest(producer["output_sha256"]):
        raise ValueError("derived producer output SHA-256 differs")
    if entry.get("bytes") != producer.get("output_bytes"):
        raise ValueError("derived producer output size differs")
    if producer.get("exit_code") != 0 or producer.get("inputs_stable") is not True:
        raise ValueError("derived producer did not finish on stable inputs")
    if producer.get("reader_safe") is not True:
        raise ValueError("derived producer lacks immutable reader protection")
    raw = producer.get("raw_evidence")
    if not isinstance(raw, list) or not raw:
        raise ValueError("derived producer lacks raw evidence")
    for ref in raw:
        evidence.checked_ref(ref)


def verify_consumer(claim: dict[str, Any], evidence: Evidence) -> None:
    consumer = evidence.load(claim.get("consumer_ref"), CONSUMER_SCHEMA)
    if row_id(consumer.get("row", {})) != row_id(claim["row"]):
        raise ValueError("consumer names another frozen row")
    if digest(consumer.get("lock_sha256")) != digest(claim["lock_sha256"]):
        raise ValueError("consumer lock differs")
    for field in ("source_sha256", "tool_sha256", "jdk_sha256"):
        if digest(consumer.get(field)) != digest(claim[field]):
            raise ValueError(f"consumer {field} differs")
    entries = consumer.get("ordered_classpath")
    if not isinstance(entries, list) or not entries:
        raise ValueError("compile consumer needs nonempty ordered classpath")
    trace = evidence.load(consumer.get("trace_ref"), TRACE_SCHEMA)
    if row_id(trace.get("row", {})) != row_id(claim["row"]) \
            or trace.get("complete") is not True \
            or trace.get("configured_count") != len(entries) \
            or not isinstance(trace.get("capture_command"), str) \
            or not trace["capture_command"] \
            or trace.get("writer_excluded") is not True \
            or digest(trace.get("input_before_sha256")) != digest(trace.get("input_after_sha256")):
        raise ValueError("consumer trace is incomplete or changed during capture")
    for field in ("lock_sha256", "source_sha256", "tool_sha256", "jdk_sha256"):
        if digest(trace.get(field)) != digest(claim[field]):
            raise ValueError(f"consumer trace {field} differs")
    trace_entries = trace.get("ordered_classpath")
    if not isinstance(trace_entries, list) or len(trace_entries) != len(entries):
        raise ValueError("consumer trace count differs from claimed classpath")
    trace_raw = trace.get("raw_evidence")
    if not isinstance(trace_raw, list) or not trace_raw:
        raise ValueError("consumer trace lacks raw capture evidence")
    for ref in trace_raw:
        evidence.checked_ref(ref)
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict) or entry.get("index") != index:
            raise ValueError(f"classpath index is missing or reordered at {index}")
        if not isinstance(entry.get("coordinate"), str) or not entry["coordinate"]:
            raise ValueError(f"classpath coordinate missing at {index}")
        if not isinstance(entry.get("path"), str) or not entry["path"]:
            raise ValueError(f"classpath path missing at {index}")
        if not isinstance(entry.get("bytes"), int) or entry["bytes"] <= 0:
            raise ValueError(f"classpath size missing at {index}")
        digest(entry.get("sha256"))
        captured = trace_entries[index]
        if not isinstance(captured, dict) or any(
            captured.get(field) != entry[field]
            for field in ("index", "path", "bytes", "sha256")
        ):
            raise ValueError(f"claimed classpath differs from captured trace at {index}")
        if "coordinate" in captured and captured["coordinate"] != entry["coordinate"]:
            raise ValueError(f"claimed coordinate differs from captured trace at {index}")
        if entry.get("kind") == "upstream":
            verify_digest_join(entry, evidence, claim["lock_sha256"])
        elif entry.get("kind") == "derived":
            verify_producer(entry, evidence)
        else:
            raise ValueError(f"unknown classpath origin at {index}")


def verify_result(ref: Any, row: dict[str, Any], claim: dict[str, Any],
                  evidence: Evidence, scope_hash: str,
                  repeat: bool = False) -> dict[str, Any]:
    result = evidence.load(ref, RESULT_SCHEMA)
    if row_id(result.get("row", {})) != row_id(row):
        raise ValueError("result names another frozen row")
    for field in ("lock_sha256", "source_sha256", "tool_sha256", "jdk_sha256"):
        if digest(result.get(field)) != digest(claim.get(field)):
            raise ValueError(f"result {field} differs")
    if digest(result.get("input_before_sha256")) != digest(result.get("input_after_sha256")):
        raise ValueError("result input identity drifted")
    if result.get("writer_excluded") is not True or result.get("children_exited") is not True:
        raise ValueError("result lacks writer exclusion or child exit")
    raw = result.get("raw_evidence")
    if not isinstance(raw, list) or not raw:
        raise ValueError("result lacks raw evidence")
    for item in raw:
        evidence.checked_ref(item)
    outcome = result.get("outcome")
    if outcome == "pass":
        if result.get("exit_code") != 0:
            raise ValueError("passing result has nonzero exit")
    elif outcome == "expected_red" and row["operation"] in TEST_OPERATIONS and not repeat:
        red = evidence.load(claim.get("expected_red_ref"), RED_SCHEMA)
        if row_id(red.get("row", {})) != row_id(row) \
                or digest(red.get("scope_sha256")) != scope_hash \
                or red.get("expected_exit_code") != result.get("exit_code") \
                or not isinstance(red.get("witness_outcomes"), dict) \
                or not red["witness_outcomes"] \
                or red["witness_outcomes"] != result.get("witness_outcomes"):
            raise ValueError("expected-red result lacks matching frozen review")
        reviewed = red.get("review_evidence")
        if not isinstance(reviewed, list) or not reviewed:
            raise ValueError("expected-red review lacks source evidence")
        for item in reviewed:
            evidence.checked_ref(item)
    else:
        raise ValueError("result outcome is not a supported pass or reviewed expected red")
    operation = row["operation"]
    if operation in COMPILE_OPERATIONS and result.get("task_executed") is not True and not repeat:
        raise ValueError("compile result did not execute the selected task")
    if operation == "gradle_compile" and result.get("rust_invoked") is not False:
        raise ValueError("Gradle fallback did not establish Rust independence")
    if operation == "gradle_compile" and (result.get("offline") is not True
                                           or result.get("network_downloads") != 0):
        raise ValueError("Gradle compile lacks verified offline resolution")
    minimum = row.get("minimum_selected")
    if minimum is not None and result.get("selected_count", -1) < minimum:
        raise ValueError("selected test count below frozen minimum")
    if operation in TEST_OPERATIONS and (not isinstance(result.get("selected_count"), int)
                                      or result["selected_count"] <= 0):
        raise ValueError("test operation selected no tests")
    expected_witnesses = row.get("witness_outcomes")
    if expected_witnesses is not None and result.get("witness_outcomes") != expected_witnesses:
        raise ValueError("witness outcomes differ from frozen selection")
    return result


def validate_claim(claim: Any, rows: dict[tuple[str, str, str], dict[str, Any]],
                   evidence: Evidence, scope_hash: str) -> tuple[tuple[str, str, str], str]:
    if not isinstance(claim, dict) or not isinstance(claim.get("row"), dict):
        raise ValueError("claim lacks row")
    identity = row_id(claim["row"])
    row = rows.get(identity)
    if row is None or row.get("status") != "required":
        raise ValueError("row is absent or not required in frozen scope")
    if digest(claim.get("lock_sha256")) != digest(row.get("lock_sha256")):
        raise ValueError("claim lock differs from frozen scope")
    for field in ("source_sha256", "tool_sha256", "jdk_sha256"):
        digest(claim.get(field))
    if row["operation"] in COMPILE_OPERATIONS:
        verify_consumer(claim, evidence)
    result = verify_result(claim.get("result_ref"), row, claim, evidence, scope_hash)
    if row["operation"] in COMPILE_OPERATIONS:
        repeated = verify_result(claim.get("repeat_ref"), row, claim, evidence,
                                 scope_hash, repeat=True)
        if digest(result["input_before_sha256"]) != digest(repeated["input_before_sha256"]):
            raise ValueError("unchanged repeat names a different input inventory")
        if repeated.get("reuse_proven") is not True:
            raise ValueError("unchanged repeat lacks reuse evidence")
    if claim.get("negative_ref") is not None:
        negative = evidence.load(claim["negative_ref"], RESULT_SCHEMA)
        if row_id(negative.get("row", {})) != identity or negative.get("exit_code") == 0 \
                or negative.get("rejected_before_consumer") is not True:
            raise ValueError("negative mismatch was not rejected before consumption")
        if digest(negative.get("source_sha256")) != digest(claim["source_sha256"]) \
                or digest(negative.get("tool_sha256")) != digest(claim["tool_sha256"]) \
                or negative.get("writer_excluded") is not True \
                or negative.get("children_exited") is not True:
            raise ValueError("negative mismatch used another source/tool or active writer")
        raw_evidence = negative.get("raw_evidence")
        if not isinstance(raw_evidence, list) or not raw_evidence:
            raise ValueError("negative mismatch lacks raw evidence")
        for raw in raw_evidence:
            evidence.checked_ref(raw)
    return identity, "green" if result["outcome"] == "pass" else "historical_red"


def validate_batch(scope: dict[str, Any], scope_hash: str, prior_hash: str,
                   batch: dict[str, Any], evidence_root: Path) -> dict[str, Any]:
    if batch.get("schema") != SCHEMA or digest(batch.get("scope_sha256")) != scope_hash \
            or digest(batch.get("prior_ledger_sha256")) != prior_hash:
        raise ValueError("claim batch has stale scope or prior ledger identity")
    rows = {row_id(row): row for row in scope["rows"]}
    if len(rows) != len(scope["rows"]):
        raise ValueError("frozen scope contains duplicate rows")
    claims = batch.get("claims")
    if not isinstance(claims, list) or not claims:
        raise ValueError("claim batch is empty")
    evidence = Evidence(evidence_root)
    seen: set[tuple[str, str, str]] = set()
    accepted: list[dict[str, str]] = []
    rejected: list[dict[str, Any]] = []
    for index, claim in enumerate(claims):
        try:
            identity, state = validate_claim(claim, rows, evidence, scope_hash)
            if identity in seen:
                raise ValueError("duplicate row in claim batch")
            seen.add(identity)
            accepted.append({**dict(zip(("projection", "profile", "operation"), identity)),
                             "state": state})
        except (KeyError, TypeError, ValueError, OSError) as error:
            rejected.append({"index": index, "reason": str(error)})
    evidence.recheck()
    return {"schema": "sfm:g6_typed_claim_validation@1", "scope_sha256": scope_hash,
            "prior_ledger_sha256": prior_hash,
            "accepted": accepted if not rejected else [],
            "checked_but_not_joinable": accepted if rejected else [],
            "rejected": rejected, "batch_eligible_for_join": not rejected,
            "cumulative_ledger_changed": False}


def verify_ledger_head(head: dict[str, Any], scope_hash: str, prior_hash: str) -> None:
    if head.get("schema") != HEAD_SCHEMA or digest(head.get("scope_sha256")) != scope_hash \
            or digest(head.get("current_ledger_sha256")) != prior_hash \
            or type(head.get("revision")) is not int or head["revision"] < 0:
        raise ValueError("authoritative ledger head does not name the selected prior ledger")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scope", type=Path, required=True)
    parser.add_argument("--scope-sha256", required=True)
    parser.add_argument("--prior-ledger", type=Path, required=True)
    parser.add_argument("--prior-sha256", required=True)
    parser.add_argument("--ledger-head", type=Path, required=True,
                        help="coordinator-owned current-ledger pointer; old ledgers are rejected")
    parser.add_argument("--claims", type=Path, required=True)
    parser.add_argument("--evidence-root", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    scope_hash = digest(args.scope_sha256)
    prior_hash = digest(args.prior_sha256)
    if sha256_file(args.scope) != scope_hash or sha256_file(args.prior_ledger) != prior_hash:
        parser.error("scope or prior ledger SHA-256 differs")
    scope = read_json(args.scope)
    batch = read_json(args.claims)
    try:
        verify_ledger_head(read_json(args.ledger_head), scope_hash, prior_hash)
    except ValueError as error:
        parser.error(str(error))
    head_hash = sha256_file(args.ledger_head)
    result = validate_batch(scope, scope_hash, prior_hash, batch, args.evidence_root)
    if sha256_file(args.prior_ledger) != prior_hash or sha256_file(args.scope) != scope_hash:
        parser.error("scope or prior ledger changed during validation")
    if sha256_file(args.ledger_head) != head_hash:
        parser.error("authoritative ledger head changed during validation")
    if not args.out.parent.is_dir():
        parser.error("output parent must already exist")
    with args.out.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(result, stream, indent=2, sort_keys=True)
        stream.write("\n")
    return 0 if not result["rejected"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
