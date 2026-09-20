"""Offline regressions for the evidence contract; never starts Minecraft."""
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import zlib


# Reduced from the hosted run that exposed the wrapper/JVM log distinction.
CLI_LOG = """[ci-container rust] Minecraft JVM output written to /workspace/platform/minecraft/build/sfm-toolchain/run/runGameTestPreview/console.log
[ci-container rust] Validated game puppet preview completion.
  retained_viewport=false
"""
# Marker emitted by SFMGamePuppetHarness and consumed by the canonical CLI.
GAME_LOG = "[Render thread/INFO] SFM_GAME_PUPPET_COMPLETE failed=0 total=1\n"
PUPPETS = {
    "title_screen_capture": ["loading-overlay", "title-screen-fading-in", "title-screen-settled"],
    "game_test_orbit_capture": [f"orbit-{i:02d}" for i in range(8)],
}


def png_chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def fixture_png():
    return (b"\x89PNG\r\n\x1a\n"
            + png_chunk(b"IHDR", struct.pack(">IIBBBBB", 320, 240, 8, 2, 0, 0, 0))
            + png_chunk(b"IDAT", zlib.compress((b"\0" + b"\x80" * (320 * 3)) * 240))
            + png_chunk(b"IEND", b""))


class VerifyEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="sfm-container-evidence-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for puppet, captures in PUPPETS.items():
            run = self.root / puppet
            previews = run / "previews"
            previews.mkdir(parents=True)
            (run / "exit-code.txt").write_text("0\n", encoding="utf-8")
            (run / "console.log").write_text(CLI_LOG, encoding="utf-8")
            (run / "game-console.log").write_text(GAME_LOG, encoding="utf-8")
            manifest = {"captures": []}
            for name in captures:
                path = f"{name}.png"
                (previews / path).write_bytes(fixture_png())
                manifest["captures"].append({"puppet": puppet, "capture": name,
                                             "path": path, "width": 320, "height": 240})
            (previews / "preview-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        self.title = self.root / "title_screen_capture"

    def verify(self, expected_success):
        result = subprocess.run([sys.executable, str(Path(__file__).with_name("verify.py")),
                                 str(self.root)], capture_output=True, text=True, check=False)
        self.assertEqual(result.returncode == 0, expected_success, result.stdout + result.stderr)
        return result

    def test_non_tty_wrapper_without_marker_uses_jvm_evidence(self):
        result = self.verify(True)
        self.assertEqual(json.loads(result.stdout)["runs"], [
            {"puppet": "title_screen_capture", "screenshots": 3},
            {"puppet": "game_test_orbit_capture", "screenshots": 8},
        ])

    def test_wrapper_marker_cannot_replace_missing_jvm_marker(self):
        (self.title / "console.log").write_text(GAME_LOG, encoding="utf-8")
        (self.title / "game-console.log").write_text(CLI_LOG, encoding="utf-8")
        self.verify(False)

    def test_missing_jvm_log_fails(self):
        (self.title / "game-console.log").unlink()
        self.verify(False)

    def test_duplicate_completion_fails(self):
        (self.title / "game-console.log").write_text(GAME_LOG * 2, encoding="utf-8")
        self.verify(False)

    def test_failed_puppet_marker_fails(self):
        (self.title / "game-console.log").write_text(
            GAME_LOG + "SFM_GAME_PUPPET_FAILED puppet=title_screen_capture\n", encoding="utf-8")
        self.verify(False)

    def test_failed_or_multiple_puppet_completion_fails(self):
        for marker in ("SFM_GAME_PUPPET_COMPLETE failed=1 total=1\n",
                       "SFM_GAME_PUPPET_COMPLETE failed=0 total=2\n",
                       "SFM_GAME_PUPPET_COMPLETE_PENDING failed=0 total=1\n"):
            with self.subTest(marker=marker):
                (self.title / "game-console.log").write_text(marker, encoding="utf-8")
                self.verify(False)

    def test_nonzero_process_exit_fails_with_good_artifacts(self):
        (self.title / "exit-code.txt").write_text("1\n", encoding="utf-8")
        self.verify(False)

    def test_missing_referenced_png_fails(self):
        (self.title / "previews/loading-overlay.png").unlink()
        self.verify(False)

    def test_incomplete_manifest_fails(self):
        path = self.title / "previews/preview-manifest.json"
        manifest = json.loads(path.read_text(encoding="utf-8"))
        manifest["captures"].pop()
        path.write_text(json.dumps(manifest), encoding="utf-8")
        self.verify(False)


if __name__ == "__main__":
    unittest.main()
