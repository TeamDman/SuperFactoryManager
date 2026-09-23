"""Verify independent, fresh game-puppet runs using only Python's stdlib."""
import json
from pathlib import Path
import re
import struct
import sys

artifacts = Path(sys.argv[1]).resolve()
expected = {
    "title_screen_capture": {
        "loading-overlay", "title-screen-fading-in", "title-screen-settled"
    },
    "game_test_orbit_capture": {f"orbit-{index:02d}" for index in range(8)},
}
results = []
for puppet, expected_captures in expected.items():
    run = artifacts / puppet
    if (run / "exit-code.txt").read_text().strip() != "0":
        raise SystemExit(f"Puppet process failed: {puppet}")
    # CLI stdout reports build/launch progress. The raw JVM markers are in the
    # canonical run log, which run.sh snapshots before each subsequent launch.
    log = (run / "game-console.log").read_text(encoding="utf-8", errors="replace")
    completions = re.findall(r"SFM_GAME_PUPPET_COMPLETE(?=\s)[^\r\n]*", log)
    if (len(completions) != 1
            or not re.fullmatch(r"SFM_GAME_PUPPET_COMPLETE failed=0 total=1\s*", completions[0])
            or "SFM_GAME_PUPPET_FAILED" in log):
        raise SystemExit(f"Missing single-puppet successful completion marker: {puppet}")
    root = (run / "previews").resolve()
    manifest = json.loads((root / "preview-manifest.json").read_text(encoding="utf-8"))
    captures = manifest.get("captures", [])
    observed_puppets = {capture["puppet"].removeprefix("sfm:") for capture in captures}
    if observed_puppets != {puppet}:
        raise SystemExit(f"Unexpected screenshot set for {puppet}: {observed_puppets}")
    capture_names = {capture["capture"] for capture in captures}
    if capture_names != expected_captures or len(captures) != len(expected_captures):
        raise SystemExit(f"Expected exactly {len(expected_captures)} distinct captures for {puppet}; "
                         f"found {len(captures)} entries with names {sorted(capture_names)}")
    seen = set()
    for capture in captures:
        path = (root / capture["path"]).resolve()
        if not path.is_relative_to(root) or path in seen:
            raise SystemExit(f"Unsafe or duplicate screenshot reference: {capture['path']}")
        seen.add(path)
        if not path.is_file():
            raise SystemExit(f"Missing referenced screenshot: {capture['path']}")
        with path.open("rb") as stream:
            header = stream.read(24)
        if header[:8] != b"\x89PNG\r\n\x1a\n" or len(header) != 24:
            raise SystemExit(f"Invalid PNG: {path.relative_to(root)}")
        width, height = struct.unpack(">II", header[16:24])
        if (width, height) != (capture["width"], capture["height"]):
            raise SystemExit(f"Screenshot dimensions disagree with manifest: {capture['path']}")
        if width < 320 or height < 240:
            raise SystemExit(f"Unexpected screenshot dimensions: {width}x{height}")
    results.append({"puppet": puppet, "screenshots": len(seen)})
print(json.dumps({"passed": True, "runs": results}, indent=2))
