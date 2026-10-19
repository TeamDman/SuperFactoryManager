//! Trusted-local historical dependency reuse, separate from fresh NFRT execution.
//! Full input identity plus rehashed output receipts replace marker/existence trust.
//! Same-user malicious manifest forgery is outside this developer-cache authority.

use super::build_snapshot_receipt::ensure_owned_directory;
use super::local_dependency_consumption::LocalDependencyConsumption;
use super::local_dependency_key::LocalDependencyKey;
use super::local_dependency_manifest::LocalDependencyManifest;
use super::nfrt_launch_contract::PreparedNfrtInvocationInput;
use super::nfrt_preparation_phase::NfrtPreparationPhase;
use crate::artifact_lock::ArtifactLock;
use crate::cancellation::CancellationToken;
use crate::file_identity::file_identity;
use crate::source_projection::candidate_lock::is_reparse;
use crate::source_projection::nfrt_producer_output_receipt::NfrtProducerOutputReceipt;
use eyre::Result;
use eyre::ensure;
use sha2::Digest;
use sha2::Sha256;
use std::fs::File;
use std::fs::OpenOptions;
use std::fs::{self};
use std::io::Read;
use std::io::Seek;
use std::io::SeekFrom;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;

const MAX_MANIFEST: u64 = 8 * 1024 * 1024;
const MAX_OUTPUT: u64 = 512 * 1024 * 1024;

pub(super) struct LocalDependencyCache {
    key: LocalDependencyKey,
    directory: PathBuf,
    lock: Option<ArtifactLock>,
    held: Vec<File>,
    ancestry: Vec<File>,
}

impl LocalDependencyCache {
    pub(super) fn begin(
        root: &Path,
        key: LocalDependencyKey,
        cancellation: &CancellationToken,
    ) -> Result<Self> {
        ensure!(
            cfg!(windows),
            "Local dependency held-path reuse is Windows-only"
        );
        cancellation.bail_if_cancelled()?;
        ensure!(
            environment_allows_reuse(),
            "Local dependency cache declined inherited JVM controls"
        );
        key.validate()?;
        let parent = root
            .parent()
            .ok_or_else(|| eyre::eyre!("Cache root has no owner"))?;
        ensure_owned_directory(parent, root)?;
        let mut ancestry = lease_ancestors(root)?;
        let directory = root.join(key.digest());
        ensure_owned_directory(root, &directory)?;
        ancestry.extend(lease_ancestors(&directory)?);
        let path = directory.join("producer.lock");
        if let Ok(metadata) = fs::symlink_metadata(&path) {
            ensure!(
                metadata.is_file() && !is_reparse(&metadata),
                "Unsafe cache producer lock"
            );
        }
        let lock = ArtifactLock::acquire_with_cancellation(
            &path,
            "local dependency producer",
            cancellation.clone(),
        )?;
        Ok(Self {
            key,
            directory,
            lock: Some(lock),
            held: Vec::new(),
            ancestry,
        })
    }

    /// Corruption is a diagnosed miss; old bytes remain available for inspection.
    pub(super) fn lookup(
        &mut self,
        prepared: &PreparedNfrtInvocationInput,
        cancellation: &CancellationToken,
    ) -> Result<Option<PathBuf>> {
        cancellation.bail_if_cancelled()?;
        if !environment_allows_reuse() {
            return Ok(None);
        }
        ensure!(
            self.lock.is_some(),
            "Cache lookup requires its producer lease"
        );
        let phase = NfrtPreparationPhase::start("local_dependency.lookup");
        let result = self.read_entry(cancellation);
        let (manifest, entry, files, directories) = match result {
            Ok(Some(value)) => value,
            Ok(None) => return phase.finish(Ok(None)),
            Err(error) => {
                cancellation.bail_if_cancelled()?;
                tracing::warn!(%error, key = %self.key.digest(), "Local dependency cache rejected; preserving it and preparing fresh inputs");
                return phase.finish(Ok(None));
            }
        };
        let consumption = LocalDependencyConsumption::reused(&self.key, prepared, &manifest)?;
        let record = persist_record(
            &self.directory,
            "consumption-",
            &facet_json::to_string(&consumption)?,
        )?;
        self.held.extend(files);
        self.ancestry.extend(directories);
        tracing::info!(key = %self.key.digest(), authority = "trusted-local-developer-cache",
            historical_production = %manifest.production_contract_sha256,
            current_consumption = %prepared.contract_identity(), receipt = %record.display(),
            "Verified historical dependency reuse; no fresh NFRT execution claimed");
        phase.finish(Ok(Some(entry.join("classes.jar"))))
    }

    /// Must run inside the successful private fresh-host callback, not on a DTO.
    pub(super) fn publish(
        &mut self,
        prepared: &PreparedNfrtInvocationInput,
        classes: &Path,
        sources: &Path,
        receipts: &[&NfrtProducerOutputReceipt; 2],
        cancellation: &CancellationToken,
    ) -> Result<()> {
        cancellation.bail_if_cancelled()?;
        if !environment_allows_reuse() {
            return Ok(());
        }
        ensure!(
            self.lock.is_some(),
            "Cache publication requires its producer lease"
        );
        let phase = NfrtPreparationPhase::start("local_dependency.publish");
        let manifest = LocalDependencyManifest::from_fresh(&self.key, prepared, *receipts)?;
        // Failed/partial attempts remain diagnostic evidence, never discoverable hits.
        let entry = tempfile::Builder::new()
            .prefix("entry-")
            .tempdir_in(&self.directory)?
            .keep();
        let _entry_lease = lease_ancestors(&entry)?;
        copy_verified(
            classes,
            &entry.join("classes.jar"),
            manifest.classes_bytes,
            &manifest.classes_sha256,
            cancellation,
        )?;
        copy_verified(
            sources,
            &entry.join("sources.jar"),
            manifest.sources_bytes,
            &manifest.sources_sha256,
            cancellation,
        )?;
        write_new(
            &entry.join("manifest.json"),
            facet_json::to_string(&manifest)?.as_bytes(),
        )?;
        cancellation.bail_if_cancelled()?;
        // The complete owned copy is retained before the discoverable pointer.
        // Crash before pointer publication leaves unused evidence, never a hit.
        self.commit_pointer(&entry, cancellation)?;
        tracing::info!(key = %self.key.digest(), entry = %entry.display(), "Published verified historical dependency candidate");
        phase.finish(Ok(()))
    }

    pub(super) fn release_producer(&mut self) {
        // Independent SFM compilers need not wait for this successful producer.
        drop(self.lock.take());
    }

    /// Cooperating readers and writers hold the same per-key producer lock.
    /// A torn head is a miss; immutable entries and previous raw heads survive.
    fn commit_pointer(&self, entry: &Path, cancellation: &CancellationToken) -> Result<()> {
        ensure!(
            self.lock.is_some(),
            "Cache publication requires its producer lease"
        );
        cancellation.bail_if_cancelled()?;
        let metadata = fs::symlink_metadata(entry)?;
        ensure!(
            metadata.is_dir() && !is_reparse(&metadata),
            "Cache publication entry is a link or non-directory"
        );
        ensure!(
            entry.parent() == Some(self.directory.as_path()),
            "Cache publication escaped its key directory"
        );
        let name = entry
            .file_name()
            .and_then(|value| value.to_str())
            .ok_or_else(|| eyre::eyre!("Cache entry is not Unicode"))?;
        ensure!(
            valid_entry(name),
            "Unsafe local dependency cache publication name"
        );
        let target = self.directory.join("current.json");
        let (mut pointer, existed) = open_pointer_for_update(&target)?;
        if existed {
            let mut raw = Vec::new();
            (&mut pointer).take(4097).read_to_end(&mut raw)?;
            ensure!(
                raw.len() as u64 == pointer.metadata()?.len(),
                "Cache pointer changed during read"
            );
            persist_bytes(&self.directory, "previous-head-", ".bin", &raw)?;
        }
        cancellation.bail_if_cancelled()?;
        let encoded = facet_json::to_string(&name.to_owned())?;
        pointer.set_len(0)?;
        pointer.seek(SeekFrom::Start(0))?;
        pointer.write_all(encoded.as_bytes())?;
        pointer.sync_all()?;
        Ok(())
    }

    #[expect(
        clippy::type_complexity,
        reason = "The temporary result transfers manifest, entry and held file/directory guards without another public record type."
    )]
    fn read_entry(
        &self,
        cancellation: &CancellationToken,
    ) -> Result<Option<(LocalDependencyManifest, PathBuf, Vec<File>, Vec<File>)>> {
        let pointer = self.directory.join("current.json");
        match fs::symlink_metadata(&pointer) {
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
            result => {
                result?;
            }
        }
        let (_, bytes) = read_checked(&pointer, 4096)?;
        let name: String = facet_json::from_slice(&bytes)?;
        ensure!(
            valid_entry(&name),
            "Unsafe local dependency cache entry name"
        );
        let entry = self.directory.join(name);
        let directories = lease_ancestors(&entry)?;
        let (manifest_file, raw) = read_checked(&entry.join("manifest.json"), MAX_MANIFEST)?;
        let manifest: LocalDependencyManifest = facet_json::from_slice(&raw)?;
        manifest.validate(&self.key)?;
        ensure!(
            raw == facet_json::to_string(&manifest)?.as_bytes(),
            "Cache manifest encoding changed"
        );
        let classes = verify_file(
            &entry.join("classes.jar"),
            manifest.classes_bytes,
            &manifest.classes_sha256,
            cancellation,
        )?;
        let sources = verify_file(
            &entry.join("sources.jar"),
            manifest.sources_bytes,
            &manifest.sources_sha256,
            cancellation,
        )?;
        Ok(Some((
            manifest,
            entry,
            vec![manifest_file, classes, sources],
            directories,
        )))
    }
}

/// These inherited launcher controls are outside the selected SDK/argument key.
/// Decline reuse/publication rather than changing the established fresh policy.
pub(super) fn environment_allows_reuse() -> bool {
    for name in ["_JAVA_OPTIONS", "JDK_ALTERNATE_VM"] {
        if std::env::var_os(name).is_some() {
            tracing::info!(
                environment_name = name,
                "local_dependency_cache_declined_inherited_jvm_control"
            );
            return false;
        }
    }
    true
}

fn open_pointer_for_update(target: &Path) -> Result<(File, bool)> {
    let existed = match fs::symlink_metadata(target) {
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => false,
        Ok(metadata) => {
            ensure!(
                metadata.is_file() && !is_reparse(&metadata),
                "Cache pointer is a link or non-file"
            );
            true
        }
        Err(error) => return Err(error.into()),
    };
    let mut options = OpenOptions::new();
    options.read(true).write(true).create_new(!existed);
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt as _;
        options.share_mode(0).custom_flags(0x0020_0000)
    };
    #[cfg(target_os = "linux")]
    {
        use std::os::unix::fs::OpenOptionsExt as _;
        options.custom_flags(0o400_000)
    };
    // No truncate before validating this exact held handle. Retain all
    // ancestry leases; directory rename sharing is never weakened.
    let pointer = options.open(target)?;
    ensure!(
        pointer.metadata()?.is_file() && !is_reparse(&pointer.metadata()?),
        "Opened cache pointer is unsafe"
    );
    #[cfg(windows)]
    ensure_single_link(&pointer)?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::MetadataExt as _;
        ensure!(pointer.metadata()?.nlink() == 1, "Hardlinked cache pointer");
    }
    ensure!(
        pointer.metadata()?.len() <= 4096,
        "Cache pointer exceeds bound"
    );
    Ok((pointer, existed))
}

fn valid_entry(name: &str) -> bool {
    name.starts_with("entry-")
        && name.len() > 6
        && name.len() <= 96
        && name
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-')
}

fn persist_record(directory: &Path, prefix: &str, text: &str) -> Result<PathBuf> {
    persist_bytes(directory, prefix, ".json", text.as_bytes())
}

fn persist_bytes(directory: &Path, prefix: &str, suffix: &str, bytes: &[u8]) -> Result<PathBuf> {
    let mut record = tempfile::Builder::new()
        .prefix(prefix)
        .suffix(suffix)
        .tempfile_in(directory)?;
    record.write_all(bytes)?;
    record.as_file().sync_all()?;
    let (file, path) = record.keep().map_err(|error| error.error)?;
    drop(file);
    Ok(path)
}

fn read_checked(path: &Path, bound: u64) -> Result<(File, Vec<u8>)> {
    let mut file = open_read(path)?;
    let length = file.metadata()?.len();
    ensure!(length <= bound, "Cache metadata input exceeds bound");
    let mut raw = Vec::new();
    (&mut file).take(bound + 1).read_to_end(&mut raw)?;
    ensure!(
        raw.len() as u64 == length,
        "Cache metadata changed while reading"
    );
    Ok((file, raw))
}

fn verify_file(
    path: &Path,
    expected: u64,
    digest: &str,
    cancellation: &CancellationToken,
) -> Result<File> {
    let mut phase = NfrtPreparationPhase::start("local_dependency.verify_output");
    let mut file = open_read(path)?;
    let identity = file_identity(&file)?;
    ensure!(
        expected > 0 && expected <= MAX_OUTPUT && file.metadata()?.len() == expected,
        "Cache output length differs"
    );
    let actual = stream(&mut file, expected, None, cancellation)?;
    ensure!(
        actual == digest && file_identity(&file)? == identity && file.metadata()?.len() == expected,
        "Cache output bytes changed"
    );
    phase.work(1, expected);
    phase.success();
    Ok(file)
}

fn copy_verified(
    source: &Path,
    destination: &Path,
    expected: u64,
    digest: &str,
    cancellation: &CancellationToken,
) -> Result<()> {
    let mut phase = NfrtPreparationPhase::start("local_dependency.copy_successful_output");
    let mut source = open_read(source)?;
    ensure!(
        source.metadata()?.len() == expected && expected > 0 && expected <= MAX_OUTPUT,
        "Fresh dependency output length differs"
    );
    let mut target = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(destination)?;
    let actual = stream(&mut source, expected, Some(&mut target), cancellation)?;
    ensure!(
        actual == digest && target.metadata()?.len() == expected,
        "Fresh dependency output differs from its sealed receipt"
    );
    target.sync_all()?;
    phase.work(1, expected);
    phase.success();
    Ok(())
}

fn stream(
    file: &mut File,
    expected: u64,
    mut target: Option<&mut File>,
    cancellation: &CancellationToken,
) -> Result<String> {
    file.seek(SeekFrom::Start(0))?;
    let mut hash = Sha256::new();
    let mut total = 0_u64;
    let mut buffer = vec![0_u8; 64 * 1024].into_boxed_slice();
    loop {
        cancellation.bail_if_cancelled()?;
        let count = file.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        total += u64::try_from(count)?;
        ensure!(total <= expected, "Dependency output grew during read");
        hash.update(&buffer[..count]);
        if let Some(output) = target.as_mut() {
            output.write_all(&buffer[..count])?;
        }
    }
    ensure!(total == expected, "Dependency output shrank during read");
    Ok(format!("sha256:{:x}", hash.finalize()))
}

fn write_new(path: &Path, bytes: &[u8]) -> Result<()> {
    let mut output = OpenOptions::new().write(true).create_new(true).open(path)?;
    output.write_all(bytes)?;
    output.sync_all()?;
    Ok(())
}

pub(super) fn open_read(path: &Path) -> Result<File> {
    ensure!(
        path.is_absolute(),
        "Dependency cache input must be absolute"
    );
    let metadata = fs::symlink_metadata(path)?;
    ensure!(
        metadata.is_file() && !is_reparse(&metadata),
        "Dependency cache input is a link or non-file"
    );
    let mut options = OpenOptions::new();
    options.read(true);
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
    let file = options.open(path)?;
    ensure!(
        file.metadata()?.is_file() && !is_reparse(&file.metadata()?),
        "Opened dependency cache input is unsafe"
    );
    #[cfg(windows)]
    ensure_single_link(&file)?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::MetadataExt as _;
        ensure!(
            file.metadata()?.nlink() == 1,
            "Hardlinked dependency cache input"
        );
    }
    Ok(file)
}

#[cfg(windows)]
fn ensure_single_link(file: &File) -> Result<()> {
    use std::os::windows::io::AsRawHandle as _;
    use windows::Win32::Foundation::HANDLE;
    use windows::Win32::Storage::FileSystem::FILE_STANDARD_INFO;
    use windows::Win32::Storage::FileSystem::FileStandardInfo;
    use windows::Win32::Storage::FileSystem::GetFileInformationByHandleEx;
    let mut information = FILE_STANDARD_INFO::default();
    // SAFETY: the borrowed file owns the live handle; the initialized buffer matches FILE_STANDARD_INFO.
    unsafe {
        GetFileInformationByHandleEx(
            HANDLE(file.as_raw_handle()),
            FileStandardInfo,
            std::ptr::from_mut(&mut information).cast(),
            u32::try_from(std::mem::size_of_val(&information))?,
        )
    }?;
    ensure!(
        information.NumberOfLinks == 1,
        "Hardlinked dependency cache input"
    );
    Ok(())
}

pub(super) fn lease_ancestors(directory: &Path) -> Result<Vec<File>> {
    ensure!(
        directory.is_absolute(),
        "Dependency cache directory must be absolute"
    );
    let mut files = Vec::new();
    for ancestor in directory.ancestors().collect::<Vec<_>>().into_iter().rev() {
        let metadata = fs::symlink_metadata(ancestor)?;
        ensure!(
            metadata.is_dir() && !is_reparse(&metadata),
            "Dependency ancestry is a link or non-directory"
        );
        #[cfg(windows)]
        {
            use std::os::windows::fs::OpenOptionsExt as _;
            let file = OpenOptions::new()
                .access_mode(0x0081)
                .share_mode(1)
                .custom_flags(0x0220_0000)
                .open(ancestor)?;
            ensure!(
                file.metadata()?.is_dir() && !is_reparse(&file.metadata()?),
                "Opened dependency ancestry is unsafe"
            );
            files.push(file);
        }
    }
    Ok(files)
}

pub(super) fn implementation_identity() -> String {
    // Only the dependency transformation/transfer implementation is bound.
    // Report rendering, CLI display and SFM authored Java/test inputs are absent.
    let mut hash = Sha256::new();
    for bytes in [
        include_bytes!("nfrt_host/SFMHostTiming.java").as_slice(),
        include_bytes!("nfrt_host/SFMNamedHostPreparationMain.java").as_slice(),
        include_bytes!("nfrt_host/SFMNamedNeoFormLauncher.java").as_slice(),
        include_bytes!("nfrt_host/SFMNamedEngineFactory.java").as_slice(),
        include_bytes!("nfrt_host/SFMHostRecompileAction.java").as_slice(),
        include_bytes!("nfrt_host/SFMHostExternalToolAction.java").as_slice(),
        include_bytes!("nfrt_host/SFMHostTransformToolAction.java").as_slice(),
        include_bytes!("nfrt_host/SFMHostTransformPreparation.java").as_slice(),
        include_bytes!("nfrt_host/SFMHostProjectInputs.java").as_slice(),
        include_bytes!("nfrt_host/SFMHostChannel.java").as_slice(),
        include_bytes!("nfrt_host/SFMHostProducerSealer.java").as_slice(),
        include_bytes!("nfrt_host/SFMHostGraph.java").as_slice(),
        include_bytes!("nfrt_host/SFMFreshProducerBindings.java").as_slice(),
        include_bytes!("nfrt_host/SFMClosedDownloadManager.java").as_slice(),
        include_bytes!("nfrt_host/SFMRustContractConverter.java").as_slice(),
        include_bytes!("nfrt_owned_host.rs").as_slice(),
        include_bytes!("nfrt_host_session.rs").as_slice(),
        include_bytes!("nfrt_host_protocol.rs").as_slice(),
        include_bytes!("nfrt_child_tool.rs").as_slice(),
        include_bytes!("nfrt_child_process.rs").as_slice(),
        include_bytes!("../source_projection/nfrt_input_store.rs").as_slice(),
        include_bytes!("../source_projection/nfrt_held_producer.rs").as_slice(),
    ] {
        hash.update(u64::try_from(bytes.len()).unwrap_or(u64::MAX).to_le_bytes());
        hash.update(bytes);
    }
    format!("sha256:{:x}", hash.finalize())
}

#[cfg(all(test, windows))]
#[path = "local_dependency_cache_tests.rs"]
mod tests;
