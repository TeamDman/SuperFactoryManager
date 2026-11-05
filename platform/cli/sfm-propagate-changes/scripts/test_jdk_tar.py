"""Offline regression tests for the JBRSDK tar validation helper."""
import gzip
import hashlib
import importlib.util
import io
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest

HELPER = Path(__file__).resolve().parents[1] / "src" / "jdk_tar.py"
spec = importlib.util.spec_from_file_location("jdk_tar", HELPER)
jdk_tar = importlib.util.module_from_spec(spec)
spec.loader.exec_module(jdk_tar)


def archive_bytes(extra=()):
    data = io.BytesIO()
    with tarfile.open(fileobj=data, mode="w:gz") as archive:
        entries = [
            ("jdk/bin/java", b"java", 0o755, None),
            ("jdk/bin/javac", b"javac", 0o755, None),
            ("jdk/lib/modules", b"modules", 0o644, None),
            ("jdk/legal/LICENSE", b"license", 0o644, None),
            ("jdk/legal/other/LICENSE", b"", 0o777, "../LICENSE"),
            *extra,
        ]
        for name, body, mode, link in entries:
            info = tarfile.TarInfo(name)
            info.mode = mode
            if link is None:
                info.size = len(body)
                archive.addfile(info, io.BytesIO(body))
            elif isinstance(link, tuple):
                info.type = tarfile.LNKTYPE
                info.linkname = link[1]
                archive.addfile(info)
            else:
                info.type = tarfile.SYMTYPE
                info.linkname = link
                archive.addfile(info)
    return data.getvalue()


def run_helper(archive, mode, home, digest=None):
    with tempfile.TemporaryFile() as source:
        source.write(archive)
        source.seek(0)
        return subprocess.run(
            [sys.executable, "-I", "-S", str(HELPER), mode, str(home),
             digest or hashlib.sha512(archive).hexdigest()],
            stdin=source, capture_output=True, text=True, check=False,
        )


class JdkTarTests(unittest.TestCase):
    def test_extract_and_audit_preserve_modes_and_links(self):
        data = archive_bytes()
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            self.assertEqual(run_helper(data, "extract", home).returncode, 0)
            self.assertEqual((home / "bin/java").stat().st_mode & 0o777, 0o755)
            self.assertEqual((home / "legal/other/LICENSE").readlink(), Path("../LICENSE"))
            self.assertEqual(run_helper(data, "verify", home).returncode, 0)
            (home / "bin/java").write_bytes(b"tampered")
            self.assertNotEqual(run_helper(data, "verify", home).returncode, 0)

    def test_wrong_digest_never_extracts(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertNotEqual(run_helper(archive_bytes(), "extract", tmp, "0" * 128).returncode, 0)
            self.assertEqual(list(Path(tmp).iterdir()), [])

    def test_truncated_gzip_with_matching_digest_never_extracts(self):
        with tempfile.TemporaryDirectory() as tmp:
            truncated = archive_bytes()[:-8]
            self.assertNotEqual(run_helper(truncated, "extract", tmp).returncode, 0)
            self.assertEqual(list(Path(tmp).iterdir()), [])

    def test_nonzero_data_after_tar_end_never_extracts(self):
        with tempfile.TemporaryDirectory() as tmp:
            data = gzip.compress(gzip.decompress(archive_bytes()) + b"hidden")
            self.assertNotEqual(run_helper(data, "extract", tmp).returncode, 0)
            self.assertEqual(list(Path(tmp).iterdir()), [])

    def test_unsafe_paths_and_links_never_extract(self):
        cases = [
            ("jdk/../outside", b"bad", 0o644, None),
            ("jdk/escape", b"", 0o777, "../../outside"),
            ("jdk/absolute", b"", 0o777, "/etc/passwd"),
            ("jdk/link", b"", 0o777, "bin"),
        ]
        for bad in cases[:3]:
            with self.subTest(bad=bad), tempfile.TemporaryDirectory() as tmp:
                self.assertNotEqual(run_helper(archive_bytes([bad]), "extract", tmp).returncode, 0)
                self.assertEqual(list(Path(tmp).iterdir()), [])
        with tempfile.TemporaryDirectory() as tmp:
            bad = [cases[3], ("jdk/link/child", b"bad", 0o644, None)]
            self.assertNotEqual(run_helper(archive_bytes(bad), "extract", tmp).returncode, 0)
            self.assertEqual(list(Path(tmp).iterdir()), [])

    def test_duplicate_extra_and_changed_mode_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            duplicate = [("jdk/bin/java", b"other", 0o755, None)]
            self.assertNotEqual(run_helper(archive_bytes(duplicate), "extract", tmp).returncode, 0)
            self.assertEqual(list(Path(tmp).iterdir()), [])
        data = archive_bytes()
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            self.assertEqual(run_helper(data, "extract", home).returncode, 0)
            (home / "bin/java").chmod(0o644)
            self.assertNotEqual(run_helper(data, "verify", home).returncode, 0)
            (home / "bin/java").chmod(0o755)
            (home / "extra").write_text("extra")
            self.assertNotEqual(run_helper(data, "verify", home).returncode, 0)

    def test_expanded_size_limit_precedes_extraction(self):
        data = archive_bytes()
        with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
            old = jdk_tar.MAX_CONTENT
            try:
                jdk_tar.MAX_CONTENT = 1
                with self.assertRaises(ValueError):
                    jdk_tar.archive_entries(archive)
            finally:
                jdk_tar.MAX_CONTENT = old

    def test_decompressed_stream_bound_includes_metadata(self):
        old = jdk_tar.MAX_STREAM
        try:
            jdk_tar.MAX_STREAM = 4
            stream = jdk_tar.BoundedStream(io.BytesIO(b"12345"))
            self.assertEqual(stream.read(4), b"1234")
            with self.assertRaises(ValueError):
                stream.read(1)
            with self.assertRaises(ValueError):
                stream.seek(5)
        finally:
            jdk_tar.MAX_STREAM = old

    def test_safe_hardlink_and_tampered_symlink(self):
        data = archive_bytes([("jdk/lib/modules-copy", b"", 0o644, ("hard", "jdk/lib/modules"))])
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            self.assertEqual(run_helper(data, "extract", home).returncode, 0)
            self.assertEqual((home / "lib/modules").stat().st_ino,
                             (home / "lib/modules-copy").stat().st_ino)
            link = home / "legal/other/LICENSE"
            link.unlink()
            link.symlink_to("../../lib/modules")
            self.assertNotEqual(run_helper(data, "verify", home).returncode, 0)

    def test_unsafe_hardlink_and_case_colliding_directories_are_rejected(self):
        bad_cases = [
            [("jdk/lib/copy", b"", 0o644, ("hard", "other/modules"))],
            [("jdk/Extra/one", b"1", 0o644, None),
             ("jdk/extra/two", b"2", 0o644, None)],
        ]
        for bad in bad_cases:
            with self.subTest(bad=bad), tempfile.TemporaryDirectory() as tmp:
                self.assertNotEqual(run_helper(archive_bytes(bad), "extract", tmp).returncode, 0)
                self.assertEqual(list(Path(tmp).iterdir()), [])


if __name__ == "__main__":
    unittest.main()
