use super::dependency_index_identity::DEPENDENCY_SYMBOL_INDEX_STORE_FORMAT_VERSION;
use super::dependency_index_identity::DependencySymbolIndexIdentity;
use crate::artifact_lock::ArtifactLock;
use crate::paths::CacheHome;
use eyre::Context;
use facet::Facet;
use std::fs;
use std::fs::File;
use std::io::ErrorKind;
use std::io::Read;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;

pub const DEPENDENCY_SYMBOL_INDEX_MANIFEST_SCHEMA: &str = "sfm.dependency-symbol-index-manifest/1";
pub const DEPENDENCY_SYMBOL_INDEX_PAYLOAD_SCHEMA: &str = "sfm.dependency-symbol-index-payload/1";
pub const DEPENDENCY_SYMBOL_INDEX_PORTABLE_ROOT: &str = "$sfm-cache/symbol-index/v3";
pub const DEPENDENCY_SYMBOL_INDEX_MANIFEST_FILE: &str = "manifest.json";
pub const DEPENDENCY_SYMBOL_INDEX_PAYLOAD_FILE: &str = "payload.ndjson";

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum DependencySymbolIndexCompleteness {
    Complete,
    Partial,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum DependencySymbolIndexInputStatus {
    Ready,
    Missing,
    Stale,
    Unavailable,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum DependencySymbolIndexProbeStatus {
    Ready,
    Missing,
    Stale,
    Partial,
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
pub struct DependencySymbolIndexSourceInput {
    pub dependency: String,
    pub component: String,
    pub provider: String,
    pub portable_origin: String,
    pub fingerprint: String,
    pub status: DependencySymbolIndexInputStatus,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencySymbolIndexCreationMetadata {
    pub created_at_utc: String,
    pub producer: String,
    pub refresh_duration_ms: u64,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencySymbolIndexCounts {
    pub source_files: u64,
    pub definitions: u64,
    pub usages: u64,
    pub diagnostics: u64,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencySymbolIndexPayloadDescriptor {
    pub schema: String,
    pub file: String,
    pub hash: String,
    pub size_bytes: u64,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencySymbolIndexManifest {
    pub schema: String,
    pub identity: DependencySymbolIndexIdentity,
    pub creation: DependencySymbolIndexCreationMetadata,
    pub source_inputs: Vec<DependencySymbolIndexSourceInput>,
    pub counts: DependencySymbolIndexCounts,
    pub completeness: DependencySymbolIndexCompleteness,
    pub format_fingerprint: String,
    pub payload: DependencySymbolIndexPayloadDescriptor,
}

/// Versioned envelope around the concrete dependency-index data model.
///
/// Phase 0.8 query integration supplies a Facet body type. Keeping the wrapper
/// generic lets that model evolve behind `body_schema` and the identity's
/// format fingerprint without coupling storage to the live workspace index.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencySymbolIndexPayload<T> {
    pub schema: String,
    pub identity: DependencySymbolIndexIdentity,
    pub body_schema: String,
    pub body: T,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DependencySymbolIndexPortableLayout {
    pub root: String,
    pub manifest: String,
    pub payload: String,
    pub lock: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DependencySymbolIndexConcreteLayout {
    pub root: PathBuf,
    pub manifest: PathBuf,
    pub payload: PathBuf,
    pub lock: PathBuf,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DependencySymbolIndexLayout {
    pub portable: DependencySymbolIndexPortableLayout,
    pub concrete: DependencySymbolIndexConcreteLayout,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DependencySymbolIndexProbe {
    pub status: DependencySymbolIndexProbeStatus,
    pub loadable: bool,
    pub reason: String,
    pub expected_identity: String,
    pub observed_identity: Option<String>,
    pub layout: DependencySymbolIndexLayout,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DependencySymbolIndexPublishOutcome {
    Published,
    AlreadyPresent,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoadedDependencySymbolIndex<T> {
    pub manifest: DependencySymbolIndexManifest,
    pub payload: DependencySymbolIndexPayload<T>,
}

#[derive(Clone, Debug)]
pub struct DependencySymbolIndexStore {
    cache_home: CacheHome,
}

impl<T> DependencySymbolIndexPayload<T> {
    #[must_use]
    pub fn new(
        identity: DependencySymbolIndexIdentity,
        body_schema: impl Into<String>,
        body: T,
    ) -> Self {
        Self {
            schema: DEPENDENCY_SYMBOL_INDEX_PAYLOAD_SCHEMA.to_owned(),
            identity,
            body_schema: body_schema.into(),
            body,
        }
    }
}

impl DependencySymbolIndexManifest {
    /// Construct a manifest whose descriptor authenticates the exact payload bytes.
    ///
    /// # Errors
    ///
    /// Returns an error when the identity, source inputs, payload schema, or
    /// payload serialization is invalid.
    pub fn for_payload<T: Facet<'static>>(
        identity: DependencySymbolIndexIdentity,
        creation: DependencySymbolIndexCreationMetadata,
        mut source_inputs: Vec<DependencySymbolIndexSourceInput>,
        counts: DependencySymbolIndexCounts,
        completeness: DependencySymbolIndexCompleteness,
        payload: &DependencySymbolIndexPayload<T>,
    ) -> eyre::Result<Self> {
        identity.validate()?;
        validate_payload_envelope(payload, &identity, &payload.body_schema)?;
        source_inputs.sort();
        validate_source_inputs(&source_inputs, completeness)?;
        let payload_bytes = serialize_payload(payload)?;
        let manifest = Self {
            schema: DEPENDENCY_SYMBOL_INDEX_MANIFEST_SCHEMA.to_owned(),
            format_fingerprint: identity
                .projection
                .format
                .index_algorithm_fingerprint
                .clone(),
            identity,
            creation,
            source_inputs,
            counts,
            completeness,
            payload: DependencySymbolIndexPayloadDescriptor {
                schema: DEPENDENCY_SYMBOL_INDEX_PAYLOAD_SCHEMA.to_owned(),
                file: DEPENDENCY_SYMBOL_INDEX_PAYLOAD_FILE.to_owned(),
                hash: content_hash(&payload_bytes),
                size_bytes: u64::try_from(payload_bytes.len())
                    .wrap_err("dependency symbol index payload is too large")?,
            },
        };
        validate_manifest_shape(&manifest)?;
        Ok(manifest)
    }

    /// Construct a manifest for an already-streamed payload without loading
    /// that payload into memory.
    ///
    /// # Errors
    ///
    /// Returns an error for invalid identity/source metadata or when the
    /// prepared payload cannot be hashed and measured.
    pub fn for_prepared_payload(
        identity: DependencySymbolIndexIdentity,
        creation: DependencySymbolIndexCreationMetadata,
        mut source_inputs: Vec<DependencySymbolIndexSourceInput>,
        counts: DependencySymbolIndexCounts,
        completeness: DependencySymbolIndexCompleteness,
        payload_schema: &str,
        payload_path: &Path,
    ) -> eyre::Result<Self> {
        identity.validate()?;
        if payload_schema.trim().is_empty()
            || identity.projection.format.payload_schema != payload_schema
        {
            eyre::bail!("prepared payload schema does not match the index identity");
        }
        source_inputs.sort();
        validate_source_inputs(&source_inputs, completeness)?;
        let (hash, size_bytes) = content_hash_file(payload_path)?;
        let manifest = Self {
            schema: DEPENDENCY_SYMBOL_INDEX_MANIFEST_SCHEMA.to_owned(),
            format_fingerprint: identity
                .projection
                .format
                .index_algorithm_fingerprint
                .clone(),
            identity,
            creation,
            source_inputs,
            counts,
            completeness,
            payload: DependencySymbolIndexPayloadDescriptor {
                schema: payload_schema.to_owned(),
                file: DEPENDENCY_SYMBOL_INDEX_PAYLOAD_FILE.to_owned(),
                hash,
                size_bytes,
            },
        };
        validate_manifest_shape(&manifest)?;
        Ok(manifest)
    }
}

impl DependencySymbolIndexStore {
    #[must_use]
    pub const fn new(cache_home: CacheHome) -> Self {
        Self { cache_home }
    }

    /// Return the process-coordination lock used by memory-intensive live
    /// branch queries.
    ///
    /// Unlike publication locks, this lock is deliberately shared by every
    /// dependency-index identity. The scarce resource is machine memory while
    /// Java parser workers are active, so two different branches must not run
    /// their live indexing phases concurrently either.
    #[must_use]
    pub fn live_query_lock_path(&self) -> PathBuf {
        self.cache_home
            .join("minecraft-toolchain")
            .join("symbol-index")
            .join(format!("v{DEPENDENCY_SYMBOL_INDEX_STORE_FORMAT_VERSION}"))
            .join(".locks")
            .join("live-query.lock")
    }

    /// Compute portable and machine-concrete paths without touching the filesystem.
    ///
    /// # Errors
    ///
    /// Returns an error when the identity digest is malformed.
    pub fn layout(
        &self,
        identity: &DependencySymbolIndexIdentity,
    ) -> eyre::Result<DependencySymbolIndexLayout> {
        let key = identity.cache_key()?;
        let portable_root = format!("{DEPENDENCY_SYMBOL_INDEX_PORTABLE_ROOT}/{key}");
        let concrete_version_root = self
            .cache_home
            .join("minecraft-toolchain")
            .join("symbol-index")
            .join(format!("v{DEPENDENCY_SYMBOL_INDEX_STORE_FORMAT_VERSION}"));
        let concrete_root = concrete_version_root.join(key);
        Ok(DependencySymbolIndexLayout {
            portable: DependencySymbolIndexPortableLayout {
                root: portable_root.clone(),
                manifest: format!("{portable_root}/{DEPENDENCY_SYMBOL_INDEX_MANIFEST_FILE}"),
                payload: format!("{portable_root}/{DEPENDENCY_SYMBOL_INDEX_PAYLOAD_FILE}"),
                lock: format!("{DEPENDENCY_SYMBOL_INDEX_PORTABLE_ROOT}/.locks/{key}.lock"),
            },
            concrete: DependencySymbolIndexConcreteLayout {
                root: concrete_root.clone(),
                manifest: concrete_root.join(DEPENDENCY_SYMBOL_INDEX_MANIFEST_FILE),
                payload: concrete_root.join(DEPENDENCY_SYMBOL_INDEX_PAYLOAD_FILE),
                lock: concrete_version_root
                    .join(".locks")
                    .join(format!("{key}.lock")),
            },
        })
    }

    /// Inspect an expected immutable index without creating directories, locks,
    /// or files.
    ///
    /// # Errors
    ///
    /// Returns an error only when the expected identity itself is invalid.
    pub fn probe(
        &self,
        expected: &DependencySymbolIndexIdentity,
    ) -> eyre::Result<DependencySymbolIndexProbe> {
        expected.validate()?;
        let layout = self.layout(expected)?;
        Ok(probe_layout(expected, layout))
    }

    /// Load a payload only after validating the manifest identity and payload bytes.
    ///
    /// # Errors
    ///
    /// Returns an error for missing, stale, incomplete-and-unloadable, corrupt,
    /// truncated, or schema-incompatible entries.
    pub fn load<T: Facet<'static>>(
        &self,
        expected: &DependencySymbolIndexIdentity,
        expected_body_schema: &str,
    ) -> eyre::Result<LoadedDependencySymbolIndex<T>> {
        expected.validate()?;
        let layout = self.layout(expected)?;
        let (manifest, payload_bytes) = read_validated_entry(&layout.concrete.root, expected)?;
        let payload_text = std::str::from_utf8(&payload_bytes)
            .wrap_err("dependency symbol index payload is not UTF-8 JSON")?;
        let payload: DependencySymbolIndexPayload<T> = facet_json::from_str(payload_text)
            .wrap_err("Failed to decode dependency symbol index payload")?;
        validate_payload_envelope(&payload, expected, expected_body_schema)?;
        Ok(LoadedDependencySymbolIndex { manifest, payload })
    }

    /// Stage, authenticate, and atomically publish an immutable index under a
    /// scoped artifact lock.
    ///
    /// # Errors
    ///
    /// Returns an error for invalid input, lock/filesystem failures, publication
    /// validation failures, or a different payload already stored under the
    /// same semantic identity.
    pub fn publish<T: Facet<'static>>(
        &self,
        manifest: &DependencySymbolIndexManifest,
        payload: &DependencySymbolIndexPayload<T>,
    ) -> eyre::Result<DependencySymbolIndexPublishOutcome> {
        self.publish_with_precommit(manifest, payload, |_| Ok(()))
    }

    /// Atomically publish a payload that was produced as a bounded stream.
    ///
    /// # Errors
    ///
    /// Returns an error for authentication, locking, staging, or atomic
    /// replacement failures. Existing valid data is preserved on every error.
    pub fn publish_prepared(
        &self,
        manifest: &DependencySymbolIndexManifest,
        payload_path: &Path,
    ) -> eyre::Result<DependencySymbolIndexPublishOutcome> {
        manifest.identity.validate()?;
        validate_manifest_shape(manifest)?;
        validate_manifest_payload_file(manifest, payload_path)?;
        let manifest_bytes = facet_json::to_string_pretty(manifest)
            .wrap_err("Failed to serialize dependency symbol index manifest")?
            .into_bytes();
        let layout = self.layout(&manifest.identity)?;
        let version_root = layout.concrete.root.parent().ok_or_else(|| {
            eyre::eyre!(
                "dependency symbol index identity path has no version-root parent: {}",
                layout.concrete.root.display()
            )
        })?;
        fs::create_dir_all(version_root).wrap_err_with(|| {
            format!(
                "Failed to create dependency symbol index root {}",
                version_root.display()
            )
        })?;
        let _lock = ArtifactLock::acquire(
            &layout.concrete.lock,
            format!("dependency symbol index {}", manifest.identity.digest),
        )?;
        let temporary = tempfile::Builder::new()
            .prefix(".sfm-symbol-index-")
            .tempdir_in(version_root)
            .wrap_err_with(|| {
                format!(
                    "Failed to create staged dependency index under {}",
                    version_root.display()
                )
            })?;
        let staged_root = temporary.path().join("entry");
        fs::create_dir(&staged_root)
            .wrap_err_with(|| format!("Failed to create {}", staged_root.display()))?;
        copy_synced(
            payload_path,
            &staged_root.join(DEPENDENCY_SYMBOL_INDEX_PAYLOAD_FILE),
        )?;
        write_synced(
            &staged_root.join(DEPENDENCY_SYMBOL_INDEX_MANIFEST_FILE),
            &manifest_bytes,
        )?;
        read_validated_manifest(&staged_root, &manifest.identity)
            .wrap_err("Staged dependency symbol index failed validation")?;

        let existing_probe = probe_layout(&manifest.identity, layout.clone());
        if existing_probe.loadable
            && existing_probe.status == DependencySymbolIndexProbeStatus::Ready
        {
            let existing_manifest = read_manifest(&layout.concrete.manifest)?;
            if existing_manifest.payload.hash == manifest.payload.hash
                && existing_manifest.payload.size_bytes == manifest.payload.size_bytes
            {
                return Ok(DependencySymbolIndexPublishOutcome::AlreadyPresent);
            }
            eyre::bail!(
                "dependency symbol index identity collision: {} already authenticates a different payload; existing valid data was preserved",
                manifest.identity.digest
            );
        }
        replace_directory_atomically(
            &staged_root,
            &layout.concrete.root,
            temporary.path(),
            &manifest.identity,
        )?;
        Ok(DependencySymbolIndexPublishOutcome::Published)
    }

    /// Validate and locate a streamed payload without reading it wholesale.
    ///
    /// # Errors
    ///
    /// Returns an error for missing, stale, corrupt, or schema-incompatible
    /// entries.
    pub fn validated_payload(
        &self,
        expected: &DependencySymbolIndexIdentity,
        expected_payload_schema: &str,
    ) -> eyre::Result<(DependencySymbolIndexManifest, PathBuf)> {
        let layout = self.layout(expected)?;
        let manifest = read_validated_manifest(&layout.concrete.root, expected)?;
        if manifest.payload.schema != expected_payload_schema {
            eyre::bail!(
                "dependency symbol index payload schema `{}` does not match `{expected_payload_schema}`",
                manifest.payload.schema
            );
        }
        Ok((manifest, layout.concrete.payload))
    }

    fn publish_with_precommit<T: Facet<'static>>(
        &self,
        manifest: &DependencySymbolIndexManifest,
        payload: &DependencySymbolIndexPayload<T>,
        precommit: impl FnOnce(&Path) -> eyre::Result<()>,
    ) -> eyre::Result<DependencySymbolIndexPublishOutcome> {
        manifest.identity.validate()?;
        validate_payload_envelope(payload, &manifest.identity, &payload.body_schema)?;
        validate_manifest_shape(manifest)?;
        let payload_bytes = serialize_payload(payload)?;
        validate_manifest_payload(manifest, &payload_bytes)?;
        let manifest_bytes = facet_json::to_string_pretty(manifest)
            .wrap_err("Failed to serialize dependency symbol index manifest")?
            .into_bytes();
        let layout = self.layout(&manifest.identity)?;
        let version_root = layout
            .concrete
            .root
            .parent()
            .expect("identity directory always has a version-root parent");
        fs::create_dir_all(version_root).wrap_err_with(|| {
            format!(
                "Failed to create dependency symbol index root {}",
                version_root.display()
            )
        })?;
        let _lock = ArtifactLock::acquire(
            &layout.concrete.lock,
            format!("dependency symbol index {}", manifest.identity.digest),
        )?;

        let temporary = tempfile::Builder::new()
            .prefix(".sfm-symbol-index-")
            .tempdir_in(version_root)
            .wrap_err_with(|| {
                format!(
                    "Failed to create staged dependency index under {}",
                    version_root.display()
                )
            })?;
        let staged_root = temporary.path().join("entry");
        fs::create_dir(&staged_root)
            .wrap_err_with(|| format!("Failed to create {}", staged_root.display()))?;
        write_synced(
            &staged_root.join(DEPENDENCY_SYMBOL_INDEX_PAYLOAD_FILE),
            &payload_bytes,
        )?;
        write_synced(
            &staged_root.join(DEPENDENCY_SYMBOL_INDEX_MANIFEST_FILE),
            &manifest_bytes,
        )?;
        read_validated_entry(&staged_root, &manifest.identity)
            .wrap_err("Staged dependency symbol index failed validation")?;
        precommit(&staged_root)?;

        let existing_probe = probe_layout(&manifest.identity, layout.clone());
        if existing_probe.loadable
            && existing_probe.status == DependencySymbolIndexProbeStatus::Ready
        {
            let existing_manifest = read_manifest(&layout.concrete.manifest)?;
            if existing_manifest.payload.hash == manifest.payload.hash
                && existing_manifest.payload.size_bytes == manifest.payload.size_bytes
            {
                return Ok(DependencySymbolIndexPublishOutcome::AlreadyPresent);
            }
            eyre::bail!(
                "dependency symbol index identity collision: {} already authenticates a different payload; existing valid data was preserved",
                manifest.identity.digest
            );
        }

        replace_directory_atomically(
            &staged_root,
            &layout.concrete.root,
            temporary.path(),
            &manifest.identity,
        )?;
        Ok(DependencySymbolIndexPublishOutcome::Published)
    }
}

fn probe_layout(
    expected: &DependencySymbolIndexIdentity,
    layout: DependencySymbolIndexLayout,
) -> DependencySymbolIndexProbe {
    let expected_identity = expected.digest.clone();
    if !layout.concrete.root.exists() {
        return DependencySymbolIndexProbe {
            status: DependencySymbolIndexProbeStatus::Missing,
            loadable: false,
            reason: "dependency symbol index directory does not exist".to_owned(),
            expected_identity,
            observed_identity: None,
            layout,
        };
    }
    let manifest = match read_manifest(&layout.concrete.manifest) {
        Ok(manifest) => manifest,
        Err(error) => {
            return DependencySymbolIndexProbe {
                status: DependencySymbolIndexProbeStatus::Partial,
                loadable: false,
                reason: format!("manifest is missing or invalid: {error}"),
                expected_identity,
                observed_identity: None,
                layout,
            };
        }
    };
    let observed_identity = Some(manifest.identity.digest.clone());
    if let Err(error) = manifest.identity.validate() {
        return DependencySymbolIndexProbe {
            status: DependencySymbolIndexProbeStatus::Partial,
            loadable: false,
            reason: format!("manifest identity is invalid: {error}"),
            expected_identity,
            observed_identity,
            layout,
        };
    }
    if manifest.identity != *expected {
        return DependencySymbolIndexProbe {
            status: DependencySymbolIndexProbeStatus::Stale,
            loadable: false,
            reason: "manifest identity does not match the current semantic inputs".to_owned(),
            expected_identity,
            observed_identity,
            layout,
        };
    }
    if let Err(error) = validate_manifest_shape(&manifest)
        .and_then(|()| validate_manifest_payload_file(&manifest, &layout.concrete.payload))
    {
        return DependencySymbolIndexProbe {
            status: DependencySymbolIndexProbeStatus::Partial,
            loadable: false,
            reason: format!("manifest or payload validation failed: {error}"),
            expected_identity,
            observed_identity,
            layout,
        };
    }
    match manifest.completeness {
        DependencySymbolIndexCompleteness::Complete => DependencySymbolIndexProbe {
            status: DependencySymbolIndexProbeStatus::Ready,
            loadable: true,
            reason: "manifest and payload are valid and complete".to_owned(),
            expected_identity,
            observed_identity,
            layout,
        },
        DependencySymbolIndexCompleteness::Partial => DependencySymbolIndexProbe {
            status: DependencySymbolIndexProbeStatus::Partial,
            loadable: true,
            reason: "manifest and payload are valid but dependency coverage is partial".to_owned(),
            expected_identity,
            observed_identity,
            layout,
        },
    }
}

fn read_validated_entry(
    root: &Path,
    expected: &DependencySymbolIndexIdentity,
) -> eyre::Result<(DependencySymbolIndexManifest, Vec<u8>)> {
    let manifest = read_validated_manifest(root, expected)?;
    let payload_path = root.join(DEPENDENCY_SYMBOL_INDEX_PAYLOAD_FILE);
    let payload_bytes = fs::read(&payload_path)
        .wrap_err_with(|| format!("Failed to read {}", payload_path.display()))?;
    Ok((manifest, payload_bytes))
}

fn read_validated_manifest(
    root: &Path,
    expected: &DependencySymbolIndexIdentity,
) -> eyre::Result<DependencySymbolIndexManifest> {
    let manifest_path = root.join(DEPENDENCY_SYMBOL_INDEX_MANIFEST_FILE);
    let payload_path = root.join(DEPENDENCY_SYMBOL_INDEX_PAYLOAD_FILE);
    let manifest = read_manifest(&manifest_path)?;
    validate_manifest_shape(&manifest)?;
    manifest.identity.validate()?;
    if manifest.identity != *expected {
        eyre::bail!(
            "dependency symbol index is stale: expected {}, found {}",
            expected.digest,
            manifest.identity.digest
        );
    }
    validate_manifest_payload_file(&manifest, &payload_path)?;
    Ok(manifest)
}

fn read_manifest(path: &Path) -> eyre::Result<DependencySymbolIndexManifest> {
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let input = std::str::from_utf8(&bytes)
        .wrap_err_with(|| format!("{} is not UTF-8 JSON", path.display()))?;
    facet_json::from_str(input).wrap_err_with(|| {
        format!(
            "Failed to decode dependency index manifest {}",
            path.display()
        )
    })
}

fn validate_manifest_shape(manifest: &DependencySymbolIndexManifest) -> eyre::Result<()> {
    if manifest.schema != DEPENDENCY_SYMBOL_INDEX_MANIFEST_SCHEMA {
        eyre::bail!(
            "manifest schema `{}` does not match `{DEPENDENCY_SYMBOL_INDEX_MANIFEST_SCHEMA}`",
            manifest.schema
        );
    }
    manifest.identity.validate()?;
    if manifest.identity.projection.format.store_format_version
        != DEPENDENCY_SYMBOL_INDEX_STORE_FORMAT_VERSION
    {
        eyre::bail!(
            "manifest store format version {} is not supported by v{}",
            manifest.identity.projection.format.store_format_version,
            DEPENDENCY_SYMBOL_INDEX_STORE_FORMAT_VERSION
        );
    }
    if manifest.identity.projection.format.payload_schema != manifest.payload.schema {
        eyre::bail!("manifest payload schema is incompatible with this store");
    }
    if manifest.payload.file != DEPENDENCY_SYMBOL_INDEX_PAYLOAD_FILE {
        eyre::bail!(
            "manifest payload file `{}` is not the fixed portable payload name",
            manifest.payload.file
        );
    }
    if manifest.format_fingerprint
        != manifest
            .identity
            .projection
            .format
            .index_algorithm_fingerprint
    {
        eyre::bail!("manifest format fingerprint does not match its identity");
    }
    validate_source_inputs(&manifest.source_inputs, manifest.completeness)?;
    Ok(())
}

fn validate_manifest_payload(
    manifest: &DependencySymbolIndexManifest,
    payload_bytes: &[u8],
) -> eyre::Result<()> {
    let actual_size = u64::try_from(payload_bytes.len())
        .wrap_err("dependency symbol index payload is too large")?;
    if actual_size != manifest.payload.size_bytes {
        eyre::bail!(
            "dependency symbol index payload size mismatch: expected {}, found {actual_size}",
            manifest.payload.size_bytes
        );
    }
    let actual_hash = content_hash(payload_bytes);
    if actual_hash != manifest.payload.hash {
        eyre::bail!(
            "dependency symbol index payload hash mismatch: expected {}, found {actual_hash}",
            manifest.payload.hash
        );
    }
    Ok(())
}

fn validate_manifest_payload_file(
    manifest: &DependencySymbolIndexManifest,
    payload_path: &Path,
) -> eyre::Result<()> {
    let (actual_hash, actual_size) = content_hash_file(payload_path)?;
    if actual_size != manifest.payload.size_bytes {
        eyre::bail!(
            "dependency symbol index payload size mismatch: expected {}, found {actual_size}",
            manifest.payload.size_bytes
        );
    }
    if actual_hash != manifest.payload.hash {
        eyre::bail!(
            "dependency symbol index payload hash mismatch: expected {}, found {actual_hash}",
            manifest.payload.hash
        );
    }
    Ok(())
}

fn validate_payload_envelope<T>(
    payload: &DependencySymbolIndexPayload<T>,
    expected: &DependencySymbolIndexIdentity,
    expected_body_schema: &str,
) -> eyre::Result<()> {
    if payload.schema != DEPENDENCY_SYMBOL_INDEX_PAYLOAD_SCHEMA {
        eyre::bail!("dependency symbol index payload wrapper schema is unsupported");
    }
    payload.identity.validate()?;
    if payload.identity != *expected {
        eyre::bail!("dependency symbol index payload identity does not match its manifest");
    }
    if expected_body_schema.trim().is_empty() || payload.body_schema != expected_body_schema {
        eyre::bail!(
            "dependency symbol index body schema `{}` does not match `{expected_body_schema}`",
            payload.body_schema
        );
    }
    Ok(())
}

fn validate_source_inputs(
    inputs: &[DependencySymbolIndexSourceInput],
    completeness: DependencySymbolIndexCompleteness,
) -> eyre::Result<()> {
    for input in inputs {
        for (name, value) in [
            ("dependency", input.dependency.as_str()),
            ("component", input.component.as_str()),
            ("provider", input.provider.as_str()),
            ("fingerprint", input.fingerprint.as_str()),
        ] {
            if value.trim().is_empty() {
                eyre::bail!("dependency index source input {name} must not be empty");
            }
        }
        validate_portable_origin(&input.portable_origin)?;
    }
    if completeness == DependencySymbolIndexCompleteness::Complete
        && inputs
            .iter()
            .any(|input| input.status != DependencySymbolIndexInputStatus::Ready)
    {
        eyre::bail!("a complete dependency index cannot contain non-ready source inputs");
    }
    Ok(())
}

fn validate_portable_origin(origin: &str) -> eyre::Result<()> {
    if origin.is_empty()
        || origin.contains('\\')
        || origin.starts_with('/')
        || origin.starts_with("//")
        || origin.as_bytes().get(1) == Some(&b':')
        || Path::new(origin)
            .components()
            .any(|component| !matches!(component, std::path::Component::Normal(_)))
    {
        eyre::bail!("dependency index source origin `{origin}` is not portable");
    }
    Ok(())
}

fn serialize_payload<T: Facet<'static>>(
    payload: &DependencySymbolIndexPayload<T>,
) -> eyre::Result<Vec<u8>> {
    Ok(facet_json::to_string(payload)
        .wrap_err("Failed to serialize dependency symbol index payload")?
        .into_bytes())
}

fn content_hash(bytes: &[u8]) -> String {
    format!("blake3:{}", blake3::hash(bytes).to_hex())
}

fn content_hash_file(path: &Path) -> eyre::Result<(String, u64)> {
    let mut file = File::open(path)
        .wrap_err_with(|| format!("Failed to open dependency index payload {}", path.display()))?;
    let mut hasher = blake3::Hasher::new();
    let mut size = 0_u64;
    let mut buffer = vec![0_u8; 64 * 1024].into_boxed_slice();
    loop {
        let read = file
            .read(&mut buffer)
            .wrap_err_with(|| format!("Failed to read {}", path.display()))?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
        size = size.saturating_add(u64::try_from(read).unwrap_or(u64::MAX));
    }
    Ok((format!("blake3:{}", hasher.finalize().to_hex()), size))
}

fn write_synced(path: &Path, bytes: &[u8]) -> eyre::Result<()> {
    let mut file =
        File::create(path).wrap_err_with(|| format!("Failed to create {}", path.display()))?;
    file.write_all(bytes)
        .wrap_err_with(|| format!("Failed to write {}", path.display()))?;
    file.sync_all()
        .wrap_err_with(|| format!("Failed to sync {}", path.display()))
}

fn copy_synced(source: &Path, destination: &Path) -> eyre::Result<()> {
    fs::copy(source, destination).wrap_err_with(|| {
        format!(
            "Failed to copy dependency index payload {} to {}",
            source.display(),
            destination.display()
        )
    })?;
    File::options()
        .write(true)
        .open(destination)
        .wrap_err_with(|| format!("Failed to reopen {}", destination.display()))?
        .sync_all()
        .wrap_err_with(|| format!("Failed to sync {}", destination.display()))
}

fn replace_directory_atomically(
    staged: &Path,
    destination: &Path,
    temporary_root: &Path,
    expected: &DependencySymbolIndexIdentity,
) -> eyre::Result<()> {
    let backup = temporary_root.join("previous");
    let had_previous = destination.exists();
    if had_previous {
        fs::rename(destination, &backup).wrap_err_with(|| {
            format!(
                "Failed to stage existing dependency index {} for replacement",
                destination.display()
            )
        })?;
    }
    if let Err(error) = fs::rename(staged, destination) {
        if had_previous {
            let _restore_result = fs::rename(&backup, destination);
        }
        return Err(error).wrap_err_with(|| {
            format!(
                "Failed to publish dependency symbol index {}",
                destination.display()
            )
        });
    }
    if let Err(validation_error) = read_validated_entry(destination, expected) {
        let removal = fs::remove_dir_all(destination);
        let restoration = had_previous.then(|| fs::rename(&backup, destination));
        if let Err(removal_error) = removal {
            return Err(validation_error).wrap_err(format!(
                "Published index failed validation and could not be removed: {removal_error}"
            ));
        }
        if let Some(Err(restoration_error)) = restoration {
            return Err(validation_error).wrap_err(format!(
                "Published index failed validation and the previous entry could not be restored: {restoration_error}"
            ));
        }
        return Err(validation_error).wrap_err("Published dependency index failed validation");
    }
    if had_previous {
        match fs::remove_dir_all(&backup) {
            Ok(()) => {}
            Err(error) if error.kind() == ErrorKind::NotFound => {}
            Err(error) => {
                tracing::warn!(
                    path = %backup.display(),
                    error = %error,
                    "failed to remove replaced dependency index backup"
                );
            }
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::super::dependency_index_identity::DEPENDENCY_SYMBOL_INDEX_IDENTITY_SCHEMA;
    use super::super::dependency_index_identity::DependencyArtifactProjection;
    use super::super::dependency_index_identity::DependencyComponentProjection;
    use super::super::dependency_index_identity::DependencyIndexContextProjection;
    use super::super::dependency_index_identity::DependencyIndexFormatProjection;
    use super::super::dependency_index_identity::DependencyIndexParserProjection;
    use super::super::dependency_index_identity::DependencyProjection;
    use super::super::dependency_index_identity::DependencyRepositoryProjection;
    use super::super::dependency_index_identity::DependencySemanticProperty;
    use super::super::dependency_index_identity::DependencySourceProviderKind;
    use super::super::dependency_index_identity::DependencySourceProviderProjection;
    use super::super::dependency_index_identity::DependencySymbolIndexIdentityProjection;
    use super::super::dependency_index_identity::EffectiveDependencyLockProjection;
    use super::*;
    use crate::java_analysis::DependencyResolutionSelection;
    use std::sync::Arc;
    use std::sync::Barrier;

    const BODY_SCHEMA: &str = "sfm.dependency-symbol-index-test-body/1";

    #[derive(Clone, Debug, Eq, Facet, PartialEq)]
    struct TestBody {
        symbols: Vec<String>,
    }

    #[test]
    fn dependency_index_layout_is_portable_but_relocates_with_cache_home() {
        let identity = fixture_identity();
        let first = DependencySymbolIndexStore::new(CacheHome(PathBuf::from("cache-a")))
            .layout(&identity)
            .expect("first layout");
        let second = DependencySymbolIndexStore::new(CacheHome(PathBuf::from("cache-b")))
            .layout(&identity)
            .expect("second layout");

        assert_eq!(first.portable, second.portable);
        assert_ne!(first.concrete, second.concrete);
        assert!(
            first
                .portable
                .root
                .starts_with(DEPENDENCY_SYMBOL_INDEX_PORTABLE_ROOT)
        );
        assert!(!Path::new(&first.portable.root).is_absolute());
        assert_eq!(identity, fixture_identity());
    }

    #[test]
    fn live_query_lock_serializes_independent_top_level_queries() {
        let directory = tempfile::tempdir().expect("cache root");
        let store = DependencySymbolIndexStore::new(CacheHome(directory.path().to_path_buf()));
        let lock_path = store.live_query_lock_path();
        let first = ArtifactLock::acquire(&lock_path, "first live query")
            .expect("first query lock should be acquired");
        let (started_tx, started_rx) = std::sync::mpsc::channel();
        let (acquired_tx, acquired_rx) = std::sync::mpsc::channel();
        let second_path = lock_path.clone();
        let second = std::thread::spawn(move || {
            started_tx.send(()).expect("announce second query");
            let lock = ArtifactLock::acquire(&second_path, "second live query")
                .expect("second query should acquire after release");
            acquired_tx.send(()).expect("announce acquired query lock");
            drop(lock);
        });

        started_rx
            .recv_timeout(std::time::Duration::from_secs(1))
            .expect("second query should start");
        std::thread::sleep(std::time::Duration::from_millis(50));
        assert!(
            matches!(
                acquired_rx.try_recv(),
                Err(std::sync::mpsc::TryRecvError::Empty)
            ),
            "second query must wait while the first owns the live-query lock"
        );

        drop(first);
        acquired_rx
            .recv_timeout(std::time::Duration::from_secs(1))
            .expect("second query should proceed after the first releases the lock");
        second.join().expect("second query thread should finish");

        let relocated = DependencySymbolIndexStore::new(CacheHome(PathBuf::from("other-cache")))
            .live_query_lock_path();
        assert_ne!(lock_path, relocated);
    }

    #[test]
    fn dependency_index_probe_distinguishes_missing_ready_and_declared_partial() {
        let directory = tempfile::tempdir().expect("cache root");
        let store = DependencySymbolIndexStore::new(CacheHome(directory.path().to_path_buf()));
        let identity = fixture_identity();
        let missing = store.probe(&identity).expect("missing probe");
        assert_eq!(missing.status, DependencySymbolIndexProbeStatus::Missing);
        assert!(!missing.loadable);

        let (ready_manifest, ready_payload) = publication(
            identity.clone(),
            DependencySymbolIndexCompleteness::Complete,
            vec![ready_input()],
            "ready",
        );
        store
            .publish(&ready_manifest, &ready_payload)
            .expect("publish ready");
        let ready = store.probe(&identity).expect("ready probe");
        assert_eq!(ready.status, DependencySymbolIndexProbeStatus::Ready);
        assert!(ready.loadable);

        let partial_directory = tempfile::tempdir().expect("partial cache root");
        let partial_store =
            DependencySymbolIndexStore::new(CacheHome(partial_directory.path().to_path_buf()));
        let mut unavailable = ready_input();
        unavailable.status = DependencySymbolIndexInputStatus::Unavailable;
        let (partial_manifest, partial_payload) = publication(
            identity.clone(),
            DependencySymbolIndexCompleteness::Partial,
            vec![unavailable],
            "partial",
        );
        partial_store
            .publish(&partial_manifest, &partial_payload)
            .expect("publish partial");
        let partial = partial_store.probe(&identity).expect("partial probe");
        assert_eq!(partial.status, DependencySymbolIndexProbeStatus::Partial);
        assert!(partial.loadable);
    }

    #[test]
    fn prepared_stream_payload_is_hashed_published_and_located_without_buffering() {
        use crate::java_analysis::DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA;
        use crate::java_analysis::DependencyJavaSymbolIndexStreamHeader;
        use crate::java_analysis::JavaSourceSpanOutput;
        use crate::java_analysis::JavaSymbolDefinitionOutput;
        use crate::java_analysis::JavaSymbolIdentityOutput;
        use crate::java_analysis::JavaSymbolKind;
        use crate::java_analysis::ResolutionConfidence;
        use crate::java_analysis::scan_dependency_java_symbol_index;
        use std::collections::BTreeSet;

        let directory = tempfile::tempdir().expect("cache root");
        let prepared = tempfile::NamedTempFile::new().expect("prepared payload");
        let store = DependencySymbolIndexStore::new(CacheHome(directory.path().to_path_buf()));
        let identity = fixture_identity_with_schema(DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA);
        let span = JavaSourceSpanOutput {
            path: "dependency/example/A.java".to_owned(),
            source_set: "dependency:example".to_owned(),
            source_hash: "blake3:source".to_owned(),
            start_byte: 0,
            end_byte: 1,
            start_line: 1,
            start_column: 1,
            end_line: 1,
            end_column: 2,
        };
        let definition = JavaSymbolDefinitionOutput {
            symbol: JavaSymbolIdentityOutput {
                kind: JavaSymbolKind::Class,
                owner: "example".to_owned(),
                name: "A".to_owned(),
                descriptor: None,
                qualified_name: "example.A".to_owned(),
            },
            identifier_span: span.clone(),
            declaration_span: span,
            confidence: ResolutionConfidence::Resolved,
        };
        fs::write(
            prepared.path(),
            format!(
                concat!(
                    "{}\n",
                    "definition\tclass\texample\tA\t\texample.A\tdependency:example\t{}\n",
                    "definition\tclass\texample\tNeverDecoded\t\texample.NeverDecoded\tdependency:example\tnot-json\n",
                    "usage\tmethod\texample.NeverDecoded\trun\t()V\texample.NeverDecoded.run()V\t\tnot-json\n",
                ),
                facet_json::to_string(&DependencyJavaSymbolIndexStreamHeader::new(
                    identity.clone()
                ))
                .expect("header"),
                facet_json::to_string(&definition).expect("definition")
            ),
        )
        .expect("stream payload");
        let manifest = DependencySymbolIndexManifest::for_prepared_payload(
            identity.clone(),
            DependencySymbolIndexCreationMetadata {
                created_at_utc: "2026-08-09T00:00:00Z".to_owned(),
                producer: "unit-test".to_owned(),
                refresh_duration_ms: 1,
            },
            vec![ready_input()],
            DependencySymbolIndexCounts {
                source_files: 1,
                definitions: 2,
                usages: 1,
                diagnostics: 0,
            },
            DependencySymbolIndexCompleteness::Complete,
            DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA,
            prepared.path(),
        )
        .expect("stream manifest");

        assert_eq!(
            store
                .publish_prepared(&manifest, prepared.path())
                .expect("publish prepared stream"),
            DependencySymbolIndexPublishOutcome::Published
        );
        let (loaded_manifest, payload_path) = store
            .validated_payload(&identity, DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA)
            .expect("validated stream path");
        assert_eq!(loaded_manifest, manifest);
        let resolution_identifiers = BTreeSet::from(["A".to_owned()]);
        let resolution_member_accesses = BTreeSet::new();
        let query_scan = scan_dependency_java_symbol_index(
            &payload_path,
            &identity,
            &manifest.counts,
            DependencyResolutionSelection::MatchingVocabulary {
                identifiers: &resolution_identifiers,
                member_accesses: &resolution_member_accesses,
            },
            |_, _, _, _, qualified_name| qualified_name == "example.A",
            |_, _, _, _, _| false,
        )
        .expect("scan streamed payload");
        assert_eq!(query_scan.body.definitions, vec![definition.clone()]);
        assert_eq!(query_scan.resolution.len(), 1);
        assert_eq!(query_scan.resolution[0].symbol, definition.symbol);
    }

    #[test]
    fn dependency_index_probe_rejects_corrupt_and_truncated_payloads() {
        for corruption in [b"{".as_slice(), b"different valid-looking bytes".as_slice()] {
            let directory = tempfile::tempdir().expect("cache root");
            let store = DependencySymbolIndexStore::new(CacheHome(directory.path().to_path_buf()));
            let identity = fixture_identity();
            let (manifest, payload) = publication(
                identity.clone(),
                DependencySymbolIndexCompleteness::Complete,
                vec![ready_input()],
                "original",
            );
            store
                .publish(&manifest, &payload)
                .expect("publish original");
            let layout = store.layout(&identity).expect("layout");
            fs::write(&layout.concrete.payload, corruption).expect("corrupt payload");

            let probe = store.probe(&identity).expect("corrupt probe");
            assert_eq!(probe.status, DependencySymbolIndexProbeStatus::Partial);
            assert!(!probe.loadable);
            let _error = store
                .load::<TestBody>(&identity, BODY_SCHEMA)
                .expect_err("corrupt payload must not load");
        }
    }

    #[test]
    fn failed_dependency_index_publication_preserves_prior_valid_index() {
        let directory = tempfile::tempdir().expect("cache root");
        let store = DependencySymbolIndexStore::new(CacheHome(directory.path().to_path_buf()));
        let identity = fixture_identity();
        let (first_manifest, first_payload) = publication(
            identity.clone(),
            DependencySymbolIndexCompleteness::Complete,
            vec![ready_input()],
            "first",
        );
        store
            .publish(&first_manifest, &first_payload)
            .expect("publish first");
        let (replacement_manifest, replacement_payload) = publication(
            identity.clone(),
            DependencySymbolIndexCompleteness::Complete,
            vec![ready_input()],
            "replacement",
        );

        let error = store
            .publish_with_precommit(&replacement_manifest, &replacement_payload, |_| {
                eyre::bail!("synthetic cancellation after staging")
            })
            .expect_err("publication must fail");
        assert!(error.to_string().contains("synthetic cancellation"));

        let loaded = store
            .load::<TestBody>(&identity, BODY_SCHEMA)
            .expect("prior index remains loadable");
        assert_eq!(loaded.payload.body.symbols, vec!["first"]);
        assert_eq!(
            store.probe(&identity).expect("probe").status,
            DependencySymbolIndexProbeStatus::Ready
        );
    }

    #[test]
    fn dependency_index_manifest_identity_mismatch_is_stale_and_never_loaded() {
        let directory = tempfile::tempdir().expect("cache root");
        let store = DependencySymbolIndexStore::new(CacheHome(directory.path().to_path_buf()));
        let expected = fixture_identity();
        let mut changed_projection = expected.projection.clone();
        changed_projection.context.minecraft_version = "1.20.1".to_owned();
        let observed = DependencySymbolIndexIdentity::from_projection(changed_projection)
            .expect("changed identity");
        let (manifest, payload) = publication(
            observed,
            DependencySymbolIndexCompleteness::Complete,
            vec![ready_input()],
            "stale",
        );
        let expected_layout = store.layout(&expected).expect("expected layout");
        fs::create_dir_all(&expected_layout.concrete.root).expect("identity directory");
        fs::write(
            &expected_layout.concrete.payload,
            serialize_payload(&payload).expect("payload bytes"),
        )
        .expect("payload");
        fs::write(
            &expected_layout.concrete.manifest,
            facet_json::to_string_pretty(&manifest).expect("manifest JSON"),
        )
        .expect("manifest");

        let probe = store.probe(&expected).expect("stale probe");
        assert_eq!(probe.status, DependencySymbolIndexProbeStatus::Stale);
        assert!(!probe.loadable);
        let _error = store
            .load::<TestBody>(&expected, BODY_SCHEMA)
            .expect_err("stale payload must not load");
    }

    #[test]
    fn dependency_index_concurrent_publication_cannot_mix_manifest_and_payload() {
        let directory = tempfile::tempdir().expect("cache root");
        let store = DependencySymbolIndexStore::new(CacheHome(directory.path().to_path_buf()));
        let identity = fixture_identity();
        let (first_manifest, first_payload) = publication(
            identity.clone(),
            DependencySymbolIndexCompleteness::Complete,
            vec![ready_input()],
            "first",
        );
        let (second_manifest, second_payload) = publication(
            identity.clone(),
            DependencySymbolIndexCompleteness::Complete,
            vec![ready_input()],
            "second",
        );
        let barrier = Arc::new(Barrier::new(2));
        let first_store = store.clone();
        let first_barrier = Arc::clone(&barrier);
        let first = std::thread::spawn(move || {
            first_barrier.wait();
            first_store.publish(&first_manifest, &first_payload)
        });
        let second_store = store.clone();
        let second = std::thread::spawn(move || {
            barrier.wait();
            second_store.publish(&second_manifest, &second_payload)
        });
        let results = [
            first.join().expect("first publisher"),
            second.join().expect("second publisher"),
        ];
        assert_eq!(results.iter().filter(|result| result.is_ok()).count(), 1);
        assert_eq!(results.iter().filter(|result| result.is_err()).count(), 1);

        let loaded = store
            .load::<TestBody>(&identity, BODY_SCHEMA)
            .expect("load concurrent publication");
        assert!(
            loaded.payload.body.symbols == vec!["first"]
                || loaded.payload.body.symbols == vec!["second"]
        );
        assert_eq!(
            store.probe(&identity).expect("coherent probe").status,
            DependencySymbolIndexProbeStatus::Ready
        );
    }

    fn publication(
        identity: DependencySymbolIndexIdentity,
        completeness: DependencySymbolIndexCompleteness,
        inputs: Vec<DependencySymbolIndexSourceInput>,
        symbol: &str,
    ) -> (
        DependencySymbolIndexManifest,
        DependencySymbolIndexPayload<TestBody>,
    ) {
        let payload = DependencySymbolIndexPayload::new(
            identity.clone(),
            BODY_SCHEMA,
            TestBody {
                symbols: vec![symbol.to_owned()],
            },
        );
        let manifest = DependencySymbolIndexManifest::for_payload(
            identity,
            DependencySymbolIndexCreationMetadata {
                created_at_utc: "2026-08-09T00:00:00Z".to_owned(),
                producer: "unit-test".to_owned(),
                refresh_duration_ms: 1,
            },
            inputs,
            DependencySymbolIndexCounts {
                source_files: 1,
                definitions: 1,
                usages: 0,
                diagnostics: 0,
            },
            completeness,
            &payload,
        )
        .expect("manifest");
        (manifest, payload)
    }

    fn ready_input() -> DependencySymbolIndexSourceInput {
        DependencySymbolIndexSourceInput {
            dependency: "minecraft".to_owned(),
            component: "client".to_owned(),
            provider: "platform-client".to_owned(),
            portable_origin: "dependency/minecraft/client/platform-client".to_owned(),
            fingerprint: "blake3:source".to_owned(),
            status: DependencySymbolIndexInputStatus::Ready,
        }
    }

    fn fixture_identity() -> DependencySymbolIndexIdentity {
        fixture_identity_with_schema(DEPENDENCY_SYMBOL_INDEX_PAYLOAD_SCHEMA)
    }

    fn fixture_identity_with_schema(payload_schema: &str) -> DependencySymbolIndexIdentity {
        DependencySymbolIndexIdentity::from_projection(DependencySymbolIndexIdentityProjection {
            schema: DEPENDENCY_SYMBOL_INDEX_IDENTITY_SCHEMA.to_owned(),
            effective_lock: EffectiveDependencyLockProjection {
                minecraft_dependency: "minecraft".to_owned(),
                loader_dependency: "forge".to_owned(),
                repositories: vec![DependencyRepositoryProjection {
                    id: "forge".to_owned(),
                    url: "https://maven.minecraftforge.net".to_owned(),
                }],
                dependencies: vec![DependencyProjection {
                    id: "minecraft".to_owned(),
                    kind: "minecraft".to_owned(),
                    role: "platform".to_owned(),
                    components: vec![DependencyComponentProjection {
                        id: "client".to_owned(),
                        scopes: vec!["compile".to_owned(), "runtime".to_owned()],
                        acquisition: vec![DependencySemanticProperty {
                            key: "requested-version".to_owned(),
                            value: "1.19.2".to_owned(),
                        }],
                        artifact: DependencyArtifactProjection {
                            id: "minecraft-client".to_owned(),
                            content_hash: "blake3:minecraft".to_owned(),
                            resolved_coordinate: None,
                            provenance: "toolchain-generated".to_owned(),
                        },
                        preferred_provider: Some(DependencySourceProviderProjection {
                            id: "platform-client".to_owned(),
                            kind: DependencySourceProviderKind::PlatformPipeline,
                            priority: 0,
                            portable_roots: vec!["src/main/java".to_owned()],
                            declaration: vec![DependencySemanticProperty {
                                key: "kind".to_owned(),
                                value: "minecraft".to_owned(),
                            }],
                            derived_checks: vec![DependencySemanticProperty {
                                key: "fingerprint".to_owned(),
                                value: "blake3:platform".to_owned(),
                            }],
                        }),
                    }],
                }],
            },
            context: DependencyIndexContextProjection {
                minecraft_version: "1.19.2".to_owned(),
                loader_id: "forge".to_owned(),
                loader_version: "43.4.0".to_owned(),
                java_release: "17".to_owned(),
                toolchain_profile: "default".to_owned(),
                active_features: vec!["main".to_owned()],
            },
            parser: DependencyIndexParserProjection {
                parser: "arborium-java".to_owned(),
                parser_version: "2.18.1".to_owned(),
                grammar_fingerprint: "blake3:grammar".to_owned(),
            },
            format: DependencyIndexFormatProjection {
                store_format_version: DEPENDENCY_SYMBOL_INDEX_STORE_FORMAT_VERSION,
                payload_schema: payload_schema.to_owned(),
                index_algorithm_fingerprint: "java-index/1".to_owned(),
            },
        })
        .expect("fixture identity")
    }
}
