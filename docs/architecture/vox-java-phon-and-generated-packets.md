# Vox Java, Phon, packaging, and future generated SFM packets

This document records the verified architecture and repository research behind
Track 5 of the in-game workspace plan. It is intentionally more detailed than
the task checklist so that future implementation does not repeat the same
survey.

Research snapshot: 2026-07-23.

## Names and layer boundaries

The relevant projects are separate layers:

- **Facet** describes Rust type shapes and supplies the reflection information
  from which portable schemas can be produced.
- **Phon** is the schema-aware binary representation, schema-closure format,
  schema identity and compatibility-plan layer.
- **Phon/Vox code generation** projects Rust service and type descriptions into
  non-Rust source code.
- **Vox** adds connections, service lanes, method identity, request/response
  correlation, typed errors, cancellation, schema binding and lifecycle above
  Phon.
- **Minecraft/Forge/NeoForge packets** carry mod messages over the already
  established Minecraft client/server connection.

Phon is therefore not merely another spelling of Minecraft's `StreamCodec`.
Both associate values with binary representations, but Phon additionally
supports canonical schemas, cross-language generation and compatibility plans.
Vox is an RPC layer above that representation.

## Verified Facet and Vox state

The maintained fork is checked out at `G:\Programming\Repos\facet`.

At the time of this survey:

- local `main` was clean at
  `5fd9cfaa46b4babc1f79d10d714600e710c28c2f`;
- local `main` tracked `mine/main`, where `mine` is
  `https://github.com/teamdman/facet`;
- `origin` was `https://github.com/facet-rs/facet`;
- cached refs showed `mine/main` 75 commits ahead and zero behind cached
  `origin/main` at `3b20e02a2`, but the cache was eleven days old and is not
  evidence about current upstream;
- current local Vox and current upstream source exposed Rust, TypeScript and
  Swift, but no Java target, runtime or hosted subject;
- `vox/DEVELOP.md` and `vox-codegen` crate documentation contained stale Java
  claims even though `vox/xtask/src/main.rs` had no `--java` option and
  `vox/rust/vox-codegen/src/targets/mod.rs` exported only Swift and TypeScript;
  and
- Phon also had no Java runtime or Java generator. A conforming Vox Java
  implementation is therefore blocked on a Java Phon baseline, not merely on
  adding `targets::java` to Vox.

Java support existed before historical commit
`bd6265411e21deffa5598c4f2719dcaddacf6d7a`, which removed `vox/java/**` during
the schema-aware Phon/wire rewrite. The parent commit
`b0593a9f6fec57737508e575b01e4bb079a828cf` may be used as provenance for Java
naming, `CompletableFuture`-shaped APIs, generated-file headers and simple
subject-launch scaffolding.

The historical wire implementation must not be restored. In particular, its
COBS/zero-delimited framing, legacy handshake, ad hoc payload codecs,
String-only dispatch and 32-bit truncation of canonical 64-bit method ids do
not implement current Vox.

## Current Vox protocol obligations

The Java implementation must follow the specification and conformance fixtures,
not translate the Rust implementation by intuition. The first supported stack
includes:

1. raw TCP link establishment and the six-byte `VOXL` link prologue;
2. unsigned 32-bit little-endian length-delimited messages;
3. the framed Vox transport `VOTH`/`VOTA` prologue;
4. the self-describing Phon connection handshake (`Hello`,
   `HelloYourself`, `LetsGo`, or a structured refusal);
5. compatible schema closures and decode plans;
6. connection control lane zero;
7. explicit nonzero service-lane open/accept/reject/close;
8. per-lane request ids, canonical 64-bit method ids and request/response/error
   correlation; and
9. explicit connection-driver, timeout, cancellation and shutdown lifecycle.

Current Vox does not negotiate one global semantic protocol-version integer.
The transport prologue is versioned, while Vox message evolution is negotiated
through exchanged schemas and compatibility plans. An SFM shaped-intent
service may additionally expose an application capability/revision contract,
but it must not replace the Phon compatibility handshake.

## First honest Java subset

The first accepted implementation is deliberately bounded:

- Java 17 source and bytecode because Minecraft 1.19.2 uses Java 17;
- TCP only;
- pure Java runtime with no JNI;
- unary request/response in both directions on one duplex connection;
- generated DTOs, callers, handlers, dispatchers and service descriptors;
- nested records/enums/options/results needed by the proving service;
- real schema exchange, binding deduplication and compatibility planning;
- application errors, protocol/call errors, cancellation, timeouts and graceful
  or abrupt disconnect; and
- deterministic generated source and a publishable Java artifact.

The first subset explicitly rejects, at generation time where possible:

- `Tx<T>` and `Rx<T>` channels;
- file-descriptor capabilities;
- WebSocket, Unix-domain, shared-memory and Iroh transports;
- runtime Rust `Shape` pointers or layouts;
- arbitrary dynamic Facet values;
- reconnect, automatic retry or request replay; and
- optimized/JIT decode plans.

Unsupported shapes must produce precise code-generation diagnostics rather than
compile successfully and fail unpredictably at runtime.

## Java implementation components

### Phon Java

The Phon Java component owns:

- Java schema and generic value models;
- canonical schema/value encoding and decoding;
- schema-closure parsing;
- the same schema-id/BLAKE3 result as the reference implementation;
- compatibility-plan construction or interpretation;
- bounded readers and allocation limits;
- typed adapters for generated Java records and sealed interfaces; and
- malformed, truncated, oversized and incompatible-input behavior.

Non-Rust peers consume canonical schemas emitted from Rust definitions. They do
not independently derive wire identity from Java reflection.

### Rust-side Java generators

The generators own:

- `phon-codegen::java`;
- `vox-codegen::targets::java`;
- Java names and scalar/container/record/enum/result mappings;
- embedded canonical schema bytes and descriptors;
- generated callers, handler interfaces and dispatchers;
- unsigned/canonical id handling using Java `long`, never an `int` switch;
- a real `cargo xtask codegen --java`;
- deterministic regeneration and drift detection; and
- Java 17 compilation of all generated fixtures.

### Vox Java runtime

The runtime owns:

- link halves and bounded framed TCP I/O;
- transport and Phon connection handshakes;
- the explicit connection driver;
- service-lane state machines;
- request allocation, pending-call correlation and response delivery;
- schema binding and per-lane schema tracking;
- cancellation, timeout, graceful drain and abrupt EOF behavior;
- bounded queues, frames and concurrent requests; and
- a Java hosted subject that exits on disconnect or inactivity.

Connection, lane and schema state machines should have one implementation owner.
Splitting their invariants across several agents would create a difficult
integration boundary.

## Upstream acceptance gates

Before SFM consumes the artifact, Facet/Vox must prove:

- byte-identical Java encode/decode for the relevant Phon conformance corpus;
- matching schema ids and compatible/incompatible evolution fixtures;
- malformed, truncated, oversized and resource-bound failures;
- transport prologue, empty/fragmented frame, EOF and close behavior;
- Rust server to generated Java client;
- generated Java server to Rust client;
- bidirectional calls on one connection;
- `echo(String)`, a nested DTO and a fallible method;
- successful and user-error results;
- unknown method and invalid payload as call-level errors where specified;
- cancellation, timeout and abrupt disconnect;
- first-call schema binding and repeated-call binding reuse;
- compatible evolution, followed by an incompatible call that leaves the
  connection usable when the specification requires it;
- deterministic generation checked into or reproduced by CI;
- `javac --release 17`;
- Windows and Linux Java subject lifecycle; and
- the Facet repository's formatting, lint, test, documentation and Tracey
  requirements.

The existing golden-vector documentation must be reconciled where filenames or
descriptions still say `postcard` while current TypeScript code decodes the
vectors through Phon. Fixtures are normative only after that discrepancy is
understood and documented.

## SFM packet comparison

SFM currently preserves one home-grown packet abstraction across Minecraft
versions:

- each packet is a Java record implementing `SFMPacket`;
- a nested `SFMPacketDaddy<T>` supplies direction, class, encoder, decoder and
  handler;
- the encoder and decoder manually write/read fields in matching order; and
- `SFMPackets` adapts the same abstraction to the loader API for that version.

The loader-facing shells differ:

| Minecraft version | SFM transport registration |
| --- | --- |
| 1.19.2 | Forge `SimpleChannel.registerMessage` and `FriendlyByteBuf` |
| 1.20.4 | NeoForge custom payload registration and `FriendlyByteBuf` |
| 1.21.1 and later | `CustomPacketPayload`, `PayloadRegistrar` and `StreamCodec<RegistryFriendlyByteBuf, T>` |

The newer `StreamCodec` construction still delegates to the same hand-written
`SFMPacketDaddy.encode` and `.decode` functions. It packages an encoder/decoder
pair for the modern API; it does not provide cross-language schemas, automatic
record generation, compatibility planning, RPC correlation or cancellation.

The first Vox integration does not replace this network:

```text
Minecraft client <---- SFM packet channel ----> Minecraft server

Minecraft Java process <---- Vox over local TCP ----> Rust helper
```

Minecraft packets already inherit the authenticated Minecraft session, player
and menu context. Vox initially serves a separately authenticated local
Java/Rust tooling boundary.

## Future generated Minecraft payloads

After the Vox Java and Phon foundation is stable, the same shape/code-generation
investment may reduce SFM packet boilerplate. This is a distinct future track,
not a requirement for the first Vox bridge.

The proposed architecture is:

1. define a loader-neutral packet/payload schema in Rust using Facet-compatible
   shapes plus explicit packet metadata;
2. generate the Java payload record and loader-neutral field codec;
3. generate or register a descriptor containing stable packet identity,
   direction, bounds and codec;
4. adapt that descriptor to `SimpleChannel`/`FriendlyByteBuf` on older versions
   and `CustomPacketPayload`/`StreamCodec` on newer versions; and
5. keep authorization, sender/world/menu validation and game behavior in
   hand-written Java handlers.

Rust is the build-time source of truth. No Rust runtime is required inside
Minecraft merely to use generated packets.

The schema must represent more than field types:

- stable packet id independent of registration order and preferably independent
  of a Java simple-class-name rename;
- serverbound/clientbound direction;
- string, collection, nesting and total-payload bounds;
- optional fields and explicit compatibility policy;
- protocol phase if SFM later uses configuration/login payloads;
- documentation and deprecation;
- whether a field requires registry access; and
- the Java handler interface the generated payload expects.

Minecraft-specific types such as `ItemStack`, `BlockPos`, `Component`,
`ResourceLocation`/`Identifier`, registries and NBT cannot be inferred as plain
portable Phon scalars. They need an explicit adapter catalog. Each adapter has:

- one logical schema identity;
- a Java type for each supported version family;
- bounded encode/decode functions; and
- a version-specific implementation isolated behind
  `@MCVersionDependentBehaviour` where necessary.

There are two possible generated wire strategies:

1. generate direct `FriendlyByteBuf`/`RegistryFriendlyByteBuf` operations and a
   `StreamCodec` adapter for modern versions; or
2. encode the portable payload to Phon bytes and carry those bytes inside one
   Minecraft custom payload.

The direct strategy better matches Minecraft conventions and existing packets.
The Phon-envelope strategy offers schema evolution and one codec across
versions, but adds overhead and would require an intentional compatibility and
failure policy. It should be benchmarked and prototyped rather than assumed.

Migration should begin with a small packet containing portable fields, then a
bounded string/enum packet such as the disk-program mutation, and only later a
registry-aware `ItemStack` packet. Generated and legacy codecs should be tested
against captured vectors before replacing a live packet id.

Generated payloads may remove repetitive record/codec/registration code. They
must not generate or obscure:

- permission checks;
- sender, world, hand, menu or item validation;
- game-thread scheduling;
- denial/audit behavior; or
- side effects.

## SFM bridge and observable proof

After the upstream artifact is frozen, SFM owns a narrow Java adapter rather
than a fork of the Vox runtime:

- `SFMVoxBridge` and peer abstraction;
- process-scoped lifecycle and bounded I/O executor;
- loopback and authentication security policy;
- generated shaped-intent service adapter;
- fixed structured reverse-action allowlist;
- status model and composable multiplexer panel; and
- lockfile-driven dependency and bundle declaration.

All network callbacks decode and validate off-thread, then enqueue Minecraft
state changes through `Minecraft.getInstance().execute`. eframe remains in its
own process and owns its own window/event loop.

### Portable colour-prompt presentation boundary

Before implementing the Vox colour-picker flow, refactor the reusable Java
ARGB colour-input panel so its area above the editing controls is a composable
header slot. The default remains the current two-line header: a vertical
composition of centered formatted-text panels, preserving today’s title and
supporting text, spacing, alignment and compact-layout behavior. Callers may
replace that slot with another Java-owned panel when a prompt needs different
local presentation; the colour controls and typed ARGB result remain unchanged.

The Vox wire contract must carry portable structured prompt content only (for
example, title, explanatory text, and bounded formatting data). Rust supplies
that content; the Java bridge validates it and renders a local centered-text
header panel, then supplies it to the colour panel’s header slot. Rust never
sends an arbitrary Minecraft panel, widget tree, renderer callback or layout
instruction across the wire. Java owns Minecraft UI composition, bounds,
formatting policy, accessibility and theme resolution; Rust owns the semantic
prompt intent and its typed result.

The focused validation slice must prove that the ordinary colour panel is
visually unchanged, a Vox prompt uses the custom local header, long or
multi-line content stays bounded and centered at supported GUI scales, invalid
or oversized prompt fields are rejected or safely truncated, and the typed
ARGB result still round-trips. The puppet proof should capture both the
default header and the connected Vox prompt, including cancellation,
disconnect/timeout and no-stale-callback terminal states. This keeps the
colour panel reusable without making ordinary theme editing depend on Vox.

The first visual proof is:

1. open **Vox bridge** from the command palette;
2. show connected/authenticated/schema-compatible state;
3. originate a typed colour intent from Java;
4. show a pending request in Minecraft;
5. capture the real eframe form;
6. return a typed ARGB result and show its swatch/value in Minecraft;
7. let Rust request one fixed allowlisted Echo action;
8. visibly audit and execute it on the Minecraft client thread;
9. reject a forbidden action without execution; and
10. show cancellation, timeout or disconnect as a terminal state with no stale
    callback.

The CLI owns the deterministic Rust peer used for puppet acceptance. Java does
not launch an arbitrary user-configured executable. Minecraft puppet captures
and a separate eframe capture are combined into one HTML report.

### Terminal-first bridge direction — 2026-07-25

The colour-picker flow remains a valid presentation-boundary example, but it is
not the first user-facing cross-language feature. The practical first bridge is
an in-game terminal/console surface based on the same generated service
discipline. It must have a Java-local implementation backed by a bounded
virtual filesystem and safe deterministic commands, so mounted editing and a
useful terminal remain available without Rust. When the optional endpoint is
available, Java uses the same contract as a Vox client and Rust may provide
Teamy Studio's process-backed terminal engine, VT state, replay, and semantic
symbol/handle metadata.

The terminal panel, input routing, layout, theme, bounds, accessibility and
fallback behavior remain Java-owned. Rust supplies portable terminal state and
semantic intent only; it never supplies Minecraft panels, widgets, renderers or
layout instructions. Endpoint discovery, capability negotiation, cancellation,
disconnect, malformed-frame handling and puppet-visible Java-local versus
Rust-connected states are part of the first terminal slice. The detailed
contract, phase ordering and Teamy Studio references live in [Vox Terminal
Bridge and Graceful Degradation Plan](../tasks/vox%20terminal%20bridge%20and%20graceful%20degradation%20plan.md).

## Authentication and authority

The narrow first security model is:

- bind an ephemeral loopback endpoint;
- generate a random per-session capability of at least 256 bits;
- pass endpoint and capability only to the owned helper;
- verify the capability as a claim through connection/lane policy;
- redact it from normal logs and persistent configuration;
- bound frames, queues, concurrent requests and time;
- accept structured requests from an exact allowlist;
- do not accept arbitrary Brigadier command strings;
- do not expose dynamic action registration after the SFM action registry has
  frozen; and
- close the connection and owned helper when the Minecraft client shuts down.

Loopback is a routing restriction, not authentication.

## Packaging research

The SFM schema-v3 dependency lock currently records:

| Minecraft version | ANTLR lockfile treatment |
| --- | --- |
| 1.19.2 | `org.antlr:antlr4:4.9.1` in `codegen` scope only |
| 1.20.4 | `org.antlr:antlr4:4.13.1` in `codegen` scope only |
| 1.21.1 | `org.antlr:antlr4:4.13.1` in `codegen` scope only |
| 26.1.2 | generator plus `antlr4-runtime:4.13.1` in `bundle` scope |

`gradle/dependencies-from-lock.gradle` maps `bundle` to `jarJar`. However,
`gradle/jar-jar.gradle` currently selects the `jarJar` output as the published
artifact only for Minecraft 26.1.2. Earlier branches select the ordinary `jar`
task even though their loader dependency graphs contain JarJar infrastructure.
That is promising evidence, not proof that SFM can publish and load a nested
library on those versions.

The packaging research track must determine, for at least 1.19.2, 1.20.4,
1.21.1 and 26.1.2:

- whether the applied ForgeGradle/NeoGradle plugin exposes `jarJar` configuration
  and task support;
- the required nested-JAR metadata and version-range behavior;
- whether the produced development, reobfuscated and published artifacts differ;
- whether the nested library is present in the actual selected publication;
- classloader visibility and duplicate-library conflict behavior;
- whether relocation/shading is needed;
- how the schema-v3 lockfile should declare compile/runtime/bundle scopes; and
- whether the mod launches in a clean isolated instance without the dependency
  installed separately.

The track must use `sfm-propagate-changes` commands rather than invoking Gradle
directly. It should produce a report, reproducible commands, inspected artifact
contents and a minimal harmless probe library before changing the production
Vox dependency.

Preferred packaging order:

1. one small, low- or zero-dependency Java 17 Phon/Vox artifact;
2. loader-native Jar-in-Jar driven by the SFM lockfile;
3. shaded/relocated classes in the SFM artifact if native nesting is not
   consistently available; and
4. source vendoring only as a final fallback.

Generated service bindings may reasonably live in SFM source. Vendoring the
entire Phon/Vox runtime would make upstream maintenance, conformance and
security review harder.

Vox Java is not accepted for SFM until a published 1.19.2 mod works in a clean
instance without a separately installed Vox JAR.

## Delegation and merge order

The coordinator first synchronizes and publishes the maintained Facet fork,
then records a Java 17 contract/specification checkpoint on integration branch
`teamy/vox-java`. Every worktree is created separately from the exact pushed
SHA and verified before an agent is attached.

The proposed Facet worktrees are:

| Owner | Branch | Worktree |
| --- | --- | --- |
| Integration/coordinator | `teamy/vox-java` | `G:\Programming\Repos\facet-worktrees\vox-java` |
| Phon Java | `teamy/vox-java-phon` | `G:\Programming\Repos\facet-worktrees\vox-java-phon` |
| Java generators | `teamy/vox-java-codegen` | `G:\Programming\Repos\facet-worktrees\vox-java-codegen` |
| Vox Java runtime | `teamy/vox-java-runtime` | `G:\Programming\Repos\facet-worktrees\vox-java-runtime` |

The SFM packaging research is independent of the final artifact API:

| Owner | Branch | Worktree |
| --- | --- | --- |
| Jar-in-Jar research | `feat/1.19.2/vox-packaging-research` | `D:\Repos\Minecraft\SFM\worktrees\1.19.2-vox-packaging-research` |

Recommended waves:

1. coordinator synchronizes Facet and freezes the Java public/runtime/codegen
   interfaces and supported subset;
2. Phon Java, generator snapshots and Vox runtime scaffolding proceed against
   that checkpoint, while packaging research may run independently;
3. integrate Phon, then generators, then the connection/runtime and hosted
   subject;
4. run cross-language conformance and freeze a publishable artifact;
5. implement and verify SFM artifact bundling;
6. create `feat/1.19.2/vox-bridge` at
   `D:\Repos\Minecraft\SFM\worktrees\1.19.2-vox-bridge`;
7. implement the colour-intent/action bridge and combined visual report; and
8. only then consider channels, generalized shaped forms, generated Minecraft
   packets or coordinated OS-window behavior.

Subagents do not edit the canonical SFM task plan. Facet agents may update
Facet-owned specification and technical documentation required by their code.
The coordinator owns integration, pushes, cross-repository revision selection,
plan status and final evidence.
