# Choosing an SFM toolchain cache for a worktree

Every SFM checkout is a Git repository context. Its intended logical toolchain
path is `<checkout>/sfm-toolchain-data/`, outside all projection directories.
Generated Gradle projects resolve this path relative to their own configuration;
they do not need Rust. Gradle may still keep its own mutable `build/` output in
each projection. Rust and Gradle sharing a directory does not make their derived
formats interchangeable.

This guide describes a **decision before worktree creation**. Ask Teamy before
creating a worktree, cache directory, filesystem link or new development
workspace. No link type is selected or enabled by this document. The current
Rust catalog route selects the checkout-local native root by default, while
Gradle still requires an explicit property. A root-level link is not yet
admitted by the native root guard. A separately approved test link/worktree
may be created only after G7 selects a
link type, this guide is reviewed, and the relevant guard implementation is ready;
its purpose is to run live acceptance, not to establish production sharing.
Do not create a production sharing link until those live tests pass and the
production change is separately approved.

## Decision tree

1. **Is a new worktree approved and needed?** If not, use the existing checkout.
   An isolated checkout may use its own ordinary `sfm-toolchain-data/` directory.
   Do not create a directory merely because this document describes it.
2. **Is sharing intended?** If isolation is desired, keep an ordinary directory.
   If sharing is desired, first identify the physical target and every known
   checkout using it. The target must be durable, outside any disposable
   worktree/build tree, and have enough free space for the next operation.
3. **Is the target compatible and owned?** Read its ownership marker, schema
   and producer/consumer tool versions, contents, permissions, active
   publisher/reader records and frozen input receipts. An absent, incompatible, damaged or inaccessible target is
   not a reason to link blindly or silently copy it. Keep isolation or stop for
   an explicit repair decision.
4. **Which link, if any, is appropriate?** The G7 decision in
   [the migration plan](tasks/sfm-toolchain-shared-cache-migration-plan.md)
   must select and validate a supported root-level link type first. A relative
   Windows directory symlink and a local-volume junction have different
   permission and relocation properties. Neither warrants changing OS security
   settings. Other platforms need their own verified choice.
5. **Will both tools resolve the same physical target?** Record the checkout
   logical path and the canonical physical target, volume/file identity and
   ownership marker. Rust per-key locks, publication and held readers must use
   one physical namespace despite multiple logical aliases. Generated Gradle
   must resolve the same logical path from its config location without invoking
   Rust. Keep nested links/reparse points disallowed.
6. **Has live qualification passed?** After separate approval to make a test
   link/worktree, verify two linked checkouts use one checked entry for equal
   inputs; a new resource adds a distinct entry; concurrent same-key producers
   serialize; changed inputs/schema remain separate; mutable project output,
   snapshots, worlds and runtime sessions stay checkout/instance-local. Refuse
   broken, redirected or switched targets. Until then, sharing is a design,
   not an accepted runtime mode.

Never make a root link automatically or fall back to hidden physical copies.
Before changing an existing root, check disk headroom and active processes. If
space is insufficient, stop and report; do not clean, relocate or delete data.

## Current command boundaries

Catalog `jar` and `run` operations and `source project compile/jar` select
`<checkout>/sfm-toolchain-data` by default. `--cache-root` selects a guarded
override for one invocation. Native build and run operations require an explicit
`--java-home` so the selected JDK can be validated against the root.
`dependency source` and `symbol index refresh/show` select the exact branch
worktree's `<checkout>/sfm-toolchain-data` through the configured branch
selector, even when invoked from another working directory. Each accepts an
explicit `--cache-root` before its subcommand; the native-root marker and
overlap checks apply to either selection. This does not import old source trees.
Other branch dependency commands (except cache-independent lock migration),
branch symbol queries, generated-project symbol queries, and the branch JDK
source worker use the same guarded checkout selection. A ready symbol-server
JDK root is projected from its resolved canonical source domain; unavailable
roots are inert placeholders outside the cache. Explicit `--cache-root` exists for `dependency source` and
`symbol index`; ordinary branch query commands select the branch checkout.
The installed global executable and unrelated `cache`, `container`, and
`gradle` commands retain their own contracts. Do not infer that all
source-analysis providers or full symbol indexes have been acquired.

Generated
Gradle `gradle/repositories.gradle` uses the same logical root only with
`-PsfmUseRepositoryToolchainData=true`; without that property it retains its
earlier cache selection. Historical branch adapters and older installed CLI
binaries retain their earlier routing. This source change does not authorize a
link, old-data migration or retirement, and does not prove every stage has a
common representation. Plain historical 4.34.0 source remains the comparison
baseline.

## Pruning is report-only until separately approved

Current-checkout locks and recipes can identify some expected downloads and
derived keys, but lazy runtime inputs, old schema entries, unavailable owners,
preserved snapshots and rollback receipts may be unknown. An entry absent from
one checkout's expected set is a **candidate**, not an orphan. A shared cache
cannot be pruned from one checkout's view. Future retirement needs the union of
all physical-target owners, active reader/publisher exclusion, exact entry
identity and digest rechecks, a coordinated maintenance window and Teamy's
approval for an exact list. Never recursively traverse a checkout link to
delete a shared target. Removing a link and reclaiming its target are separate
actions. To return a linked checkout to isolation, first obtain approval for
the exact link removal and replacement directory. Quiesce its publishers and
readers, record and recheck the logical link and physical target identities,
remove only the link object with a link-aware operation that cannot traverse
the target, and verify the shared target remains intact. Create an ordinary
empty checkout-local root only under that approval, verify its marker and
permissions, then requalify input resolution. If any identity or owner is
uncertain, stop; never silently copy, relocate or delete shared bytes.

See the plan's G7, M2.1, M2.3, M3.9, M5 and M6 gates for the implementation and
acceptance ledger. This guide does not authorize creating or deleting anything.
