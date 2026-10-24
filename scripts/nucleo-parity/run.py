#!/usr/bin/env python3
"""Offline, dependency-pinned differential tests. Never installs tools or downloads crates."""
import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

PIN = "8c16d47cdfa9607d3e44df5f81c635c6f43c65ee"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--upstream", type=Path, required=True, help="clean clone of helix-editor/nucleo at the pinned revision")
parser.add_argument("--build-dir", type=Path, required=True, help="scratch output directory; logs are replaced on rerun")
parser.add_argument("--benchmarks", action="store_true")
args = parser.parse_args()
here = Path(__file__).resolve().parent
root = here.parent.parent
source = root / "platform/minecraft/core-liquid-template/src/main/java/ca/teamdman/sfm/client/search/nucleo"
def input_identity():
    inputs = [*source.glob("*.java"), *here.glob("*.java"), *here.glob("*.py"), here / "oracle.rs", here / "Cargo.lock"]
    return {
        "sfm_commit": subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip(),
        "sfm_dirty": bool(subprocess.check_output(["git", "-C", str(root), "status", "--porcelain"], text=True)),
        "inputs_sha256": {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(inputs)},
    }

before = input_identity()
upstream = args.upstream.resolve()
build = args.build_dir.resolve()
build.mkdir(parents=True, exist_ok=True)
def verify_upstream():
    if subprocess.check_output(["git", "-C", str(upstream), "rev-parse", "HEAD"], text=True).strip() != PIN:
        raise SystemExit("Upstream revision must be " + PIN)
    if subprocess.check_output(["git", "-C", str(upstream), "diff", "HEAD", "--", "matcher", "LICENSE"], text=True):
        raise SystemExit("Upstream matcher/license has tracked modifications")

verify_upstream()
rust_version = subprocess.check_output(["rustc", "--version"], text=True).strip()
if rust_version != "rustc 1.99.1 (58cf8f9c3 2026-10-13)":
    raise SystemExit("Use the existing pinned Rust 1.99.1; do not install/upgrade automatically")
java_home = os.environ.get("JAVA_HOME")
java = Path(java_home) / "bin/java" if java_home else Path(shutil.which("java"))
javac = Path(java_home) / "bin/javac" if java_home else Path(shutil.which("javac"))
env = dict(os.environ, CARGO_TARGET_DIR=str(build / "target"), CARGO_NET_OFFLINE="true")

def run(name, command, **kwargs):
    print(name, flush=True)
    with (build / (name + ".log")).open("w") as log:
        result = subprocess.run(list(map(str, command)), stdout=log, stderr=subprocess.STDOUT,
                                env=env, timeout=300, **kwargs)
    if result.returncode:
        print((build / (name + ".log")).read_text()[-6000:], file=sys.stderr)
        raise SystemExit(f"{name} failed; preserved log: {build / (name + '.log')}")

# This is a standalone package pointing directly to unchanged upstream sources, not a patched matcher.
# Exact cached versions are separate oracle dependencies; the SFM dependency graph is untouched.
(build / "Cargo.toml").write_text(f'''[package]
name = "sfm-nucleo-oracle"
version = "0.0.0"
edition = "2021"
[lib]
name = "nucleo_matcher"
path = {json.dumps(str(upstream / "matcher/src/lib.rs"))}
[[bin]]
name = "oracle"
path = {json.dumps(str(here / "oracle.rs"))}
[dependencies]
memchr = "=2.7.6"
unicode-segmentation = {{ version = "=1.12.0", optional = true }}
[features]
default = ["unicode-normalization", "unicode-casefold"]
unicode-normalization = []
unicode-casefold = []
unicode-segmentation = ["dep:unicode-segmentation"]
''')
shutil.copyfile(here / "Cargo.lock", build / "Cargo.lock")
cargo = ["cargo", "--offline", "--locked", "--manifest-path", build / "Cargo.toml", "--release"]
run("upstream-default-tests", [cargo[0], "test", *cargo[1:], "--features", "unicode-segmentation", "--lib"])
run("upstream-codepoint-tests", [cargo[0], "test", *cargo[1:], "--lib", "--", "--skip", "utf32_str::tests::test_grapheme_truncation"])
run("rust-build", [cargo[0], "build", *cargo[1:]])
oracle = build / "target/release/oracle"
run("unicode-regenerate", [sys.executable, here / "generate_tables.py", oracle, build / "NucleoUnicode.java"])
if (build / "NucleoUnicode.java").read_bytes() != (source / "NucleoUnicode.java").read_bytes():
    raise SystemExit("Unicode snapshot drift")
(build / "classes").mkdir(exist_ok=True)
run("javac", [javac, "--release", "17", "-Xlint:all", "-d", build / "classes", *sorted(source.glob("*.java")),
              here / "JavaOracle.java", here / "CoreChecks.java", here / "Benchmark.java"])
run("core-checks", [java, "-ea", "-cp", build / "classes", "CoreChecks"])
run("utf16-validator-checks", [java, "-cp", build / "classes", "JavaOracle", "validator-checks"])
run("differential", [sys.executable, here / "compare.py", upstream, build, java])

if args.benchmarks:
    namespaces = ["minecraft", "create", "mekanism", "thermal", "ae2", "sfm", "mod_alpha", "mod_beta", "mod_gamma"]
    materials = ["iron", "copper", "gold", "tin", "bronze", "steel", "silver", "lead", "diamond", "redstone", "certus_quartz", "uranium"]
    shapes = ["ingot", "nugget", "ore", "dust", "plate", "block", "gear", "rod", "wire", "tool", "storage_cell", "large_cogwheel"]
    fixture = build / "generated-registry-ids.txt"
    fixture.write_text("".join(f"{namespaces[i % 9]}:{materials[(i // 9) % 12]}_{shapes[(i // 108) % 12]}_{i // 1296:03d}\n" for i in range(100000)))
    # Run sequentially. No Java/Rust benchmark overlap, and no fixture preparation inside timed loops.
    with (build / "java-benchmark.csv").open("w") as out, (build / "java-benchmark-notes.txt").open("w") as err:
        subprocess.run([str(java), "-Xms512m", "-Xmx512m", "-cp", str(build / "classes"), "Benchmark", str(fixture)],
                       stdout=out, stderr=err, check=True, timeout=300)
    with (build / "rust-benchmark.csv").open("w") as out:
        subprocess.run([str(oracle), "bench", str(fixture)], stdout=out, check=True, timeout=300)
    java_rows = [r for r in csv.DictReader((build / "java-benchmark.csv").open()) if r["mode"] == "java-score"]
    rust_rows = list(csv.DictReader((build / "rust-benchmark.csv").open()))
    if len(java_rows) != 32 or len(rust_rows) != 32:
        raise SystemExit("Incomplete benchmark output")
    for j, r in zip(java_rows, rust_rows):
        if (j["count"], j["query"], j["checksum"]) != (r["count"], r["query"], r["checksum"]):
            raise SystemExit("Benchmark score checksum mismatch")

if input_identity() != before:
    raise SystemExit("Source or checkout identity changed during verification; this run cannot certify a tested commit")
verify_upstream()
receipt = {
    "upstream": PIN, "rust": rust_version,
    **before,
    "java": subprocess.check_output([str(javac), "-version"], text=True).strip(),
    "inputs_unchanged_during_run": True,
    "benchmarks": args.benchmarks,
}
(build / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
print((build / "differential.log").read_text(), end="")
print("PASS. Evidence:", build)
