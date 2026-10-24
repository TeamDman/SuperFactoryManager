# Terminal consumer review and aggregate qualification

**Later live follow-up:** [publication and partial live qualification](vox-terminal-live-qualification-20261024.md) records the now-available producer source and the remaining PowerShell runtime prerequisite.

This follow-up reviews the local consumer checkpoint `6d44dc86faccbe7756fae25693b4e37a88b2f557`
(implementation `bcbd4a29434ed496aa6a387842b3283d16fd0162`). The earlier
[consumer report](vox-terminal-readiness-consumer-20261024.md) and its evidence
remain historical records. Review correction commit: **f9b3e7e5737967d5f40aed1f6dfd187af8f7db8b**.

## Review findings and correction

A separate adversarial pass inspected the actual callback/state transitions,
then added tests that do not derive their expectations from the implementation.
It found one demonstrated defect: the generic operation wrapper cleared a
transient rejection after any successful response, even when that response's
session ID had failed the operation's current-session check.

The new `wrongSessionSuccessCannotClearTheCurrentInputFailure` test was rendered
by the native projector and compiled against the previously tested consumer.
The resulting run had **23 passes and one failure**: the current structured
error was incorrectly replaced with `Optional.empty`. The correction removes
the unconditional clearing and requires response session identity as well as
the current client/generation and open-service checks. Interaction selection
acceptance and error clearing now make one locked decision. The same response
identity check is applied to the synchronous Liquid branch and to explicit
successful operation sites.

The review also checked:

- **Exactly-once paste completion:** registration and submission share the
  service lock; unsuccessful enqueue removes the registration and returns false.
  Completion marks/removes a pending request under that lock before scheduling
  its callback. Close/reconnect complete remaining registrations before driver
  shutdown can discard queued jobs. A late reply sees the completed state.
- **Callback isolation:** callbacks run outside the monitor on the existing
  cleanup executor. Added tests cover a callback closing the service and then
  throwing, and an executor rejecting enqueue. These passed against the prior
  implementation as well as the final aggregate. No duplicate or retained
  unaccepted request was found.
- **Cancellation/generation boundaries:** existing tests cover queued and
  in-flight close, reconnect, stale success/failure, and stale confirmation.
  Completion already decided before close remains that one completion; UI
  epoch guards reject delayed old-session UI effects. Cancellation cannot revoke
  input that may already have been forwarded and never authorizes replay.
- **Typed application versus transport errors:** only input
  `INVALID_REQUEST && retryable` retains the session. Ordinary invalid requests,
  disconnected results, other retryable codes, and non-input errors do not use
  the readiness exception. Code/message/retryable/server sequence are retained.
- **Confirmation and replay:** FAILED paste results cannot carry confirmation
  content, and only CONFIRMATION_REQUIRED opens the dialog. Approved-paste
  failure is displayed. Neither rejection, timeout, cancellation nor legacy
  execute's ambiguous send path retries or executes a fallback copy.

There are no remaining demonstrated defects in this reviewed change. This does
not claim a separate human review or live producer compatibility.

## Aggregate and compatibility results

Final native aggregate: **2,576 found, 2,570 passed, zero failed, zero skipped,
six aborted; exit zero**. All **211 terminal-package tests passed**, including
24 service tests and 40 panel tests. The three new tests are included in these
counts, not added to them afterward.

The initial full aggregate found 2,573 tests: 2,502 passed, 65 failed, six
aborted. Every failure was caused by release-review recovery storage attempting
to write `/home/agent/.local/share/teamdman` on the read-only filesystem. The
existing `SFMReleaseReviewStore.recoveryRoot()` supports `LOCALAPPDATA`; setting
it only for the test process to
`/workspace/sfm-terminal-readiness-review-20261024/localappdata` resolved all 65.
A direct rerun of those exact compiled classes had 2,567 passes, zero failures,
six aborted. No filesystem permission, security setting, or source in the
release-review component was changed.

The final native run builds the corrected 1.19.2 projection and runs the entire
Java test aggregate, including the three newly added service regressions.
Source validation remains enabled and portable locked artifacts are required.
Main, GameTest, data-generation, and test compilation are included; GameTest
execution and a Minecraft launch are not.

The six optional tests abort because their external inputs are unavailable or
not selected: item icon repository coverage, installed symbol worker, captured
release-review scale fixture, installed syntax worker, ledger companion opt-in,
and captured review-projection fixture. Exact test names and assumption messages
are retained in `optional-gates.json`. They are not passing gates, and no worker
was installed merely to remove these aborts.

The final native-rendered legacy 1.20.1 input probe compiled and passed, including
the added wrong-session response assertion, typed rejection, recovery, narrow
classification, timeout, and no fallback replay. Its presentation polling is
disabled to isolate input; it is not a full legacy project/game test. A missing
legacy-only type qualification found during that compile was corrected. The
qualification does not change the rendered 1.19.2 source used by the aggregate,
as recorded by an exact final-render comparison.

Final 1.19.2/1.19.4 projection checks passed with zero drift. The service,
three model/interface files, and the service test file are byte-identical across
those two projections.
Other Minecraft versions were not individually built or run. The Rust CLI was
unchanged, so its Rust-only `check-all.ps1` build/lint/test aggregate was not
repeated; PowerShell is also unavailable in this executor.

## Live qualification prerequisite

No new Rust Terminal service or Minecraft instance was launched. Live startup
input end-to-end qualification requires the tested unpublished Terminal source
`63a03c0a3f43e541860b7a31597d44e4bbba2c69` (report
`44aa0a052d24615f85d475e14874918a0090d795`) from task
`01a1671f-225d-7536-89bc-ebebf799b0f9`, or its verified matching runnable artifact,
to be made accessible in this owning executor with its launch instructions.
That source/artifact transfer is pending; `teamy-terminal` is not installed in
this executor's PATH. The Java contract fixture is not that producer. The
already established Minecraft `sfm:echo` result remains preserved evidence and
was not relaunched to stand in for this distinct gate.

## Operational handoff

- Branch remains `task/parchment-handshake-20261023`. Prior consumer, TLS, and
  handshake commits remain ancestors; `origin/main` remains
  `122e4b31ea2875a0894d648c51c5ee5f78c589e6`.
- Frozen dependencies/pins, wire definitions, feature declarations and release
  snapshots are unchanged. No acquisition, installation, dependency upgrade,
  publication, push, merge, desktop or GPU operation was performed in this review.
- The unchanged native launcher is
  `/workspace/sfm-handshake-20261023/tools/sfm-propagate-changes-native`, revision
  `2e8e6141dd2a339289ef91a1781c2a63cecfcfac`, SHA256
  `7e7fb12ed75251ff9c48b61467793ee94d44e1aab528774a00b93acea74476ff`.
  **User must run install.ps1: no.** The existing Java 17 test and exact Temurin
  21.0.8+9 producer SDKs were reused; only Java source was rebuilt.
- The native aggregate has a 20-minute bound plus 20-second shutdown grace;
  direct aggregate reproduction has a 10-minute bound, and focused probes have
  60-second bounds. The existing four-CPU/16-GiB limits remain unchanged.
- Final task-owned build/test processes are stopped. No cache lock reset or game/
  display cleanup was needed. Final process and tool identities are in the receipt.

Run the available aggregate again in this executor with:

```bash
bash docs/tasks/vox-terminal-readiness-review-20261024-evidence/reproduce-aggregate.sh
```

This performs native source sync and the full native JUnit run, using only the
existing tools and a task-local recovery directory. Inspect the generated
`manual-rerun/aggregate.stderr` and NDJSON log. No game/server is needed.

The [review evidence](vox-terminal-readiness-review-20261024-evidence/) includes
the red regression, final aggregate outcomes, optional gates, legacy proof,
source checks, provenance and copyable commands. Full working logs remain at
`/workspace/sfm-terminal-readiness-review-20261024`. The local consumer checkpoint
is ready within these tested boundaries; the separate live Terminal gate remains
pending the named source/artifact transfer.
