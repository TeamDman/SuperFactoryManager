# Native projection client launch

Status: all-projection launch extension active, 9 October 2026.

## Expanded acceptance contract

The 9 October follow-up explicitly rejects stopping at Forge development
launches. The remaining scope is native client launch for all 10 Minecraft
targets, for both development and previous-release catalog entries. Keep the
projection selector mandatory, with no branch or Gradle fallback.

| ID | User requirement | Implementation and proof |
| --- | --- | --- |
| L1 | Launch every supported dev projection, including NeoForm | Preserve held NeoForm inputs through runtime preparation; target launch matrix |
| L2 | Launch previous-release projections from Liquid-selected sources | Select checked release inputs, retain immutable locks and generated-file protection |
| L3 | Discover available projections in the CLI | Verify and document `source list --repo-root .` |

Intent audit: extraction captured dev, release and discovery separately;
traceability maps each to the work below; omission review confirms neither
compilation nor a single 1.19.2 startup satisfies all-version launch support.
Dependencies stay frozen. Stop if disk space runs out. No new goal was requested.

### [~] Complete native launch ownership

Extend the existing catalog launch adapter and `engine_run.rs`. NeoForm runtime
preparation must consume its held class JAR inside the owning callback, not an
unchecked copied path. Release projections must retain their selected original
dependency recipe. Add focused tests for target admission and selection.

### [ ] Validate and install all-projection launching

Run the Rust gate and complete runtime preparation across the 10-target
dev/release matrix. Boot representatives of the Forge, initial NeoForm and
newest split-runtime launcher families. Record preparation and startup outcomes
separately; preparation is not a claim of a successful boot. Earlier 1.19.2
evidence is only foundation. Verify
`source list`, update contributor instructions and install the final CLI.

Current evidence (extension not yet installed):

| Minecraft key | Release runtime preparation | Dev runtime preparation | Actual startup evidence |
| --- | --- | --- | --- |
| 1.19.2 | Passed | Passed previously | Dev title screen; release first client tick |
| 1.19.4 | Passed | Passed | Not boot-tested separately |
| 1.20 | Pending | In progress | Not boot-tested separately |
| 1.20.1 | Pending | Pending | Not boot-tested separately |
| 1.20.2 | Passed | Passed | Dev rendering initialized |
| 1.20.3 | In progress | Passed | Not boot-tested separately |
| 1.20.4 | Pending | Pending | Not boot-tested separately |
| 1.21.0 | Passed | Passed | Not boot-tested separately |
| 1.21.1 | Pending | In progress | Not boot-tested separately |
| 26.1.2 | Pending | Passed | Dev rendering initialized |

The three test clients opened during this extension (release 1.19.2, dev 1.20.2
and dev 26.1.2) were closed normally; each launch command exited 0. Preparation
uses `run client --projection <key> --dry-run` and includes source compilation,
runtime JARs, classpaths, assets and launch arguments, without opening a window.

The existing `source list --repo-root .` command returned all 20 keys. The
extension's first Rust run passed 1,795 active tests (22 ignored), test phase
81.387 seconds. Final validation must include subsequent smoke-hook admission
and release preview identity adjustments. No dependency declarations changed.
The subsequent final-source suite passed 1,689 library tests but one unrelated
working-tree capture test exceeded its observation deadline under concurrent
platform-build load. Rerun the gate without competing builds; do not change
the deadline or count that run as passing.

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

The extension now retains the NeoForm owner through runtime preparation and
launch. Its held-input adapter currently requires Windows. The validation
matrix above distinguishes complete runtime preparation from actual boots;
neither is a claim that every gameplay feature has been tested.
