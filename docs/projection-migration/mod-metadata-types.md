# Mod metadata type contracts

Reviewed: 2026-11-15

Status: The eight domain fields and SFM witnesses are implemented and qualified
by [plan item 2.1g](plan.md): the policy passes all four Rust packages, library
coverage includes all 88 parser/domain tests, and fresh CLI metadata checks pass.
Full repository qualification remains red on existing lint/runner debt. The 2.1f
124-test parser result is historical.

## Keep platform requirements and SFM choices distinct

A mod ID is a loader identifier, a GitHub username is an account identifier, and a
Maven group is an artifact coordinate. They need different constructors. The first
properties pass remains a dictionary of strings. Facet's second stage invokes
the domain constructors through String proxies or the author-list field proxy.

The generic model accepts other identities within SFM's metadata policies. Those
policies include GitHub-only authors and issue trackers, strict SemVer, and
lowercase ASCII group segments. It is not a schema for every loader-supported
metadata format.

### Mod ID

Forge's 1.19.x implementation enforces `^[a-z][a-z0-9_]{1,63}$`: 2-64 ASCII
characters, a lowercase initial letter, then lowercase letters, digits or
underscores. Current FancyModLoader main also permits dot-separated segments.
That newer allowance does not establish compatibility with SFM's oldest targets.
Use the older rule as the portable SFM `ModId` contract; a target-specific broader
identifier would need a separate policy. Resource namespaces have a different,
broader character set and must not supply the mod-ID validator.
[Forge implementation](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.19.x/fmlloader/src/main/java/net/minecraftforge/fml/loading/moddiscovery/ModInfo.java),
[current NeoForge implementation](https://raw.githubusercontent.com/neoforged/FancyModLoader/main/loader/src/main/java/net/neoforged/fml/loading/moddiscovery/ModInfo.java).

`sfm`, `a1` and `mod_name` fit the portable syntax. `SFM`, `1mod`, `my-mod`,
`my.mod`, `my mod` and a one-character identifier do not. The particular
ID `sfm` belongs to `SfmModId`; lexical `ModId` admits the valid `other`, `another`
and `decoy` values used by identity-guard tests. The checked SFM document requires
the concrete witness before catalog release parsing.

### GitHub username

Ordinary public usernames use ASCII letters/digits and single internal hyphens.
A name cannot begin/end with a hyphen or contain consecutive hyphens. GitHub's
account documentation specifies a 39-character limit. Preserve the original
presentation spelling. Syntax does not prove availability or account existence.
[GitHub signup](https://github.com/signup),
[documented account limits](https://docs.github.com/en/enterprise-server%403.19/admin/managing-iam/iam-configuration-reference/username-considerations-for-external-authentication).

The constructor is scoped to ordinary usernames. Enterprise Managed
Users can include an underscore and enterprise suffix; GitHub also models bot
actors separately. Do not apply a normal-user parser indiscriminately to every
API actor login. A leading `@` is mention syntax, not part of the stored username.
[Managed-user naming](https://docs.github.com/en/enterprise-cloud%40latest/admin/managing-iam/iam-configuration-reference/username-considerations-for-external-authentication),
[GitHub bot actors](https://docs.github.com/en/graphql/reference/apps#bot).

### Implemented metadata rules

| Field | Platform contract | Implemented SFM domain policy |
| --- | --- | --- |
| `ModName` | Loader display text. | Meaningful nonempty Unicode text; reject CR, LF and NUL. Preserve quotes and surrounding spaces. The single-line rule is SFM policy. |
| `ModSoftwareLicense` | Required loader text; SPDX identifiers or license links are recommended. | `SoftwareLicense::Mpl2_0` recognizes the existing label and MPL aliases. Other terms use a validated `SoftwareLicenseDeclaration`; this is not a full SPDX parser. |
| `ModVersion` | Loader version syntax is broader than strict SemVer. | `semver::Version` retains prerelease/build data. Numeric components use `u64`. Release admission remains a separate policy. |
| `ModIssueTracker` | A general issue-tracker URL. | A parsed HTTPS GitHub repository-issues URL, with explicit host, owner, repository and endpoint rules. |
| `ModGroupId` | A Maven group coordinate, conventionally following Java package naming. | One `JavaPackageName` containing a variable-length identifier list. Original segments must match `[a-z][a-z0-9_]*` and pass Java identifier parsing. |
| `ModAuthors` | Loader author display text, including names with spaces. | An ordered, nonempty list of ordinary GitHub usernames. `ModAuthorsCsvProxy` owns comma-separated wire text. |
| `ModDescription` | Loader description text, including multiline text. | Meaningful nonempty Unicode text; reject NUL. Preserve quotes, spaces and decoded newlines. Encoders own TOML/JSON quoting. |

The loader's text/URL contracts and SPDX recommendation are described in
[NeoForge mod-file documentation](https://docs.neoforged.net/docs/gettingstarted/modfiles/).
Its [versioning guidance](https://docs.neoforged.net/docs/gettingstarted/versioning/#mods)
also distinguishes recommendations from enforced version formats. SFM's strict
version parser follows the [SemVer specification](https://semver.org/spec/v2.0.0.html),
including numeric leading-zero rules, prerelease identifiers and build metadata.

Java identifiers permit Unicode, with special rules for identifier starts and
parts. They cannot be reserved keywords or boolean/null literals; `_` alone is not
an identifier in modern Java. A lowercase ASCII-only group policy is narrower
than Java syntax and must be named as such. Maven recommends reverse-domain
package-style groups; parsing cannot prove ownership of that domain.
[Maven naming guidance](https://maven.apache.org/guides/mini/guide-naming-conventions.html),
[Java identifier specification](https://docs.oracle.com/javase/specs/jls/se21/html/jls-3.html#jls-3.8).

`JavaIdentifier` uses Unicode categories from the locked regex tables (Unicode
16.0). It removes identifier-ignorable characters for identity and rejects
reserved keywords and boolean/null literals. It preserves case without Unicode
normalization. Older JDKs can differ for newly assigned characters. `ModGroupId`
checks its ASCII policy before the Java parser can remove ignorable characters.

`GitHubIssueTrackerUrl` uses `reqwest::Url`, an exact `github.com` host and the
`/owner/repository/issues` route. It rejects credentials, custom ports, query or
fragment state, raw percent-encoded paths, dot segments, backslashes and empty
authorities. HTTPS's explicit
default port is accepted by the URL parser. One optional trailing slash is
removed. Owners use `GitHubUsername`; repository names use 1 to 100 ASCII letters,
digits, underscores, dots or hyphens, excluding `.`/`..` and `.git`.
[GitHub repository creation rules](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository)
guide that narrower repository-name policy.

Parsing does not establish that an account or repository exists, issues are
enabled, or a reversed domain is owned. Those checks belong at an effect boundary.
GitHub-only issue tracking and GitHub-only authors are SFM choices, rather than
universal loader requirements. Username equality, ordering and hashing ignore
ASCII case while formatting preserves the supplied spelling.

Provider payloads need their own constraints. Modrinth's project API identifies
licenses by SPDX ID and exposes issue URLs separately. Project slugs, mod IDs,
project titles, version numbers and file display names are different fields;
do not apply one provider's limits to every mod metadata string.
[Modrinth project API](https://docs.modrinth.com/api/operations/createproject/).

## Representation

The implemented fields contain:

```rust,ignore
ModId(String)                          // Portable loader syntax
ModName(String)                        // Human display text
ModSoftwareLicense(SoftwareLicense)    // Parsed license identity
ModVersion(semver::Version)             // General SFM SemVer
ModIssueTracker(GitHubIssueTrackerUrl)  // Explicit SFM provider policy
ModGroupId(JavaPackageName)             // One dotted name
ModAuthors(Box<[GitHubUsername]>)      // Core list; CSV belongs to a proxy
ModDescription(String)                 // Decoded human text
```

`JavaPackageName` contains a variable-length identifier list. The domain fields
are private and their constructors own the parsing rules. Scalar wrappers use
`#[facet(opaque, proxy = String, traits(FromStr))]`; their `TryFrom<String>` calls
the same parser as `FromStr`. Facet deserialization therefore cannot populate
private storage with unchecked strings. `From<&Domain> for String` supplies the
wire form. `ModAuthors` contains parsed identities; its field proxy splits commas,
trims separator whitespace and rejects empty or quoted entries. It emits bare
commas while preserving author order.

`SoftwareLicense` has a known MPL 2.0 variant and a `Declared` variant containing a
private validated `SoftwareLicenseDeclaration`. Other declarations must be
nonempty, unpadded, single-line text without NUL. Their syntax does not establish
SPDX registration or legal validity. No SPDX parser dependency was added. Only
the known MPL variant exposes `Some("MPL-2.0")` through `spdx_id()`.

MPL identity is separate from the original wire label. Its accepted aliases are
`MPL-2.0`, `Mozilla Public License Version 2.0` and `Mozilla Public License 2.0`;
formatting emits `MPL-2.0`.
The repository's `LICENSE.txt` and every checked `mod_license` value say Mozilla
Public License Version 2.0; SPDX identifies that license as `MPL-2.0`.
[SPDX MPL 2.0 entry](https://spdx.org/licenses/MPL-2.0.html).

## Concrete SFM identities and checked documents

Seven unit structs provide the fixed SFM values: `SfmModId`, `SfmModName`,
`SfmModSoftwareLicense`, `SfmModIssueTracker`, `SfmModGroupId`, `SfmModAuthors` and
`SfmModDescription`. Each exposes the authored `VALUE`, converts into its generic
domain type, and supports checked conversion from borrowed or owned generic
values. All eight witness-to-generic conversions, including the exact numeric
version, use explicit `TryFrom` implementations and the generic type's checked
parser or constructor. Fixed constants pass the same validation as file input.
No conversion asserts parser success with `expect` or `unwrap`. The round-trip
tests require `eyre::Result<Generic>` so an unchecked `From` and its implicit
`TryFrom<Error = Infallible>` cannot replace this API silently.
Rustdoc links the generic parsing rules and
[SFM's authored properties](https://github.com/TeamDman/SuperFactoryManager/blob/d16af8c38/platform/minecraft/gradle.properties).

The ID, name, group and description require the fixed value. License checks the
MPL identity across its aliases. Issue tracking compares the same GitHub
owner/repository without ASCII case sensitivity after URL parsing. Authors must
contain exactly the single `TeamDman` identity, also ignoring ASCII case.

`SfmGradleDotPropertiesFileContent` checks those seven conversions and stores a
private generic document snapshot plus seven public zero-sized witness fields.
It exposes the generic snapshot only through an immutable `as_generic()` or
`AsRef` borrow. Consuming `From<SfmGradleDotPropertiesFileContent>` returns the
generic document for callers that need to change validated metadata. The private
snapshot prevents identity changes while retaining the checked SFM document.

Version remains a dynamic `ModVersion`, because different projections select
different releases. `require_version::<4, 35, 0>()` produces the zero-sized
`SfmModVersion<4, 35, 0>` witness only for exactly `4.35.0`, without prerelease or
build data. The witness converts to/from `ModVersion`; it proves that exact
version, rather than publication eligibility. This avoids an eight-parameter
generic document or a runtime string hidden inside a fixed-value type.

The catalog plan parses the checked SFM document before constructing
`CatalogReleaseProperties`. `CatalogReleaseVersion` still owns release-only
version admission; general `ModVersion` parsing retains development versions.

## Repository evidence and implementation acceptance

Inspected 31 authored properties files plus the retained flat project file:

| Key | Current data |
| --- | --- |
| `mod_id` | `sfm` |
| `mod_name` | `Super Factory Manager (SFM)` |
| `mod_license` | `Mozilla Public License Version 2.0` |
| `mod_version` | `4.34.0` in 21 files; `4.35.0` in 11 |
| `mod_issue_tracker` | `https://github.com/TeamDman/SuperFactoryManager/issues` |
| `mod_group_id` | `ca.teamdman.sfm` |
| `mod_authors` | `TeamDman` |
| `mod_description` | Existing two-line description, escaped in the source file |

The shared complete-properties fixture now uses the fixed SFM name, license,
issue tracker, group, author and description. Earlier placeholders included
`Fixture license`, `https://example.invalid/issues` and `Fixture author`; they
were replaced to exercise the adopted policies. Generic tests retain valid
alternate IDs and development versions. Separate SFM-document tests cover each
wrong but generically valid identity.

`Cargo.toml` now declares direct pinned `semver = "=1.0.27"` and
`regex = "=1.12.2"` dependencies using versions already present in the lockfile.
SemVer parsing uses the existing library; regex supplies Java Unicode categories.
`reqwest::Url` remains the parsed URL representation. Direct dependency-edge
changes also need consideration when reviewing whole-lock Forge preparation keys.

Current library coverage includes mod-ID boundary lengths/characters; ordinary
GitHub username syntax and list encoding; URL host/path confusion; Java identifier
and package rules; the legacy MPL label; SemVer prerelease/build cases; all authored
files; and SFM/release-selection guards. Encoder tests include quotes, Unicode and
newlines. Tests also exercise Facet String-proxy construction and zero-sized
witness conversions. All 88 parser/domain tests have exactly one current-binary
runner owner; the full runner retains its 11 recorded ownership failures. The
selected `sfm-4.35.0/mc-1.19.2` plan verified metadata with zero source writes or
network requests and no publication authorization. See plan item 2.1f for the
current binary identity and source/review-state preservation evidence.
