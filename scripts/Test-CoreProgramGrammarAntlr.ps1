# Isolated syntax proof for authored SFML grammar feature combinations.
# Runtime-created files live only in a new goal-owned temporary directory.
# No public catalog, generated projection, dependency cache or JDK is modified.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][string]$CliPath,
    [Parameter(Mandatory)][string]$CacheRoot,
    [Parameter(Mandatory)][string]$Blake3VerifierPath
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskUtf8 = [Text.UTF8Encoding]::new($false)
$taskRoot = [IO.Path]::GetFullPath($RepoRoot)
$taskCli = [IO.Path]::GetFullPath($CliPath)
$taskCache = [IO.Path]::GetFullPath($CacheRoot)
$taskVerifier = [IO.Path]::GetFullPath($Blake3VerifierPath)
$taskCore = Join-Path $taskRoot 'platform/minecraft/core-liquid-template'
$taskGrammarRelative = 'src/main/antlr/sfml/SFML.g4'
$taskAliasRelative = 'src/main/java/proof/SFMLGrammar.java'
$taskRegistryRelative = 'platform/minecraft/core-liquid-template/feature-definitions.json'
$taskCatalogRelative = 'platform/minecraft/projections.json'
$taskOwners = @('client_frame_language','client_frame_render','client_inbox','client_program_actions','packet_computation','packet_transport_private','runtime_resource_cleanup','sfml_execution_side','sfml_worded_intervals')
$taskTemporary = Join-Path ([IO.Path]::GetTempPath()) ('sfm-core-antlr-syntax-proof-' + [Guid]::NewGuid().ToString('N'))
if (Test-Path -LiteralPath $taskTemporary) { throw 'Fresh proof directory already exists' }
[void][IO.Directory]::CreateDirectory($taskTemporary)

function Get-TaskSha256([byte[]]$Bytes) {
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}
function Read-TaskBounded([string]$Path, [long]$Limit = 1048576) {
    $taskItem = Get-Item -LiteralPath $Path
    if ($taskItem.PSIsContainer -or ($taskItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $taskItem.Length -gt $Limit) {
        throw 'Proof input is not a bounded regular file'
    }
    $taskBytes = [IO.File]::ReadAllBytes($Path)
    if ($taskBytes.Length -gt $Limit) { throw 'Proof input grew beyond its bound' }
    return ,$taskBytes
}
function Write-TaskFresh([string]$Path, [byte[]]$Bytes) {
    if (-not [IO.Path]::GetFullPath($Path).StartsWith($taskTemporary + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to write outside this exact proof directory'
    }
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Path))
    $taskStream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $taskStream.Write($Bytes); $taskStream.Flush() } finally { $taskStream.Dispose() }
}
function Invoke-TaskProcess([string]$Exe, [string[]]$Arguments, [string]$WorkingDirectory, [int]$Timeout = 60000) {
    $taskInfo = [Diagnostics.ProcessStartInfo]::new()
    $taskInfo.FileName = $Exe
    $taskInfo.WorkingDirectory = $WorkingDirectory
    $taskInfo.UseShellExecute = $false
    $taskInfo.CreateNoWindow = $true
    $taskInfo.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $taskInfo.RedirectStandardOutput = $true
    $taskInfo.RedirectStandardError = $true
    foreach ($taskArgument in $Arguments) { [void]$taskInfo.ArgumentList.Add($taskArgument) }
    $taskProcess = [Diagnostics.Process]::new()
    $taskProcess.StartInfo = $taskInfo
    try {
        [void]$taskProcess.Start()
        $taskOut = $taskProcess.StandardOutput.ReadToEndAsync()
        $taskErr = $taskProcess.StandardError.ReadToEndAsync()
        if (-not $taskProcess.WaitForExit($Timeout)) {
            $taskProcess.Kill($true)
            $taskProcess.WaitForExit()
            throw 'Exact proof child exceeded its bounded timeout'
        }
        $taskResult = [pscustomobject]@{exit_code=$taskProcess.ExitCode;stdout=$taskOut.GetAwaiter().GetResult();stderr=$taskErr.GetAwaiter().GetResult()}
        if (($taskResult.stdout + $taskResult.stderr) -match '(?i)not enough space|no space left|disk is full') {
            throw 'STOP: disk-space error; user intervention required'
        }
        return $taskResult
    } finally { $taskProcess.Dispose() }
}
function Get-TaskClosure([string[]]$Requested, $Definitions) {
    $taskSet = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $taskPending = [Collections.Generic.Stack[string]]::new()
    foreach ($taskName in $Requested) { if ($taskSet.Add($taskName)) { $taskPending.Push($taskName) } }
    while ($taskPending.Count -gt 0) {
        $taskName = $taskPending.Pop()
        if (-not $Definitions.Contains($taskName)) { throw 'Unknown functional owner in proof fixture' }
        foreach ($taskRequired in $Definitions[$taskName].requires) {
            if ($taskSet.Add($taskRequired)) { $taskPending.Push($taskRequired) }
        }
    }
    return ,@($taskSet | Sort-Object -CaseSensitive)
}

try {
    $taskSourceBytes = Read-TaskBounded (Join-Path $taskCore $taskGrammarRelative)
    $taskRegistryBytes = Read-TaskBounded (Join-Path $taskRoot $taskRegistryRelative)
    $taskCatalogBytes = Read-TaskBounded (Join-Path $taskRoot $taskCatalogRelative)
    $taskDefinitions = $taskUtf8.GetString($taskRegistryBytes) | ConvertFrom-Json -AsHashtable
    $taskPublicCatalog = $taskUtf8.GetString($taskCatalogBytes) | ConvertFrom-Json -AsHashtable
    if ($taskPublicCatalog.Count -ne 20) { throw 'Expected exact twenty public catalog contexts' }
    $taskLocks = @{
        '4.9.1' = @{relative='platform/minecraft/sfm-toolchain.lock.json';coordinates=@('org.antlr:antlr4:4.9.1','org.antlr:antlr-runtime:3.5.2','org.antlr:antlr4-runtime:4.9.1','org.antlr:ST4:4.3','org.abego.treelayout:org.abego.treelayout.core:1.0.3','org.glassfish:javax.json:1.0.4')}
        '4.13.1' = @{relative='platform/minecraft/core-liquid-template/build/lockfiles/26.1.2/schema-2.json';coordinates=@('org.antlr:antlr4:4.13.1','org.antlr:antlr4-runtime:4.13.1','org.antlr:antlr-runtime:3.5.3','org.antlr:ST4:4.3.4','org.abego.treelayout:org.abego.treelayout.core:1.0.3','com.ibm.icu:icu4j:72.1')}
    }
    $taskToolEvidence = [Collections.Generic.List[object]]::new()
    $taskVerifiedCoordinates = @{}
    $taskClasspaths = @{}
    $taskLockEvidence = @{}
    foreach ($taskVersion in @('4.9.1','4.13.1')) {
        $taskSpec = $taskLocks[$taskVersion]
        $taskLockBytes = Read-TaskBounded (Join-Path $taskRoot $taskSpec.relative) 8388608
        $taskLock = $taskUtf8.GetString($taskLockBytes) | ConvertFrom-Json
        $taskLockEvidence[$taskVersion] = [pscustomobject]@{path=$taskSpec.relative;sha256=('sha256:'+(Get-TaskSha256 $taskLockBytes))}
        $taskPaths = [Collections.Generic.List[string]]::new()
        foreach ($taskCoordinate in $taskSpec.coordinates) {
            $taskMatches = @($taskLock.artifacts | Where-Object coordinate -EQ $taskCoordinate)
            if ($taskMatches.Count -ne 1 -or $taskMatches[0].hash -notmatch '^blake3:[0-9a-f]{40}$') { throw 'Missing/ambiguous exact locked ANTLR artifact' }
            $taskArtifact = $taskMatches[0]
            if ($taskVerifiedCoordinates.ContainsKey($taskCoordinate)) {
                if ($taskVerifiedCoordinates[$taskCoordinate].locked_hash -ne $taskArtifact.hash) { throw 'Toolchains disagree on a shared artifact hash' }
                $taskPaths.Add($taskVerifiedCoordinates[$taskCoordinate].private_path)
                continue
            }
            if (-not $taskArtifact.cache_path.StartsWith('$sfm-cache\maven\', [StringComparison]::Ordinal)) { throw 'Unexpected locked Maven cache boundary' }
            $taskCacheRelative = $taskArtifact.cache_path.Substring('$sfm-cache\'.Length)
            if ($taskCacheRelative.Contains('..')) { throw 'Unsafe locked cache path' }
            $taskCachedPath = Join-Path $taskCache $taskCacheRelative
            $taskBytes = Read-TaskBounded $taskCachedPath 67108864
            $taskPrivatePath = Join-Path $taskTemporary ('tools/' + $taskCacheRelative.Replace('\','/'))
            Write-TaskFresh $taskPrivatePath $taskBytes
            $taskVerified = Invoke-TaskProcess $taskVerifier @($taskPrivatePath, $taskArtifact.hash) $taskTemporary
            if ($taskVerified.exit_code -ne 0 -or $taskVerified.stdout.Trim() -notmatch '^verified blake3:([0-9a-f]{64}) ([0-9]+) bytes$') { throw 'Locked BLAKE3 verification failed before ANTLR execution' }
            $taskFullBlake3 = $Matches[1]
            if ([long]$Matches[2] -ne $taskBytes.Length -or ('blake3:'+$taskFullBlake3.Substring(0,40)) -ne $taskArtifact.hash) { throw 'Verifier evidence does not match exact locked bytes' }
            $taskEvidence = [pscustomobject]@{coordinate=$taskCoordinate;locked_hash=$taskArtifact.hash;full_blake3=('blake3:'+$taskFullBlake3);sha256=('sha256:'+(Get-TaskSha256 $taskBytes));bytes=$taskBytes.Length;locked_checksum_verified=$true}
            $taskToolEvidence.Add($taskEvidence)
            $taskVerifiedCoordinates[$taskCoordinate] = @{locked_hash=$taskArtifact.hash;private_path=$taskPrivatePath}
            $taskPaths.Add($taskPrivatePath)
        }
        $taskClasspaths[$taskVersion] = [string]::Join([IO.Path]::PathSeparator, $taskPaths)
    }

    $taskPrimaryLock = Get-Content -LiteralPath (Join-Path $taskRoot 'platform/minecraft/sfm-toolchain.lock.json') -Raw | ConvertFrom-Json
    $taskPins = @($taskPrimaryLock.jdk_pins | Where-Object major -EQ 17)
    if ($taskPins.Count -ne 1) { throw 'Expected one exact Java17 pin' }
    $taskPin = $taskPins[0]
    $taskArtifacts = @($taskPin.artifacts | Where-Object platform -EQ 'windows-x64')
    if ($taskArtifacts.Count -ne 1 -or $taskPin.version -ne '17.0.14' -or $taskPin.build -ne 'b1367.22' -or $taskPin.vendor -ne 'JetBrains' -or $taskPin.flavor -ne 'jbrsdk') { throw 'Unexpected pinned JDK identity' }
    $taskJdkArtifact = $taskArtifacts[0]
    $taskJdkDigest = $taskJdkArtifact.sha512.ToLowerInvariant()
    if ($taskJdkDigest -notmatch '^[0-9a-f]{128}$') { throw 'Invalid pinned archive digest' }
    $taskJdkHome = Join-Path $taskCache ('jbrsdk/installs/' + $taskJdkDigest)
    $taskArchive = Join-Path $taskCache ('jbrsdk/archives/' + $taskJdkDigest + '.zip')
    if ((Get-FileHash -LiteralPath $taskArchive -Algorithm SHA512).Hash.ToLowerInvariant() -ne $taskJdkDigest) { throw 'Pinned JDK archive checksum mismatch' }
    $taskReceipt = Get-Content -LiteralPath (Join-Path $taskJdkHome '.sfm-jbrsdk-receipt') -Raw
    foreach ($taskExpected in @('major=17','vendor=JetBrains',('version='+$taskPin.version),('build='+$taskPin.build),'flavor=jbrsdk','platform=windows-x64',('url='+$taskJdkArtifact.url),('sha512='+$taskJdkDigest))) {
        if (-not ($taskReceipt -split '\r?\n' -ccontains $taskExpected)) { throw 'Pinned JDK receipt identity mismatch' }
    }
    $taskZip = [IO.Compression.ZipFile]::OpenRead($taskArchive)
    $taskJdkFileCount = 0
    try {
        $taskReleases = @($taskZip.Entries | Where-Object FullName -Match '/release$')
        if ($taskReleases.Count -ne 1) { throw 'Pinned JDK ZIP has ambiguous home' }
        $taskPrefix = $taskReleases[0].FullName.Substring(0, $taskReleases[0].FullName.Length - 'release'.Length)
        foreach ($taskEntry in $taskZip.Entries) {
            if ($taskEntry.Name.Length -eq 0) { continue }
            if (-not $taskEntry.FullName.StartsWith($taskPrefix, [StringComparison]::Ordinal)) { throw 'Pinned SDK entry escapes its home' }
            $taskRelative = $taskEntry.FullName.Substring($taskPrefix.Length)
            if ($taskRelative.Contains('..')) { throw 'Unsafe pinned SDK entry' }
            $taskInstalled = Join-Path $taskJdkHome $taskRelative
            $taskItem = Get-Item -LiteralPath $taskInstalled
            if ($taskItem.Length -ne $taskEntry.Length -or ($taskItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Cached SDK entry differs from pinned archive' }
            $taskZipStream = $taskEntry.Open()
            $taskFileStream = [IO.File]::OpenRead($taskInstalled)
            $taskHash = [Security.Cryptography.SHA256]::Create()
            try {
                $taskZipHash = [Convert]::ToHexString($taskHash.ComputeHash($taskZipStream))
                $taskInstalledHash = [Convert]::ToHexString($taskHash.ComputeHash($taskFileStream))
                if ($taskZipHash -cne $taskInstalledHash) { throw 'Cached SDK contents differ from pinned archive' }
            } finally { $taskHash.Dispose(); $taskZipStream.Dispose(); $taskFileStream.Dispose() }
            $taskJdkFileCount++
        }
    } finally { $taskZip.Dispose() }
    $taskJava = Join-Path $taskJdkHome 'bin/java.exe'
    $taskJavaVersion = Invoke-TaskProcess $taskJava @('-version') $taskTemporary
    if ($taskJavaVersion.exit_code -ne 0 -or $taskJavaVersion.stderr -notmatch '17\.0\.14' -or $taskJavaVersion.stderr -notmatch 'b1367\.22') { throw 'Pinned Java executable identity mismatch' }

    $taskFixture = Join-Path $taskTemporary 'fixture'
    Write-TaskFresh (Join-Path $taskFixture $taskRegistryRelative) $taskRegistryBytes
    Write-TaskFresh (Join-Path $taskFixture ('platform/minecraft/core-liquid-template/' + $taskAliasRelative)) $taskSourceBytes
    $taskFixtureCatalog = [ordered]@{}
    foreach ($taskKey in @($taskPublicCatalog.Keys | Sort-Object -CaseSensitive)) { $taskFixtureCatalog[$taskKey] = $taskPublicCatalog[$taskKey] }
    $taskCombinations = @{}
    for ($taskBits = 0; $taskBits -lt [Math]::Pow(2,$taskOwners.Count); $taskBits++) {
        $taskRequested = @(for ($taskIndex = 0; $taskIndex -lt $taskOwners.Count; $taskIndex++) { if ($taskBits -band (1 -shl $taskIndex)) { $taskOwners[$taskIndex] } })
        $taskEnabled = Get-TaskClosure $taskRequested $taskDefinitions
        $taskCombinations[[string]::Join(',', $taskEnabled)] = $taskEnabled
    }
    if ($taskCombinations.Count -ne 58) { throw 'Dependency-valid combination scope changed; explicit review required' }
    $taskCombinationKeys = [Collections.Generic.List[string]]::new()
    $taskIndex = 0
    foreach ($taskCombination in @($taskCombinations.Keys | Sort-Object -CaseSensitive)) {
        $taskKey = 'proof/combination-{0:d3}' -f $taskIndex++
        $taskFixtureCatalog[$taskKey] = @{minecraft_version='1.19.2';environment='dev';features=@($taskCombinations[$taskCombination])}
        $taskCombinationKeys.Add($taskKey)
    }
    Write-TaskFresh (Join-Path $taskFixture $taskCatalogRelative) $taskUtf8.GetBytes(($taskFixtureCatalog | ConvertTo-Json -Depth 12))
    $taskRenders = [Collections.Generic.List[object]]::new()
    $taskRenderedBodies = @{}
    foreach ($taskKey in $taskFixtureCatalog.Keys) {
        $taskRendered = Invoke-TaskProcess $taskCli @('--output-format','json','source','render','--repo-root',$taskFixture,'--projection',$taskKey,'--file',$taskAliasRelative) $taskTemporary
        if ($taskRendered.exit_code -ne 0) { throw ('Production fixture rendering failed: ' + $taskKey + ' ' + $taskRendered.stderr) }
        $taskReport = $taskRendered.stdout | ConvertFrom-Json
        if ($taskReport.source_sha256 -ne ('sha256:'+(Get-TaskSha256 $taskSourceBytes)) -or $taskReport.writes_performed -or $taskReport.compiled -or $taskReport.full_project_generated) { throw 'Unexpected real-renderer proof result' }
        $taskBytes = $taskUtf8.GetBytes($taskReport.rendered_content)
        if ($taskReport.rendered_sha256 -ne ('sha256:'+(Get-TaskSha256 $taskBytes))) { throw 'Production renderer output digest mismatch' }
        $taskRenderedBodies[$taskKey] = $taskBytes
        $taskRenders.Add([pscustomobject]@{key=$taskKey;enabled_features=$taskReport.projection.enabled_features;source_sha256=$taskReport.source_sha256;rendered_sha256=$taskReport.rendered_sha256;bytes=$taskBytes.Length;exit_code=$taskRendered.exit_code;public_catalog_context=$taskPublicCatalog.Contains($taskKey)})
    }

    $taskProofCases = [Collections.Generic.List[object]]::new()
    foreach ($taskKey in $taskCombinationKeys) { $taskProofCases.Add(@{key=$taskKey;tool='4.9.1';name=$taskKey.Replace('/','-')}) }
    $taskProofCases.Add(@{key='sfm-4.34.0/mc-1.19.2';tool='4.9.1';name='released-grammar-4-9-1'})
    $taskProofCases.Add(@{key='sfm-4.34.0/mc-26.1.2';tool='4.13.1';name='released-grammar-4-13-1'})
    $taskGenerations = [Collections.Generic.List[object]]::new()
    $taskFailures = 0
    foreach ($taskCase in $taskProofCases) {
        $taskCaseRoot = Join-Path $taskTemporary ('cases/' + $taskCase.name)
        $taskGrammar = Join-Path $taskCaseRoot 'SFML.g4'
        $taskOut = Join-Path $taskCaseRoot 'generated'
        Write-TaskFresh $taskGrammar $taskRenderedBodies[$taskCase.key]
        [void][IO.Directory]::CreateDirectory($taskOut)
        $taskResult = Invoke-TaskProcess $taskJava @('-cp',$taskClasspaths[$taskCase.tool],'org.antlr.v4.Tool','-visitor','-Xexact-output-dir','-o',$taskOut,$taskGrammar) $taskCaseRoot
        $taskStdout = $taskResult.stdout.Replace($taskTemporary,'<goal-owned-temp>').Replace($taskJdkHome,'<pinned-sdk>')
        $taskStderr = $taskResult.stderr.Replace($taskTemporary,'<goal-owned-temp>').Replace($taskJdkHome,'<pinned-sdk>')
        $taskDiagnostics = @([regex]::Matches($taskStdout + $taskStderr,'(?m)^(?:error|warning)\([0-9]+\):.*$') | ForEach-Object Value)
        $taskFiles = @(Get-ChildItem -LiteralPath $taskOut -File | Sort-Object Name | ForEach-Object { [pscustomobject]@{name=$_.Name;bytes=$_.Length;sha256=('sha256:'+(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant())} })
        $taskMissing = @(@('SFMLLexer.java','SFMLParser.java','SFMLVisitor.java','SFMLBaseVisitor.java') | Where-Object { -not (Test-Path -LiteralPath (Join-Path $taskOut $_) -PathType Leaf) })
        $taskPassed = $taskResult.exit_code -eq 0 -and $taskDiagnostics.Count -eq 0 -and $taskMissing.Count -eq 0
        if (-not $taskPassed) { $taskFailures++ }
        $taskGenerations.Add([pscustomobject]@{key=$taskCase.key;antlr_tool=$taskCase.tool;exit_code=$taskResult.exit_code;diagnostics=$taskDiagnostics;implicit_token_warnings=@($taskDiagnostics|Where-Object {$_ -match 'implicit definition of token|warning\(125\)'});stdout=$taskStdout;stderr=$taskStderr;generated_files=$taskFiles;missing_required_files=$taskMissing;passed=$taskPassed})
        Write-Host ('ANTLR_PROOF_PROGRESS {0}/{1} failures={2}' -f $taskGenerations.Count,$taskProofCases.Count,$taskFailures)
    }
    if ((Get-TaskSha256 (Read-TaskBounded (Join-Path $taskCore $taskGrammarRelative))) -ne (Get-TaskSha256 $taskSourceBytes) -or (Get-TaskSha256 (Read-TaskBounded (Join-Path $taskRoot $taskRegistryRelative))) -ne (Get-TaskSha256 $taskRegistryBytes) -or (Get-TaskSha256 (Read-TaskBounded (Join-Path $taskRoot $taskCatalogRelative))) -ne (Get-TaskSha256 $taskCatalogBytes)) { throw 'Public source/catalog/registry changed during proof' }
    $taskProof = [pscustomobject]@{
        schema='sfm:core_program_grammar_antlr_proof@1'
        validation='isolated_antlr_generation_not_minecraft_or_java_compilation'
        source=@{path=('platform/minecraft/core-liquid-template/'+$taskGrammarRelative);sha256=('sha256:'+(Get-TaskSha256 $taskSourceBytes));bytes=$taskSourceBytes.Length}
        inputs=@{catalog_sha256=('sha256:'+(Get-TaskSha256 $taskCatalogBytes));feature_definitions_sha256=('sha256:'+(Get-TaskSha256 $taskRegistryBytes));cli_sha256=('sha256:'+(Get-FileHash -LiteralPath $taskCli -Algorithm SHA256).Hash.ToLowerInvariant());verifier_sha256=('sha256:'+(Get-FileHash -LiteralPath $taskVerifier -Algorithm SHA256).Hash.ToLowerInvariant());locks=$taskLockEvidence}
        renderer=@{implementation='current worktree CLI source render with real CoreCatalog and render_java_source';test_only_alias=$taskAliasRelative;alias_bytes_equal_original_grammar=$true;reason='source render currently accepts .java inputs only';independent_expansion_used=$false;public_catalog_entries_copied_without_changes=20;dependency_valid_combinations=58;unique_combination_grammar_hashes=@($taskRenders|Where-Object {-not $_.public_catalog_context}|Select-Object -ExpandProperty rendered_sha256 -Unique).Count;real_generation_template_true_claim='Covered separately by registered core selection regression tests, not this .java-alias CLI proof.'}
        jdk=@{vendor=$taskPin.vendor;version=$taskPin.version;build=$taskPin.build;flavor=$taskPin.flavor;major=17;platform='windows-x64';archive_sha512=$taskJdkDigest;archive_checksum_verified=$true;installed_files_equal_verified_archive=$taskJdkFileCount;receipt_identity_verified=$true;java_version_exit_code=$taskJavaVersion.exit_code;java_version_stderr=$taskJavaVersion.stderr}
        tool_artifacts=@($taskToolEvidence)
        rendered_contexts=@($taskRenders)
        generations=@($taskGenerations)
        results=@{real_rendered_contexts=$taskRenders.Count;antlr_invocations=$taskGenerations.Count;failed_cases=$taskFailures;all_cases_passed=($taskFailures -eq 0);public_inputs_unchanged=$true;dependency_cache_writes=$false;jdk_writes=$false;cargo_runs=$false;minecraft_builds=$false;java_compilation=$false;temporary_fixture_retained=$true;temporary_absolute_paths_persisted=$false}
    }
    $taskReportPath = Join-Path $taskTemporary 'proof-report.json'
    Write-TaskFresh $taskReportPath $taskUtf8.GetBytes(($taskProof | ConvertTo-Json -Depth 20))
    Write-Host ('ANTLR_PROOF_REPORT ' + $taskReportPath)
    Write-Host ('ANTLR_PROOF_COMPLETE failures=' + $taskFailures)
    if ($taskFailures -ne 0) { exit 1 }
} catch {
    if ($_.Exception.Message -match '(?i)not enough space|no space left|disk is full|disk-space') {
        Write-Error 'STOP: disk-space error; user intervention required. No cleanup performed.'
    }
    throw
}
