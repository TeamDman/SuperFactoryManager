# Vox credit retirement diagnostic

This review experiment always fetches the recorded original Facet baseline,
`f2afdece6c79e64085d2f8c047e22fe16b2c8c54`, independently of SFM's current
toolchain lock. That immutable revision contained the failing Vox Java runtime;
its recorded packaged artifact hash was
`blake3:4d1e88353f941be926fdf84f1dd8da9bd594b60f`.

SFM now pins the repaired revision, so selecting diagnostic source from the
current lock would no longer reproduce the baseline failure and would apply the
candidate patch twice. The helper records both its fixed baseline and the current
SFM lock identity in the receipt, while the fetched Git commit must match the
baseline exactly.

The workflow applies `credit-candidate.patch` only inside a fresh temporary
baseline checkout. It compiles diagnostic classes there and produces logs and
receipts. It never supplies classes or JARs to SFM builds, writes to their cache,
or changes a dependency declaration, lockfile, or published artifact.

## Observed failure and reduction

Two native Linux builds failed in the unchanged `VoxRuntimeTest` after its first
40-item transfer, with the next call observing a closed lane. A diagnostic run of
the same pinned Java sources with JBR 17.0.6 exposed the earlier driver exception:
`message for unknown channel 1:1`, followed by connection shutdown. With logging,
the original test failed while awaiting the first response instead of on its next
call. That is a timing difference within the same first transfer.

A later hosted comparison passed both the unchanged original and candidate tests.
The original full-test failure is therefore timing-dependent; a single passing
baseline run does not invalidate the preserved failures or prove the race is gone.

The exception trace does not contain the channel message body. Late receiver
credit is inferred from the sender/receiver roles and first-transfer test sequence,
and tested directly with the deterministic driver probe. The closest passing
baseline delivers the same credit before local sender Close. Delivering it after
Close fails with the exact unknown-channel exception.

## Proposed behavior

The pinned reference implementation and specification are at Facet revision
`f2afdece6c79e64085d2f8c047e22fe16b2c8c54`:

- `vox/rust/vox-core/src/driver.rs`, `handle_channel` / `GrantCredit`:
  add permits only if the channel's credit semaphore still exists; otherwise
  the credit has no effect.
- `vox/docs/content/spec/rpc.md`, `rpc.flow-control.credit.grant`:
  the receiver may grant credit after the channel exists.
- The same specification's `rpc.channel.close` forbids subsequent sender Items.
  It does not introduce an acknowledgement or fence for credit already travelling
  in the opposite direction.

The patch moves the existing Java credit validation before channel lookup, then
discards valid credit for a nonzero channel ID when no active channel remains on
an accepted inbound or open outbound lane. Java's existing handling of known
locally closed lanes stays in place. Unknown lanes, lane zero, and outbound lanes
still awaiting acceptance continue to reject credit. This lane gate preserves
Java's existing strictness; the Rust connection layer also filters inactive lanes
before its driver receives channel messages.

Active channels still enforce direction and add credit normally. There is no retained history, expiry,
new bound, or allocation per discarded credit. The existing Java positive
`int` credit range is preserved; this proposal does not expand it to all `u32`.
For an absent nonzero channel on an accepted lane, valid credit is discarded
without reconstructing that old channel's allocation or parity. No credit is
saved for a future channel and no channel is created, matching the reference
driver's handling of missing channel credit.

Missing-channel Item, Close, and Reset behavior stays unchanged. Rust's Reset
handling consults terminal-channel state, so treating every unknown Reset as a
no-op would need a separate review.

## Validation contract

Pushes to the diagnostic branch and the default manual mode (`reduced`) compile
fresh, untouched original baseline sources and run only two deterministic probe inputs once:
credit before local Close must pass; the same credit after Close must fail with
`message for unknown channel 1:1` from `VoxConnection.processInboundChannel`.
The receipt records both real exit codes and `baseline-failure-reproduced` only
when both expected outcomes and the exact exception match. An unexpected pass,
different failure, timeout, or build error fails the diagnostic job. This job
proves the reduced baseline bug; its success does not mean the runtime is fixed.
It does not run or retry the full original test.

The explicit manual `full` mode runs the full comparison once. The original job
uses untouched original baseline sources and remains failed when that test fails. The
candidate job applies only the proposed `VoxConnection.java` change, checks that
the original test source is unchanged, and runs the same test plus each retained
probe variant once. There are no retries or allowed failures.

The 19 variants cover active/closed/missing sender credit, delay beyond the old
bounded-history proposal, malformed or out-of-range credit, active wrong-direction
credit, invalid lane and zero-channel IDs, a not-yet-accepted lane, known local lane
closure, and unchanged Item/Close/Reset errors. The probes invoke the actual pinned
private driver seam with real registered ServiceLane state, real wire types, and generated channel metadata.
Malformed-value variants deliberately bypass decoding to test driver validation.

Artifacts include exact source/patch/probe hashes, source and test counts, command
arguments, compiler output, original test output, JVM exceptions, and each probe's
output and exit status. A candidate success is evidence for review, not a dependency
update or a claim that SFM CI is fixed.
