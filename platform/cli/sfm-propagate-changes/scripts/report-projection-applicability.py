"""Emit a source-backed candidate G6 operation ledger; never run builds.

Usage: python report-projection-applicability.py REPO_ROOT > receipt.json
This is not the G6 scope freeze: plain launch applicability, exact selectors,
Gradle fallback rows and 18 unmaterialized portable locks still need review.
"""

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


OPERATIONS = (
    "source_render",
    "frozen_lock",
    "native_compile",
    "native_jar",
    "named_historical_jar",
    "portable_fe_gametest",
    "infrastructure_guard_gametest",
    "progress_gametest",
    "client_visual",
)


def family_for(key):
    parts = key.split("/")
    if len(parts) != 2 or any(part in ("", ".", "..") or "\\" in part
                              for part in parts):
        raise ValueError(f"Unsafe or invalid projection key: {key}")
    name, target = parts
    if name == "sfm-dev":
        return "dev"
    for release in ("sfm-4.34.0", "sfm-4.35.0"):
        if name == release:
            return "plain-4.34" if release.endswith("4.34.0") else "plain-4.35"
        suffix = name.removeprefix(release) if name.startswith(release + "-") else None
        if suffix in ("-extended", "-issue610-portable", "-issue610", "-issue610-visual"):
            return suffix[1:]
    raise ValueError(f"Unclassified catalog projection: {key}")


def classify(key, family, features, lock_present):
    rows = []
    for operation in OPERATIONS:
        required = False
        reason = ""
        if operation == "source_render":
            required, reason = True, "catalog-declared projection"
        elif operation == "frozen_lock":
            required, reason = True, "catalog-selected frozen dependency graph"
        elif operation in ("native_compile", "native_jar"):
            required = family != "plain-4.35"
            reason = ("reviewed 4.34 native recipe or declared development route" if required
                      else "no reviewed 4.35 plain native release recipe")
        elif operation == "named_historical_jar":
            required = family == "plain-4.34"
            reason = ("ten reviewed 4.34 named recipes" if required
                      else "named historical recipe is scoped to plain 4.34")
        else:
            feature = {
                "portable_fe_gametest": "issue_610_portable_fixture",
                "infrastructure_guard_gametest": "gametest_infrastructure_invariants",
                "progress_gametest": "issue_610_progress_tests",
                "client_visual": "issue_610_visual_evidence",
            }[operation]
            required = feature in features
            reason = f"feature {feature} {'declared' if required else 'absent'}"
        blocked = required and not lock_present and operation != "source_render"
        rows.append({
            "projection": key,
            "operation": operation,
            "status": "blocked" if blocked else ("required" if required else "N/A"),
            "required_when_unblocked": blocked,
            "reason": reason + ("; generated lock absent" if blocked else ""),
            "declaration": "platform/minecraft/projections.json",
        })
    return rows


def report(repo):
    catalog_path = repo / "platform/minecraft/projections.json"
    payload = catalog_path.read_bytes()
    catalog = json.loads(payload)
    if not isinstance(catalog, dict):
        raise ValueError("Projection catalog must be a keyed object")
    recipes = json.loads((repo / "platform/minecraft/build-configuration/released-native.json")
                         .read_text(encoding="utf-8"))
    reviewed_targets = {item["target"] for item in recipes["targets"]}
    rows = []
    keys = []
    for key, projection in sorted(catalog.items()):
        family = family_for(key)
        target = key.split("/", 1)[1].removeprefix("mc-")
        if family == "plain-4.34" and target not in reviewed_targets:
            raise ValueError(f"Missing reviewed 4.34 native recipe for {key}")
        lock_path = repo / "platform/minecraft/projections" / key / "sfm-toolchain.lock.json"
        lock_present = lock_path.is_file()
        lock = json.loads(lock_path.read_text(encoding="utf-8")) if lock_present else None
        features = set(projection["features"])
        keys.append({
            "key": key,
            "family": family,
            "target": target,
            "minecraft_version": projection["minecraft_version"],
            "environment": projection["environment"],
            "features": sorted(features),
            "lock_present": lock_present,
            "lock_sha256": hashlib.sha256(lock_path.read_bytes()).hexdigest()
            if lock_present else None,
            "profiles": [profile["id"] for profile in lock.get("profiles", [])]
            if lock_present else [],
        })
        rows.extend(classify(key, family, features, lock_present))
    return {
        "schema": 1,
        "scope": "source-backed candidate rows; G6 not frozen and no runtime proof implied",
        "catalog_sha256": hashlib.sha256(payload).hexdigest(),
        "catalog_keys": len(keys),
        "materialized_locks": sum(key["lock_present"] for key in keys),
        "families": dict(sorted(Counter(key["family"] for key in keys).items())),
        "row_statuses": dict(sorted(Counter(row["status"] for row in rows).items())),
        "open_decisions": [
            "ordinary plain-release launch applicability",
            "exact guard and progress selectors and selected-test counts",
            "dev 26.1.2 ae2_inscribers zero-match applicability",
            "18 portable generated locks and their declared profiles",
            "separate Gradle fallback and external Prism/tracked-server rows",
        ],
        "keys": keys,
        "rows": rows,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo_root", type=Path)
    args = parser.parse_args()
    print(json.dumps(report(args.repo_root), indent=2))
