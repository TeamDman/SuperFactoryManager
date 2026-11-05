#!/usr/bin/env python3
"""Derive an independently auditable Java trust store without changing a JDK.

The caller supplies the exact fingerprints to exclude and, optionally, the
expected fingerprint of one environment CA. This prepares a store; it does
not configure Java processes to use it or authenticate the source JDK.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

PASSWORD = "changeit"  # Public password used by the JDK cacerts stores.
FINGERPRINT = re.compile(r"[0-9A-F]{64}\Z")
CERTIFICATE = re.compile(rb"-----BEGIN CERTIFICATE-----\s*([A-Za-z0-9+/=\s]+?)-----END CERTIFICATE-----")
ENVIRONMENT_ALIAS = "sfm-cloud-environment-ca"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def fingerprint(value: str) -> str:
    value = value.replace(":", "").upper()
    require(bool(FINGERPRINT.fullmatch(value)), "expected a SHA-256 fingerprint")
    return value


def certificate_fingerprint(data: bytes) -> str:
    matches = list(CERTIFICATE.finditer(data))
    require(len(matches) == 1 and data.count(b"-----BEGIN CERTIFICATE-----") == 1,
            "expected exactly one PEM certificate")
    der = base64.b64decode(re.sub(rb"\s", b"", matches[0].group(1)), validate=True)
    return hashlib.sha256(der).hexdigest().upper()


def digest(path: Path) -> str:
    with path.open("rb") as file:
        return hashlib.file_digest(file, "sha256").hexdigest()


def keytool_command(keytool: Path, *args: str) -> str:
    environment = os.environ.copy()
    for name in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS",
                 "JDK_JAVAC_OPTIONS", "JDK_ALTERNATE_VM", "CLASSPATH"):
        environment.pop(name, None)
    result = subprocess.run(
        [str(keytool), "-J-Duser.language=en", "-J-Duser.country=US", *args],
        env=environment, capture_output=True, text=True, check=False,
    )
    require(result.returncode == 0, f"keytool failed: {result.stderr[-1500:]}")
    return result.stdout


def entries(store: Path, keytool: Path) -> dict[str, str]:
    text = keytool_command(keytool, "-list", "-rfc", "-keystore", str(store),
                           "-storepass", PASSWORD)
    result: dict[str, str] = {}
    for chunk in re.split(r"(?:^|\n)Alias name: ", text)[1:]:
        alias, _, remainder = chunk.partition("\n")
        require(alias and alias not in result, "invalid or duplicate keystore alias")
        require("Entry type: trustedCertEntry" in remainder,
                f"non-certificate entry is not accepted: {alias}")
        result[alias] = certificate_fingerprint(remainder.encode())
    require(bool(result), "keystore contained no trusted certificates")
    return result


def prepare(source: Path, keytool: Path, destination: Path, denied: set[str],
            extra_ca: Path | None, expected_extra: str | None) -> dict:
    source = source.absolute()
    destination = destination.absolute()
    require(source.is_file() and not source.is_symlink(), "source must be a regular, non-symlink file")
    require(bool(denied), "at least one denied fingerprint is required")
    denied = {fingerprint(item) for item in denied}
    require((extra_ca is None) == (expected_extra is None),
            "extra CA certificate and expected fingerprint must be supplied together")
    extra = None
    if extra_ca is not None:
        require(extra_ca.is_file() and not extra_ca.is_symlink(),
                "extra CA must be a regular, non-symlink file")
        extra = certificate_fingerprint(extra_ca.read_bytes())
        require(extra == fingerprint(expected_extra or ""), "extra CA fingerprint mismatch")
        require(extra not in denied, "extra CA is on the denied list")

    original_digest = digest(source)
    before = entries(source, keytool)
    removed = {alias: value for alias, value in before.items() if value in denied}
    expected = {alias: value for alias, value in before.items() if value not in denied}
    require(bool(expected), "filter would remove every source trust anchor")
    if extra and extra not in expected.values():
        require(ENVIRONMENT_ALIAS not in expected, "environment CA alias already exists")
        expected[ENVIRONMENT_ALIAS] = extra

    # mkdir refuses an existing output, including an existing symlink. The
    # source and archive remain untouched even if keytool or verification fails.
    destination.mkdir(mode=0o700)
    store = destination / "cacerts"
    pending = destination / ".cacerts.pending"
    shutil.copyfile(source, pending)
    os.chmod(pending, 0o600)
    for alias in removed:
        keytool_command(keytool, "-delete", "-alias", alias, "-keystore", str(pending),
                        "-storepass", PASSWORD)
    if extra and extra not in before.values():
        keytool_command(keytool, "-importcert", "-noprompt", "-alias", ENVIRONMENT_ALIAS,
                        "-file", str(extra_ca), "-keystore", str(pending), "-storepass", PASSWORD)
    after = entries(pending, keytool)
    require(after == expected, "filtered store differs from the intended anchors")
    require(not set(after.values()) & denied, "a denied certificate remains")
    require(digest(source) == original_digest, "source trust store changed during preparation")
    pending.rename(store)
    receipt = {
        "schema": "sfm:derived_java_trust_store@1",
        "source": str(source), "source_sha256": original_digest,
        "filtered": str(store), "filtered_sha256": digest(store),
        "denied_sha256": sorted(denied), "removed_aliases": removed,
        "extra_ca_sha256": extra, "source_entries": len(before),
        "filtered_entries": len(after),
    }
    with (destination / "receipt.json").open("x", encoding="utf-8") as handle:
        json.dump(receipt, handle, indent=2, sort_keys=True)
        handle.write("\n")
    return receipt


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--keytool", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--deny-sha256", required=True, action="append")
    parser.add_argument("--extra-ca-cert", type=Path)
    parser.add_argument("--extra-ca-sha256")
    args = parser.parse_args()
    try:
        result = prepare(args.source, args.keytool, args.output_dir, set(args.deny_sha256),
                         args.extra_ca_cert, args.extra_ca_sha256)
    except (OSError, ValueError) as error:
        print(f"prepare-java-trust: {error}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
