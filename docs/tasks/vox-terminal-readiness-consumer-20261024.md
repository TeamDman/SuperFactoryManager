# Terminal startup input rejection — SFM consumer qualification

**Later review:** [review correction and full aggregate qualification](vox-terminal-readiness-review-20261024.md) supersede the final checkpoint below. This report retains the original 61-test evidence.

## Scope and source

Cloud-only consumer implementation on `task/parchment-handshake-20261023`, based
on `8632d22e89476eaad87f80261e2916e690da8674`. This preserves the migration at
`0918604ac558906c93a9c4ab449400e8f75792ce`, native-roots-only launcher commit
`2e8e6141dd2a339289ef91a1781c2a63cecfcfac`, and prior real Minecraft handshake
evidence. Implementation commit: **bcbd4a29434ed496aa6a387842b3283d16fd0162**.

The parent supplied the reviewed Terminal contract from source
`63a03c0a3f43e541860b7a31597d44e4bbba2c69`, report
`44aa0a052d24615f85d475e14874918a0090d795`, task
`01a1671f-225d-7536-89bc-ebebf799b0f9`. Those identities are trusted handoff
provenance, not a claim that this task fetched or ran that Rust Terminal binary.

The required core is this consumer change and its regression qualification.
No additional stretch item is claimed. Explorer/editor/matcher work is outside
this task. No pushes, merges, publication, installations, desktop/GPU work, or
network/security configuration changes were performed.

## Behavior

- Application exceptions retain the complete structured error. Only an **input**
  result with `INVALID_REQUEST` **and** `retryable=true` is treated as startup
  rejection before forwarding. Message text never selects this behavior.
- That rejection retains the session, transport generation, and streams. The
  current input error is visible and clears after a successful current-session
  operation. It requires a new user action; nothing queues a retry or replays the
  failed request. Other error codes, non-retryable invalid requests, non-input
  failures, and transport/timeouts retain transport retirement behavior.
- The UI snapshot distinguishes transient input rejection from connection
  failure. Even before the first frame, this rejection does not offer or trigger
  a connection retry. The event history may retain a past failure message after
  the current error clears.
- Each locally accepted paste completes once with `PASTED`,
  `CONFIRMATION_REQUIRED`, or `FAILED` plus a structured error. Close/reconnect
  cancels outstanding accepted work, including jobs removed from the driver
  queue. Stale replies cannot complete twice. Failure carries no confirmation
  identity and cannot open a confirmation dialog; approved-paste failures are
  also surfaced. Calls rejected before enqueue return false without a callback.
- A successful send acknowledges forwarding, not command execution. Async
  boolean true acknowledges local enqueue only. Synchronous legacy execute
  never falls back to local execution once an input send was attempted, including
  ambiguous transport failure. A failure before any input attempt retains the
  existing local fallback.

All edits are in the authored Liquid core, its existing test files, and the
gated gameplay changelog. Native projection sync generated the development
outputs. Frozen release snapshots were not edited. Existing five-argument
connection snapshot and three-argument paste constructors remain available;
`FAILED` and the additional error field are local consumer API changes, not Vox
wire changes.

## Verification

Final native results: **21/21 service tests and 40/40 panel tests passed**,
with zero failures, skips, or aborted tests. Both commands exited zero. The
1.19.2 build compiled 1,573 main, 494 GameTest, 6 data-generation, and 480 test
Java sources; the two selected suites account for the 61 executed tests.

The service suite uses the real pinned Java Vox client and dispatcher over
loopback, with an established lane injected to isolate input behavior from
Terminal presentation negotiation. It covers arbitrary/empty/Unicode messages,
all four error fields, same-session recovery and stream retention, text/key/
mouse/coalesced motion/cancel/execute, narrow error classification, non-input
separation, stale generations, guarded/approved paste, timeout ambiguity,
close/reconnect cancellation, and duplicate-completion prevention.

Panel tests cover failure visibility without confirmation, approved-paste
failure, stale confirmation suppression, and startup rejection before the first
frame without reconnect. The native runner compiles the projection's Java inputs
and tests before selecting these suites. Compiling GameTest sources is not a
GameTest execution result.

Native source sync/check passed for `sfm-dev/mc-1.19.2` and `sfm-dev/mc-1.19.4`.
The service and three changed model/interface files are byte-identical between
those projections. No full 1.19.4 game build/run is claimed.

The native-rendered synchronous 1.20.1 service, interface, and frame compiled
with Java 17 and passed the independent `LegacyReadinessProbe`. It checks typed
rejection, manual retry, transient clear, same session/generation, ordinary
invalid/disconnected/other-retryable separation, and no local replay on timeout.
The final service render matches those tested bytes. This single-file render
probe isolates input (presentation polling disabled in the fixture); it is not
a generated full 1.20.1 project or full-game qualification. Other legacy Liquid
versions share that branch but were not individually compiled/run.

Unrun here: actual new Rust Terminal service, PTY startup, new live Minecraft
interaction, GameTest execution, graphics, and manual in-game UI checks. The
previous Rust-to-Minecraft `sfm:echo` qualification remains historical evidence
in the preserved handshake commit; it was not unnecessarily repeated.

## Dependency and build provenance

Dependency posture is frozen. Cargo/Minecraft declarations, lockfiles, feature
definitions, projection declarations, runtime source pins, and wire fields are
unchanged. The Java runtime remains Vox source
`5628c00d9f3816199f324a9ccc081de8a3a1b1bc`, JAR 614,530 bytes, SHA256
`be441a57e11d4981d283306fe471874e625bf2f423c83a91ce8f44dde623d062`.
Facet-format remains `642d268fc01eced0a861bded044342ff88733eec`.

Native preparation exposed missing offline Maven metadata for already locked
test inputs. Six checked-in fixture POMs were restored after exact recorded
SHA256/size verification. Missing parent/BOM/POM metadata for the locked Guava,
Error Prone, JUnit, JMH, and related existing test graph was acquired from
`https://repo.maven.apache.org/maven2/` using verified HTTPS, the existing filtered
CA store, no redirects, and no retries. Official POM SHA1 sidecars were checked;
full SHA256, size, URL, TLS verification, and local-cache catalog entries are
retained in the metadata receipts. The native runner verified the existing
locked binary artifacts. No project dependency version or source identity was
changed and no unpinned substitute/runtime was introduced.

Early preparation attempts stopped on those metadata misses before JUnit. A
later compile caught an incorrect UI accessor introduced during the final
lifecycle refinement; it was corrected in authored source and the final tests
were rerun. These intermediate failures are retained as diagnostic evidence,
not presented as passing test runs. No denied host, TLS bypass, approval bypass,
or unresolved acquisition blocker occurred.

## Operational readiness and manual handoff

- Native CLI sources are unchanged. The existing audited absolute-path launcher
  remains valid: `/workspace/sfm-handshake-20261023/tools/sfm-propagate-changes-native`,
  version 0.1.1, revision `2e8e6141dd2a339289ef91a1781c2a63cecfcfac`, SHA256
  `7e7fb12ed75251ff9c48b61467793ee94d44e1aab528774a00b93acea74476ff`.
- **User must run install.ps1: no.** No CLI executable/runtime producer source
  changed. Native source sync and native test build compiled the changed Java;
  no installer or toolchain upgrade was run.
- Existing test SDK: Temurin 17.0.20.1+1 at `/workspace/.sfm-env/jdk17`.
  Existing exact producer SDK: `/workspace/sfm-handshake-20261023/sdk/jdk-21.0.8+9`,
  identity `Eclipse Adoptium|21.0.8+9-LTS`; no new SDK installation.
- Native test processes are bounded by 20-minute (service) / 10-minute (panel)
  timeouts and a 20-second interrupt/kill grace. The existing environment limits
  are four CPUs and 16 GiB. The independent legacy probe was also bounded.
- The previous game/display processes were already stopped. This task launched
  only build/test processes and short-lived loopback fixtures. Final process
  state and cache identity are recorded in the evidence receipt; no game or
  display is intentionally left running, and no lock reset was required.
- The filtered CA bundle SHA256 remains
  `ee7d8a9353d49d866898ce0d49091bcdf207d71e323ebfe0af9d2020e691c99c`.
  `origin/main` remains `122e4b31ea2875a0894d648c51c5ee5f78c589e6`.

From the preserved task branch in this executor, run:

```bash
bash docs/tasks/vox-terminal-readiness-consumer-20261024-evidence/reproduce-tests.sh
```

The script uses the absolute audited launcher, source sync, existing SDKs,
portable locked artifacts, and the two exact suite filters. It starts with no
game/server needed. Expect the final counts above and exit zero; inspect the
new `manual-rerun/*.stderr` summaries and NDJSON logs. It does not launch a game.
Live manual interaction against the reviewed Rust Terminal remains a separate
compatibility gate requiring that service, not a blocker to the tested consumer
contract fix.

Curated evidence is in
[`vox-terminal-readiness-consumer-20261024-evidence/`](vox-terminal-readiness-consumer-20261024-evidence/).
Its manifest hashes the retained files. Full working logs/caches remain at
`/workspace/sfm-terminal-readiness-20261024` and the native project cache named
in the receipt. No signed redirect credentials or game authentication material
is included.
