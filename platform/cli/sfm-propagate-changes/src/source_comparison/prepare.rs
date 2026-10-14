use super::input::ComparisonInput;
use super::report::ComparisonReport;
use super::request::ComparisonRequest;
use crate::cancellation::CancellationToken;
use crate::source_projection::candidate_lock::checked_directory;
use eyre::Result;
use eyre::ensure;
use std::fs;
use std::path::Path;

/// Prepare a fresh bundle with evidence, HTML and two read-only captured sides.
///
/// # Errors
/// Rejects overlap, existing output and write/render failures.
/// Publishes by a single rename after preparation.
pub fn prepare(
    request: &ComparisonRequest,
    output: &Path,
    cancellation: &CancellationToken,
) -> Result<ComparisonReport> {
    ensure!(output.is_absolute(), "comparison output must be absolute");
    let name = output
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| eyre::eyre!("comparison output needs a directory name"))?;
    crate::source_projection::promotion::validate_relative_path(name)?;
    let parent = checked_directory(
        output
            .parent()
            .ok_or_else(|| eyre::eyre!("comparison output needs a parent"))?,
    )?;
    let destination = parent.join(name);
    ensure!(!destination.exists(), "comparison output already exists");
    for input in [&request.left, &request.right] {
        let root = match input {
            ComparisonInput::Directory { root } => root,
            ComparisonInput::PinnedGit { repository, .. }
            | ComparisonInput::Projection { repository, .. } => repository,
        };
        let root = dunce::canonicalize(root)?;
        ensure!(
            !dunce::canonicalize(&parent)?.starts_with(&root),
            "comparison output overlaps selected source root"
        );
    }
    let report = super::compare(request, cancellation)?;
    let json = facet_json::to_string_pretty(&report)?;
    let html = crate::release_report::comparison_render::render(&report, cancellation)?;
    let staging = tempfile::Builder::new()
        .prefix(".sfm-comparison-")
        .tempdir_in(&parent)?;
    for (name, snapshot) in [("left", &report.left), ("right", &report.right)] {
        let side = staging.path().join(name);
        fs::create_dir(&side)?;
        for (relative, file) in &snapshot.files {
            cancellation.bail_if_cancelled()?;
            let path = side.join(relative);
            fs::create_dir_all(
                path.parent()
                    .ok_or_else(|| eyre::eyre!("file has no parent"))?,
            )?;
            fs::write(&path, &file.bytes)?;
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt as _;
                fs::set_permissions(
                    &path,
                    fs::Permissions::from_mode(if file.mode == Some(0o100_755) {
                        0o555
                    } else {
                        0o444
                    }),
                )?;
            }
            #[cfg(not(unix))]
            {
                let mut permissions = fs::metadata(&path)?.permissions();
                permissions.set_readonly(true);
                fs::set_permissions(&path, permissions)?;
            }
        }
    }
    fs::write(staging.path().join("comparison.json"), json)?;
    fs::write(staging.path().join("index.html"), html)?;
    cancellation.bail_if_cancelled()?;
    fs::rename(staging.path(), &destination)?;
    Ok(report)
}
