"""Emit the frozen source-applicability scope for G6; never run builds.

Usage: python report-projection-applicability.py REPO_ROOT > receipt.json
Blocked rows remain required work, not passes. Runtime and Gradle evidence is
recorded separately; this scope freeze does not claim that evidence exists.
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
    "native_gametest_server",
    "ordinary_plain_client",
    "gradle_compile",
    "named_historical_jar",
    "portable_fe_gametest",
    "infrastructure_guard_gametest",
    "infrastructure_negative_witnesses",
    "dev26_ae2_inscribers",
    "progress_gametest",
    "client_visual",
)

PROOF = {
    "source_render": "rendered source/resource inventory and historical comparison where applicable",
    "frozen_lock": "selected lock identity and strict dependency resolution receipt",
    "native_compile": "successful final-source native main and applicable test compilation",
    "native_jar": "verified final-source JAR contents and SHA-256",
    "native_gametest_server": "server launch and nonzero selected-test result from final-source JAR",
    "ordinary_plain_client": "ordinary client launch without a smoke harness, or an exact blocked-route diagnostic",
    "gradle_compile": "independent Gradle-without-Rust compilation receipt",
    "named_historical_jar": "reviewed historical recipe and JAR identity receipt",
    "portable_fe_gametest": "one selected FE test; red second consumer 0 FE, green 100000 FE",
    "infrastructure_guard_gametest": "positive selected test with intact infrastructure and platform",
    "infrastructure_negative_witnesses": "each selected negative witness has its specified expected failure or opt-in pass",
    "dev26_ae2_inscribers": "nonzero exact AE2 selection and asserted result; prior zero-match is a blocker",
    "progress_gametest": "resource progress and seven JDT scenarios selected and asserted",
    "client_visual": "inspected version/projection-labelled framebuffer and interaction receipt",
}

SELECTOR = {
    "portable_fe_gametest": ("sfm:issue_610_portable_controls", 1),
    "infrastructure_guard_gametest": ("sfm:water_tank_plus_split", 1),
    "infrastructure_negative_witnesses": ("sfm:sfm_integrity_witness_*", 4),
    "dev26_ae2_inscribers": ("sfm:ae2_inscribers", 1),
    "progress_gametest": ("sfm:issue_610_*progress*", 8),
    "client_visual": ("sfm:issue_610_visual_controls", 1),
}

SOURCE_BASIS = {
    "ordinary_plain_client": "platform/cli/sfm-propagate-changes/src/jar_build/engine_development_project.rs",
    "portable_fe_gametest": "platform/minecraft/core-liquid-template/src/gametest/java/ca/teamdman/sfm/gametest/tests/regression/Issue610PortableControlsGameTest.java",
    "infrastructure_guard_gametest": "platform/minecraft/core-liquid-template/src/gametest/java/ca/teamdman/sfm/gametest/tests/water_tank/WaterTankPlusSplitGameTest.java",
    "infrastructure_negative_witnesses": "platform/minecraft/core-liquid-template/src/gametest/java/ca/teamdman/sfm/gametest/SFMGameTestInfrastructureWitnesses.java",
    "dev26_ae2_inscribers": "platform/minecraft/core-liquid-template/src/gametest/java/ca/teamdman/sfm/gametest/tests/compat/ae2/Ae2InscribersGameTest.java; platform/minecraft/gradle/source-excludes/26.1.2/gametest-java.txt",
    "progress_gametest": "platform/minecraft/core-liquid-template/src/gametest/java/ca/teamdman/sfm/gametest/tests/compat/justdirethings/Issue610JdtProgressGameTestGenerator.java",
    "client_visual": "platform/minecraft/core-liquid-template/src/gametest/java/ca/teamdman/sfm/gametest/tests/compat/justdirethings/Issue610VisualControlsGameTest.java",
}


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
    target = key.split("/", 1)[1]
    for operation in OPERATIONS:
        required = False
        external = False
        route_blocked = False
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
        elif operation == "native_gametest_server":
            declared = bool(features & {"issue_610_portable_fixture",
                                        "gametest_infrastructure_invariants",
                                        "issue_610_progress_tests",
                                        "issue_610_visual_evidence"})
            required = declared and profile in ("rust-toolchain", "unmaterialized")
            external = declared and profile == "gradle"
            reason = ("declared test feature and native server route" if required else
                      "declared test feature; Gradle route separately owned" if external else
                      "no declared test feature on this route")
        elif operation == "ordinary_plain_client":
            plain = family in ("plain-4.34", "plain-4.35")
            required = plain and family == "plain-4.34" and profile == "legacy-schema-v2"
            external = plain and family == "plain-4.35" and profile == "legacy-schema-v2"
            reason = ("plain 4.35 uses the separately owned Gradle client route" if external
                      else "catalog client permits launch without a smoke harness" if required
                      else "ordinary plain client route is not this profile")
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
        elif operation == "dev26_ae2_inscribers":
            required = family == "dev" and target == "mc-26.1.2" and profile == "rust-toolchain"
            external = family == "dev" and target == "mc-26.1.2" and profile == "gradle"
            route_blocked = required
            reason = ("authored 26.1.2 source excludes compat/ae2/**; prior exact selector selected zero; compatibility or applicability decision required"
                      if required else "Gradle GameTest route separately owned" if external else
                      "this targeted zero-match investigation is scoped to dev 26.1.2")
        else:
            feature = {
                "portable_fe_gametest": "issue_610_portable_fixture",
                "infrastructure_guard_gametest": "gametest_infrastructure_invariants",
                "infrastructure_negative_witnesses": "gametest_infrastructure_invariants",
                "progress_gametest": "issue_610_progress_tests",
                "client_visual": "issue_610_visual_evidence",
            }[operation]
            declared = feature in features or (operation == "client_visual" and
                                               family == "extended" and target == "mc-1.21.1")
            required = declared and profile in ("rust-toolchain", "unmaterialized")
            external = declared and profile == "gradle"
            reason = (f"feature {feature} declared; native test route" if required
                      else f"feature {feature} declared; Gradle route separately owned" if external
                      else f"feature {feature} absent or not selected by this profile")
        blocked = route_blocked or (required and not lock_present and operation != "source_render")
        selector, minimum_selected = SELECTOR.get(operation, (None, None))
        if operation == "infrastructure_negative_witnesses" and target != "mc-26.1.2":
            minimum_selected = 5
        if operation == "client_visual" and "issue_610_visual_evidence" not in features:
            selector, minimum_selected = None, None
        witness_outcomes = None
        if operation == "infrastructure_negative_witnesses" and (required or blocked):
            witness_outcomes = {
                "sfm_integrity_witness_floor_rejected": "expected failure",
                "sfm_integrity_witness_floor_allowed": "expected pass with explicit opt-in",
                "sfm_integrity_witness_helper_floor_prewrite_rejected": "expected pass after pre-write rejection",
                "sfm_integrity_witness_run_block_rejected": "expected failure",
            }
            if target != "mc-26.1.2":
                witness_outcomes["sfm_integrity_witness_command_block_rejected"] = "expected failure"
        rows.append({
            "projection": key,
            "profile": profile,
            "operation": operation,
            "status": "blocked" if blocked else ("required" if required else
                      "external" if external else "N/A"),
            "required_when_unblocked": blocked,
            "reason": reason + ("; generated lock absent" if required and not lock_present else ""),
            "declaration": "platform/minecraft/projections.json",
            "source_basis": SOURCE_BASIS.get(operation, "platform/minecraft/core-liquid-template/project-inputs.json"),
            "expected_proof": PROOF[operation],
            "selector": selector if required or blocked else None,
            "minimum_selected": minimum_selected if required or blocked else None,
            "witness_outcomes": witness_outcomes,
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
            classified = classify(key, family, features, lock_present, profile)
            for row in classified:
                row["lock_sha256"] = keys[-1]["lock_sha256"]
                row["route_owner"] = ("independent Gradle or external launcher" if
                                      row["status"] == "external" else "SFM native CLI")
            rows.extend(classified)
    missing_locks = len(keys) - sum(key["lock_present"] for key in keys)
    return {
        "schema": 3,
        "scope": "frozen source-applicability rows only; legacy-v2 and unmaterialized are synthetic profile labels; blocked rows remain required and no runtime proof is implied",
        "selection_contract": "SFMGameTestDiscovery.filterSelectedTests uses comma-separated exact/wildcard selectors and rejects zero matches; source-derived minimum_selected is not a runtime result",
        "catalog_sha256": hashlib.sha256(payload).hexdigest(),
        "catalog_keys": len(keys),
        "materialized_locks": sum(key["lock_present"] for key in keys),
        "families": dict(sorted(Counter(key["family"] for key in keys).items())),
        "row_statuses": dict(sorted(Counter(row["status"] for row in rows).items())),
        "blocked_prerequisites": [
            *([f"{missing_locks} catalog locks and their declared profiles must materialize before execution"]
              if missing_locks else []),
            "dev 26.1.2 ae2_inscribers is explicitly source-excluded; decide compatibility or evidence-backed N/A before rerun",
            "Gradle-without-Rust and external Prism/tracked-server evidence is separately owned",
        ],
        "external_route_boundaries": [
            {"route": "Gradle-without-Rust", "owner": "generated projection Gradle build",
             "basis": "platform/minecraft/core-liquid-template/project-inputs.json",
             "proof": "independent Gradle build, no SFM native-stage cache inference"},
            {"route": "Prism client sync/launch", "owner": "Prism instance and launcher",
             "basis": "platform/cli/sfm-propagate-changes/src/cli/client/client_cli.rs",
             "proof": "tracked instance/runtime receipt, not a native build cache hit"},
            {"route": "tracked server jar update", "owner": "configured server mods directory",
             "basis": "platform/cli/sfm-propagate-changes/src/cli/jar/jar_update_servers_cli.rs",
             "proof": "explicit update/launch receipt; mutable server data excluded from stage cache"},
        ],
        "keys": keys,
        "rows": rows,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo_root", type=Path)
    args = parser.parse_args()
    print(json.dumps(report(args.repo_root), indent=2))
