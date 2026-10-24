Independent metadata-proposal review — 2026-10-24.

Reviewed repository HEAD `704ddb5485173093cad42c07975fa5aabfc247ed`, the native resolver and test-root traversal source, the locked Dev dependency declarations, cached embedded POMs, all 22 scratch POMs and their four fetch manifests, `receipts.json`, the proposed catalog, precondition receipt, README, admission receipt and aggregate-run evidence. No cache/catalog/source edits, network requests, builds, test runs, installs, pushes or merges were performed by this reviewer. This scratch review is the only reviewer-written artifact for this task.

**Outcome:** no remaining scope, path, hash, size, chain or catalog-preservation defect found in the bounded proposal. This review does not authorize admission.

Verified independently:

- There are exactly 22 distinct candidate POM coordinates and canonical relative paths, totaling 235,353 bytes. Every local file matches its recorded SHA-256 and byte count. Every receipt has HTTP 200/exit 0 and an exact HTTPS Maven Central URL matching its coordinate-derived path. No executable artifact is included in the proposal.
- The chains trace to the declared unit-test roots and their parent/BOM/runtime dependencies. The 12 own-POM candidates correspond to already-locked artifact versions; the other 10 are parent/BOM metadata. Third-party test-only dependencies, build plugins and unused JUnit BOM entries are not added to the candidate catalog.
- Two own-POM candidates, checker-qual and junit-jupiter-api, have cached JARs without the expected embedded POM. Ten other own-POM candidates have no JAR in their canonical cache path at review time. Their receipts correctly state that a subsequently restored locked JAR might provide embedded metadata; absence of that fallback has not been established for those ten.
- The proposed catalog has exactly 23 unique entries: the unchanged approved JavaParser parent plus the 22 candidates. All proposed pin fields match the receipts. The live catalog remains one entry, SHA-256 `bd9ce82e5f34a251b472178cea292d3739faaf1595682e9371cdf124dd2b05fd`, matching the recorded precondition. The admitted JavaParser POM matches its approved hash/size, and all 22 candidate cache paths remain absent.
- The misleading `runtimeDependencies` receipt field was renamed to `declaredDependencies`. Raw third-party test dependencies are now accurately labeled; they were never included in the proposed catalog.

**Execution evidence versus source evidence:** aggregate.stderr records the unfiltered native run stopping before JUnit at `com.google.errorprone:error_prone_parent:2.36.0@pom`. That is the only new runner-confirmed metadata blocker. The same log records restoration of the already-locked `javaparser-core:3.26.4` JAR from its frozen URL with native verification of BLAKE3 `1055b7a25b9cf1274901059912a1b7d455197a9b`; it is not a dependency-version change. The other 21 metadata proposals are supported by inspected source/POM chains, not by 21 subsequent runner failures.

The resolver intentionally reads pinned cache metadata or frozen embedded POMs; it does not fetch missing parents silently. Admission must preserve or reconcile intervening catalog changes against the recorded precondition. User authorization currently covers only the already-admitted JavaParser parent. Admission of these 22 POMs and the expanded catalog is **pending separate approval**, not completed or denied. No claim is made that this proposal guarantees the full test closure or aggregate tests will pass; later metadata, artifact, resolver or test failures remain possible.

Reviewed artifact bindings:

- `proposed-bundle/receipts.json`: SHA-256 `06fe2be27f9a6818dd4ec7e0c511255412d139af5b19f8f14e289ec605813516`.
- `proposed-bundle/proposed-catalog-NOT-APPLIED.json`: SHA-256 `f01c30b9da8484b0c47767a5f86f99e071b0deb25e0c2bad6c9b1f27b40cfd80`.
- Exact coordinate/URL/hash/size/chain table: `proposed-bundle/README.md`; execution result and exact command: `result.json`.
