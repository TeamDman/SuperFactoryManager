# Parsing and identity owners

Reviewed 2026-11-12 after the metadata domain-type and concrete SFM identity
requests. Paths below are relative to `platform/cli/sfm-propagate-changes/src/`.
The metadata types, checked SFM document and no-unwrap conversion refinement are
implemented. [Plan item 2.1g](plan.md) records the current all-package policy checks,
2,576 unique library passes, 30 ignored tests and fresh CLI metadata evidence.
The repository gate remains red on existing lint/runner debt. Earlier parser
results below describe their respective checkpoints. See
[the metadata type review](mod-metadata-types.md) for the implemented policies and
source links. The later extraction candidates remain proposals.

## Gradle properties: structured model

`GradleDotPropertiesFileContent` now has 23 named fields. The raw-text tuple from
the first extraction was superseded by the structured-properties request.

| Fields | Parsed representation |
| --- | --- |
| Eight mod metadata fields | `ModId`, `ModName`, `ModSoftwareLicense`, `ModVersion`, `ModIssueTracker`, `ModGroupId`, `ModAuthors` and `ModDescription`. String proxies or the author-list field proxy invoke their constructors. |
| Minecraft/Neo version identifiers and JVM arguments | Named `String` fields, with values decoded from properties escapes. Version spelling and qualifiers are retained. |
| `org.gradle.daemon` | Required `bool`; dotted names map through `#[facet(rename = "...")]`. |
| Gradle debug, parallel and caching switches | `Option<bool>`; absent settings are not replaced with invented defaults. |
| Mapping channel, NeoGradle parchment settings, pack format | Version-specific options; pack format is `Option<u32>`. |
| Mapping version | `Option<MinecraftMappingVersion>` uses a field proxy; official, dated Parchment and cross-version Parchment remain distinct typed forms. |
| Minecraft, Neo and loader version ranges | `SemverVersionRange`, with `SemverVersionBound::{Included, Excluded, Unbounded}` endpoints. Exact `[1.21.1]` has equal included endpoints; `[21.0.0-beta,)` has no upper bound. |

Fifteen fields are common and required; eight fields are optional across the current
31 authored files. `RawGradlePropertiesDocument::deserialize` uses the existing
pinned Facet reflection API to populate fields by their wire names. This is the
second stage: scalar types, metadata proxies and `SemverVersionRange::from_str`
parse the raw strings. Unknown/duplicate keys, missing required fields and invalid
typed values fail construction. No JSON round-trip or manual field-name/type
switch is involved. Range parsing establishes structure;
it deliberately provides no version ordering, containment or union support. Its
Facet shape is opaque so reflection cannot bypass the parser to set private bounds;
Rust accessors expose the parsed endpoints.

## Metadata construction and SFM identity

Scalar metadata wrappers keep their storage private and use an opaque String
proxy for Facet. `TryFrom<String>` invokes the same associated parser as `FromStr`;
the reverse conversion supplies wire text. This keeps parsing rules effective
through both properties deserialization and other Facet formats.
`ModAuthorsCsvProxy` handles comma-separated usernames while `ModAuthors` contains
an ordered, nonempty list of parsed `GitHubUsername` values.

The generic document accepts other identities within SFM's chosen policies.
`ModId` uses the older portable Forge ASCII rule. Issue trackers and authors use
GitHub-specific types; `ModGroupId` requires lowercase ASCII package segments.
Generic `JavaIdentifier` and `JavaPackageName` permit Java Unicode syntax, with
documented Unicode-table limits. `ModVersion` uses strict SemVer and preserves
prerelease/build data. `SoftwareLicense` recognizes MPL 2.0 semantically and stores
other nonempty, unpadded, single-line declarations in a private validated type.
It does not implement the complete SPDX grammar or registry.

`SfmGradleDotPropertiesFileContent` checks seven fixed identity conversions. Its
generic document snapshot is private, and its seven public identity fields are
zero-sized unit witnesses. `as_generic()` and `AsRef` expose only an immutable
generic borrow. A consuming conversion returns the generic document when changes
are needed, without retaining the checked wrapper. The type therefore does not
need eight generic parameters or duplicate runtime copies of fixed strings.

The SFM version stays dynamic. `require_version::<MAJOR, MINOR, PATCH>()` returns
an exact `SfmModVersion<MAJOR, MINOR, PATCH>` zero-sized witness after checking all
three numbers and rejecting prerelease/build data. This proof remains separate
from release selection and publication eligibility. The catalog plan now parses
the checked SFM document; `CatalogReleaseProperties` retains the release-only
version policy through `CatalogReleaseVersion`.

Rustdoc links platform rules and labels narrower SFM policies. The concrete
identity types also link the authored SFM properties. The
[metadata review](mod-metadata-types.md) records the sources, fixed values,
comparison rules and test coverage. The current binary assigns all 88 parser/domain
tests exactly one runner owner; the full runner retains its 11 recorded ownership
failures. The conversion refinement keeps
simple fixed-value `From` implementations in their generic type modules and uses
fallible `TryFrom` for authors, group and issue-tracker parsing. Direct `semver 1.0.27` and `regex 1.12.2` dependency edges use existing
locked versions; earlier checkpoints' unchanged-dependency statements apply only
to those checkpoints.

## First pass: string dictionary

The raw document owns
`BTreeMap<GradlePropertiesKey, GradlePropertiesValue>`. Keys and values contain
strings; the first pass handles file syntax and string escapes. It does not inspect
property names to choose booleans, numbers or ranges. Domain-invalid text such as
`minecraft_version_range=not-a-range` is valid raw string data. Facet conversion to
the named schema is the stage that rejects it.

`GradlePropertiesKey` owns literal ASCII key syntax, including dotted names.
`GradlePropertiesValue` owns decoded escapes. Duplicate keys fail before insertion,
including identical repeated values. Facet conversion also rejects two aliases
that address the same named field. The raw line parser returns an optional
key/value pair; it does not compute or return source spans.

The description's literal `\n` becomes a newline string value. ASCII delimiter
whitespace after `=` is skipped; escaped leading spaces and trailing spaces remain
data. Literal `key=value` spelling is the supported key syntax; escaped keys,
colon/whitespace separators, physical continuations and bare CR are rejected.
These rules describe the project format rather than all Java properties syntax.

`SemverVersionRange` owns bracketed range parsing, invoked through Facet and
`FromStr` for the three named range fields. It retains partial and four-component
version identifiers used by authored properties; the rename does not impose
strict three-component SemVer parsing or add version comparison.

## Source editing is a separate operation

`GradlePropertiesVersionEdit { source: String }` owns the explicit version rewrite.
It computes temporary source offsets while applying the edit and patches only the
existing value, preserving comments, order, escapes, Unicode and LF/CRLF/EOF bytes.
Unrelated repeated keys remain editable; a version edit requires exactly one
`mod_version`. Historical fragments need not satisfy the complete SFM schema.
Failed edits leave the original artifact intact.

The earlier `Range<usize>` fields were byte positions, not version intervals.
The 2.1d correction removes `GradleProperty`, `GradlePropertiesDocument` and the
persistent span map so this metadata is absent from first-pass dictionary parsing.
SFM identity now belongs to the checked SFM document and its witnesses. Release
version admission stays in `CatalogReleaseProperties` and the catalog plan.

Historical 2.1d validation passed 63 focused tests, including all 31 authored
property files and the raw-string/typed-conversion regression. All 27 parser/range
tests had one runner owner. The fresh CLI passed help/version and one selected metadata
plan; no all-projection rerun was needed. At that checkpoint the required gate
retained its 74 baseline Clippy errors with none added. Dependencies were unchanged.
See plan item 2.1d for source/binary evidence; 2.1c results describe the superseded
implementation.

## Composite fields use parsing proxies

The dated mapping value remains a concrete
`(chrono::NaiveDate, MinecraftVersion)` tuple. `MinecraftDateVersionPairProxy`
owns its calendar string codec. The full model field is
`Option<MinecraftMappingVersion>` with
`#[facet(default, opaque, proxy = MinecraftMappingVersionProxy)]`.

The user confirmed all authored forms should be retained:

- `Official(MinecraftVersion)` stores a version-only mapping such as `1.20.1`.
- `Parchment((NaiveDate, MinecraftVersion))` stores a date/source-version pair such
  as `2022.11.27-1.19.2`.
- `CrossVersionParchment { mappings: (NaiveDate, MinecraftVersion), target: MinecraftVersion }`
  retains source mappings and the separate target version.

For `1.19.3-2023.03.12-1.19.4`, the mapping pair is date `2023.03.12` plus source
Minecraft `1.19.3`; the target is `1.19.4`. The existing
`jar_build/engine_plan.rs::parchment_coordinate` uses the first version for the
mapping artifact coordinate. The typed representation preserves both meanings.

`MinecraftMappingVersionProxy` owns the supported wire forms and delegates the
calendar pair codec to `MinecraftDateVersionPairProxy`. A legacy two-part
version-first alias such as `1.19.2-2022.11.27` is accepted and formats date-first.
The tuple stays inside the Parchment variants; the plain core enum needs no Facet
derive. Pinned Facet's opaque field proxy handles that without a dependency or
feature change. Generic properties deserialization enters the field proxy before
handling Option, while raw properties parsing continues to return only strings.

Historical 2.1e validation passed 73 focused tests, including all 31 authored
property files, and a fresh CLI metadata check for `sfm-4.35.0/mc-1.19.2`. All 37
parser/proxy tests had one runner owner. The full gate retained 74 baseline Clippy
errors with none added. Dependencies, authored properties and staged contents were
unchanged at that checkpoint. Plan item 2.1e records its source/binary identity and
local review route; earlier 2.1d evidence is historical.

## Remaining Gradle consumers need explicit policies

| Consumers | Current behavior to preserve or deliberately change |
| --- | --- |
| `source_projection/frozen_recipe_metadata.rs` | Finds version spans and proves all other bytes unchanged. Closest next consumer; preserve opaque-byte and version-token rules. |
| `source_projection/{project_layout,native_project_target,candidate_lock}.rs`, `cli/source/source_cli.rs` | Requires one named property with varying whitespace rules. A strict canonical accessor is not a drop-in replacement. |
| `cli/{github/github_cli,curseforge/curseforge_cli,modrinth/modrinth_cli,git/git_tag_cli,jar/jar_shared}.rs` | Legacy helpers take the first matching version. Their publication migration must choose an ambiguity policy explicitly. |
| `jar_build/{engine_mappings_compare_artifacts,retained_compile_inputs}.rs` | Map construction lets the last duplicate win. Move these together with compatibility fixtures. |
| `source_projection/released_native_inputs.rs` | Whole-map parsing rejects duplicates for every key, which is stronger than selected-key access. Retain that rule. |

Do not add a loose/strict boolean to hide these differences. Use named operations
or separate validated views when their migration is authorized. TOML assignments,
JDK release files and Gradle scripts need their own format owners.

## Next candidates

Future file-content types should expose parsed structure. Keep source text separately
when exact bytes are needed for edits or cache identities.

| Priority / proposed type | Evidence and responsibility |
| --- | --- |
| 1. `SfmlChangelogFileContent` | `source_projection/catalog_release/catalog_release_changelog.rs`, `cli/{github/github_cli,curseforge/curseforge_cli,modrinth/modrinth_cli}.rs` repeat UTF-8, newline and release-heading handling. Keep `CatalogReleaseChangelog` as the selected nonempty section and digest. Preserve historical preview-heading policy explicitly. |
| 2. `CatalogProjectionKey` | `source_projection/{projection_catalog,provenance}.rs` and `catalog_release/catalog_release_request.rs` repeatedly validate portable exact keys. Construction should prove syntax only; catalog membership and filesystem ownership remain separate. Reuse `CatalogProjectionIdentity` for a resolved context. |
| 3. `Sha256Digest` | `jar_build/local_dependency_key.rs`, `source_projection/provenance.rs` and `cli/source/release_modrinth_cli.rs` pass/validate hexadecimal strings independently. A private 32-byte value can own parsing, formatting and equality while preserving the existing hexadecimal wire format. Existing `jar_build/hash.rs::ContentHash` stores 20 bytes for SHA-1/truncated BLAKE3 and cannot represent full SHA-256 unchanged. A digest does not prove provenance or authorization. |
| 4. `SupportedMinecraftTarget` | `source_projection/projection_catalog.rs` owns the target/upstream table; `core_inputs.rs` repeats lookup. A supported target should expose exact matrix ID and upstream spelling, preserving `1.21.0` versus `1.21`. Reuse the table; numeric `MinecraftVersion` equality erases trailing-zero distinctions. Keep loader/build metadata separate. |
| 5. `ModDescriptorFileContent` | `jar_build/engine.rs::metadata_assignment` and `cli/dependency/dependency_artifact_accept_cli.rs::metadata_assignment` repeat rendered TOML assignment parsing. Own quoting, duplicate-field policy and field lookup; do not treat TOML as Gradle properties. |

Two larger format extractions also merit later review: `JdkReleaseFileContent`
(`jdk_preferences.rs`, `jar_build/vox_producer_contract.rs`) and
`TsrgMappingsFileContent` (`jar_build/{engine_mappings_compare_artifacts,engine_execute}.rs`,
`jar_build/source_identifier_remapper.rs`). Keep their quotation/dialect rules and exact
cache-hashed bytes; retain existing mapping models instead of replacing them.

The next small extraction is `SfmlChangelogFileContent`. Review its legacy versus
exact-heading behavior before moving the parser.
