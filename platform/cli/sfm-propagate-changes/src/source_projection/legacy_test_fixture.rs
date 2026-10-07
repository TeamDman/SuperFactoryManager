//! Test-only historical witnesses for the retired snapshot workflow.
//! Production generation never reads this checkpoint.

use eyre::Result;
use eyre::ensure;
use std::io::Cursor;
use std::path::Path;

pub(crate) const CHECKPOINT: &str = "556e4f0994b88873189eff18e4eef4ebcd629279";

pub(crate) fn read(root: &Path, path: &str) -> Result<Vec<u8>> {
    let output = super::release_baseline::frozen_git_command(root)
        .env("GIT_ALLOW_PROTOCOL", "")
        .args(["show", &format!("{CHECKPOINT}:{path}")])
        .output()?;
    ensure!(
        output.status.success(),
        "historical fixture is unavailable: {path}"
    );
    Ok(output.stdout)
}

pub(crate) fn materialize(destination: &Path, paths: &[&str]) -> Result<()> {
    let root = Path::new(env!("CARGO_MANIFEST_DIR"))
        .ancestors()
        .nth(3)
        .unwrap();
    let output = super::release_baseline::frozen_git_command(root)
        .env("GIT_ALLOW_PROTOCOL", "")
        .args(["archive", "--format=zip", CHECKPOINT])
        .args(paths)
        .output()?;
    ensure!(
        output.status.success(),
        "cannot read historical fixture archive"
    );
    let mut archive = zip::ZipArchive::new(Cursor::new(output.stdout))?;
    archive.extract(destination)?;
    Ok(())
}
