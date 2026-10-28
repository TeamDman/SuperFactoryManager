"""Report direct frozen artifact path reachability without downloading or deleting.

Usage: python report-locked-cache-artifacts.py REPO_ROOT > receipt.json
Only catalog-selected generated lock files are read. This deliberately does not
infer runtime assets, transformed outputs, source trees, or cache orphans.
"""

import argparse
from collections import defaultdict
import json
from pathlib import Path


def portable_cache_relative(value):
    if not isinstance(value, str):
        return None
    portable = value.replace("\\", "/")
    prefix = "$sfm-cache/"
    if not portable.startswith(prefix):
        return None
    parts = portable[len(prefix):].split("/")
    if not parts or any(not part or part in (".", "..") or ":" in part for part in parts):
        raise ValueError(f"Unsafe frozen cache path: {value}")
    return "/".join(parts)


def report(repo):
    catalog_path = repo / "platform/minecraft/projections.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    if not isinstance(catalog, dict):
        raise ValueError("Projection catalog is not a keyed object")
    by_path = defaultdict(list)
    missing_locks = []
    noncache_artifacts = []
    locks = 0
    for key in sorted(catalog):
        parts = key.split("/")
        if len(parts) != 2 or any(not part or part in (".", "..") or "\\" in part for part in parts):
            raise ValueError(f"Unsafe catalog projection key: {key}")
        lock_path = repo / "platform/minecraft/projections" / key / "sfm-toolchain.lock.json"
        if not lock_path.is_file():
            missing_locks.append(key)
            continue
        lock = json.loads(lock_path.read_text(encoding="utf-8"))
        locks += 1
        for artifact in lock.get("artifacts", []):
            path = artifact.get("cache_path")
            relative = portable_cache_relative(path)
            if relative is None:
                noncache_artifacts.append({"projection": key, "artifact": artifact.get("id"),
                                           "cache_path": path})
                continue
            by_path[relative].append({"projection": key, "artifact": artifact.get("id"),
                                      "hash": artifact.get("hash"),
                                      "provenance": artifact.get("provenance")})
    conflicts = []
    for path, claims in by_path.items():
        hashes = sorted({claim["hash"] for claim in claims if claim["hash"]})
        if len(hashes) > 1:
            conflicts.append({"relative_path": path, "hashes": hashes, "claims": claims})
    return {
        "schema": 1,
        "scope": "direct artifact cache_path claims in catalog-selected generated locks only",
        "catalog_keys": len(catalog),
        "materialized_locks": locks,
        "missing_locks": missing_locks,
        "artifact_claims": sum(len(claims) for claims in by_path.values()),
        "unique_relative_paths": len(by_path),
        "same_path_hash_conflicts": conflicts,
        "noncache_artifacts": noncache_artifacts,
        "paths": dict(sorted(by_path.items())),
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo_root", type=Path)
    args = parser.parse_args()
    print(json.dumps(report(args.repo_root), indent=2))
