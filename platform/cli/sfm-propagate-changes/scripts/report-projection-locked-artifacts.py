"""Report frozen artifact/source inputs for the current G6 projection catalog.

This is a lock-side inventory, not ordered planner classpaths or resolution proof.
Usage: python -B report-projection-locked-artifacts.py REPO_ROOT > receipt.json
"""

import argparse
from collections import Counter
import hashlib
import importlib.util
import json
from pathlib import Path


def applicability_report(script_dir: Path, repo: Path):
    path = script_dir / "report-projection-applicability.py"
    spec = importlib.util.spec_from_file_location("sfm_applicability_report", path)
    if spec is None or spec.loader is None:
        raise ValueError(f"Cannot load applicability reporter: {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.report(repo)


def provider_rows(lock):
    rows = []
    for dependency in lock.get("dependencies", []):
        for component in dependency.get("components", []):
            for provider in component.get("source_providers", []):
                if len(provider) != 1:
                    raise ValueError("Source provider must have one tagged variant")
                kind, record = next(iter(provider.items()))
                if not isinstance(record, dict):
                    raise ValueError(f"Invalid {kind} source provider")
                rows.append({
                    "dependency": dependency.get("id"),
                    "component": component.get("id"),
                    "kind": kind,
                    "id": record.get("id"),
                    "declaration": record.get("declaration"),
                    "derived_checks": record.get("derived_checks"),
                })
    return rows


def declared_dependencies(lock):
    rows = []
    for dependency in lock.get("dependencies", []):
        rows.append({
            "id": dependency.get("id"),
            "kind": dependency.get("kind"),
            "role": dependency.get("role"),
            "components": [
                {
                    "id": component.get("id"),
                    "scopes": (component.get("declaration") or {}).get("scopes", []),
                    "artifact_id": (component.get("derived_checks") or {}).get("artifact_id"),
                }
                for component in dependency.get("components", [])
            ],
        })
    return rows

def lock_inventory(repo: Path, key_record: dict, required_rows: list):
    key = key_record["key"]
    path = repo / "platform/minecraft/projections" / key / "sfm-toolchain.lock.json"
    data = path.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != key_record["lock_sha256"]:
        raise ValueError(f"G6 lock digest drift: {key}")
    lock = json.loads(data)
    schema = lock["schema_version"]
    if schema not in (2, 4):
        raise ValueError(f"Unsupported lock schema {schema}: {key}")
    artifacts = lock.get("artifacts", [])
    if not isinstance(artifacts, list):
        raise ValueError(f"Invalid artifact list: {key}")
    return {
        "key": key,
        "family": key_record["family"],
        "minecraft_version": key_record["minecraft_version"],
        "lock_sha256": digest,
        "lock_schema": schema,
        "required_operations": sorted({row["operation"] for row in required_rows}),
        "required_profiles": sorted({row["profile"] for row in required_rows}),
        "artifact_count": len(artifacts),
        "artifacts": artifacts,
        "source_providers": provider_rows(lock) if schema == 4 else [],
        "declared_dependencies": declared_dependencies(lock) if schema == 4 else [],
        "legacy_dependencies": lock.get("dependencies", []) if schema == 2 else [],
        "planner_classpath_status": "unverified; lock records do not define ordered invocation classpaths",
    }


def report(repo: Path):
    repo = repo.resolve(strict=True)
    g6 = applicability_report(Path(__file__).resolve().parent, repo)
    required = {}
    for row in g6["rows"]:
        if row["status"] in ("required", "blocked"):
            required.setdefault(row["projection"], []).append(row)
    projections = []
    for key in g6["keys"]:
        if not key["lock_present"]:
            raise ValueError(f"Current catalog lock absent: {key['key']}")
        projections.append(lock_inventory(repo, key, required.get(key["key"], [])))
    sources = Counter()
    purposes = Counter()
    provider_kinds = Counter()
    declared_tool_artifacts = 0
    for projection in projections:
        tool_ids = {dependency["id"] for dependency in projection["declared_dependencies"]
                    if dependency["kind"] == "tool"}
        for artifact in projection["artifacts"]:
            sources[artifact.get("provenance") or artifact.get("source") or "unspecified"] += 1
            if (artifact.get("owner") or {}).get("dependency_id") in tool_ids:
                declared_tool_artifacts += 1
            for purpose in artifact.get("purposes", []):
                purposes[purpose] += 1
        provider_kinds.update(provider["kind"] for provider in projection["source_providers"])
    return {
        "schema": 1,
        "scope": "current G6 catalog lock-side records; no acquisition, ordered classpath or runtime proof",
        "catalog_sha256": g6["catalog_sha256"],
        "catalog_keys": len(projections),
        "required_or_blocked_rows": sum(len(rows) for rows in required.values()),
        "lock_schemas": dict(sorted(Counter(p["lock_schema"] for p in projections).items())),
        "artifact_records": sum(p["artifact_count"] for p in projections),
        "artifact_provenances": dict(sorted(sources.items())),
        "artifact_purposes": dict(sorted(purposes.items())),
        "declared_tool_artifact_records": declared_tool_artifacts,
        "source_provider_kinds": dict(sorted(provider_kinds.items())),
        "projections": projections,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo_root", type=Path)
    args = parser.parse_args()
    print(json.dumps(report(args.repo_root), indent=2))