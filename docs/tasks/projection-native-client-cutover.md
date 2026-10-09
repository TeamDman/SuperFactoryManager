# Native projection client launch

Status: client selector cutover installed and verified, 9 October 2026.

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
- [x] Test argument rejection and projection runtime isolation.
- [x] Verify real 1.19.2 startup with the installed CLI.
- [x] Run the Rust gate, update launch documentation, commit and install.
- [ ] Follow up on the 120-second smoke timeout during Forge background scanning.

## Evidence and risks

The full Rust gate passed 1,794 active tests, with 22 existing ignored tests.
Its test phase took 91.686 seconds. Formatting, Clippy and the default-feature
executable build passed. Parser tests reject missing projection selection and
the old branch argument, including when both selectors are supplied.

The first real 1.19.2 smoke launch compiled main, GameTest and datagen sources
and started Minecraft, but exceeded its 120-second startup timeout. A normal
launch with the installed executable reused platform and dependency caches.
A thread dump showed Forge's background class scan delaying startup. That scan
completed; `sfm instance list` then confirmed a responsive 1.19.2 client at
`net.minecraft.client.gui.screens.TitleScreen`. The normal client was left open
for manual testing. The smoke run is not recorded as passing.

Implementation commit: `a0eaca671`, pushed to main. The repository installer
completed after that commit. The PATH executable reported revision `a0eaca671`
and SHA-256 `645384069FDC306A0C12C72F74A7681F1E75E3C602910EB0D6164B93B86D3A5E`.
Its removed `--branch` argument fails parsing. User installation required: no.

JDWP launch options
are preserved, but the separate branch-based hotswap command has not been
migrated or verified against this cache.

The prepared catalog owner survives building and launch. Input and SDK checks
remain enabled. Runtime files and saves use the selected projection's run
directory rather than a version checkout. Preview evidence includes the
projection identity instead of inventing a branch identity.

The current named NeoForm executor has compilation support but a separate
application-launch boundary. Do not claim all-version runtime verification from
a 1.19.2 smoke test. Record unsupported routes explicitly rather than launching
another projection or the old branch.
