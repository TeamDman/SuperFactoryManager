//! Reviewed JUnit-only inputs. Never changes the production lock or resolver.
use crate::cancellation::CancellationToken;
use crate::source_projection::core_catalog::read_bounded_input;
fn sha256(bytes: &[u8]) -> String {
    crate::source_projection::provenance::sha256(bytes)
        .trim_start_matches("sha256:")
        .to_owned()
}
use std::collections::BTreeMap;
use std::path::Path;
mod approved_pins;
mod artifact_catalog;
mod authority;
mod bindings;
mod cache;
mod classpath_bindings;
mod configuration;
mod development_compile;
#[cfg(all(test, any(windows, target_os = "linux")))]
mod development_compile_tests;
mod execution_receipt;
mod launch_inputs;
pub(super) use launch_inputs::LaunchInputs;
mod exclusion_witness;
mod preflight;
mod preflight_issue;
mod preflight_report;
mod prepared_junit_inputs;
mod provision_artifact;
mod provision_receipt;
mod role_binding;
mod role_input;
mod role_selection;
#[cfg(test)]
mod role_tests;
mod roles;
mod roots;
mod runtime;
mod source_artifact;
mod source_dependency;
mod source_lock;
mod source_lock_reference;
mod supplement;
mod target_recipe;
mod target_report;
mod test_pin;
mod test_root;
#[cfg(test)]
mod tests;
mod verified_test_artifact;
pub(super) use development_compile::prepare_for_compile_plan;
pub(crate) use preflight::preflight;
use preflight_issue::PreflightIssue;
use preflight_report::PreflightReport;
pub(super) use prepared_junit_inputs::PreparedJunitInputs;
pub(super) use runtime::enforce_selected;
pub(super) use runtime::prepare_for_plan;
pub(super) use runtime::write_execution_receipt;
use test_pin::TestPin;

// Immutable authority for the already-reviewed JUnit closure; live development
// runtime upgrades must not rewrite its historical receipt or change its bytes.
const SOURCE: &str = "platform/minecraft/core-liquid-template/build/lockfiles/1.19.2/schema-4.test-dependency-authority.json";
const PROVENANCE: &str = "platform/minecraft/core-liquid-template/build/lockfiles/1.19.2/schema-4.test-dependency-provenance.json";
const CONFIGURATION: &str = "platform/minecraft/build-configuration/released-native.json";
const SOURCE_SHA: &str = "198c141c2e45ed29a90935e84565e10f861aa0895233bb9b4a10cb8d2c109d41";
const PROVENANCE_SHA: &str = "d279856aa66ac537c3b7f27497b567697af8d4c07fd089b2f85dc66ac5a4b93f";
const CONFIGURATION_SHA: &str = "9d0520814c0cefb04245e0989201aa5773a48e2ab0879271d699ad7d47946c17";
const MAX_METADATA_BYTES: u64 = 8 * 1024 * 1024;
const MAX_ARTIFACT_BYTES: u64 = 64 * 1024 * 1024;
const MAX_TOTAL_BYTES: u64 = 512 * 1024 * 1024;

fn issue(
    report: &mut PreflightReport,
    target: Option<&str>,
    scope: &str,
    code: &str,
    message: String,
) {
    report.issues.push(PreflightIssue {
        scope: scope.to_owned(),
        code: code.to_owned(),
        target_ids: target.map(str::to_owned).into_iter().collect(),
        coordinate: None,
        path: None,
        expected: None,
        actual: None,
        message,
    });
}

fn read_input(
    report: &mut PreflightReport,
    root: &Path,
    path: &str,
    limit: u64,
) -> eyre::Result<Vec<u8>> {
    eyre::ensure!(
        report.bytes_read < MAX_TOTAL_BYTES,
        "JUnit preflight read budget exhausted"
    );
    let bytes = read_bounded_input(root, path, limit.min(MAX_TOTAL_BYTES - report.bytes_read))?;
    report.files_checked += 1;
    report.bytes_read += bytes.len() as u64;
    Ok(bytes)
}

fn read_pinned(
    report: &mut PreflightReport,
    root: &Path,
    path: &str,
    expected: &str,
) -> eyre::Result<Vec<u8>> {
    let bytes = read_input(report, root, path, MAX_METADATA_BYTES)?;
    let actual = sha256(&bytes);
    eyre::ensure!(
        actual == expected,
        "JUnit authority hash mismatch for {path}: expected {expected}, actual {actual}"
    );
    Ok(bytes)
}

fn identity<T: facet::Facet<'static>>(value: &T) -> eyre::Result<String> {
    Ok(sha256(facet_json::to_string(value)?.as_bytes()))
}

fn require_complete(report: &PreflightReport) -> eyre::Result<()> {
    eyre::ensure!(
        report.closure_complete,
        "JUnit dependency preflight refused before build/Java: {}. Run `test preflight --repo-root <repo>` for all omissions. No cache repair or dependency refresh performed.",
        facet_json::to_string(&report.issues)?
    );
    Ok(())
}
