"""Inspect retained native javac argument files without preparing or fetching inputs.

Usage: py -3 -B report-retained-javac-classpaths.py REPO_ROOT EVIDENCE_DIR
The three receipt names below select one already completed run per loader family.
"""

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re


SAMPLES = (
    ("forge-1.19.2", "sfm-4.35.0-issue610-portable/mc-1.19.2",
     "portable-green1192-compile-20261029-runner-currentcli"),
    ("forge-1.20.1", "sfm-4.35.0-issue610-portable/mc-1.20.1",
     "portable-green1201-compile-20261029-currentcli"),
    ("neoforge-1.20.4", "sfm-4.35.0-issue610-portable/mc-1.20.4",
     "portable-green1204-compile-20261029-runner-currentcli"),
)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalized(path):
    return str(path).replace("/", "\\").lower()


def inspect(repo, evidence, family, key, receipt):
    run_path = evidence / (receipt + ".run.json")
    log_path = evidence / (receipt + ".ndjson")
    run = json.loads(run_path.read_text(encoding="utf-8"))
    if run.get("exit_code") != 0 or not run.get("source_stable"):
        raise ValueError(f"No completed source-stable compile: {receipt}")
    messages = []
    tool_messages = []
    for line in log_path.open(encoding="utf-8"):
        event = json.loads(line)
        message = event.get("fields", {}).get("message", "")
        if message.startswith("javac main: start "):
            messages.append(message)
        if message.startswith("Java tool ") and ": start main=" in message:
            tool_messages.append(message)
    if len(messages) != 1:
        raise ValueError(f"Expected one main javac invocation: {receipt}")
    match = re.search(r"argfile=(.*)$", messages[0])
    if not match:
        raise ValueError(f"No argfile locator: {receipt}")
    args_path = Path(match.group(1))
    lines = args_path.read_text(encoding="utf-8").splitlines()
    if lines.count("-classpath") != 1:
        raise ValueError(f"Expected one javac classpath: {args_path}")
    paths = lines[lines.index("-classpath") + 1].split(";")
    if len({normalized(path) for path in paths}) != len(paths):
        raise ValueError(f"Duplicate javac classpath entry: {args_path}")

    lock_path = repo / "platform/minecraft/projections" / key / "sfm-toolchain.lock.json"
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    shared = repo / "sfm-toolchain-data/minecraft-toolchain"
    artifacts = {}
    for artifact in lock["artifacts"]:
        portable = artifact.get("cache_path", "")
        if portable.startswith("$sfm-cache\\"):
            physical = shared / portable[len("$sfm-cache\\"):].replace("\\", "/")
            artifacts[normalized(physical)] = artifact["id"]
    categories = Counter()
    members = []
    for ordinal, path_text in enumerate(paths):
        path = Path(path_text)
        if not path.is_file():
            raise ValueError(f"Missing retained classpath input: {path}")
        identity = normalized(path)
        if identity in artifacts:
            category = "current-lock-artifact-path"
            artifact_id = artifacts[identity]
        elif "\\minecraft-toolchain\\minecraft\\libraries\\" in identity:
            category = "minecraft-version-manifest-library-path"
            artifact_id = None
        elif "\\sfm-toolchain-data\\" in identity:
            category = "derived-or-project-path"
            artifact_id = None
        else:
            category = "other-path"
            artifact_id = None
        categories[category] += 1
        members.append({"ordinal": ordinal, "path": path_text, "category": category,
                        "current_lock_artifact_id": artifact_id})
    tools = []
    for message in tool_messages:
        tool_match = re.fullmatch(
            r"Java tool ([^:]+): start main=(\S+) argfile=(.+?) log=(.+)",
            message,
        )
        if not tool_match:
            raise ValueError(f"Malformed Java tool invocation: {message}")
        tool_id, main_class, tool_argfile, _ = tool_match.groups()
        tool_argfile = Path(tool_argfile)
        tool_lines = tool_argfile.read_text(encoding="utf-8").splitlines()
        classpath_flag = next((flag for flag in ("-cp", "-classpath")
                               if flag in tool_lines), None)
        if classpath_flag is None or tool_lines.count(classpath_flag) != 1:
            raise ValueError(f"Missing unique Java tool classpath: {tool_argfile}")
        classpath_index = tool_lines.index(classpath_flag)
        if classpath_index + 1 >= len(tool_lines):
            raise ValueError(f"Empty Java tool classpath: {tool_argfile}")
        tool_paths = tool_lines[classpath_index + 1].split(";")
        if len({normalized(path) for path in tool_paths}) != len(tool_paths):
            raise ValueError(f"Duplicate Java tool classpath entry: {tool_argfile}")
        tool_members = []
        for ordinal, path_text in enumerate(tool_paths):
            path = Path(path_text)
            if not path.is_file():
                raise ValueError(f"Missing retained Java tool input: {path}")
            artifact_id = artifacts.get(normalized(path))
            tool_members.append({"ordinal": ordinal, "path": path_text,
                                 "current_lock_artifact_id": artifact_id})
        tools.append({"tool_id": tool_id, "main_class": main_class,
                      "retained_argfile": str(tool_argfile),
                      "argfile_sha256": sha256(tool_argfile),
                      "ordered_classpath": tool_members})
    return {
        "family": family, "projection": key, "receipt": receipt,
        "run_receipt_sha256": sha256(run_path), "log_sha256": sha256(log_path),
        "retained_argfile": str(args_path), "argfile_sha256": sha256(args_path),
        "current_lock_sha256": sha256(lock_path),
        "source_before": run["source_before"], "cli_sha256": run["cli_sha256"],
        "classpath_entries": len(paths), "category_counts": dict(sorted(categories.items())),
        "ordered_members": members,
        "java_tool_invocations": tools,
    }


def report(repo, evidence):
    return {
        "schema": 1,
        "scope": "Retained successful 2026-10-29 main-javac and Forge Java-tool invocations; current lock path joins are not invocation-time hash verification",
        "samples": [inspect(repo, evidence, *sample) for sample in SAMPLES],
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo_root", type=Path)
    parser.add_argument("evidence_dir", type=Path)
    args = parser.parse_args()
    print(json.dumps(report(args.repo_root.resolve(strict=True),
                            args.evidence_dir.resolve(strict=True)), indent=2))
