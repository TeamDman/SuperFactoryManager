"""Private Windows held-input authority for optional baseline disassembly reuse.

Only explicitly selected local paths are opened. Retained read-share-only leaves
and top-down directory leases protect existing identities, not future children.
SdkImage therefore also checks exact selected membership at effect boundaries.
This does not attest arbitrary native loading from outside the selected SDK.
"""

from contextlib import contextmanager
import ctypes
import hashlib
import ntpath
import os
from pathlib import Path
import platform
import re
import stat
import time


class AuthorityError(RuntimeError):
    """A selected input or held identity cannot safely authorize reuse."""


class CapabilityUnavailable(AuthorityError):
    """The required Windows filesystem/handle capability is unavailable."""


_DWORD = ctypes.c_uint32
_HANDLE = ctypes.c_void_p


class _FileTime(ctypes.Structure):
    _fields_ = [("low", _DWORD), ("high", _DWORD)]


class _HandleInformation(ctypes.Structure):
    _fields_ = [
        ("attributes", _DWORD),
        ("creation", _FileTime),
        ("access", _FileTime),
        ("write", _FileTime),
        ("volume", _DWORD),
        ("size_high", _DWORD),
        ("size_low", _DWORD),
        ("links", _DWORD),
        ("index_high", _DWORD),
        ("index_low", _DWORD),
    ]


class _FileIdInformation(ctypes.Structure):
    _fields_ = [("volume", ctypes.c_uint64), ("file_id", ctypes.c_ubyte * 16)]


class _Overlapped(ctypes.Structure):
    _fields_ = [
        ("internal", ctypes.c_size_t),
        ("internal_high", ctypes.c_size_t),
        ("offset", _DWORD),
        ("offset_high", _DWORD),
        ("event", _HANDLE),
    ]


_API = None
_INVALID_HANDLE = ctypes.c_void_p(-1).value
_DIRECTORY = 0x10
_REPARSE = 0x400
_OPEN_REPARSE = 0x00200000
_BACKUP_SEMANTICS = 0x02000000
_READ_SHARE = 1
_READ_WRITE_SHARE = 3
_OPEN_EXISTING = 3
_OPEN_ALWAYS = 4
_MAX_PATH_CHARS = 32768
_RESERVED = re.compile(r"^(?:CON|PRN|AUX|NUL|CONIN\$|CONOUT\$|COM[1-9¹²³]|LPT[1-9¹²³])(?:\.|$)", re.I)


def _windows_api():
    global _API
    if os.name != "nt":
        raise CapabilityUnavailable("Baseline held-input authority requires Windows")
    if _API is not None:
        return _API
    try:
        api = ctypes.WinDLL("kernel32", use_last_error=True)
        signatures = {
            "CreateFileW": ([ctypes.c_wchar_p, _DWORD, _DWORD, ctypes.c_void_p, _DWORD, _DWORD, _HANDLE], _HANDLE),
            "CloseHandle": ([_HANDLE], ctypes.c_int),
            "GetFileType": ([_HANDLE], _DWORD),
            "GetFileInformationByHandle": ([_HANDLE, ctypes.POINTER(_HandleInformation)], ctypes.c_int),
            "GetFileInformationByHandleEx": ([_HANDLE, ctypes.c_int, ctypes.c_void_p, _DWORD], ctypes.c_int),
            "GetFinalPathNameByHandleW": ([_HANDLE, ctypes.c_wchar_p, _DWORD, _DWORD], _DWORD),
            "GetDriveTypeW": ([ctypes.c_wchar_p], _DWORD),
            "LockFileEx": ([_HANDLE, _DWORD, _DWORD, _DWORD, _DWORD, ctypes.POINTER(_Overlapped)], ctypes.c_int),
            "UnlockFileEx": ([_HANDLE, _DWORD, _DWORD, _DWORD, ctypes.POINTER(_Overlapped)], ctypes.c_int),
        }
        for name, (arguments, result) in signatures.items():
            function = getattr(api, name)
            function.argtypes = arguments
            function.restype = result
    except (AttributeError, OSError) as error:
        raise CapabilityUnavailable(f"Windows held-input API unavailable: {error}") from error
    _API = api
    return api


def _failure(operation, path, code=None):
    code = ctypes.get_last_error() if code is None else code
    message = f"{operation} {path}: WinError {code}: {ctypes.FormatError(code).strip()}"
    if code in (2, 3):
        return FileNotFoundError(code, message, str(path))
    if code in (1, 50, 87, 120, 124):
        return CapabilityUnavailable(message)
    return AuthorityError(message)


def _normal_path(value):
    raw = os.fspath(value)
    if not isinstance(raw, str) or not raw or "\x00" in raw:
        raise AuthorityError("Selected path must be a nonempty normal Unicode path")
    drive, tail = ntpath.splitdrive(raw)
    if not re.fullmatch(r"[A-Za-z]:", drive) or not tail.startswith(("\\", "/")):
        raise AuthorityError(f"Selected path must be absolute on a local drive: {raw}")
    parts = tail.replace("/", "\\").split("\\")[1:]
    if parts and parts[-1] == "":
        parts.pop()
    for part in parts:
        if (not part or part in (".", "..") or any(character in '<>:"|?*' for character in part)
                or part.endswith((".", " ")) or _RESERVED.match(part)
                or any(ord(character) < 32 for character in part)):
            raise AuthorityError(f"Unsafe or ambiguous selected path component: {raw}")
    path = Path(raw)
    if _windows_api().GetDriveTypeW(drive + "\\") != 3:
        raise CapabilityUnavailable(f"Selected path is not on a fixed local drive: {raw}")
    return path


def _path_key(path):
    return ntpath.normcase(ntpath.normpath(str(path)))


def _native_path(path):
    return "\\\\?\\" + str(path)


def _close_handle(handle):
    if handle not in (None, _INVALID_HANDLE):
        _windows_api().CloseHandle(handle)


def _open(path, *, directory=False, lock=False, directory_share=_READ_SHARE):
    api = _windows_api()
    access = 0x81 if directory else (0xC0000000 if lock else 0x80000000)
    flags = _OPEN_REPARSE | (_BACKUP_SEMANTICS if directory else 0)
    share = directory_share if directory else (_READ_WRITE_SHARE if lock else _READ_SHARE)
    handle = api.CreateFileW(
        _native_path(path), access, share,
        None, _OPEN_ALWAYS if lock else _OPEN_EXISTING, flags, None,
    )
    if handle in (None, _INVALID_HANDLE):
        raise _failure("Cannot open held input", path)
    return handle


def _final_path(handle, path):
    api = _windows_api()
    size = api.GetFinalPathNameByHandleW(handle, None, 0, 0)
    if not size:
        raise _failure("Cannot resolve held path", path)
    if size >= _MAX_PATH_CHARS:
        raise AuthorityError(f"Held path exceeds its bound: {path}")
    buffer = ctypes.create_unicode_buffer(size + 1)
    length = api.GetFinalPathNameByHandleW(handle, buffer, len(buffer), 0)
    if not length:
        raise _failure("Cannot resolve held path", path)
    if length >= len(buffer):
        raise AuthorityError(f"Held path spelling changed during resolution: {path}")
    resolved = buffer.value
    if not resolved.startswith("\\\\?\\") or resolved.startswith("\\\\?\\UNC\\"):
        raise AuthorityError(f"Held input has a nonlocal final path: {path}")
    resolved = resolved[4:]
    if _path_key(resolved) != _path_key(path):
        raise AuthorityError(f"Selected path resolves through an alias: {path}")
    return resolved


def _snapshot(handle, path, kind):
    api = _windows_api()
    if api.GetFileType(handle) != 1:
        raise CapabilityUnavailable(f"Selected input is not an ordinary disk handle: {path}")
    information = _HandleInformation()
    if not api.GetFileInformationByHandle(handle, ctypes.byref(information)):
        raise _failure("Cannot inspect held input", path)
    identity = _FileIdInformation()
    if not api.GetFileInformationByHandleEx(handle, 18, ctypes.byref(identity), ctypes.sizeof(identity)):
        raise _failure("Full 128-bit file identity unavailable", path)
    if not any(identity.file_id):
        raise CapabilityUnavailable(f"Windows returned no stable full file identity: {path}")
    attributes = information.attributes
    if attributes & _REPARSE or bool(attributes & _DIRECTORY) != (kind == "directory"):
        raise AuthorityError(f"Selected input is a reparse point or has the wrong type: {path}")
    if kind != "directory" and information.links != 1:
        raise AuthorityError(f"Selected file must have exactly one link: {path}")
    return {
        "identity": (int(identity.volume), bytes(identity.file_id)),
        "attributes": int(attributes),
        "length": (int(information.size_high) << 32) | int(information.size_low),
        "links": int(information.links),
        "creation": (int(information.creation.high) << 32) | int(information.creation.low),
        "write": (int(information.write.high) << 32) | int(information.write.low),
    }


def _same(current, original, kind):
    fields = ("identity", "attributes") if kind == "directory" else (
        ("identity", "attributes", "links") if kind == "lock"
        else ("identity", "attributes", "links", "length", "creation", "write")
    )
    return all(current[field] == original[field] for field in fields)


class HeldInput:
    """A seekable read-only leaf whose Windows handle remains owned by its authority."""

    def __init__(self, owner, path, handle, metadata, max_bytes):
        import msvcrt
        self._owner = owner
        self.path = path
        self.length = metadata["length"]
        self._metadata = metadata
        self._max_bytes = max_bytes
        self.stream = None
        descriptor = None
        transferred = False
        try:
            descriptor = msvcrt.open_osfhandle(handle, os.O_RDONLY | os.O_BINARY)
            transferred = True
            self.stream = os.fdopen(descriptor, "rb")
            descriptor = None
            self._handle = msvcrt.get_osfhandle(self.stream.fileno())
            digest = hashlib.sha256()
            remaining = self.length
            while remaining:
                block = self.stream.read(min(1024 * 1024, remaining))
                if not block:
                    raise AuthorityError(f"Held input shortened while hashing: {path}")
                digest.update(block)
                remaining -= len(block)
            if self.stream.read(1):
                raise AuthorityError(f"Held input grew while hashing: {path}")
            self.digest = digest.hexdigest()
            self.stream.seek(0)
            self.recheck()
        except BaseException:
            if self.stream is not None:
                self.stream.close()
            elif descriptor is not None:
                os.close(descriptor)
            elif not transferred:
                _close_handle(handle)
            raise

    def recheck(self):
        self._owner._require_open()
        if self.stream is None or self.stream.closed:
            raise AuthorityError(f"Held input stream closed before last use: {self.path}")
        current = _snapshot(self._handle, self.path, "file")
        if not _same(current, self._metadata, "file"):
            raise AuthorityError(f"Held input identity or metadata changed: {self.path}")

    def read_bytes(self):
        self.recheck()
        position = self.stream.tell()
        try:
            self.stream.seek(0)
            data = self.stream.read(self.length + 1)
            if len(data) != self.length or hashlib.sha256(data).hexdigest() != self.digest:
                raise AuthorityError(f"Held input bytes changed: {self.path}")
            self.recheck()
            return data
        finally:
            self.stream.seek(position)


class HeldAuthority:
    """Retain checked local directory ancestry, leaves and per-key OS locks."""

    def __init__(self, directory_share=_READ_SHARE):
        if (isinstance(directory_share, bool) or not isinstance(directory_share, int)
                or directory_share not in (_READ_SHARE, _READ_WRITE_SHARE)):
            raise AuthorityError("Directory sharing must be read-only or read/write, always denying delete")
        _windows_api()
        # Mutable cache directories permit cooperating child publication while
        # retaining their own identity. Selected SDK/archive owners keep 1.
        self._directory_share = directory_share
        self._closed = False
        self._directories = {}
        self._files = {}
        self._locks = []

    def __enter__(self):
        self._require_open()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.close()

    def _require_open(self):
        if self._closed:
            raise AuthorityError("Held-input authority is already closed")

    def _retain_directory(self, path, create):
        key = _path_key(path)
        if key in self._directories:
            record = self._directories[key]
            if str(path) != str(record["path"]):
                probe = _open(path, directory=True, directory_share=self._directory_share)
                try:
                    if not _same(_snapshot(probe, path, "directory"), record["metadata"], "directory"):
                        raise AuthorityError(f"Case/alias directory identity differs: {path}")
                finally:
                    _close_handle(probe)
            return
        try:
            handle = _open(path, directory=True, directory_share=self._directory_share)
        except FileNotFoundError:
            if not create:
                raise
            try:
                os.mkdir(path)
            except FileExistsError:
                pass
            handle = _open(path, directory=True, directory_share=self._directory_share)
        try:
            metadata = _snapshot(handle, path, "directory")
            canonical = _final_path(handle, path)
        except BaseException:
            _close_handle(handle)
            raise
        self._directories[key] = {"path": path, "handle": handle, "metadata": metadata, "canonical": canonical}

    def directory(self, path, create=False):
        self._require_open()
        original = _normal_path(path)
        parents = list(reversed(original.parents)) + [original]
        for index, parent in enumerate(parents):
            self._retain_directory(parent, bool(create and index > 0))
        return original

    def file(self, path, max_bytes):
        self._require_open()
        if isinstance(max_bytes, bool) or not isinstance(max_bytes, int) or max_bytes < 0:
            raise AuthorityError("Held file byte bound must be a nonnegative integer")
        original = _normal_path(path)
        self.directory(original.parent)
        key = _path_key(original)
        existing = self._files.get(key)
        if existing is not None:
            if existing.length > max_bytes:
                raise AuthorityError(f"Held input exceeds the caller's byte bound: {original}")
            if str(existing.path) != str(original):
                probe = _open(original)
                try:
                    if not _same(_snapshot(probe, original, "file"), existing._metadata, "file"):
                        raise AuthorityError(f"Case/alias file identity differs: {original}")
                finally:
                    _close_handle(probe)
            existing.recheck()
            return existing
        handle = _open(original)
        try:
            metadata = _snapshot(handle, original, "file")
            _final_path(handle, original)
            if metadata["length"] > max_bytes:
                raise AuthorityError(f"Selected input exceeds its {max_bytes}-byte bound: {original}")
        except BaseException:
            _close_handle(handle)
            raise
        held = HeldInput(self, original, handle, metadata, max_bytes)
        self._files[key] = held
        return held

    @contextmanager
    def exclusive(self, path, timeout=30, on_wait=None):
        self._require_open()
        if isinstance(timeout, bool) or not isinstance(timeout, (int, float)) or not 0 <= timeout <= 3600:
            raise AuthorityError("Per-key lock timeout must be between zero and 3600 seconds")
        original = _normal_path(path)
        self.directory(original.parent, create=True)
        if _path_key(original) in self._files:
            raise AuthorityError("A read-only held input cannot also be a producer lock")
        handle = _open(original, lock=True)
        record = {"path": original, "handle": handle, "metadata": None, "locked": False, "closed": False, "overlapped": _Overlapped()}
        self._locks.append(record)
        try:
            record["metadata"] = _snapshot(handle, original, "lock")
            _final_path(handle, original)
            started = time.monotonic()
            while not _windows_api().LockFileEx(handle, 3, 0, 1, 0, ctypes.byref(record["overlapped"])):
                error = ctypes.get_last_error()
                if error not in (32, 33):
                    raise _failure("Cannot acquire per-key producer lock", original, error)
                if on_wait is not None:
                    on_wait()
                remaining = timeout - (time.monotonic() - started)
                if remaining <= 0:
                    raise AuthorityError(f"Timed out waiting for per-key producer lock: {original}")
                time.sleep(min(0.05, remaining))
            record["locked"] = True
            if not _same(_snapshot(handle, original, "lock"), record["metadata"], "lock"):
                raise AuthorityError(f"Producer lock identity changed: {original}")
            yield original
        finally:
            self._release_lock(record)

    def _release_lock(self, record):
        if record["closed"]:
            return
        if record["locked"]:
            _windows_api().UnlockFileEx(record["handle"], 0, 1, 0, ctypes.byref(record["overlapped"]))
        _close_handle(record["handle"])
        record["closed"] = True

    def recheck(self):
        self._require_open()
        for record in self._directories.values():
            if not _same(_snapshot(record["handle"], record["path"], "directory"), record["metadata"], "directory"):
                raise AuthorityError(f"Held directory identity changed: {record['path']}")
        for held in self._files.values():
            held.recheck()
        for record in self._locks:
            if not record["closed"] and record["metadata"] is not None:
                if not _same(_snapshot(record["handle"], record["path"], "lock"), record["metadata"], "lock"):
                    raise AuthorityError(f"Held producer lock identity changed: {record['path']}")

    def close(self):
        if self._closed:
            return
        for record in reversed(self._locks):
            self._release_lock(record)
        for held in reversed(list(self._files.values())):
            if held.stream is not None:
                held.stream.close()
        for record in reversed(list(self._directories.values())):
            _close_handle(record["handle"])
        self._closed = True


class SdkImage:
    """Retained, bounded release and complete bin/lib/conf image of one explicit SDK."""

    MAX_ENTRIES = 16384
    MAX_FILE_BYTES = 1024 ** 3
    MAX_TOTAL_BYTES = 2 * 1024 ** 3
    MAX_RELEASE_BYTES = 1024 ** 2

    def __init__(self, sdk_root, javap):
        self._authority = HeldAuthority()
        self._closed = False
        try:
            self.root = self._authority.directory(sdk_root)
            self.javap = _normal_path(javap)
            if _path_key(self.javap) != _path_key(self.root / "bin" / "javap.exe"):
                raise AuthorityError("Selected javap must be the exact SDK bin/javap.exe")
            self._inventory = self._capture()
            required = {"release", "bin/java.exe", "bin/javac.exe", "bin/javap.exe", "lib/modules"}
            if not required.issubset(self._inventory):
                raise AuthorityError("Selected SDK image lacks release/java/javac/javap/modules")
            for name in required:
                if self._inventory[name]["kind"] != "file":
                    raise AuthorityError(f"Required SDK file has the wrong type: {name}")
            self.javap_sha256 = self._inventory["bin/javap.exe"]["sha256"]
            self.release_sha256 = self._inventory["release"]["sha256"]
            self.descriptor = {
                "schema": "sfm:baseline_javap_sdk_image@1",
                "os": "windows", "arch": platform.machine().lower(),
                "entries": [dict(self._inventory[name]) for name in sorted(self._inventory)],
            }
            self.recheck()
        except BaseException:
            self.close()
            raise

    def __enter__(self):
        if self._closed:
            raise AuthorityError("SDK image is already closed")
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.close()

    def _capture(self):
        inventory = {}
        total = 0
        release = self._authority.file(self.root / "release", self.MAX_RELEASE_BYTES)
        inventory["release"] = {"path": "release", "kind": "file", "bytes": release.length, "sha256": release.digest}
        total += release.length
        pending = []
        for name in ("bin", "lib", "conf"):
            self._authority.directory(self.root / name)
            inventory[name] = {"path": name, "kind": "directory"}
            pending.append(name)
        while pending:
            relative = pending.pop()
            for name, kind in self._children(relative, len(inventory)):
                if name in inventory:
                    raise AuthorityError(f"Duplicate selected SDK entry: {name}")
                path = self.root.joinpath(*name.split("/"))
                if kind == "directory":
                    self._authority.directory(path)
                    inventory[name] = {"path": name, "kind": kind}
                    pending.append(name)
                else:
                    held = self._authority.file(path, min(self.MAX_FILE_BYTES, self.MAX_TOTAL_BYTES - total))
                    total += held.length
                    if total > self.MAX_TOTAL_BYTES:
                        raise AuthorityError("Selected SDK image exceeds its total byte bound")
                    inventory[name] = {"path": name, "kind": kind, "bytes": held.length, "sha256": held.digest}
                if len(inventory) > self.MAX_ENTRIES:
                    raise AuthorityError("Selected SDK image exceeds its entry bound")
        return inventory

    def _children(self, relative, prior_count):
        path = self.root.joinpath(*relative.split("/"))
        rows = []
        names = set()
        with os.scandir(path) as selected:
            for entry in selected:
                if prior_count + len(rows) >= self.MAX_ENTRIES:
                    raise AuthorityError("Selected SDK membership exceeds its entry bound")
                name = relative + "/" + entry.name
                _normal_path(self.root.joinpath(*name.split("/")))
                folded = entry.name.casefold()
                if folded in names:
                    raise AuthorityError(f"Ambiguous case-colliding SDK membership: {name}")
                names.add(folded)
                metadata = entry.stat(follow_symlinks=False)
                if getattr(metadata, "st_file_attributes", 0) & _REPARSE:
                    raise AuthorityError(f"Selected SDK membership is a reparse point: {name}")
                if stat.S_ISDIR(metadata.st_mode):
                    kind = "directory"
                elif stat.S_ISREG(metadata.st_mode):
                    kind = "file"
                else:
                    raise AuthorityError(f"Selected SDK membership is not an ordinary file/directory: {name}")
                rows.append((name, kind))
        return sorted(rows)

    def recheck(self):
        if self._closed:
            raise AuthorityError("SDK image is already closed")
        self._authority.recheck()
        membership = {"release": "file", "bin": "directory", "lib": "directory", "conf": "directory"}
        pending = ["bin", "lib", "conf"]
        while pending:
            relative = pending.pop()
            for name, kind in self._children(relative, len(membership)):
                if name in membership:
                    raise AuthorityError(f"Duplicate selected SDK membership: {name}")
                expected = self._inventory.get(name)
                if expected is None or expected["kind"] != kind:
                    raise AuthorityError(f"Selected SDK membership changed: {name}")
                membership[name] = kind
                if kind == "directory":
                    pending.append(name)
        if membership != {name: row["kind"] for name, row in self._inventory.items()}:
            raise AuthorityError("Selected SDK membership changed")
        self._authority.recheck()

    def close(self):
        if not self._closed:
            self._authority.close()
            self._closed = True
