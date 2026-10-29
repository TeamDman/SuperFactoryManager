"""Emit a source-backed candidate G6 operation ledger; never run builds.

Usage: python report-projection-applicability.py REPO_ROOT > receipt.json
This is not the G6 scope freeze: plain launch applicability, exact selectors,
Gradle fallback qualification and 18 unmaterialized portable locks still need review.
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
    "gradle_compile",
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


def applicability_profiles(lock):
    if lock is None:
        return ("unmaterialized",)
    if "profiles" not in lock:
        if lock.get("schema_version") != 2:
            raise ValueError("Unprofiled lock has an unknown historical schema")
        return ("legacy-schema-v2",)
    ids = tuple(profile["id"] for profile in lock["profiles"])
    if not ids or len(ids) != len(set(ids)):
        raise ValueError("Profiled lock has absent or duplicate profile ids")
    if set(ids) - {"gradle", "rust-toolchain"}:
        raise ValueError("Profiled lock has an unreviewed profile id")
    return ids


def classify(key, family, features, lock_present, profile):
    rows = []
    for operation in OPERATIONS:
        required = False
        external = False
        reason = ""
        if operation == "source_render":
            required, reason = True, "catalog-declared projection"
        elif operation == "frozen_lock":
            required, reason = True, "catalog-selected frozen dependency graph"
        elif operation in ("native_compile", "native_jar"):
            required = (family != "plain-4.35" and
                        profile in ("rust-toolchain", "legacy-schema-v2", "unmaterialized"))
            reason = ("reviewed 4.34 native recipe or declared development route" if required
                      else "native route does not use this profile")
        elif operation == "gradle_compile":
            required = ((family == "plain-4.35" and profile == "legacy-schema-v2")
                        or profile == "unmaterialized")
            external = (profile == "gradle" or
                        (family == "plain-4.34" and profile == "legacy-schema-v2"))
            if profile == "unmaterialized":
                reason = "generated Gradle profile cannot be reviewed until lock materializes"
            elif required:
                reason = "plain 4.35 has no reviewed native release recipe"
            elif external:
                reason = "Gradle fallback is separately owned"
            else:
                reason = "Gradle route does not use this profile"
        elif operation == "named_historical_jar":
            required = family == "plain-4.34" and profile == "legacy-schema-v2"
            reason = ("ten reviewed 4.34 named recipes" if required
                      else "named historical recipe is scoped to plain 4.34")
        else:
            feature = {
                "portable_fe_gametest": "issue_610_portable_fixture",
                "infrastructure_guard_gametest": "gametest_infrastructure_invariants",
                "progress_gametest": "issue_610_progress_tests",
                "client_visual": "issue_610_visual_evidence",
            }[operation]
            declared = feature in features
            required = declared and profile in ("rust-toolchain", "unmaterialized")
            external = declared and profile == "gradle"
            reason = (f"feature {feature} declared; native test route" if required
                      else f"feature {feature} declared; Gradle route separately owned" if external
                      else f"feature {feature} absent or not selected by this profile")
        blocked = required and not lock_present and operation != "source_render"
        rows.append({
            "projection": key,
            "profile": profile,
            "operation": operation,
            "status": "blocked" if blocked else ("required" if required else
                      "external" if external else "N/A"),
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
        profiles = applicability_profiles(lock)
        features = set(projection["features"])
        keys.append({
            "key": key,
            "family": family,
            "target": target,
            "minecraft_version": projection["minecraft_version"],
            "environment": projection["environment"],
            "features": sorted(features),
            "lock_present": lock_present,
            "lock_schema_version": lock.get("schema_version") if lock else None,
            "lock_sha256": hashlib.sha256(lock_path.read_bytes()).hexdigest()
            if lock_present else None,
            "profiles": [profile["id"] for profile in lock.get("profiles", [])]
            if lock_present else [],
            "applicability_profiles": profiles,
        })
        for profile in profiles:
            rows.extend(classify(key, family, features, lock_present, profile))
    return {
    "schema": 2,
        "scope": "source-backed candidate profile rows; legacy-v2 and unmaterialized are synthetic profile labels; G6 not frozen and no runtime proof implied",
        "catalog_sha256": hashlib.sha256(payload).hexdigest(),
        "catalog_keys": len(keys),
        "materialized_locks": sum(key["lock_present"] for key in keys),
        "families": dict(sorted(Counter(key["family"] for key in keys).items())),
        "row_statuses": dict(sorted(Counter(row["status"] for row in rows).items())),
        "open_decisions": [
            "ordinary plain-release launch applicability",
            "exact guard and progress selectors and selected-test counts",
            "dev 26.1.2 ae2_inscribers zero-match applicability",
            "18 portable generated locks and their actual declared profiles",
            "Gradle fallback qualification and external Prism/tracked-server rows",
        ],
        "keys": keys,
        "rows": rows,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo_root", type=Path)
    args = parser.parse_args()
    print(json.dumps(report(args.repo_root), indent=2))
