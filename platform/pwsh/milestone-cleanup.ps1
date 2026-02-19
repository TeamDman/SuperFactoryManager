Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repo_root = Get-Content $env:APPDATA\teamdman\sfm-propagate-changes\config\repo_root.txt
$repo = "TeamDman/SuperFactoryManager"
$gradleProperties = "$repo_root/platform/minecraft/gradle.properties"
$modVersion = (Select-String -Path $gradleProperties -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value.Trim()
$milestoneTitle = "v$modVersion"

Write-Host "Preparing milestone $milestoneTitle for $repo"
$milestones = gh api "repos/$repo/milestones?state=open" --paginate | ConvertFrom-Json
$milestone = $milestones | Where-Object { $_.title -eq $milestoneTitle }
if ($null -eq $milestone) {
    Write-Host -ForegroundColor "Red" "Failed to find milestone $milestoneTitle, is it already closed?"
    exit 1
}

$milestoneIssues = gh issue list `
--repo $repo `
--milestone "$milestoneTitle" `
--state all `
--limit 500 `
--json number,labels,title,closed,milestone `
| ConvertFrom-Json

$issuesAwaitingRelease = $milestoneIssues | Where-Object { -not $_.closed -and ($_.labels | Where-Object { $_.name -eq "implemented awaiting release" })}
Write-Host "Found $($issuesAwaitingRelease.Count) issues awaiting release:"
foreach ($issue in $issuesAwaitingRelease) {
    Write-Host " - $($issue.title) (#$($issue.number))"
}
if ($issuesAwaitingRelease.Count -gt 0) {
    $response = Read-Host "Close these issues? (y/n)"
    if ($response -eq "y") {
        foreach ($issue in $issuesAwaitingRelease) {
            gh issue close $issue.number --repo $repo
            gh issue edit $issue.number --repo $repo --remove-label "implemented awaiting release"
            Write-Host "Closed issue #$($issue.number) and removed label implemented awaiting release"
            $issue.closed = $true
        }
    } else {
        Write-Host "Aborting milestone cleanup"
        exit 0
    }
}

$unclosedIssues = $milestoneIssues | Where-Object { -not $_.closed }
if ($unclosedIssues.Count -gt 0) {
    Write-Host "The following issues are still open, cannot close milestone:"
    foreach ($issue in $unclosedIssues) {
        Write-Host " - $($issue.title) (#$($issue.number))"
    }
    exit 1
}

$response = Read-Host "Close the milestone? (y/n)"
if ($response -eq "y") {
    gh api -X PATCH "repos/$repo/milestones/$milestoneNumber" -f state=closed
    Write-Host "Closed milestone $milestoneTitle"
} else {
    Write-Host "Milestone not closed"
}