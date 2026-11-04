"""Fail-closed publication check for a declared SFM cloud image scope.

Run `verify` inside a disposable Linux container started with `--network none`.
The command never changes the declared scope or dependency locks.
"""

import argparse
import fnmatch
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import sys

SCHEMA = "sfm:cloud-offline-scope@1"
STAGES = ("check", "compile", "jar")
IMMUTABLE_CACHE_ROOTS = (
    "dependency-remap-v1", "forge-preparation-v1", "local-dependencies-v1",
    "minecraft-toolchain", "sources",
)


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def project_lock(repo, key):
    return repo / "platform" / "minecraft" / "projections" / key / "sfm-toolchain.lock.json"


def git(repo, *args):
    completed = subprocess.run(
        ["git", "-C", str(repo), *args], capture_output=True, text=True, check=True
    )
    return completed.stdout.strip()


def catalog_at(repo):
    path = repo / "platform" / "minecraft" / "projections.json"
    return path, json.loads(path.read_text(encoding="utf-8"))


def draft(repo, cli, patterns):
    catalog_path, catalog = catalog_at(repo)
    keys = sorted(
        key for key in catalog if any(fnmatch.fnmatchcase(key, pattern) for pattern in patterns)
    )
    if not keys:
        raise ValueError("no catalog targets match the requested patterns")
    return {
        "schema": SCHEMA,
        "state": "draft",
        "source_commit": git(repo, "rev-parse", "HEAD"),
        "catalog_sha256": sha256(catalog_path),
        "cli_sha256": sha256(cli),
        "targets": [
            {"projection": key, "lock_sha256": sha256(project_lock(repo, key)), "stages": list(STAGES)}
            for key in keys
        ],
    }


def require_network_none():
    if sys.platform != "linux":
        raise ValueError("publication gate requires a Linux --network none container")
    interfaces = {name for _, name in socket.if_nameindex()}
    if interfaces != {"lo"}:
        raise ValueError(f"expected only loopback interface, found {sorted(interfaces)}")
    for name in ("/proc/net/route", "/proc/net/ipv6_route"):
        path = Path(name)
        if not path.exists():
            raise ValueError(f"cannot verify network isolation: {name} is absent")
        lines = path.read_text(encoding="ascii").splitlines()
        if name.endswith("ipv6_route"):
            if any(line.split() and line.split()[0] == "0" * 32 and line.split()[1] == "00" for line in lines):
                raise ValueError("IPv6 default route is present")
        elif any(line.split()[1:2] == ["00000000"] for line in lines[1:]):
            raise ValueError("IPv4 default route is present")


def validate_scope(repo, cli, scope):
    if scope.get("schema") != SCHEMA or scope.get("state") != "declared":
        raise ValueError("scope must be reviewed and marked declared at the current schema")
    if git(repo, "status", "--porcelain", "--untracked-files=no"):
        raise ValueError("tracked checkout content is dirty")
    if scope.get("source_commit") != git(repo, "rev-parse", "HEAD"):
        raise ValueError("source commit changed; refresh the scope and image")
    catalog_path, catalog = catalog_at(repo)
    if scope.get("catalog_sha256") != sha256(catalog_path):
        raise ValueError("projection catalog changed; refresh the scope and image")
    if scope.get("cli_sha256") != sha256(cli):
        raise ValueError("CLI binary changed; refresh the scope and image")
    targets = scope.get("targets")
    if not isinstance(targets, list) or not targets:
        raise ValueError("declared scope must contain at least one target")
    seen = set()
    for target in targets:
        key = target["projection"]
        if key not in catalog or key in seen:
            raise ValueError(f"unknown or duplicate catalog target: {key}")
        seen.add(key)
        if target.get("lock_sha256") != sha256(project_lock(repo, key)):
            raise ValueError(f"lock changed for {key}; refresh the scope and image")
        stages = target.get("stages")
        if stages != list(STAGES):
            raise ValueError(f"{key} must freshly check, compile and package")
        if key.startswith(("sfm-4.34.0/mc-", "sfm-4.35.0/mc-")) and not target.get("released_recipe"):
            raise ValueError(f"{key} requires its exact historical released recipe")
    return targets


def cached_files(repo):
    """Inventory managed dependency/tool roots; ignore mutable receipts and run sessions."""
    root = repo / "sfm-toolchain-data"
    files = set()
    for name in IMMUTABLE_CACHE_ROOTS:
        directory = root / name
        if not directory.exists():
            continue
        if directory.is_symlink():
            raise ValueError(f"managed cache root is a link: {directory}")
        for base, dirs, names in os.walk(directory, followlinks=False):
            for child in dirs + names:
                path = Path(base) / child
                if path.is_symlink():
                    raise ValueError(f"managed cache entry is a link: {path}")
            files.update(str((Path(base) / name).relative_to(root)) for name in names)
    return files


def verify(repo, cli, scope):
    require_network_none()
    targets = validate_scope(repo, cli, scope)
    receipts = repo / "sfm-toolchain-data" / "check-everything-native" / "receipts"
    if receipts.exists() and any(receipts.rglob("*.pass.json")):
        raise ValueError("candidate image contains matrix pass receipts; prepare a clean validation layer")
    before = cached_files(repo)
    results = []
    for target in targets:
        key = target["projection"]
        command = [str(cli), "--output-format", "json", "matrix", "--repo-root", str(repo),
                   "--projection", key, "--fresh"]
        for stage in STAGES:
            command.extend(("--stage", stage))
        if target.get("released_recipe"):
            command.extend(("--released-recipe", target["released_recipe"]))
        completed = subprocess.run(command, cwd=repo, capture_output=True, text=True)
        try:
            summary = json.loads(completed.stdout)
        except json.JSONDecodeError as error:
            raise ValueError(f"{key}: matrix did not return JSON: {completed.stderr[-1000:]}") from error
        rows = summary.get("rows", [])
        actual = {(row.get("projection"), row.get("stage")): row.get("status") for row in rows}
        expected = {(key, stage): "passed" for stage in STAGES}
        if completed.returncode != 0 or actual != expected or not summary.get("manifest", {}).get("fresh"):
            raise ValueError(f"{key}: offline fresh matrix failed: exit={completed.returncode}, rows={actual}; {completed.stderr[-1000:]}")
        results.append({"projection": key, "summary_path": summary.get("summary_path"), "rows": rows})
    added = cached_files(repo) - before
    if added:
        raise ValueError(f"managed dependency cache grew during offline gate: {sorted(added)[:8]}")
    return {"schema": "sfm:cloud-offline-readiness@1", "source_commit": scope["source_commit"],
            "catalog_sha256": scope["catalog_sha256"], "cli_sha256": scope["cli_sha256"],
            "targets": results, "status": "passed"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("draft", "check", "verify"))
    parser.add_argument("--repo-root", type=Path, required=True)
    parser.add_argument("--cli", type=Path, required=True)
    parser.add_argument("--scope", type=Path, required=True)
    parser.add_argument("--projection", action="append", default=[])
    args = parser.parse_args()
    repo, cli = args.repo_root.resolve(), args.cli.resolve()
    try:
        if args.mode == "draft":
            if not args.projection:
                raise ValueError("draft requires explicit --projection patterns")
            content = draft(repo, cli, args.projection)
            with args.scope.open("x", encoding="utf-8") as output:
                json.dump(content, output, indent=2)
                output.write("\n")
            print(f"Draft written to {args.scope}; review scope and set state to declared")
        else:
            if args.projection:
                raise ValueError("check and verify use only the declared scope; omit --projection")
            scope = json.loads(args.scope.read_text(encoding="utf-8"))
            if args.mode == "check":
                targets = validate_scope(repo, cli, scope)
                print(json.dumps({"schema": SCHEMA, "status": "current", "targets": len(targets)}))
            else:
                print(json.dumps(verify(repo, cli, scope), indent=2))
    except (OSError, ValueError, subprocess.CalledProcessError, KeyError) as error:
        print(f"cloud offline readiness failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
