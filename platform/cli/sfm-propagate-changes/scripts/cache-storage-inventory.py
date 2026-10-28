"""Read-only logical and allocated-byte inventory of selected SFM cache roots.

Usage: python cache-storage-inventory.py ROOT [ROOT ...] > receipt.json
The script never follows descendant directory reparse points or writes inside a root.
An explicitly selected linked root is scanned through its physical target once.
"""

import argparse
import ctypes
from ctypes import wintypes
import json
import os


kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
create_file = kernel32.CreateFileW
create_file.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD, ctypes.c_void_p,
                        wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
create_file.restype = wintypes.HANDLE
file_information = kernel32.GetFileInformationByHandleEx
file_information.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD]
file_information.restype = wintypes.BOOL
file_identity = kernel32.GetFileInformationByHandle
file_identity.argtypes = [wintypes.HANDLE, ctypes.c_void_p]
file_identity.restype = wintypes.BOOL
close_handle = kernel32.CloseHandle
close_handle.argtypes = [wintypes.HANDLE]
close_handle.restype = wintypes.BOOL
REPARSE_POINT = 0x400
FILE_STANDARD_INFO = 1
FILE_READ_ATTRIBUTES = 0x80
FILE_SHARE_ALL = 0x7
OPEN_EXISTING = 3


class FileStandardInfo(ctypes.Structure):
    _fields_ = [("AllocationSize", ctypes.c_int64), ("EndOfFile", ctypes.c_int64),
                ("NumberOfLinks", wintypes.DWORD), ("DeletePending", ctypes.c_ubyte),
                ("Directory", ctypes.c_ubyte)]


class FileTime(ctypes.Structure):
    _fields_ = [("low", wintypes.DWORD), ("high", wintypes.DWORD)]


class FileIdentity(ctypes.Structure):
    _fields_ = [("attributes", wintypes.DWORD), ("created", FileTime),
                ("accessed", FileTime), ("modified", FileTime),
                ("volume", wintypes.DWORD), ("size_high", wintypes.DWORD),
                ("size_low", wintypes.DWORD), ("links", wintypes.DWORD),
                ("index_high", wintypes.DWORD), ("index_low", wintypes.DWORD)]


def file_metadata(path):
    handle = create_file(path, FILE_READ_ATTRIBUTES, FILE_SHARE_ALL, None, OPEN_EXISTING, 0, None)
    if handle == wintypes.HANDLE(-1).value:
        error = ctypes.get_last_error()
        raise OSError(error, ctypes.FormatError(error), path)
    try:
        info = FileStandardInfo()
        if not file_information(handle, FILE_STANDARD_INFO, ctypes.byref(info), ctypes.sizeof(info)):
            error = ctypes.get_last_error()
            raise OSError(error, ctypes.FormatError(error), path)
        identity = FileIdentity()
        if not file_identity(handle, ctypes.byref(identity)):
            error = ctypes.get_last_error()
            raise OSError(error, ctypes.FormatError(error), path)
        file_id = (identity.volume, (identity.index_high << 32) | identity.index_low)
        return info.AllocationSize, file_id, identity.links
    finally:
        close_handle(handle)


def long_path(path):
    absolute = os.path.abspath(path)
    if absolute.startswith("\\\\?\\"):
        return absolute
    if absolute.startswith("\\\\"):
        return "\\\\?\\UNC\\" + absolute[2:]
    return "\\\\?\\" + absolute


def inventory(roots):
    seen = set()
    output = []
    for root in roots:
        readable_root = os.path.abspath(root)
        physical_root = os.path.realpath(readable_root)
        result = {
            "logical_root": readable_root,
            "physical_root": physical_root,
            "files": 0,
            "logical_bytes": 0,
            "allocated_unique_bytes": 0,
            "hardlink_aliases": 0,
            "multi_link_files": 0,
            "reparse_entries_skipped": 0,
            "errors": [],
            "children": {},
        }
        stack = [(long_path(readable_root), "")]
        while stack:
            directory, relative = stack.pop()
            try:
                entries = list(os.scandir(directory))
            except OSError as error:
                result["errors"].append(f"{directory}: {error}")
                continue
            for entry in entries:
                child_relative = os.path.join(relative, entry.name)
                try:
                    info = entry.stat(follow_symlinks=False)
                    if getattr(info, "st_file_attributes", 0) & REPARSE_POINT:
                        result["reparse_entries_skipped"] += 1
                        continue
                    if entry.is_dir(follow_symlinks=False):
                        stack.append((entry.path, child_relative))
                        continue
                    if not entry.is_file(follow_symlinks=False):
                        continue
                    category = child_relative.split(os.sep, 1)[0]
                    counts = result["children"].setdefault(category, {
                        "files": 0,
                        "logical_bytes": 0,
                        "allocated_unique_bytes": 0,
                    })
                    result["files"] += 1
                    result["logical_bytes"] += info.st_size
                    counts["files"] += 1
                    counts["logical_bytes"] += info.st_size
                    size, identity, links = file_metadata(entry.path)
                    if links > 1:
                        result["multi_link_files"] += 1
                    if identity in seen:
                        result["hardlink_aliases"] += 1
                        continue
                    seen.add(identity)
                    result["allocated_unique_bytes"] += size
                    counts["allocated_unique_bytes"] += size
                except OSError as error:
                    result["errors"].append(f"{entry.path}: {error}")
        output.append(result)
    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("roots", nargs="+", help="Existing directories to inspect; no links followed")
    args = parser.parse_args()
    for root in args.roots:
        if not os.path.isdir(root):
            parser.error(f"not an existing directory: {root}")
    print(json.dumps({"schema": 1, "roots": inventory(args.roots)}, indent=2))
