# Consumer API handoff

Package: `ca.teamdman.sfm.client.search.nucleo` (Java 17, no runtime dependencies).

Authored source root:
`platform/minecraft/core-liquid-template/src/main/java/ca/teamdman/sfm/client/search/nucleo/`

| File | Lines | UTF-8 bytes | Role |
| --- | ---: | ---: | --- |
| `NucleoMatcher.java` | 341 | 15,822 | Public matcher/options/results |
| `NucleoSearch.java` | 70 | 3,672 | Public generic batch ranking/cancellation adapter |
| `NucleoUnicode.java` | 360 | 46,956 | Required package-private frozen tables |
| Total | 771 | 66,450 | All three compile together |

Public signature synopsis (implementations omitted):

```java
// NucleoMatcher members
public static final String UPSTREAM_REVISION; // 8c16d47cdfa9607d3e44df5f81c635c6f43c65ee
public static final int MAX_QUERY_CODEPOINTS; // 2048
public static final int MAX_CANDIDATE_CODEPOINTS; // 65535
public enum CaseMode { RESPECT, IGNORE, SMART }
public enum Normalization { NEVER, SMART }
public enum Boundaries { DEFAULT, PATHS }
public record Options(CaseMode caseMode, Normalization normalization,
                      Boundaries boundaries, boolean preferPrefix) {
    public static final Options DEFAULT; // SMART, SMART, DEFAULT, false
}
public record Span(int start, int end) {} // half-open original UTF-16 offsets
public record Match(int score, List<Integer> codePointPositions,
                    List<Span> utf16Spans) {}
public static final class Candidate {
    public Candidate(String text);
    public String text();
}
public NucleoMatcher(String query);
public NucleoMatcher(String query, Options options);
public Match match(String candidate); // null means absent
public Match match(Candidate candidate); // null means absent
public int score(Candidate candidate); // -1 absent; otherwise higher is better

// NucleoSearch<T> members
public record Entry<T>(T value, NucleoMatcher.Candidate candidate, int priority) {
    public Entry(T value, String displayText); // priority 0
}
public record Result<T>(Entry<T> entry, int inputIndex, NucleoMatcher.Match match) {}
public NucleoSearch(List<Entry<T>> eligibleEntries);
public int size();
public List<Result<T>> search(String query, NucleoMatcher.Options options,
                             int limit, BooleanSupplier cancelled);
```

All result lists are immutable. Candidate preparation preserves the original
display string and is reusable across workers. Each matcher owns mutable scratch
and must belong to one worker; each `NucleoSearch.search` creates its own matcher.
It preserves payload object identity. Entry priority sorts ascending, then score
descending, then original input index ascending; it never admits a nonmatch just
because its priority is high. Empty query matches all at score zero, preserving
input order within each priority. Cancellation or thread interruption throws
`CancellationException`, with no partial results returned.

For Explorer filtering, retain its tree ordering and match only the exact field
being displayed/highlighted. This minimal code is an integration sketch; the
consumer owns its row type and context/eligibility checks:

```java
var options = new NucleoMatcher.Options(
    matchCase ? NucleoMatcher.CaseMode.RESPECT : NucleoMatcher.CaseMode.IGNORE,
    NucleoMatcher.Normalization.SMART,
    NucleoMatcher.Boundaries.DEFAULT,
    false);
var matcher = new NucleoMatcher(query, options); // worker-local
for (var row : alreadyEligibleRowsInTreeOrder) {
    var hit = matcher.match(row.displayText());
    if (hit != null) {
        visibleRows.add(row);             // retain original tree order and identity
        highlights.put(row.id(), hit.utf16Spans()); // same displayText, no normalization
    }
}
```

For an explicitly relevance-ranked picker, precompute eligible entries once
per context/registry snapshot, then score on a worker:

```java
var entries = eligibleActions.stream()
    .map(a -> new NucleoSearch.Entry<>(a, a.getComponent().getString()))
    .toList();
var snapshot = new NucleoSearch<>(entries);
var rows = snapshot.search(query, NucleoMatcher.Options.DEFAULT, 50,
    () -> currentGeneration.get() != capturedGeneration);
// UI thread: recheck generation/document/caret/active surface/snapshot identity.
// Render rows using match().utf16Spans(); activate entry().value(), unchanged.
```

The consumer must retain its own input budgets and reject unsupported overlong
or malformed UTF-16 values rather than silently truncating. This is a literal
subsequence fuzzy matcher, not edit-distance typo matching, whole-word policy,
regex, parsed Nucleo query syntax or grapheme segmentation. Keep Explorer
substring/default behavior and highlight toggles, palette eligibility/history
policies and autocomplete insertion actions under their current owners. The
full contract, upstream quirks and integration constraints are in `README.md`.

Upstream remains pinned to `8c16d47cdfa9607d3e44df5f81c635c6f43c65ee`.
Retain MPL-2.0 source attribution and all three authored
`src/main/resources/META-INF/licenses/nucleo-*` notices when integrating.
