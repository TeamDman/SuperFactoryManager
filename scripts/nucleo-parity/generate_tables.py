#!/usr/bin/env python3
"""Generate the Java snapshot from `oracle tables`; requires the pinned Rust toolchain."""
import pathlib
import subprocess
import sys

raw = subprocess.check_output([sys.argv[1], "tables"], text=True)
groups = {key: [] for key in "FNC"}
for line in raw.splitlines():
    parts = line.split("\t")
    if parts[0] == "VERSION":
        if parts[1] != "(17, 0, 0)":
            raise SystemExit("Unicode snapshot requires Rust Unicode 17.0.0")
    else:
        groups[parts[0]].extend(parts[1:])
text = '''/* SPDX-License-Identifier: MPL-2.0
 * Generated from nucleo 8c16d47cdfa9607d3e44df5f81c635c6f43c65ee by generate_tables.py.
 * Nucleo case-fold data: Unicode 15.0.0; Rust character categories: Unicode 17.0.0.
 * Copyright Pascal Kuthe and contributors. See scripts/nucleo-parity/LICENSE-MPL-2.0.txt.
 */
package ca.teamdman.sfm.client.search.nucleo;

final class NucleoUnicode {
    private NucleoUnicode() {}
    private static int[] table(String encoded) {
        String[] values = encoded.split(" ");
        int[] result = new int[values.length];
        for (int i = 0; i < result.length; i++) result[i] = Integer.parseInt(values[i], 16);
        return result;
    }
    private static int lookup(int[] table, int point, boolean ranges) {
        int low = 0, high = table.length / 2 - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1, key = table[mid * 2];
            if (point == key) return table[mid * 2 + 1];
            if (point < key) high = mid - 1; else low = mid + 1;
        }
        return ranges ? table[high * 2 + 1] : point;
    }
    static int fold(int point) {
        if (point < 128) return point >= 'A' && point <= 'Z' ? point + 32 : point;
        return lookup(FOLD, point, false);
    }
    static int normalize(int point) {
        if (point < 0xa0 || point >= 0x20a0) return point;
        return lookup(NORMALIZE, point, false);
    }
    static int charClass(int point) { return lookup(CLASSES, point, true); }
'''
for key, name in [("F", "FOLD"), ("N", "NORMALIZE"), ("C", "CLASSES")]:
    values = groups[key]
    chunks = [" ".join(values[i:i + 32]) for i in range(0, len(values), 32)]
    text += f"    private static final int[] {name} = table(\n"
    text += "\n".join('            "' + chunk + (' " +' if i < len(chunks) - 1 else '");') for i, chunk in enumerate(chunks)) + "\n"
text += "}\n"
pathlib.Path(sys.argv[2]).write_text(text)
