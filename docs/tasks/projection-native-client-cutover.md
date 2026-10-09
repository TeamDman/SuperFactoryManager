# Native projection client launch

Status: in progress, 9 October 2026.

## Contract

Replace `run client --branch` with `run client --projection`. Do not retain a
branch fallback or fabricate a worktree identity. The selected catalog entry
owns version, features, generated inputs, runtime files and saves. Use the native
Rust build and launch pipeline, not Gradle. Preserve the existing interactive,
smoke, puppet and hotswap options. Dependencies remain frozen.

The user also requested pushing main, 1.19.2 and 1.19.4. The atomic, non-force
push completed at 763077b55, e1338e4e2 and 2e3b561c1 respectively.

## Work

- [x] Inspect existing projection build ownership and legacy launch guards.
- [x] Add a projection-only client selector and reject the former branch flag.
- [x] Prepare catalog-owned inputs and use the native launch executor without
  weakening source, SDK or cache ownership checks.
- [x] Preserve client options and use projection identity in launch evidence.
- [ ] Test argument rejection, isolation and real 1.19.2 dev smoke startup.
- [ ] Run the Rust gate, update launch documentation, commit and install.

## Evidence and risks

The full Rust gate passed 1,794 active tests, with 22 existing ignored tests.
Its test phase took 91.686 seconds. Formatting, Clippy and the default-feature
executable build passed. Parser tests reject missing projection selection and
the old branch argument, including when both selectors are supplied.

The first real 1.19.2 smoke launch reached native platform preparation in its
projection-owned cache. Runtime success is still pending. JDWP launch options
are preserved, but the separate branch-based hotswap command has not been
migrated or verified against this cache.

`invoke_development_project` already owns native compilation. `execute_run`
and subprocess logging still require a legacy branch. Several preview evidence
helpers also assume a branch. Removing those guards alone is insufficient:
the prepared catalog owner must survive building and launch, and saves must
remain under the selected projection rather than a version checkout.

The current named NeoForm executor has compilation support but a separate
application-launch boundary. Do not claim all-version runtime verification from
a 1.19.2 smoke test. Record unsupported routes explicitly rather than launching
another projection or the old branch.
