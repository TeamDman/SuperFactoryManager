#!/usr/bin/env python3
"""Compare real Rust and Java results; no expected scores are synthesized here."""
import itertools
import json
import pathlib
import random
import re
import subprocess
import sys

upstream, build, java = map(pathlib.Path, sys.argv[1:4])
seed = 0x53464D4E
rng = random.Random(seed)
settings = list(itertools.product(["RESPECT", "IGNORE", "SMART"], ["NEVER", "SMART"],
                                 ["DEFAULT", "PATHS"], ["false", "true"]))
cases = []
counts = {}

def add(group, query, candidate, config=None):
    for mode in settings if config is None else [config]:
        cases.append((mode, query, candidate, group))
        counts[group] = counts.get(group, 0) + 1

def rust_string(s):
    s = re.sub(r"\\u\{([0-9a-fA-F]+)\}", lambda m: chr(int(m[1], 16)), s)
    return json.loads('"' + s + '"')

source = (upstream / "matcher/src/tests.rs").read_text()
pairs = re.findall(r'\(\s*"((?:\\.|[^"\\])*)",\s*"((?:\\.|[^"\\])*)"', source)
for candidate, query in pairs:
    add("upstream-inputs", rust_string(query), rust_string(candidate))
for q, h in [("", ""), ("", "minecraft:iron_ingot"), ("mi", "minecraft:iron_ingot"),
             ("m:ii", "minecraft:iron_ingot"), ("cog", "create:large_cogwheel"),
             ("foo", "xf foo"), ("aa", "a_a_a"), ("aa", "xaaa"),
             ("ss", "ß"), ("s", "ſ"), ("σ", "ς"), ("k", "K"),
             ("i", "İ"), ("é", "e\u0301"), ("e", "é"), ("É", "é"),
             ("😀x", "a😀_x"), ("𐐨", "x𐐀𐐨"), ("👩💻", "👩\u200d💻"),
             ("\n", "\r\n"), ("\r\n", "x\r\ny"), ("a", "x\vA"),
             ("!^a$", "!^a$"), ("a b", "a_b"), ("aaa", "aa"), ("z", "abc")]:
    add("targeted", q, h)

# Exhaustive small cases expose tie-breaking and backtracking, including repeated characters.
for size in range(6):
    for letters in itertools.product("aA_", repeat=size):
        h = "".join(letters)
        for q in ["a", "aa", "aaa", "a_", "_a", "A", "Aa"]:
            add("exhaustive", q, h, settings[(size + len(q)) % len(settings)])

alphabets = ["abcABC123_/:.- |\\\t\r\n\v", "aáàÄéßſσςΣKİµμΩ你界カ😀𐐀𐐨👩\u200d💻\u0301\u2003"]
for i in range(20000):
    alphabet = alphabets[i % 2]
    h = "".join(rng.choice(alphabet) for _ in range(rng.randrange(0, 100)))
    if h and i % 3:
        q = "".join(h[j] for j in sorted(rng.sample(range(len(h)), rng.randrange(0, min(16, len(h)) + 1))))
        if i % 4 == 0: q = q.lower()
    else:
        q = "".join(rng.choice(alphabet) for _ in range(rng.randrange(0, 16)))
    add("random", q, h, rng.choice(settings))

# Unicode scalar sampling tests the pinned classification snapshot, beyond JDK 17's Unicode data.
for i in range(2500):
    cp = rng.randrange(0x110000)
    if 0xd800 <= cp <= 0xdfff: continue
    c = chr(cp)
    add("unicode-scalars", "a" + c, "x_a" + c + "_a" + c, rng.choice(settings))

# Slab/cell limits, exact vs optimal vs greedy routes, long gaps and u16 prefix arithmetic.
for n, m in [(2047, 2), (2048, 2), (2049, 2), (255, 400), (256, 400), (257, 400),
             (400, 255), (400, 256), (401, 256), (2048, 2047), (2049, 2048),
             (8874, 2), (8875, 2), (11093, 2), (11094, 2),
             (12000, 2), (14000, 2), (65534, 2), (65535, 2)]:
    if m > n: continue
    for prefix in ["a", "á", "😀"]:
        add("boundaries", prefix + "a" * (m - 1), prefix + "x" * (n - m) + "a" * (m - 1))
for start in [21844, 21845, 21846, 32767, 65530]:
    add("boundaries", "aa", "x" * start + "aa")

for i in range(500):
    alphabet = alphabets[i % 2]
    h = "".join(rng.choice(alphabet) for _ in range(rng.randrange(400, 1400)))
    q = "".join(h[j] for j in sorted(rng.sample(range(len(h)), rng.randrange(100, 300))))
    add("random-long", q, h, rng.choice(settings))

def encode(s): return ",".join(f"{ord(c):x}" for c in s)
payload = "".join("\t".join((*mode, encode(q), encode(h))) + "\n" for mode, q, h, _ in cases)
commands = [[str(build / "target/release/oracle")],
            [str(java), "-Xmx512m", "-cp", str(build / "classes"), "JavaOracle"]]
outputs = []
for cmd in commands:
    p = subprocess.run(cmd, input=payload, text=True, capture_output=True)
    if p.returncode:
        print(p.stderr, file=sys.stderr)
        raise SystemExit(f"Failed: {cmd}")
    outputs.append(p.stdout.splitlines())
    assert len(outputs[-1]) == len(cases), (len(outputs[-1]), len(cases))
errors = []
for i, (rust, java_result) in enumerate(zip(*outputs)):
    if rust != java_result:
        mode, q, h, group = cases[i]
        errors.append(dict(index=i, settings=mode, query=q, candidate=h, group=group, rust=rust, java=java_result))
report = dict(seed=seed, compared=len(cases), groups=counts, mismatches=len(errors), first_errors=errors[:20])
(build / "comparison.json").write_text(json.dumps(report, indent=2, ensure_ascii=True) + "\n")
print(json.dumps({k: v for k, v in report.items() if k != "first_errors"}, indent=2))
for error in errors[:8]:
    error = dict(error)
    error["candidate"] = error["candidate"][:180]
    print(json.dumps(error, ensure_ascii=True))
if errors: raise SystemExit(1)

# Compare actual Atom::match_list ordering, including duplicate strings and zero-score empty queries.
lists = []
registry = ["minecraft:iron_ingot", "create:iron_sheet", "minecraft:iron_ore", "minecraft:iron_ingot",
            "thermal:iron_dust", "mekanism:enriched_iron", "create:large_cogwheel", "minecraft:air"]
for mode in settings:
    for q in ["", "i", "ii", "m:ii", "iron", "cog", "missing"]:
        lists.append((mode, q, registry))
for i in range(600):
    mode = rng.choice(settings)
    h = ["".join(rng.choice(alphabets[i % 2]) for _ in range(rng.randrange(1, 80))) for _ in range(60)]
    h.extend(h[:5])
    q = "" if i % 7 == 0 else "".join(rng.choice(alphabets[i % 2]) for _ in range(rng.randrange(1, 5)))
    lists.append((mode, q, h))
payload = "".join("\t".join((*mode, encode(q), *(encode(s) for s in h))) + "\n" for mode, q, h in lists)
ranked = [subprocess.run(cmd + ["rank"], input=payload, text=True, capture_output=True, check=True).stdout.splitlines()
          for cmd in commands]
assert len(ranked[0]) == len(ranked[1]) == len(lists)
rank_errors = [i for i, (a, b) in enumerate(zip(*ranked)) if a != b]
report["ranking_lists"] = len(lists)
report["ranking_mismatches"] = len(rank_errors)
(build / "comparison.json").write_text(json.dumps(report, indent=2) + "\n")
print(f"Rust Atom::match_list ordering: {len(lists)} lists, {len(rank_errors)} mismatches")
if rank_errors:
    i = rank_errors[0]
    print(lists[i], ranked[0][i], ranked[1][i])
    raise SystemExit(1)
