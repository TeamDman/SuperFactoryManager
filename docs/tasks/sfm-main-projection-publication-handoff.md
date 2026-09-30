# Publish one reviewed projection package target

Status: reviewed procedural handoff, 30 September 2026. This is a manual
standard-provider handoff for SP-08, not release approval. No tag, upload or
publication was performed while preparing it. The provider write examples have
not been tested against a real release.

Start with an approved, complete portable package from `source release-package`
and a separately reviewed post-promotion commit. Use the installed source
preflights, Git, GitHub CLI and the providers' standard APIs or author dashboards.
No projection-native publisher is implemented or required by this procedure.
Its future design remains separate work.

Do not use version-branch refs, shared JAR directories, filename scans,
`github release now`, `modrinth release now` or `curseforge release now`.
Choose each file from the verified target plan, never from a glob. Repeat this
procedure only for targets included in the human's approval.

If any step reports insufficient disk space, stop and wait for the user. Do not
clean up or retry. On an ambiguous provider response, stop without repeating the
write. A timeout does not prove that the provider rejected it.

## Review the inputs and authority

Use the [contributor guide](../source-projection-contributor-guide.md#advancing-the-checked-in-release-preset)
for packaging and guarded promotion. Compatibility approval and promotion must
already be complete. This document does not grant either.

Record these inputs in a private release record outside both the checkout and
package. Repository-relative paths below identify contracts, not machine paths.
Every angle-bracket value is a placeholder to replace with an already reviewed
input. Do not publish credentials, local paths or unredacted notes.

| Input | Meaning |
| --- | --- |
| `<promoted-checkout>` | Quiescent, clean Git checkout at reviewed release commit R; varies by operator |
| `<complete-package>` | Quiescent ten-JAR package, including `release-package.json` and its inventory; varies by operator |
| `<reviewed-notes-file>` | Exact approved UTF-8 changelog outside the package and checkout; varies by operator |
| B and R | B is the package's full `source_commit`; R is the later reviewed post-promotion commit, not an old version branch |
| Provider identities | Approved GitHub `owner/repo`, canonical Modrinth project ID and positive CurseForge project ID |

Record the independently reviewed `sha256:<digest>` of the completion manifest
and notes. Do not replace either digest with a newly computed value merely to
make a failed check pass. Record the existing explicit 1.20.1 Modrinth policy:
`dual-forge-neoforge` or `neoforge-only`. Do not infer it from the JAR's loader.

The selected tag must be `<mod-version>-<target-id>` and resolve to R. Target
`1.21.0` uses tag suffix `1.21.0`, while its Minecraft metadata and filename
marker use `1.21`. All selected target tags may point to the same reviewed R.

Get a human's explicit approval naming the target, exact file/hash, R, tag,
provider identities and metadata. Distinguish these actions in that approval:

1. Credential access and read-only remote ownership, duplicate and tag checks.
2. Local tag creation and one non-force push, if the reviewed tag is absent.
3. GitHub draft creation and asset upload, then publishing that draft.
4. Modrinth version creation, which can make the version publicly visible.
5. CurseForge file submission and visibility after provider approval.

One approval may name all intended actions. A local preflight's success does
not authorise any of them. Do not overwrite, delete, amend or force-push an
existing remote object under an approval to create a new release.

The Modrinth upload contract requires a canonical project ID, not a slug. If
the reviewed input starts as a slug, obtain remote-read authority and resolve it
with `GET https://api.modrinth.com/v2/project/<slug>` before approving the final
target plan. Use the returned ID as the input below, rerun the offline checks,
and independently review the new request JSON and digest. Do not replace
`project_id` inside already reviewed or hashed JSON.

## Run the offline package and target checks

Use PowerShell 7 for the examples. Confirm tool freshness with `--version` and
the [operational readiness guidance](goal%20execution%20and%20testing%20readiness%20guidelines.md).
If the required source command is missing, stop and obtain the reviewed tool;
do not substitute a legacy uploader. These checks need no provider credential.

```powershell
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = (Resolve-Path -LiteralPath '<promoted-checkout>').Path
$packageRoot = (Resolve-Path -LiteralPath '<complete-package>').Path
$notesFile = (Resolve-Path -LiteralPath '<reviewed-notes-file>').Path
$completionHash = 'sha256:<independently-reviewed-completion-digest>'
$notesHash = 'sha256:<independently-reviewed-notes-digest>'
$sourceCommit = '<full-package-source-commit-B>'
$releaseCommit = '<full-reviewed-post-promotion-commit-R>'
$targetId = '<exact-reviewed-target-id>'
$tag = '<reviewed-mod-version>-<exact-reviewed-target-id>'
$githubRepo = '<owner>/<repository>'
$modrinthProject = '<reviewed-canonical-project-id>'
$curseforgeProject = [UInt64]'<reviewed-numeric-project-id>'
$loaderPolicy = '<reviewed-1.20.1-loader-policy>'

sfm-propagate-changes --version
sfm-propagate-changes --output-format json source release-package-verify `
  --package-root $packageRoot --completion-manifest-sha256 $completionHash
if ($LASTEXITCODE -ne 0) { throw 'Complete package verification failed' }

$targetArgs = @(
  '--package-root', $packageRoot,
  '--completion-manifest-sha256', $completionHash,
  '--reviewed-source-commit', $sourceCommit,
  '--reviewed-tag', $tag,
  '--github-repo', $githubRepo,
  '--modrinth-project', $modrinthProject,
  '--curseforge-project', "$curseforgeProject",
  '--changelog-file', $notesFile,
  '--changelog-sha256', $notesHash,
  '--modrinth-1201-loader-policy', $loaderPolicy,
  '--repo-root', $repoRoot,
  '--target-id', $targetId,
  '--reviewed-release-commit', $releaseCommit
)
function Read-TargetPlan {
  $json = & sfm-propagate-changes --output-format json source release-target-plan @targetArgs
  if ($LASTEXITCODE -ne 0) { throw 'Fresh complete-package/target preflight failed' }
  $result = ($json -join "`n") | ConvertFrom-Json
  if ($result.schema -cne 'sfm:source_release_target_plan@1' -or
      $result.target.target_id -cne $targetId -or
      $result.reviewed_release_commit -cne $releaseCommit -or
      $result.package_source_commit -cne $sourceCommit -or
      $result.local_tag -cne $tag -or $result.publication_authorized -ne $false) {
    throw 'Unexpected target-plan identity or authority'
  }
  return $result
}
function Get-BytesSha256([byte[]] $bytes) {
  $hasher = [Security.Cryptography.SHA256]::Create()
  try { 'sha256:' + [Convert]::ToHexString($hasher.ComputeHash($bytes)).ToLowerInvariant() }
  finally { $hasher.Dispose() }
}
function Invoke-ReviewedGit([string[]] $gitArgs) {
  if (@(Get-ChildItem Env: | Where-Object { $_.Name -imatch '^GIT_' }).Count -ne 0) {
    throw 'Inherited GIT_* overrides: use a fresh approved session before manual Git operations'
  }
  & git @gitArgs
  if ($LASTEXITCODE -ne 0) { throw 'Git command failed; inspect state without automatically retrying' }
}
function Assert-PublicGitHubHost {
  $githubHostOverride = [Environment]::GetEnvironmentVariable('GH_HOST')
  if (-not [string]::IsNullOrEmpty($githubHostOverride) -and
      $githubHostOverride -cne 'github.com') {
    throw 'Conflicting GH_HOST: use an approved public-GitHub session'
  }
}
$plan = Read-TargetPlan
$reviewedPlan = $plan
```

Require `verified_target_count=10` from the package verifier. In the target
plan, require all four local checks to be true: `current_head_checked`,
`authored_tree_unchanged_outside_generated_roots`,
`selected_root_provenance_checked` and `selected_root_commit_tree_checked`.
The remote checks and `publication_authorized` remain false by design.

The preflight requires B to be an ancestor of R, with no committed changes
outside `platform/minecraft/mc-version/`. It checks the selected project's
complete committed ownership and current bytes. Later notes or tooling edits
outside those roots can invalidate this package-to-R relationship. Review and
package again; do not weaken the check or tag B instead.

Review and retain the exact report privately. Its Modrinth JSON includes the
notes verbatim. Inspect it before sharing. Record the target's `file_name`,
`sha256`, Minecraft version, loader, display name, provider metadata, notes hash
and `modrinth_request_metadata_sha256`. An absent local tag is allowed by this
read-only check; it does not create that tag.

Use `Invoke-ReviewedGit` for every manual Git command below. It rejects inherited
`GIT_*` variables case-insensitively on each invocation, without changing the
session environment. `git -C` alone does not neutralise repository, worktree or
namespace overrides. All GitHub CLI operations below explicitly select public
`github.com`; `Assert-PublicGitHubHost` also rejects a conflicting ambient host.

Use this fresh-byte check immediately before each upload. The package and
checkout must remain unchanged throughout. The manual workflow does not pin
all filesystem handles against hostile concurrent replacement.

```powershell
function Read-OwnedSelectedJar {
  $fresh = Read-TargetPlan # Rehashes the entire ten-JAR package again.
  if ($fresh.target.file_name -cne $reviewedPlan.target.file_name -or
      $fresh.target.sha256 -cne $reviewedPlan.target.sha256 -or
      $fresh.modrinth_request_metadata_sha256 -cne $reviewedPlan.modrinth_request_metadata_sha256) {
    throw 'Selected asset or reviewed metadata changed'
  }
  $script:plan = $fresh
  $bytes = [IO.File]::ReadAllBytes((Join-Path $packageRoot $fresh.target.file_name))
  if ((Get-BytesSha256 $bytes) -cne $fresh.target.sha256) { throw 'Selected JAR bytes changed' }
  return ,$bytes
}
```

Do not put generated reports, request JSON or downloaded receipts in the package.
Its verifier rejects extra files. Store them in the private release record.

## Check remote ownership and duplicates

Run these checks only after credential and remote-read authority. Check them
again just before each corresponding write. Pagination, hidden drafts, pending
files and archived versions matter; a public search result is not a complete
duplicate check. Authentication, permission, rate-limit and transport failures
are not evidence that an object is absent.

For GitHub, use the approved account and explicit repository:

```powershell
Assert-PublicGitHubHost
gh auth status --hostname github.com
gh api --hostname github.com user --jq '{id,login}'
gh api --hostname github.com "repos/$githubRepo" --jq '{id,full_name,permissions}'
gh api --hostname github.com "repos/$githubRepo/releases/tags/$tag"
Invoke-ReviewedGit -gitArgs @('ls-remote', "https://github.com/$githubRepo.git", "refs/tags/$tag", "refs/tags/$tag^{}")
```

Record the authenticated account, repository ID and release/tag write
permission. Only a confirmed release API 404 establishes that this tag has no
release. Any existing release, including a draft, stops new creation. Inspect
its assets and receipt under a separately reviewed recovery decision.

For Modrinth, log into the approved account, open the exact project's dashboard,
and confirm permission to create versions. Fetch
`GET https://api.modrinth.com/v2/project/<reviewed-canonical-project-id>` and
require the returned `id` to equal `$modrinthProject` and the reviewed JSON's
`project_id`. Record that ID and the approved account's project membership. Query
`GET /v2/project/<canonical-project-id>/version` with the authorised credential
and inspect the dashboard's drafts too. Stop if any version has the same
`version_number` and selected Minecraft version, or contains the selected
filename or file hash. Different loader labels do not make a duplicate safe.
Use the [project](https://docs.modrinth.com/api/operations/getproject/) and
[version-list](https://docs.modrinth.com/api/operations/getprojectversions/) contracts.

For CurseForge, open `https://authors.curseforge.com/#/projects/<project-id>/files`
under the approved account. Confirm the project identity and upload permission.
Inspect all pages and statuses for the exact filename, display name and selected
Minecraft version, including files awaiting review. Record any existing IDs;
do not upload again, amend them or interpret a public-list omission as absence.

An exact existing file may be a completed earlier action, not a new release to
create. Compare its remote metadata and downloaded bytes, then ask for recovery
direction. Never silently upsert or use an overwrite flag.

## Bind and push the exact target tag

The local preflight reports `absent` or `matches-reviewed-release-commit`.
If absent, create the tag only with explicit tag-creation authority:

```powershell
$plan = Read-TargetPlan
if ($plan.local_tag_state -ceq 'absent') {
  Invoke-ReviewedGit -gitArgs @('-C', $repoRoot, 'tag', $tag, $releaseCommit)
}
$plan = Read-TargetPlan
```

For a remote annotated tag, its `^{}` row from `ls-remote` must equal R. For a
lightweight tag, its ref row must equal R. If the remote tag points elsewhere,
stop; do not delete or move it. If absent, an authorised non-force push is:

```powershell
Invoke-ReviewedGit -gitArgs @('-C', $repoRoot, 'push', "https://github.com/$githubRepo.git", "refs/tags/${tag}:refs/tags/${tag}")
Invoke-ReviewedGit -gitArgs @('ls-remote', "https://github.com/$githubRepo.git", "refs/tags/$tag", "refs/tags/$tag^{}")
```

Verify the remote result against R and record both the tag object ID and peeled
commit. Do not push an unrelated branch or all tags. Tag changes can trigger
repository automation; review those hooks before granting push authority.

## Upload the selected GitHub asset

After the remote tag and duplicate checks, rerun the fresh selected-byte check.
Create a draft with exactly one asset and explicit repository/tag selection:

```powershell
$jarBytes = Read-OwnedSelectedJar
$jarPath = Join-Path $packageRoot $plan.target.file_name
Assert-PublicGitHubHost
gh release create $tag $jarPath --repo "github.com/$githubRepo" --verify-tag --draft `
  --title $plan.github.release_title --notes-file $notesFile --latest=false
if ($LASTEXITCODE -ne 0) { throw 'Ambiguous/failed GitHub write: inspect the draft and assets; do not retry' }
gh api --hostname github.com "repos/$githubRepo/releases/tags/$tag" --jq '{id,tag_name,draft,assets}'
```

`gh` reads the explicit file path, not `$jarBytes`; keep that path quiescent.
Rehash it after upload and independently download the draft asset to a new,
private verification directory. Compare SHA-256 with `plan.target.sha256`.
Confirm exact filename, size, release ID, asset ID, notes and tag commit. Use
`gh release download $tag --repo "github.com/$githubRepo" --pattern $plan.target.file_name --dir <new-verification-directory>`;
never add `--clobber`. The [create](https://cli.github.com/manual/gh_release_create)
and [download](https://cli.github.com/manual/gh_release_download) contracts describe these operations.

After the draft and bytes pass review, publishing needs the named publication
authority. Rerun the package/target and remote-tag checks first. Immediately
before publishing, reread the draft and download its asset again: require the
same reviewed release ID and asset ID, `draft=true`, exactly the selected asset,
unchanged title and notes, and the exact reviewed downloaded bytes. A deleted,
recreated or modified draft is a stop, not an authorised continuation. Retain
that fresh comparison with the original draft receipt.

```powershell
$plan = Read-TargetPlan
Assert-PublicGitHubHost
gh release edit $tag --repo "github.com/$githubRepo" --draft=false --verify-tag --latest=false
if ($LASTEXITCODE -ne 0) { throw 'Inspect GitHub publication state without repeating the write' }
```

Record the published release URL and final asset IDs. Do not change its tag or
replace an asset. See [publishing an existing draft](https://cli.github.com/manual/gh_release_edit).

## Send one owned multipart request

The following standard .NET HTTP procedure sends one request without retries
or redirects. Define it only in the operator's approved session. It is not a
new repository publisher. Supply credentials from the approved credential
source into private in-memory header maps; do not echo them, use URL tokens or
record command transcripts containing secrets.

```powershell
function Send-OneMultipartUpload(
  [string] $uri, [hashtable] $headers, [string] $metadataPart,
  [byte[]] $metadataBytes, [byte[]] $fileBytes, [string] $fileName
) {
  $handler = [Net.Http.HttpClientHandler]::new()
  $handler.AllowAutoRedirect = $false
  $client = [Net.Http.HttpClient]::new($handler)
  $client.Timeout = [TimeSpan]::FromSeconds(120)
  $form = [Net.Http.MultipartFormDataContent]::new()
  $response = $null
  try {
    foreach ($key in $headers.Keys) {
      if (-not $client.DefaultRequestHeaders.TryAddWithoutValidation($key, [string]$headers[$key])) {
        throw 'Invalid approved request header'
      }
    }
    $metadata = [Net.Http.ByteArrayContent]::new($metadataBytes)
    $metadata.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('application/json')
    $form.Add($metadata, $metadataPart)
    $file = [Net.Http.ByteArrayContent]::new($fileBytes)
    $file.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('application/java-archive')
    $form.Add($file, 'file', $fileName)
    $response = $client.PostAsync($uri, $form).GetAwaiter().GetResult()
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
      throw "Provider returned HTTP $([int]$response.StatusCode); inspect privately, do not retry"
    }
    return ($body | ConvertFrom-Json)
  } finally {
    if ($null -ne $response) { $response.Dispose() }
    $form.Dispose()
    $client.Dispose()
  }
}
```

## Create the exact Modrinth version

Review the exact `modrinth_request_metadata_json` string and its SHA-256. Do not
hash the entire target-plan report or reserialize that string. It preserves
the package's version, Minecraft version, loader policy and reviewed notes.
`file_parts` must be `["file"]`; `dependencies=[]`, `version_type="release"`
and `featured=false` are the current local contract.

The optional `source release-modrinth` check repeats all local checks and reads
the selected JAR into owned bytes. It requires a nonempty JAR of at most 64 MiB:

```powershell
$plan = Read-TargetPlan
sfm-propagate-changes --output-format json source release-modrinth @targetArgs `
  --request-metadata-sha256 $reviewedPlan.modrinth_request_metadata_sha256
if ($LASTEXITCODE -ne 0) { throw 'Read-only owned-request check failed' }
```

Its successful report says `dry_run=true` and `upload_performed=false`. It
discards the prepared request and cannot upload. The manual request below is
the separately authorised effect. Repeat ownership and duplicate checks first.
Prepare `$modrinthHeaders` with `Authorization` and a descriptive `User-Agent`
identifying the approved operator/tool. Do not print its values.

```powershell
$jarBytes = Read-OwnedSelectedJar
$metadataBytes = [Text.Encoding]::UTF8.GetBytes($plan.modrinth_request_metadata_json)
if ((Get-BytesSha256 $metadataBytes) -cne $reviewedPlan.modrinth_request_metadata_sha256) {
  throw 'Modrinth request metadata differs from review'
}
$createdVersion = Send-OneMultipartUpload 'https://api.modrinth.com/v2/version' `
  $modrinthHeaders 'data' $metadataBytes $jarBytes $plan.target.file_name
if ([string]::IsNullOrWhiteSpace([string]$createdVersion.id)) { throw 'Missing version ID; do not retry' }
```

This sends `data` plus one `file` part using the
[Modrinth create-version API](https://docs.modrinth.com/api/operations/createversion/).
Record the returned version ID immediately. Fetch `GET /v2/version/<id>` and
compare all requested fields with the decoded reviewed JSON: canonical project
ID, name, version number, changelog, dependencies, game versions, loaders,
release type and featured state. Require exactly the expected one-file set,
including filename and size. Download that version's exact file and
independently verify SHA-256 against the package, not just the provider's
SHA-1/SHA-512 fields. Record its URL, hashes and final visibility status, and
require the visibility to match the human-approved publication state. A
nonempty ID alone is not completed verification.
Use the [version response contract](https://docs.modrinth.com/api/operations/getversion/).

## Resolve exact CurseForge IDs and submit the file

Use the CurseForge Upload API token, not a Core API discovery key. Prepare a
private `$curseforgeHeaders` map with `X-Api-Token`. The
[official Upload API](https://support.curseforge.com/support/solutions/articles/9000197321)
defines `/api/game/versions`, numeric `gameVersions` and multipart fields
`metadata` and `file`. Do not send `gameVersionNames` as a substitute.

```powershell
$plan = Read-TargetPlan
$versions = Invoke-RestMethod -Method Get `
  -Uri 'https://minecraft.curseforge.com/api/game/versions' -Headers $curseforgeHeaders
$versionsSnapshotJson = $versions | ConvertTo-Json -Depth 32 -Compress
$versionsSnapshotHash = Get-BytesSha256 ([Text.Encoding]::UTF8.GetBytes($versionsSnapshotJson))
$resolvedVersions = @(
  foreach ($name in $plan.target.curseforge_metadata_names) {
    $versionMatches = @($versions | Where-Object { $_.name -ceq $name })
    if ($versionMatches.Count -ne 1 -or
        ($versionMatches[0].id -isnot [int] -and $versionMatches[0].id -isnot [long]) -or
        $versionMatches[0].id -le 0) {
      throw "Missing, ambiguous or invalid numeric CurseForge ID for '$name'"
    }
    [pscustomobject]@{
      name = $name; id = $versionMatches[0].id; gameVersionTypeID = $versionMatches[0].gameVersionTypeID
    }
  }
)
$cfGameVersionIds = @($resolvedVersions | ForEach-Object { $_.id })
if (@($cfGameVersionIds | Select-Object -Unique).Count -ne $cfGameVersionIds.Count) {
  throw 'Duplicate numeric CurseForge game-version IDs'
}
$resolvedVersions | Format-Table # Names and public integer IDs only.
```

Review and record every exact name, integer ID and `gameVersionTypeID`, the
decoded JSON snapshot and its UTF-8 digest, and observation time. The snapshot
digest is not a hash of raw HTTP framing. Require one ID for each name and no
extra IDs. The current intent includes Client, Server, exact Minecraft
version, loader labels and Java version. CurseForge's 1.20.1 intent includes
both Forge and NeoForge regardless of the separate Modrinth choice.
For target `1.21.0`, resolve the name `1.21`, not `1.21.0`.
No numeric ID is guessed or frozen into this document.

Review the following exact JSON bytes and their SHA-256 before submission.
They use only the selected target's metadata and resolved integers:

```powershell
$jarBytes = Read-OwnedSelectedJar
$notesBytes = [IO.File]::ReadAllBytes($notesFile)
if ((Get-BytesSha256 $notesBytes) -cne $notesHash) { throw 'Reviewed notes changed' }
$cfMetadata = [ordered]@{
  changelog = [Text.Encoding]::UTF8.GetString($notesBytes)
  changelogType = $plan.target.curseforge_changelog_type
  displayName = $plan.target.display_name
  gameVersions = $cfGameVersionIds
  releaseType = $plan.target.curseforge_release_type
}
$cfMetadataJson = $cfMetadata | ConvertTo-Json -Depth 8 -Compress
$cfMetadataBytes = [Text.Encoding]::UTF8.GetBytes($cfMetadataJson)
$cfMetadataDigest = Get-BytesSha256 $cfMetadataBytes
```

Repeat the authenticated project-file duplicate check. Confirm the recorded
numeric mapping still matches the latest versions response. After approving
the exact metadata digest and file hash, submit once:

```powershell
$createdFile = Send-OneMultipartUpload `
  "https://minecraft.curseforge.com/api/projects/$curseforgeProject/upload-file" `
  $curseforgeHeaders 'metadata' $cfMetadataBytes $jarBytes $plan.target.file_name
if ([UInt64]$createdFile.id -eq 0) { throw 'Missing file ID; inspect project files without retrying' }
```

Record the file ID immediately. In the authenticated author dashboard, confirm
that ID belongs to the approved project and has the exact filename, display
name, release type, notes and resolved version IDs. Submission and approval
are different states. Do not claim public availability while the file is
pending or rejected. Once downloadable, download that exact file and compare
SHA-256 with the package. If verification is unavailable, record the pending
check; do not replace it with an assumed pass.

## Record completion or a partial result

Retain a private, credential-free receipt linking completion-manifest and
inventory hashes, B, R, target ID, tag object/peeled commit, file/hash, notes and
metadata hashes, provider project IDs, authorised actions, operator and times.
Include GitHub release/asset IDs, Modrinth version ID and CurseForge file ID,
their final URLs/states and independently downloaded file digests.

A failure after one provider succeeds is a partial result. Record exactly what
exists, inspect remote state read-only, and request recovery direction. There
is no cross-provider transaction, rollback, retry, overwrite or deletion
authority in this procedure. Do not rerun all providers to recover one failure.

Before claiming completion, require all authorised targets/providers to have
the reviewed metadata and exact downloaded JAR bytes. Keep pending moderation
or unavailable downloads explicit. Reverify the unchanged local package after
the work. Publication does not authorise a default-branch change, retirement
of old branches, or broader gameplay-compatibility claims.

## Validation limits

The installed CLI's `release-package-verify`, `release-target-plan` and
`release-modrinth` help was checked against the current argument/schema source.
No new package or promoted fixture was created for this document. Offline
fixture tests establish the local predicates separately; they are not real
provider tests. Independent review checked the exact argument/schema and
provider contracts; all thirteen PowerShell blocks parse without errors.
Offline negatives cover inherited Git overrides, argument preservation,
no-retry behavior, GitHub host selection and CurseForge name/ID resolution.
The copyable provider procedures remain unexecuted instructions and require
candidate-specific maintainer review and a separately authorised release.
