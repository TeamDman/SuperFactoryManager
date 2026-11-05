"""Extract or audit a checksum-verified Linux SDK tarball.

The caller supplies an already-open, SHA-512-verified archive on stdin.  This
helper rechecks that handle before using it.  It never calls tarfile.extract;
links are installed only after all directory and regular-file writes finish.
"""

import gzip
import hashlib
import os
from pathlib import Path
import stat
import sys
import tarfile

MAX_ARCHIVE = 2 * 1024**3
MAX_CONTENT = 8 * 1024**3
MAX_ENTRIES = 100_000
MAX_STREAM = MAX_CONTENT + 1024**3
RECEIPT = ".sfm-jbrsdk-receipt"


def fail(message):
    raise ValueError(message)


class BoundedStream:
    """Bound the whole decompressed tar stream, including PAX metadata."""

    def __init__(self, stream):
        self.stream = stream

    def tell(self):
        return self.stream.tell()

    def read(self, size):
        if size < 0 or size > MAX_STREAM - self.tell():
            fail("SDK decompressed tar stream exceeds its limit")
        return self.stream.read(size)

    def seek(self, offset, whence=0):
        if whence == 0:
            target = offset
        elif whence == 1:
            target = self.tell() + offset
        else:
            fail("unsupported SDK archive seek")
        if target < 0 or target > MAX_STREAM:
            fail("SDK decompressed tar stream exceeds its limit")
        return self.stream.seek(target)


def safe_name(name):
    name = name.rstrip("/")
    if not name or len(name.encode("utf-8")) > 4096 or "\\" in name:
        fail("unsafe SDK archive path")
    parts = name.split("/")
    for part in parts:
        if (not part or part in (".", "..") or part.endswith((" ", "."))
                or any(ord(ch) < 32 or ch in '<>:"|?*' for ch in part)):
            fail("unsafe SDK archive component")
    return tuple(parts)


def link_target(base, target):
    if not target or target.startswith("/") or "\\" in target:
        fail("unsafe SDK link target")
    parts = list(base)
    for part in target.split("/"):
        if not part or part == ".":
            continue
        if part == "..":
            if not parts:
                fail("SDK link escapes its home")
            parts.pop()
        else:
            safe_name(part)
            parts.append(part)
    if not parts:
        fail("SDK link targets its home root")
    return tuple(parts)


def archive_entries(archive):
    entries = {}
    names = set()
    root = None
    total = 0
    count = 0
    for member in archive:
        count += 1
        if count > MAX_ENTRIES:
            fail("SDK archive has too many entries")
        parts = safe_name(member.name)
        if root is None:
            root = parts[0]
        if parts[0] != root:
            fail("SDK archive has more than one root")
        relative = parts[1:]
        if not relative:
            if not member.isdir():
                fail("SDK archive root is not a directory")
            continue
        key = tuple(part.casefold() for part in relative)
        if key in names:
            fail("SDK archive contains duplicate paths")
        names.add(key)
        if member.isdir():
            kind = "directory"
        elif member.isfile():
            kind = "file"
            total += member.size
            if member.size < 0 or total > MAX_CONTENT:
                fail("SDK archive exceeds the extraction limit")
        elif member.issym():
            kind = "symlink"
        elif member.islnk():
            kind = "hardlink"
        else:
            fail("SDK archive contains a special entry")
        if kind != "file" and member.size != 0:
            fail("SDK non-file entry has content")
        if relative == (RECEIPT,):
            fail("SDK archive contains a reserved receipt")
        entries[relative] = (kind, member)
    if not entries:
        fail("SDK archive is empty")
    for path in list(entries):
        for i in range(1, len(path)):
            parent = path[:i]
            if parent in entries and entries[parent][0] != "directory":
                fail("SDK archive has a non-directory ancestor")
            entries.setdefault(parent, ("directory", None))
    folded = set()
    for path in entries:
        key = tuple(part.casefold() for part in path)
        if key in folded:
            fail("SDK archive contains case-colliding paths")
        folded.add(key)
    for path, (kind, member) in entries.items():
        if kind == "symlink":
            target = link_target(path[:-1], member.linkname)
            if target not in entries or entries[target][0] not in ("file", "directory"):
                fail("SDK symlink target is not an archive file or directory")
        elif kind == "hardlink":
            absolute = safe_name(member.linkname)
            if absolute[0] != root or absolute[1:] not in entries:
                fail("SDK hardlink target is outside the archive home")
            if entries[absolute[1:]][0] != "file":
                fail("SDK hardlink target is not a regular file")
    for required in [("bin", "java"), ("bin", "javac"), ("lib", "modules")]:
        if required not in entries or entries[required][0] != "file":
            fail("SDK archive does not contain its required files")
    return entries


def iter_bytes(source, expected):
    remaining = expected
    while remaining:
        data = source.read(min(1024 * 1024, remaining))
        if not data:
            fail("truncated SDK archive entry")
        remaining -= len(data)
        yield data
    if source.read(1):
        fail("SDK archive entry exceeds its declared size")


def verify_archive_tail(stream, offset):
    # Consume through the gzip trailer before writing anything.  Tar permits
    # zero padding after its end marker, but not hidden nonzero content.
    stream.seek(offset)
    while True:
        remaining = MAX_STREAM - stream.tell()
        if remaining <= 0:
            fail("SDK decompressed tar stream exceeds its limit")
        chunk = stream.read(min(1024 * 1024, remaining))
        if not chunk:
            return
        if any(chunk):
            fail("SDK archive contains trailing nonzero content")


def create(archive, entries, home):
    for path, (kind, _) in sorted(entries.items(), key=lambda item: len(item[0])):
        if kind == "directory":
            home.joinpath(*path).mkdir(exist_ok=True)
    for path, (kind, member) in entries.items():
        output = home.joinpath(*path)
        if kind == "file":
            source = archive.extractfile(member)
            if source is None:
                fail("SDK archive file has no content")
            with source, output.open("xb") as out:
                for data in iter_bytes(source, member.size):
                    out.write(data)
                out.flush()
                os.fsync(out.fileno())
            os.chmod(output, member.mode & 0o777)
    for path, (kind, member) in entries.items():
        output = home.joinpath(*path)
        if kind == "symlink":
            os.symlink(member.linkname, output)
        elif kind == "hardlink":
            target = safe_name(member.linkname)[1:]
            os.link(home.joinpath(*target), output, follow_symlinks=False)
    for path, (kind, member) in sorted(entries.items(), key=lambda item: -len(item[0])):
        if kind == "directory" and member is not None:
            os.chmod(home.joinpath(*path), member.mode & 0o777)


def audit(archive, entries, home):
    actual = set()
    def raise_walk_error(error):
        raise error
    for base, dirs, files in os.walk(home, followlinks=False, onerror=raise_walk_error):
        for name in dirs + files:
            path = Path(base, name)
            rel = path.relative_to(home).parts
            if rel != (RECEIPT,):
                actual.add(rel)
                if len(actual) > len(entries):
                    fail("SDK installation has unexpected extra paths")
    if actual != set(entries):
        fail("SDK installation paths differ from the verified archive")
    for path, (kind, member) in entries.items():
        output = home.joinpath(*path)
        info = output.lstat()
        if kind == "directory":
            if not stat.S_ISDIR(info.st_mode):
                fail("SDK directory changed")
        elif kind == "symlink":
            if not stat.S_ISLNK(info.st_mode) or os.readlink(output) != member.linkname:
                fail("SDK symlink changed")
            continue
        elif kind == "hardlink":
            target = home.joinpath(*safe_name(member.linkname)[1:]).lstat()
            if not stat.S_ISREG(info.st_mode) or (info.st_dev, info.st_ino) != (target.st_dev, target.st_ino):
                fail("SDK hardlink changed")
            continue
        else:
            if not stat.S_ISREG(info.st_mode) or info.st_size != member.size:
                fail("SDK file changed")
            source = archive.extractfile(member)
            if source is None:
                fail("SDK archive file has no content")
            flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
            fd = os.open(output, flags)
            current_info = os.fstat(fd)
            if (current_info.st_dev, current_info.st_ino) != (info.st_dev, info.st_ino):
                os.close(fd)
                fail("SDK file changed while opening")
            with source, os.fdopen(fd, "rb") as current:
                for data in iter_bytes(source, member.size):
                    if current.read(len(data)) != data:
                        fail("SDK file content changed")
                if current.read(1):
                    fail("SDK file has extra content")
        if member is not None and stat.S_IMODE(info.st_mode) != member.mode & 0o777:
            fail("SDK permissions changed")


def main():
    mode, target, expected = sys.argv[1:]
    if sys.platform.startswith("linux"):
        import resource
        soft, hard = resource.getrlimit(resource.RLIMIT_AS)
        cap = 1024**3
        if soft != resource.RLIM_INFINITY:
            cap = min(cap, soft)
        if hard != resource.RLIM_INFINITY:
            cap = min(cap, hard)
        resource.setrlimit(resource.RLIMIT_AS, (cap, hard))
    source = sys.stdin.buffer
    source_stat = os.fstat(source.fileno())
    if not stat.S_ISREG(source_stat.st_mode) or source_stat.st_size > MAX_ARCHIVE:
        fail("SDK archive exceeds the download limit")
    digest = hashlib.sha512()
    source.seek(0)
    while data := source.read(1024 * 1024):
        digest.update(data)
    if digest.hexdigest() != expected:
        fail("SDK archive SHA-512 mismatch")
    source.seek(0)
    home = Path(target)
    if home.is_symlink() or not home.is_dir():
        fail("SDK target is not a real directory")
    with gzip.GzipFile(fileobj=source, mode="rb") as compressed:
        bounded = BoundedStream(compressed)
        with tarfile.open(fileobj=bounded, mode="r:") as archive:
            entries = archive_entries(archive)
            verify_archive_tail(bounded, archive.offset)
            if mode == "extract":
                if any(home.iterdir()):
                    fail("SDK extraction target is not empty")
                create(archive, entries, home)
            elif mode != "verify":
                fail("unknown SDK archive operation")
            audit(archive, entries, home)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, EOFError, tarfile.TarError) as error:
        print(f"SDK tar validation failed: {error}", file=sys.stderr)
        sys.exit(1)
