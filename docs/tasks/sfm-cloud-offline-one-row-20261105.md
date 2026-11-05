# SFM cloud offline one-row checkpoint (2026-11-05)

Status: **passed for one historical projection only**. In an isolated Linux
candidate, the unchanged cloud gate ran a fresh source check, completed native
compilation and created a fresh JAR for `sfm-4.35.0/mc-1.21.0`. The gate's
post-run immutable-input comparison passed. This is not an image publication or
full-catalog readiness result.

The tested source was commit `0cb61737d3ed1cf6516838d52ab20367ec2a36fc`. The
projection uses Minecraft `1.21`, Java 21 and the exact historical recipe
`sfm:released-native-inputs/4.34.0/1.21.0@1`. Its emitted lock SHA-256 was
`2b6930878897d7cf1db94badbd98ba58f0fe21cca61e3656c298421c3cea32c4`.
The release CLI SHA-256 was
`35a9883e24499c76efdcd149507b1c46304ada51cb5abd62c709bb0ac89e1be7`.

| Gate stage | Result | Relevant receipt detail |
| --- | --- | --- |
| Source check | passed, exit 0 | No writes performed |
| Native compile | passed, exit 0 | Compilation completed |
| JAR | passed, exit 0 | Packaged in this invocation |

The offline JAR SHA-256 was
`022cbe51d2a9126a2d7d028432d0f2bba4d7c368cefdd4b681c813d7a662cd23`.
The separately produced online JAR had a different hash; this checkpoint does
not establish byte-for-byte reproducible JARs.

The [sanitized evidence companion](evidence/sfm-cloud-offline-20261105/qualification.json)
records the full list of 73 exclusions, exact authority and receipt hashes,
stage-log hashes, isolation observations and limits. Its SHA-256 is
`80ba8aa914cd99177c708580da7330696aee44de8c4c4a9220c936e826da68a8`.
It is a derivative summary, not a gate input or a replacement for the original
receipts and full manifests. Original scope-file and canonical scope-payload
hashes are recorded separately because they identify different byte sequences.

The candidate ran with Docker `--network none`. Fresh witnesses established a
network namespace distinct from the outer environment, loopback as its only
interface, and no source, cache, archive, credential or Docker-socket mounts.
Route and mount evidence matched before and after the run. The populated
candidate's bytes, types, modes, ownership and links matched its reviewed
manifest before execution; a post-run comparison accounted for the expected
runtime outputs. The immutable cache, SDK and trust inventory matched before and
after. The recorded seed image identified only the seed, not the populated
candidate; the original full candidate manifests carry the content identities.

The offline stage's peak observed growth was 1,544,622,080 bytes. The lowest
recorded free space through the qualification checkpoint was 18,784,337,920
bytes, above the 2,684,354,560-byte floor. An earlier traced online preparation
was interrupted and retained. Candidate preflight failures were corrected and
retained before the successful gate; none is relabeled as a passing gate stage.

## Repeating this bounded check

Follow the repository-owned [cloud setup and offline gate](../codex-cloud-environment.md)
for a new qualification. In particular:

1. Start from a clean checkout of the intended commit. Verify the exact Linux
   JBR 21 archive against the tracked authority and use independently audited
   external Java and native trust stores for the current environment. Keep the
   original SDK bytes unchanged.
2. Prepare explicit application, native and SDK roots. Build a new release with
   `cloud-prepare.py build`, retain its receipt, and use the supported native
   commands to prepare the selected row's pinned cache inputs. A missing native
   leaf is initialized by the CLI, not by a handwritten ownership marker.
3. Draft a new scope for exactly `sfm-4.35.0/mc-1.21.0`, review its complete
   inventory and all exclusions, and declare it only after the source, CLI,
   receipt, SDK and trust identities agree. Do not reuse this run's scope or
   receipt after changing their bound inputs or paths.
4. Construct a candidate without host source, cache, archive, credential or
   Docker-socket mounts. Preserve and compare file bytes, types, modes, owners
   and links; verify actual namespace, interfaces, IPv4 and IPv6 routes, and
   mounts. Enforce a conservative disk budget and free-space floor.
5. Run the unchanged `cloud-offline-readiness.py verify` gate against that
   candidate and retain every stage result, output, error and final inventory
   comparison. A missing input, failed stage or isolation predicate blocks the
   result. A later fresh Codex-task qualification is a separate gate.

The other 73 projections, JUnit, GameTests, GUI, Gradle, fresh-task restoration
and G-unavailable proof are not attested here. The existing Vox and exact
Mekanism holds remain. No production environment or image was published and no
desktop backup was deleted. The original task-local evidence has no verified
long-term retention guarantee; preserve it independently before relying on this
checkpoint as durable evidence.
