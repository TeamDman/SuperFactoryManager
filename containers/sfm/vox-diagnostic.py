"""Diagnose the unchanged locked Vox Java test, without rebuilding Rust or SFM."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("workspace", "scratch", "artifacts", "java-home"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    workspace = args.workspace.resolve()
    scratch = args.scratch.resolve()
    artifacts = args.artifacts.resolve()
    java_home = args.java_home.resolve()
    artifacts.mkdir(parents=True, exist_ok=True)
    # A new disposable directory prevents this diagnostic from reusing stale classes.
    scratch.mkdir(parents=True, exist_ok=False)
    receipt = {"test_runs": 0, "outcome": "setup", "commands": []}

    def save_receipt():
        (artifacts / "receipt.json").write_text(
            json.dumps(receipt, indent=2) + "\n", encoding="utf-8")

    def run(command, log_name, timeout, *, cwd=scratch, env=None):
        receipt["commands"].append([str(value) for value in command])
        save_receipt()
        print(f"Running {log_name}", flush=True)
        with (artifacts / log_name).open("wb") as log:
            result = subprocess.run(command, cwd=cwd, env=env, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=timeout, check=False)
        print(f"{log_name}: exit {result.returncode}", flush=True)
        return result.returncode

    def required(command, log_name, timeout, **kwargs):
        code = run(command, log_name, timeout, **kwargs)
        if code:
            raise RuntimeError(f"{log_name} failed with exit {code}")

    try:
        lock_path = workspace / "platform/minecraft/sfm-toolchain.lock.json"
        lock_bytes = lock_path.read_bytes()
        lock = json.loads(lock_bytes)
        candidates = [artifact for artifact in lock["artifacts"]
                      if artifact.get("owner") == {"dependency_id": "vox-java", "component_id": "main"}]
        if len(candidates) != 1:
            raise RuntimeError("Expected exactly one locked vox-java/main artifact")
        artifact = candidates[0]
        source = artifact["source_git"]
        revision = source["commit"]
        remote = source["remote_url"]
        if not re.fullmatch(r"[0-9a-f]{40}", revision):
            raise RuntimeError("Locked Vox source must name an immutable Git commit")
        if remote.rstrip("/") != "https://github.com/TeamDman/facet":
            raise RuntimeError("Diagnostic is restricted to the existing locked Facet repository")
        receipt.update({"source_revision": revision, "source_remote": remote,
                        "locked_artifact_hash": artifact["hash"],
                        "sfm_lock_sha256": hashlib.sha256(lock_bytes).hexdigest(),
                        "locale": {key: os.environ.get(key) for key in ("LANG", "LC_ALL")}})
        # Transient materialization of the already-locked source; no developer clone or lock edits.
        required(["git", "init", "source"], "git-init.log", 15)
        checkout = scratch / "source"
        required(["git", "remote", "add", "origin", remote], "git-remote.log", 15, cwd=checkout)
        required(["git", "-c", "credential.helper=", "fetch", "--depth=1", "origin", revision],
                 "git-fetch.log", 300, cwd=checkout)
        required(["git", "-c", "core.hooksPath=/dev/null", "checkout", "--detach", "FETCH_HEAD"],
                 "git-checkout.log", 30, cwd=checkout)
        actual = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=checkout, text=True).strip()
        if actual != revision:
            raise RuntimeError("Fetched source revision differs from the SFM lock")

        roots = ("phon/java/runtime/src/main/java", "phon/java/runtime/src/test/java",
                 "vox/java/runtime/src/main/java", "vox/java/runtime/src/test/java",
                 "vox/java/generated/src/main/java", "vox/java/subject/src/main/java")
        sources = sorted(path for root in roots for path in (checkout / root).rglob("*.java"))
        if not sources:
            raise RuntimeError("No pinned Java sources found")
        classes = scratch / "classes"
        classes.mkdir()
        argfile = artifacts / "javac.args"
        arguments = ["--release", "17", "-Xlint:all", "-Werror", "-d", str(classes)]
        arguments.extend(str(path) for path in sources)
        argfile.write_text("\n".join('"' + value.replace("\\", "/") + '"'
                                     for value in arguments) + "\n", encoding="utf-8")
        receipt["source_count"] = len(sources)
        receipt["source_sha256"] = {
            str(path.relative_to(checkout)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sources}
        environment = os.environ.copy()
        environment["JAVA_HOME"] = str(java_home)
        java = java_home / "bin/java"
        javac = java_home / "bin/javac"
        required([str(java), "-XshowSettings:properties", "-version"], "java-settings.log", 15,
                 env=environment)
        required([str(javac), "@" + str(argfile)], "javac.log", 180, env=environment)
        environment["VOX_DLOG"] = "1"
        receipt["test_runs"] = 1
        code = run([str(java), "-ea",
                    "-Xlog:exceptions=info:file=" + str(artifacts / "jvm-exceptions.log"),
                    "-cp", str(classes), "org.facet.vox.VoxRuntimeTest"],
                   "original-test.log", 60, env=environment)
        receipt.update({"test_exit_code": code, "outcome": "passed" if code == 0 else "failed"})
        return code
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.SubprocessError) as failure:
        receipt.update({"outcome": "diagnostic-error", "error": str(failure)})
        print(str(failure), file=sys.stderr)
        return 1
    finally:
        save_receipt()


if __name__ == "__main__":
    sys.exit(main())
