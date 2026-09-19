"""Verify new game-puppet screenshots and completion, using only Python's stdlib."""
import json
from collections import Counter
from pathlib import Path
import re
import struct
import sys

root = Path(sys.argv[1]).resolve()
log = Path(sys.argv[2]).read_text(encoding="utf-8", errors="replace")
if not re.search(r"SFM_GAME_PUPPET_COMPLETE failed=0 total=[1-9]\d*", log):
    raise SystemExit("Missing successful game-puppet completion marker")
manifest = json.loads((root / "preview-manifest.json").read_text(encoding="utf-8"))
captures = manifest.get("captures", [])
puppets = {capture["puppet"].removeprefix("sfm:") for capture in captures}
expected = {"title_screen_capture", "game_test_orbit_capture"}
if not expected <= puppets:
    raise SystemExit(f"Missing requested screenshot sets: {expected - puppets}")
counts = Counter(capture["puppet"].removeprefix("sfm:") for capture in captures)
if counts["title_screen_capture"] < 3 or counts["game_test_orbit_capture"] < 8:
    raise SystemExit(f"Expected 3 title-screen and 8 orbit captures, found {counts}")
seen = set()
for capture in captures:
    path = (root / capture["path"]).resolve()
    if not path.is_relative_to(root) or path in seen:
        raise SystemExit(f"Unsafe or duplicate screenshot reference: {capture['path']}")
    seen.add(path)
    if not path.is_file():
        raise SystemExit(f"Missing referenced screenshot: {capture['path']}")
    header = path.read_bytes()[:24]
    if header[:8] != b"\x89PNG\r\n\x1a\n" or len(header) != 24:
        raise SystemExit(f"Invalid PNG: {path.relative_to(root)}")
    width, height = struct.unpack(">II", header[16:24])
    if (width, height) != (capture["width"], capture["height"]):
        raise SystemExit(f"Screenshot dimensions disagree with manifest: {capture['path']}")
    if width < 320 or height < 240:
        raise SystemExit(f"Unexpected screenshot dimensions: {width}x{height}")
print(json.dumps({"passed": True, "puppets": sorted(puppets), "screenshots": len(seen)}, indent=2))
