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

Write-Host -ForegroundColor Yellow "Running tests..."
cargo test --all-features --quiet
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
