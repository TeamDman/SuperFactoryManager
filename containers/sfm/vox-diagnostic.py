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
    parser.add_argument("--candidate", action="store_true",
                        help="Apply the review candidate only to the disposable pinned source")
    parser.add_argument("--reduced-only", action="store_true",
                        help="Reproduce baseline active/closed credit ordering without the full test")
    args = parser.parse_args()
    if args.candidate and args.reduced_only:
        parser.error("--reduced-only records the unchanged baseline; omit --candidate")
    workspace = args.workspace.resolve()
    scratch = args.scratch.resolve()
    artifacts = args.artifacts.resolve()
    java_home = args.java_home.resolve()
    artifacts.mkdir(parents=True, exist_ok=True)
    # A new disposable directory prevents this diagnostic from reusing stale classes.
    scratch.mkdir(parents=True, exist_ok=False)
    receipt = {"test_runs": 0, "outcome": "setup", "commands": [],
               "candidate": args.candidate, "reduced_only": args.reduced_only}

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

        if args.candidate:
            patch = workspace / "containers/sfm/vox-diagnostic/credit-candidate.patch"
            receipt["candidate_patch_sha256"] = hashlib.sha256(patch.read_bytes()).hexdigest()
            required(["git", "apply", "--check", str(patch)], "patch-check.log", 15, cwd=checkout)
            required(["git", "apply", str(patch)], "patch-apply.log", 15, cwd=checkout)
            changed = subprocess.check_output(["git", "diff", "--name-only"],
                                              cwd=checkout, text=True).splitlines()
            if changed != ["vox/java/runtime/src/main/java/org/facet/vox/VoxConnection.java"]:
                raise RuntimeError("Candidate must change only the disposable VoxConnection.java")
            required(["git", "diff", "--exit-code", "--",
                      "vox/java/runtime/src/test/java/org/facet/vox/VoxRuntimeTest.java"],
                     "original-test-unchanged.log", 15, cwd=checkout)

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
        if args.candidate or args.reduced_only:
            probe = workspace / "containers/sfm/vox-diagnostic/VoxMissingCreditProbe.java"
            receipt["probe_sha256"] = hashlib.sha256(probe.read_bytes()).hexdigest()
            required([str(javac), "--release", "17", "-Xlint:all", "-Werror", "-cp", str(classes),
                      "-d", str(classes), str(probe)], "probe-javac.log", 30, env=environment)
        environment["VOX_DLOG"] = "1"
        if args.reduced_only:
            # Both inputs use the unchanged baseline driver. The only difference
            # is that one sends the same credit after the local sender Close.
            receipt["variant_exit_codes"] = {}
            for variant in ("active_credit_passes", "late_credit_after_close_passes"):
                variant_code = run([str(java), "-ea",
                                    "-Xlog:exceptions=info:file="
                                    + str(artifacts / (variant + "-jvm-exceptions.log")),
                                    "-cp", str(classes),
                                    "org.facet.vox.VoxMissingCreditProbe", variant],
                                   "variant-" + variant + ".log", 15, env=environment)
                receipt["variant_exit_codes"][variant] = variant_code
            active_code = receipt["variant_exit_codes"]["active_credit_passes"]
            closed_code = receipt["variant_exit_codes"]["late_credit_after_close_passes"]
            failure_log = (artifacts / "variant-late_credit_after_close_passes.log").read_text(
                encoding="utf-8", errors="replace")
            expected_error = "org.facet.vox.VoxException: message for unknown channel 1:1"
            receipt["expected_failure"] = expected_error
            reproduced = (active_code == 0 and closed_code == 1
                          and expected_error in failure_log
                          and "VoxConnection.processInboundChannel(" in failure_log)
            receipt["outcome"] = ("baseline-failure-reproduced" if reproduced
                                  else "baseline-reproduction-mismatch")
            print(receipt["outcome"], flush=True)
            return 0 if reproduced else 1
        receipt["test_runs"] = 1
        code = run([str(java), "-ea",
                    "-Xlog:exceptions=info:file=" + str(artifacts / "jvm-exceptions.log"),
                    "-cp", str(classes), "org.facet.vox.VoxRuntimeTest"],
                   "original-test.log", 60, env=environment)
        receipt.update({"test_exit_code": code, "outcome": "passed" if code == 0 else "failed"})
        if args.candidate:
            variants = (
                "active_credit_passes", "late_credit_after_close_passes",
                "missing_credit_passes", "late_credit_has_no_history_window",
                "credit_on_zero_channel_fails",
                "credit_on_control_lane_fails", "credit_on_unopened_lane_fails",
                "credit_on_opening_lane_fails", "credit_after_local_lane_close_passes",
                "zero_credit_on_active_fails", "zero_credit_on_missing_fails",
                "zero_credit_on_closed_fails", "overflow_credit_on_missing_fails",
                "missing_credit_field_fails", "wrong_type_credit_fails",
                "credit_to_active_receiver_fails", "item_to_closed_sender_fails",
                "close_to_missing_channel_fails", "reset_to_closed_sender_fails_unchanged",
            )
            receipt["variant_exit_codes"] = {}
            for variant in variants:
                variant_code = run([str(java), "-ea", "-cp", str(classes),
                                    "org.facet.vox.VoxMissingCreditProbe", variant],
                                   "variant-" + variant + ".log", 15, env=environment)
                receipt["variant_exit_codes"][variant] = variant_code
            if any(receipt["variant_exit_codes"].values()):
                receipt["outcome"] = "failed"
                return 1
        return code
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.SubprocessError) as failure:
        receipt.update({"outcome": "diagnostic-error", "error": str(failure)})
        print(str(failure), file=sys.stderr)
        return 1
    finally:
        save_receipt()


if __name__ == "__main__":
    sys.exit(main())
