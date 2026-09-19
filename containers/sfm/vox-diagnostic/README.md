# Vox credit retirement diagnostic

This is a review experiment for the source already pinned by the SFM toolchain
lock. The diagnostic workflow applies `credit-candidate.patch` only inside a new
temporary checkout at that exact commit. SFM builds do not use this patch, and no
dependency declaration, lockfile, or published artifact is changed.

## Observed failure and reduction

Two native Linux builds failed in the unchanged `VoxRuntimeTest` after its first
40-item transfer, with the next call observing a closed lane. A diagnostic run of
the same pinned Java sources with JBR 17.0.6 exposed the earlier driver exception:
`message for unknown channel 1:1`, followed by connection shutdown. With logging,
the original test failed while awaiting the first response instead of on its next
call. That is a timing difference within the same first transfer.

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

Each workflow job runs the unchanged full `VoxRuntimeTest` once. The original job
uses untouched pinned sources and remains failed when that test fails. The
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
