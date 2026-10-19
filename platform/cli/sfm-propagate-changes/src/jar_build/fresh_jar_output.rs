//! Explicit fresh JAR publication inside an existing owned build/libs directory.
//!
//! This capability never authorizes replacement. The caller retains it and any
//! private output `TempDir` until the child has drained and publication finishes.
//! Copy failure or cancellation may leave a newly created final leaf; it is
//! retained rather than deleted through a potentially changed pathname.

use super::super::build_snapshot_receipt as snapshot;
use super::CancellationToken;
use crate::source_projection::candidate_lock::checked_directory;
#[cfg(windows)]
use crate::source_projection::core_input_leases::DirectoryLeases;
use eyre::Result;
use eyre::WrapErr as _;
use eyre::ensure;
use std::fs;
use std::io::Read as _;
use std::io::Seek as _;
use std::io::SeekFrom;
use std::path::Path;
use std::path::PathBuf;

const MAX_BASENAME_BYTES: usize = 128;
const PRIVATE_PREFIX: &str = "fresh-jar-";
const PRIVATE_WRITER: &str = "reobfuscated.jar";

/// Lexical only: no directory creation, source read, or output probe.
pub(super) fn resolve(
    owner: &Path,
    basename: &str,
    default_output: &Path,
    gradle_output: &Path,
) -> Result<PathBuf> {
    validate_basename(basename)?;
    super::require_named_jar_path(owner)?;
    for forbidden in [default_output, gradle_output] {
        ensure!(
            forbidden
                .file_name()
                .and_then(|name| name.to_str())
                .is_none_or(|name| !name.eq_ignore_ascii_case(basename)),
            "explicit fresh JAR name aliases a default Rust or Gradle output"
        );
    }
    let output = owner.join("build").join("libs").join(basename);
    validate_output_shape(owner, &output)?;
    Ok(output)
}

pub(super) fn validate_basename(basename: &str) -> Result<()> {
    ensure!(
        !basename.is_empty()
            && basename.len() <= MAX_BASENAME_BYTES
            && basename.as_bytes()[0].is_ascii_alphanumeric()
            && basename
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-'))
            && basename.rsplit_once('.').is_some_and(
                |(stem, extension)| !stem.is_empty() && extension.eq_ignore_ascii_case("jar")
            ),
        "fresh JAR name must be a portable ASCII .jar basename of at most 128 bytes"
    );
    let device = basename
        .split('.')
        .next()
        .unwrap_or_default()
        .to_ascii_uppercase();
    let numbered_device = device.len() == 4
        && (device.starts_with("COM") || device.starts_with("LPT"))
        && matches!(device.as_bytes()[3], b'1'..=b'9');
    ensure!(
        !matches!(device.as_str(), "CON" | "PRN" | "AUX" | "NUL") && !numbered_device,
        "fresh JAR name is a reserved Windows device"
    );
    Ok(())
}

fn validate_output_shape(owner: &Path, output: &Path) -> Result<()> {
    super::require_named_jar_path(owner)?;
    super::require_named_jar_path(output)?;
    let basename = output
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| eyre::eyre!("fresh JAR output has no Unicode basename"))?;
    validate_basename(basename)?;
    let relative = snapshot::checked_descendant(owner, output)?;
    ensure!(
        relative == Path::new("build").join("libs").join(basename),
        "fresh JAR output must be a direct child of its owner's existing build/libs"
    );
    Ok(())
}

fn existing_directory(path: &Path) -> Result<()> {
    let canonical = checked_directory(path)?;
    ensure!(
        snapshot::same_path_spelling(&canonical, path),
        "fresh JAR directory spelling is not canonical"
    );
    Ok(())
}

#[cfg(windows)]
fn portable_relative(owner: &Path, path: &Path) -> Result<String> {
    snapshot::checked_descendant(owner, path)?
        .to_str()
        .map(|relative| relative.replace('\\', "/"))
        .ok_or_else(|| eyre::eyre!("fresh JAR owned path is not Unicode"))
}

/// A fresh final leaf and, on Windows, retained non-reparse ancestor leases.
pub(super) struct FreshJarOutput {
    owner: PathBuf,
    output: PathBuf,
    private_writer: Option<PathBuf>,
    #[cfg(windows)]
    _output_ancestry: DirectoryLeases,
    #[cfg(windows)]
    private_ancestry: Option<DirectoryLeases>,
}

impl FreshJarOutput {
    /// Require an existing safe namespace; this method never creates parents.
    pub(super) fn prepare(owner: &Path, output: &Path) -> Result<Self> {
        validate_output_shape(owner, output)?;
        #[cfg(windows)]
        let output_ancestry = {
            let mut ancestry = DirectoryLeases::default();
            ancestry.prepare(owner, &portable_relative(owner, output)?)?;
            ancestry
        };
        let capability = Self {
            owner: owner.to_path_buf(),
            output: output.to_path_buf(),
            private_writer: None,
            #[cfg(windows)]
            _output_ancestry: output_ancestry,
            #[cfg(windows)]
            private_ancestry: None,
        };
        capability.recheck_absent()?;
        Ok(capability)
    }

    pub(super) fn path(&self) -> &Path {
        &self.output
    }

    fn recheck_parent(&self) -> Result<()> {
        validate_output_shape(&self.owner, &self.output)?;
        existing_directory(&self.owner)?;
        existing_directory(
            self.output
                .parent()
                .ok_or_else(|| eyre::eyre!("fresh JAR output has no parent"))?,
        )
    }

    /// Shared launch guards can check absence without surrendering the leases.
    pub(super) fn recheck_absent(&self) -> Result<()> {
        self.recheck_parent()?;
        match fs::symlink_metadata(&self.output) {
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
            Ok(_) => eyre::bail!("fresh JAR refuses an existing output of any kind"),
            Err(error) => Err(error).wrap_err("cannot inspect fresh JAR output"),
        }
    }

    /// Create one private child output directory, bound to this capability.
    ///
    /// The caller retains the returned owner until its child and copy finish.
    /// Windows private-directory leases are released after the copy attempt so
    /// `TempDir` cleanup can run while final-parent leases remain retained.
    pub(super) fn private_output_directory(
        &mut self,
        cache_project_root: &Path,
    ) -> Result<tempfile::TempDir> {
        self.recheck_absent()?;
        ensure!(
            self.private_writer.is_none(),
            "fresh JAR private writer was already selected"
        );
        let relative = snapshot::checked_descendant(&self.owner, cache_project_root)?;
        ensure!(
            !relative.as_os_str().is_empty(),
            "fresh JAR private output requires an owned cache project descendant"
        );
        existing_directory(cache_project_root)?;
        #[cfg(windows)]
        let mut ancestry = {
            let mut ancestry = DirectoryLeases::default();
            let witness = cache_project_root.join(PRIVATE_WRITER);
            ancestry.prepare(&self.owner, &portable_relative(&self.owner, &witness)?)?;
            ancestry
        };
        let directory = tempfile::Builder::new()
            .prefix(PRIVATE_PREFIX)
            .tempdir_in(cache_project_root)?;
        existing_directory(directory.path())?;
        let writer = directory.path().join(PRIVATE_WRITER);
        #[cfg(windows)]
        if let Err(error) = ancestry.prepare(&self.owner, &portable_relative(&self.owner, &writer)?)
        {
            // Release the newly held private directory before TempDir cleanup.
            drop(ancestry);
            return Err(error);
        }
        self.private_writer = Some(writer);
        #[cfg(windows)]
        drop(self.private_ancestry.replace(ancestry));
        Ok(directory)
    }

    pub(super) fn private_output_path(&self) -> Result<&Path> {
        self.private_writer
            .as_deref()
            .ok_or_else(|| eyre::eyre!("fresh JAR private writer is not prepared"))
    }

    /// Verified `create_new` copy, with cancellation before creation and success.
    ///
    /// Existing reviewed-file helpers bound/authenticate the completed
    /// input and final bytes (currently 512 MiB). It never overwrites a leaf.
    /// This is not atomic visibility: any newly created failed output is kept.
    pub(super) fn publish_copy(
        &mut self,
        input: &Path,
        cancellation: &CancellationToken,
    ) -> Result<()> {
        self.publish_copy_inner(input, cancellation, || {})
    }

    fn copy_completed(&self, input: &Path, cancellation: &CancellationToken) -> Result<fs::File> {
        self.copy_authenticated(input, cancellation, || Ok(()))
    }

    fn copy_authenticated(
        &self,
        input: &Path,
        cancellation: &CancellationToken,
        after_authentication: impl FnOnce() -> Result<()>,
    ) -> Result<fs::File> {
        // The actual read remains bounded even if a mutable source grows after
        // authentication. One extra byte distinguishes growth from exact EOF.
        let hash = super::capture_named_package_hash(input)?;
        let held = super::ReviewedSpecialSourceFile::read(input, &hash)?;
        let expected_bytes = held.bytes;
        let limit = expected_bytes
            .checked_add(1)
            .ok_or_else(|| eyre::eyre!("fresh JAR copy bound overflow"))?;
        let mut source = fs::File::open(input)?.take(limit);
        after_authentication()?;
        let mut options = fs::OpenOptions::new();
        options.read(true).write(true).create_new(true);
        #[cfg(windows)]
        {
            use std::os::windows::fs::OpenOptionsExt as _;
            options.share_mode(1).custom_flags(0x0020_0000)
        };
        #[cfg(target_os = "linux")]
        {
            use std::os::unix::fs::OpenOptionsExt as _;
            options.custom_flags(0o400_000)
        };
        cancellation.bail_if_cancelled()?;
        let mut destination = options.open(&self.output)?;
        super::require_named_regular_file(&self.output, &destination.metadata()?)?;
        let copied = std::io::copy(&mut source, &mut destination)?;
        ensure!(
            copied == expected_bytes,
            "fresh JAR source length changed after authentication"
        );
        destination.sync_all()?;
        held.recheck()?;
        let before = destination.metadata()?;
        super::require_named_regular_file(&self.output, &before)?;
        let identity = crate::file_identity::file_identity(&destination)?;
        ensure!(
            before.len() == expected_bytes,
            "fresh JAR completed copy has an unexpected length"
        );
        destination.seek(SeekFrom::Start(0))?;
        let mut raw = Vec::new();
        (&mut destination)
            .take(
                expected_bytes
                    .checked_add(1)
                    .ok_or_else(|| eyre::eyre!("fresh JAR verification length overflow"))?,
            )
            .read_to_end(&mut raw)?;
        let after = destination.metadata()?;
        super::require_named_regular_file(&self.output, &after)?;
        ensure!(
            u64::try_from(raw.len())? == expected_bytes
                && after.len() == expected_bytes
                && after.modified()? == before.modified()?
                && crate::file_identity::file_identity(&destination)? == identity
                && super::ContentHash::from_bytes(&raw, hash.algorithm) == hash,
            "fresh JAR completed copy changed bytes"
        );
        // The caller retains the destination against Windows write/delete
        // access until cancellation and successful publication checks finish.
        Ok(destination)
    }

    fn publish_copy_inner(
        &mut self,
        input: &Path,
        cancellation: &CancellationToken,
        after_copy: impl FnOnce(),
    ) -> Result<()> {
        let result = (|| {
            cancellation.bail_if_cancelled()?;
            self.recheck_absent()?;
            if let Some(writer) = &self.private_writer {
                ensure!(
                    input == writer,
                    "fresh JAR input is not its exact private writer"
                );
                #[cfg(windows)]
                if self.private_ancestry.is_none() {
                    let mut ancestry = DirectoryLeases::default();
                    ancestry.prepare(&self.owner, &portable_relative(&self.owner, writer)?)?;
                    self.private_ancestry = Some(ancestry);
                }
                existing_directory(
                    writer
                        .parent()
                        .ok_or_else(|| eyre::eyre!("private JAR has no parent"))?,
                )?;
            }
            // Ancestor leases outlive every authenticated copy step.
            let destination = self.copy_completed(input, cancellation)?;
            after_copy();
            cancellation.bail_if_cancelled()?;
            self.recheck_parent()?;
            super::require_named_regular_file(&self.output, &fs::symlink_metadata(&self.output)?)?;
            drop(destination);
            Ok(())
        })();
        #[cfg(windows)]
        drop(self.private_ancestry.take());
        result
    }
}

#[cfg(test)]
#[path = "fresh_jar_output_tests.rs"]
mod tests;
