Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-NormalizedVersion {
	param(
		[Parameter(Mandatory = $true)]
		[string]$Value
	)

	$parts = $Value.Split('.') | Where-Object { $_ -ne '' }
	while ($parts.Count -lt 3) {
		$parts += '0'
	}
	return [Version]::Parse(($parts -join '.'))
}

function Get-LatestTagForModVersion {
	param(
		[Parameter(Mandatory = $true)]
		[string]$RepoRoot,
		[Parameter(Mandatory = $true)]
		[string]$ModVersion
	)

	$tags = git -C $RepoRoot tag --list "$ModVersion-*"
	if (-not $tags -or $tags.Count -eq 0) {
		throw "No tags found matching $ModVersion-*"
	}

	$parsed = foreach ($tag in $tags) {
		if ($tag -match "^$([regex]::Escape($ModVersion))-(?<mc>[0-9]+(?:\.[0-9]+){0,2})$") {
			[PSCustomObject]@{
				Tag       = $tag
				McVersion = $Matches['mc']
				SortKey   = Get-NormalizedVersion -Value $Matches['mc']
			}
		}
	}

	if (-not $parsed -or $parsed.Count -eq 0) {
		throw "Found tags for $ModVersion, but none matched expected format $ModVersion-<mcVersion>"
	}

	return ($parsed | Sort-Object -Property SortKey -Descending | Select-Object -First 1).Tag
}

function Get-ChangelogSection {
	param(
		[Parameter(Mandatory = $true)]
		[string]$ChangelogPath,
		[Parameter(Mandatory = $true)]
		[string]$ModVersion
	)

	$lines = Get-Content -Path $ChangelogPath
	if (-not $lines -or $lines.Count -eq 0) {
		throw "Changelog file was empty: $ChangelogPath"
	}

	$currentHeadingPattern = "^----\s*$([regex]::Escape($ModVersion))\s*-+\s*$"
	$releaseHeadingPattern = '^----\s*[0-9]+(?:\.[0-9]+){1,2}\s*-+\s*$'

	$startIndex = -1
	for ($index = 0; $index -lt $lines.Count; $index++) {
		if ($lines[$index] -match $currentHeadingPattern) {
			$startIndex = $index
			break
		}
	}

	if ($startIndex -lt 0) {
		throw "Could not find changelog heading for version $ModVersion in $ChangelogPath"
	}

	$endIndex = $lines.Count
	for ($index = $startIndex + 1; $index -lt $lines.Count; $index++) {
		if ($lines[$index] -match $releaseHeadingPattern) {
			$endIndex = $index
			break
		}
	}

	$section = $lines[0..($endIndex - 1)]
	return ($section -join "`n").Trim()
}

function Get-OrderedReleaseJars {
	param(
		[Parameter(Mandatory = $true)]
		[string]$JarDir,
		[Parameter(Mandatory = $true)]
		[string]$ModVersion
	)

	if (-not (Test-Path -Path $JarDir -PathType Container)) {
		throw "Jar directory does not exist: $JarDir"
	}

	$jars = Get-ChildItem -Path $JarDir -File -Filter "*.jar" |
		Where-Object { $_.Name -match "-$([regex]::Escape($ModVersion))\.jar$" }

	if (-not $jars -or $jars.Count -eq 0) {
		throw "No jar files found in $JarDir for mod version $ModVersion"
	}

	$parsed = foreach ($jar in $jars) {
		if ($jar.Name -match '-MC(?<mc>[0-9]+(?:\.[0-9]+){0,2})-') {
			[PSCustomObject]@{
				Path     = $jar.FullName
				SortKey  = Get-NormalizedVersion -Value $Matches['mc']
				Filename = $jar.Name
			}
		} else {
			[PSCustomObject]@{
				Path     = $jar.FullName
				SortKey  = [Version]::Parse('0.0.0')
				Filename = $jar.Name
			}
		}
	}

	return $parsed |
		Sort-Object -Property SortKey, Filename |
		Select-Object -ExpandProperty Path
}

$repoRoot = (Get-Content "$env:APPDATA\teamdman\sfm-propagate-changes\config\repo_root.txt" -Raw).Trim()
$repo = "TeamDman/SuperFactoryManager"
$gradleProperties = Join-Path $repoRoot "platform/minecraft/gradle.properties"
$changelogPath = Join-Path $repoRoot "platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml"

$modVersion = (Select-String -Path $gradleProperties -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value.Trim()
$releaseTitle = "v$modVersion"

$releaseTag = Get-LatestTagForModVersion -RepoRoot $repoRoot -ModVersion $modVersion

Push-Location $repoRoot
try {
	$jarDirRaw = (& sfm-propagate-changes.exe jar dir show | Out-String).Trim()
} finally {
	Pop-Location
}

$jarDir = ($jarDirRaw -split "`r?`n" | Where-Object { $_.Trim() -ne '' } | Select-Object -Last 1).Trim()
$assets = Get-OrderedReleaseJars -JarDir $jarDir -ModVersion $modVersion
$assetArgs = $assets | ForEach-Object {
	"$_#$(Split-Path -Path $_ -Leaf)"
}
$notes = Get-ChangelogSection -ChangelogPath $changelogPath -ModVersion $modVersion

$notesFile = Join-Path $env:TEMP "sfm-release-notes-$modVersion.md"
Set-Content -Path $notesFile -Value $notes -Encoding UTF8

Write-Host "Repo:          $repo"
Write-Host "Mod version:   $modVersion"
Write-Host "Release title: $releaseTitle"
Write-Host "Release tag:   $releaseTag"
Write-Host "Jar dir:       $jarDir"
Write-Host "Assets:"
foreach ($asset in $assets) {
	Write-Host " - $(Split-Path -Path $asset -Leaf)"
}

$null = gh api "repos/$repo/releases/tags/$releaseTag" 2>$null
$releaseExists = ($LASTEXITCODE -eq 0)

$actionDescription = if ($releaseExists) {
	"update the existing release and replace assets"
} else {
	"create a new release and upload assets"
}

$response = Read-Host "Proceed to $actionDescription for tag ${releaseTag}? (y/n)"
if ($response -notin @('y', 'Y', 'yes', 'YES')) {
	Write-Host "Aborting GitHub release step"
	exit 0
}

if ($releaseExists) {
	Write-Host "Release for $releaseTag exists, updating title/notes and replacing assets..."
	gh release edit $releaseTag --repo $repo --title $releaseTitle --notes-file $notesFile
	gh release upload $releaseTag @assetArgs --repo $repo --clobber
} else {
	Write-Host "Creating release for $releaseTag..."
	gh release create $releaseTag @assetArgs --repo $repo --title $releaseTitle --notes-file $notesFile
}

Write-Host "GitHub release step complete."
