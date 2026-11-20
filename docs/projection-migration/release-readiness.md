# Release 4.35.0 readiness

4.35.0 still needs candidate-specific qualification. This ledger assumes the 10 plain
`sfm-4.35.0` projections as candidates for the current goal. Tooling completion
and release readiness are separate decisions.

Last observations: 17 November 2026. The installed default-feature CLI verified
all 10 candidates after the flat source and root Gradle files were retired.
No Minecraft, Gradle or provider operation was executed; nothing was published.

## Candidate selection

The [projection catalog](../../platform/minecraft/projections.json) supplies each
entry's upstream version, environment and declared features. The CLI's
[`SUPPORTED_TARGETS`](../../platform/cli/sfm-propagate-changes/src/source_projection/projection_definition.rs)
maps upstream versions to matrix targets. In particular, target `1.21.0` uses
upstream Minecraft `1.21`. A key's spelling does not determine its context.

Every row declares feature set A, exactly in this catalog order:
`recipe_namespace`, `release_version_4_35_0`, `quantity_sensitive_extraction_progress`.
The [feature registry](../../platform/minecraft/core-liquid-template/feature-definitions.json)
declares all 3 features for all 10 targets, with no prerequisites.

| Exact projection key | Target | Upstream | Environment | Release flag | Features |
|---|---|---|---|---|---|
| `sfm-4.35.0/mc-1.19.2` | `1.19.2` | `1.19.2` | `release` | enabled | A |
| `sfm-4.35.0/mc-1.19.4` | `1.19.4` | `1.19.4` | `release` | enabled | A |
| `sfm-4.35.0/mc-1.20` | `1.20` | `1.20` | `release` | enabled | A |
| `sfm-4.35.0/mc-1.20.1` | `1.20.1` | `1.20.1` | `release` | enabled | A |
| `sfm-4.35.0/mc-1.20.2` | `1.20.2` | `1.20.2` | `release` | enabled | A |
| `sfm-4.35.0/mc-1.20.3` | `1.20.3` | `1.20.3` | `release` | enabled | A |
| `sfm-4.35.0/mc-1.20.4` | `1.20.4` | `1.20.4` | `release` | enabled | A |
| `sfm-4.35.0/mc-1.21.0` | `1.21.0` | `1.21` | `release` | enabled | A |
| `sfm-4.35.0/mc-1.21.1` | `1.21.1` | `1.21.1` | `release` | enabled | A |
| `sfm-4.35.0/mc-26.1.2` | `26.1.2` | `26.1.2` | `release` | enabled | A |

The release flag column means `release_version_4_35_0` is explicitly declared.
Current metadata qualification must also verify rendered `mod_version=4.35.0`,
the exact release feature and current selected source ownership.

Exact source bytes used for this selection audit:

| Input | SHA-256 |
|---|---|
| `platform/minecraft/projections.json` | `8884e78a78f1a9dd3e19a1c41836539a5208b3e14183d8bff8bad88f367b28b5` |
| `platform/minecraft/core-liquid-template/feature-definitions.json` | `e4080c96e4ec2e7105248b926ed62d027bc627daa0f31d4997586a7e72887f57` |
| `platform/minecraft/core-liquid-template/project-inputs.json` | `684645a5046f3c541b832da1f6004b18db0663f986a1d9281b8c28d427d9b85c` |

## Evidence required per candidate

V means verified by the fresh installed CLI on 17 November 2026. P means pending
candidate-specific evidence. N means publication was not performed or authorized.
Older receipts, binaries and runtime reports do not fill these cells.

| Projection key | Metadata | Ownership | Receipt and JAR | Tests | Gameplay | Publication |
|---|---|---|---|---|---|---|
| `sfm-4.35.0/mc-1.19.2` | V | V | P | P | P | N |
| `sfm-4.35.0/mc-1.19.4` | V | V | P | P | P | N |
| `sfm-4.35.0/mc-1.20` | V | V | P | P | P | N |
| `sfm-4.35.0/mc-1.20.1` | V | V | P | P | P | N |
| `sfm-4.35.0/mc-1.20.2` | V | V | P | P | P | N |
| `sfm-4.35.0/mc-1.20.3` | V | V | P | P | P | N |
| `sfm-4.35.0/mc-1.20.4` | V | V | P | P | P | N |
| `sfm-4.35.0/mc-1.21.0` | V | V | P | P | P | N |
| `sfm-4.35.0/mc-1.21.1` | V | V | P | P | P | N |
| `sfm-4.35.0/mc-26.1.2` | V | V | P | P | P | N |

Metadata requires the fresh CLI's release plan for the exact key and expected
version. Ownership requires a current selected-projection check and its retained
input identities. Receipt and JAR evidence requires a successful native package receipt
bound to the same source selection and the exact JAR digest.

The Tests column requires the selected test IDs, nonzero applicable counts, execution result
and artifact identity. A test listing only establishes selection. Gameplay requires
a recorded compatibility result for the same candidate. These runtime checks
remain pending: this goal does not launch clients or servers.

Publication requires its own authorization and final provider review. Local payload
preparation does not authorize uploads, reference changes or release creation.

## Verified source observations

The installed propagation CLI reports `0.1.1`, base revision `d16af8c38`. Its
SHA-256 is `c2fce89fae1d94cb37be39e5d35480b4939a29346fe9ca2fa1ead862f1b002c7`. The revision is a base hint for this
uncommitted work; the captured source-input manifest binds the working copy.

From the repository root, the command for each exact key was equivalent to:

```powershell
sfm-propagate-changes --output-format json source release plan --repo-root . --projection <exact-key> --expected-mod-version 4.35.0
```

All 10 commands exited zero and reported current ownership, the exact release
feature, matching metadata, zero network requests and no source writes or
publication authority. They ran from an empty directory outside the checkout;
normal logs used a supplied application home. No invocation files or cache
directory were created. HEAD, index bytes and worktree status remained unchanged.

The ignored goal archive contains
[the ten-observation summary](../../.local-review/finish-projection-migration-2026-11-16/metadata-probe-after-retirement/summary.json),
with each exact command, exit status, report path and digest, ownership identity,
selected source/output hashes and changelog digest. Its SHA-256 is
`1a7d45fdc5a5615607d334b90d10b53203b84530d0c8f842717272d0697c1a50`.
The [installed-tool checks](../../.local-review/finish-projection-migration-2026-11-16/installed-cli-checks/summary.json)
record executable paths and hashes. These are local working-copy observations;
rerun them after relevant source, catalog or tool changes.

Native package receipts, SDK identities, selected mod-test counts and gameplay
results remain pending per candidate. Passing Rust tooling tests does not supply
those observations. Publication requires a later authorization and provider review.

## Record fresh evidence

For each completed cell, record the CLI binary digest, exact command, exit status,
report path and digest, and the source or artifact identities it observed. Use
repository-relative evidence paths. If selected inputs change, recheck the evidence
before carrying its result forward.

The [migration plan](plan.md) owns tooling qualification, including its required
Rust gate and fresh CLI checks. Passing that goal does not complete the pending
native artifact, test and gameplay evidence above. Leave this ledger uncommitted
for review with the implementation.
