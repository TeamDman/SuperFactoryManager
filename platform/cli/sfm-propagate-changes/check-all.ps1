param(
    [ValidateRange(1, 4)][int]$TestWorkers = 2
)

# Cargo feature variants share the target/debug CLI path. During a full gate,
# and after a failed/interrupted gate or run-profiler.ps1, that path may contain
# the memory-profiled product. Use install.ps1 or a fresh default-feature build
# for normal work; only a successful final build below restores that product.

Write-Host -ForegroundColor Yellow "Checking direct dependency policy..."
$metadataJson = cargo metadata --no-deps --format-version 1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$metadata = $metadataJson | ConvertFrom-Json
$package = $metadata.packages | Where-Object { $_.name -eq "sfm-propagate-changes" }
if ($null -eq $package) {
    Write-Error "Could not find sfm-propagate-changes in cargo metadata output."
    exit 1
}

$forbiddenDependencies = @(
    $package.dependencies | Where-Object { $_.name -in @("serde", "serde_json") }
)
if ($forbiddenDependencies.Count -gt 0) {
    $details = $forbiddenDependencies | ForEach-Object {
        if ($null -ne $_.rename) {
            "{0} (renamed to {1})" -f $_.name, $_.rename
        } else {
            $_.name
        }
    }
    Write-Error ("Direct Serde dependencies are forbidden; use Facet instead: {0}" -f ($details -join ", "))
    exit 1
}

Write-Host -ForegroundColor Yellow "Running format check..."
rustup run nightly -- cargo fmt --all
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host -ForegroundColor Yellow "Running clippy lint check..."
# cargo clippy --all-targets --all-features -- -D warnings
cargo clippy --all-features -- -D warnings
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host -ForegroundColor Yellow "Running build..."
cargo build --all-features --quiet
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host -ForegroundColor Yellow "Running bounded library and integration tests..."
# All feature code remains enabled; the unit allocator is deliberately not
# profiled. The runner verifies exact-once library coverage and uses a fresh
# process per module (per immediate child for source_projection), integration
# target and dedicated fixture.
try {
    & (Join-Path $PSScriptRoot 'scripts/test-bounded.ps1') -Workers $TestWorkers
} catch {
    Write-Error $_
    exit 1
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host -ForegroundColor Yellow "Running bin tests..."
cargo test --offline --locked --all-features --bins --quiet
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host -ForegroundColor Yellow "Running doc tests..."
cargo test --offline --locked --all-features --doc --quiet
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host -ForegroundColor Yellow "Building the default-feature operational CLI..."
cargo build --locked --offline --bin sfm-propagate-changes --quiet
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
