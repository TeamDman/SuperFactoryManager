//! Read-only native preflight for an exact catalog-owned Minecraft project.
//!
//! This is an unregistered integration foundation, not a build/run entry point.
//! It never synchronizes outputs, resolves a JDK, acquires dependencies, creates
//! caches, or claims compilation/release parity. Existing named `Check` owns the
//! generated-output transaction. A later shared preparation hook must preserve
//! release check-only policy and guarded development preparation; do not copy
//! that policy here or disguise a projection as a Git branch.

use super::candidate_lock::checked_directory;
use super::candidate_lock::checked_file;
use super::core_catalog::CoreCatalog;
use super::core_catalog::read_bounded_catalog_input;
use super::core_features::FEATURE_DEFINITIONS_PATH;
use super::core_inputs::CORE_METADATA_PATH;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::MAX_CORE_FILE_BYTES;
use super::core_inputs::MAX_CORE_METADATA_BYTES;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::named_root::catalog_projection_root;
use super::projection_catalog::CATALOG_PATH;
use super::projection_catalog::ProjectionEnvironment;
use super::projection_catalog::validate_projection_key;
use super::provenance::CatalogProjectionOwner;
use super::provenance::MAX_MANIFEST_BYTES;
use super::provenance::ProjectionProvenance;
use super::provenance::sha256;
use super::sync::CatalogProjectionIdentity;
use super::sync::MANIFEST_FILE;
use super::sync::ProjectedArtifact;
use super::sync::SyncMode;
use super::sync::sync_catalog_projection;
use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
use crate::toolchain_lockfile_schema::parse_document;
use crate::toolchain_lockfile_schema::version::v3::ArtifactLockfileV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::ToolchainComponentKindV3;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Read as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;

pub const NATIVE_DEPENDENCY_PROFILE: &str = "rust-toolchain";
const LOCKFILE: &str = "sfm-toolchain.lock.json";
const RECEIPT_SCHEMA: &str = "sfm:native_project_preflight@1";
const CACHE_PARENT: &str = "build/sfm-toolchain/native-project";

/// Portable evidence, separate from the actual repository/project paths.
///
/// The profile is explicit, not an implicit switch to newer dependency inputs.
/// These hashes bind every selected source/build output plus its named owner.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct NativeProjectReceipt {
    pub schema: String,
    pub compilation: NativeValidationStatus,
    pub release_compatibility: NativeValidationStatus,
    pub projection_key: String,
    pub environment: ProjectionEnvironment,
    pub target_id: String,
    pub minecraft_version: String,
    pub java_major: u16,
    pub loader: String,
    pub context_identity: String,
    pub project_dir: String,
    pub dependency_profile: String,
    pub catalog_sha256: String,
    pub feature_definitions_sha256: String,
    pub project_inputs_sha256: String,
    pub provenance_sha256: String,
    pub lockfile_sha256: String,
    pub effective_dependencies_sha256: String,
    pub effective_source_exclusions_sha256: String,
    pub files: BTreeMap<String, NativeProjectFile>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum NativeValidationStatus {
    NotPerformed,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct NativeProjectFile {
    pub authored_input: String,
    pub source_sha256: String,
    pub output_sha256: String,
}

/// A checked target, not a synthetic `WorktreeTarget` or native build proof.
///
/// Cache paths are derived only; preflight does not create them. Callers must
/// recheck this receipt after acquisition and immediately before execution.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct NativeProjectTarget {
    pub repo_root: PathBuf,
    pub minecraft_dir: PathBuf,
    pub cache_dir: PathBuf,
    pub cache_identity: String,
    pub receipt: NativeProjectReceipt,
}

impl NativeProjectReceipt {
    /// Stable cache identity includes context, lock/profile and provenance.
    ///
    /// # Errors
    /// Returns an error if the typed, portable receipt cannot be serialized.
    pub fn cache_identity(&self) -> Result<String> {
        Ok(sha256(facet_json::to_string(self)?.as_bytes()))
    }

    /// # Errors
    /// Returns an error if the typed receipt cannot be serialized.
    pub fn to_json(&self) -> Result<String> {
        Ok(facet_json::to_string_pretty(self)?)
    }
}

impl NativeProjectTarget {
    /// Revalidate the complete selected inputs, root policy and current output.
    ///
    /// # Errors
    /// Rejects any changed receipt or guarded path. This is a check, not a lock
    /// held across a later launch; integration must call it at execution gates.
    pub fn recheck(&self) -> Result<()> {
        let current = preflight_native_project(
            &self.repo_root,
            &self.repo_root,
            &self.receipt.projection_key,
            &self.receipt.dependency_profile,
        )?;
        ensure!(
            current == *self,
            "named native generation inputs or target identity changed; retry with the current exact projection"
        );
        Ok(())
    }
}

/// Resolve an already-current exact named project without mutation.
///
/// Compatibility is checked against selected authored lock bytes before any
/// named sync check, cache inspection or acquisition. Schema 2 remains Gradle-
/// only until a genuine released-input native adapter is implemented. Schema 3
/// has no declared profile contract and is deliberately unsupported here.
///
/// # Errors
/// Rejects unsafe/unknown roots and keys, incompatible or undeclared profiles,
/// platform/property mismatches, stale/conflicting generated outputs, changed
/// inputs, invalid provenance, and unsafe existing cache ancestors.
pub fn preflight_native_project(
    repo_root: &Path,
    invocation_dir: &Path,
    projection_key: &str,
    declared_profile: &str,
) -> Result<NativeProjectTarget> {
    ensure!(
        declared_profile == NATIVE_DEPENDENCY_PROFILE,
        "named native preflight requires explicit declared profile `{NATIVE_DEPENDENCY_PROFILE}`, not `{declared_profile}`; no implicit Gradle/Rust profile conversion is supported"
    );
    let loaded = CoreCatalog::load(repo_root, invocation_dir)?;
    let context = loaded.context(projection_key)?;
    let identity = CatalogProjectionIdentity::from_catalog(&loaded.catalog, projection_key)?;
    let metadata_bytes = read_checked(
        &loaded.repo_root,
        CORE_METADATA_PATH,
        MAX_CORE_METADATA_BYTES,
    )?;
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&metadata_bytes)?,
        &loaded.registered_features,
    )?;
    let core = checked_directory(&loaded.repo_root.join(CORE_ROOT))?;
    let inventory = discover_core_source_files(&core)?;
    let selection = select_core_inputs(&metadata, &context, &inventory)?;
    let artifacts = collect_core_artifacts(&core, &selection, &context)?;
    let profile = selected_profile(&artifacts, &identity, declared_profile)?;

    // Reuse the named boundary and Check transaction. Never introduce Apply,
    // a release auto-sync, or a duplicated development generation policy.
    let minecraft_dir = catalog_projection_root(
        &loaded.repo_root,
        &loaded.catalog,
        projection_key,
        &artifacts,
    )?;
    sync_catalog_projection(&minecraft_dir, &identity, &artifacts, SyncMode::Check).wrap_err(
        "named native preflight needs a current owned project; prepare it through the existing named source workflow (release outputs are never auto-synced)",
    )?;
    let provenance_bytes = read_checked(&minecraft_dir, MANIFEST_FILE, MAX_MANIFEST_BYTES as u64)?;
    let provenance = ProjectionProvenance::from_json(std::str::from_utf8(&provenance_bytes)?)?;
    ensure!(
        provenance.target_id == identity.target_id
            && provenance.minecraft_version == identity.minecraft_version
            && provenance.catalog.as_ref()
                == Some(&CatalogProjectionOwner {
                    projection_key: identity.projection_key.clone(),
                    environment: identity.environment,
                    context_identity: identity.context_identity.clone(),
                })
            && provenance.files.len() == artifacts.len(),
        "named native provenance has a different owner or selected file set"
    );
    let files = check_owned_files(&loaded.repo_root, &minecraft_dir, &artifacts, &provenance)?;
    recheck_generation_snapshot(
        &loaded,
        &metadata_bytes,
        &core,
        &inventory,
        &minecraft_dir,
        &provenance_bytes,
    )?;
    let project_dir = portable_path(&loaded.catalog.project_dir(projection_key)?)?;
    let receipt = NativeProjectReceipt {
        schema: RECEIPT_SCHEMA.to_owned(),
        compilation: NativeValidationStatus::NotPerformed,
        release_compatibility: NativeValidationStatus::NotPerformed,
        projection_key: identity.projection_key,
        environment: identity.environment,
        target_id: identity.target_id,
        minecraft_version: identity.minecraft_version,
        java_major: selection.build_target.java_major,
        loader: selection.build_target.loader,
        context_identity: identity.context_identity,
        project_dir,
        dependency_profile: declared_profile.to_owned(),
        catalog_sha256: loaded.catalog_sha256,
        feature_definitions_sha256: loaded.feature_definitions_sha256,
        project_inputs_sha256: sha256(&metadata_bytes),
        provenance_sha256: sha256(&provenance_bytes),
        lockfile_sha256: sha256(&artifacts[LOCKFILE].output_bytes),
        effective_dependencies_sha256: profile.dependencies_sha256,
        effective_source_exclusions_sha256: profile.source_exclusions_sha256,
        files,
    };
    let cache_identity = receipt.cache_identity()?;
    let digest = cache_identity
        .strip_prefix("sha256:")
        .ok_or_else(|| eyre::eyre!("native cache identity has no SHA-256 prefix"))?;
    let cache_relative = format!("{CACHE_PARENT}/{digest}");
    inspect_cache_ancestors(&minecraft_dir, &cache_relative)?;
    Ok(NativeProjectTarget {
        repo_root: loaded.repo_root,
        cache_dir: minecraft_dir.join(cache_relative),
        minecraft_dir,
        cache_identity,
        receipt,
    })
}

fn check_owned_files(
    repo_root: &Path,
    minecraft_dir: &Path,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
    provenance: &ProjectionProvenance,
) -> Result<BTreeMap<String, NativeProjectFile>> {
    let mut files = BTreeMap::new();
    for (output, artifact) in artifacts {
        let source_sha256 = sha256(&artifact.source_bytes);
        let output_sha256 = sha256(&artifact.output_bytes);
        ensure!(
            sha256(&read_checked(
                repo_root,
                &artifact.source_path,
                MAX_CORE_FILE_BYTES
            )?) == source_sha256,
            "selected authored input changed during native preflight: {}",
            artifact.source_path
        );
        ensure!(
            sha256(&read_checked(minecraft_dir, output, MAX_CORE_FILE_BYTES)?) == output_sha256,
            "selected generated output changed during native preflight: {output}"
        );
        let witnessed = provenance
            .files
            .get(output)
            .ok_or_else(|| eyre::eyre!("native provenance does not own `{output}`"))?;
        ensure!(
            witnessed.source_path == artifact.source_path
                && witnessed.source_sha256 == source_sha256
                && witnessed.output_sha256 == output_sha256
                && witnessed.overlay.is_none(),
            "native provenance changed after named Check: {output}"
        );
        files.insert(
            output.clone(),
            NativeProjectFile {
                authored_input: artifact.source_path.clone(),
                source_sha256,
                output_sha256,
            },
        );
    }
    Ok(files)
}

fn recheck_generation_snapshot(
    loaded: &CoreCatalog,
    metadata_bytes: &[u8],
    core: &Path,
    inventory: &BTreeSet<String>,
    minecraft_dir: &Path,
    provenance_bytes: &[u8],
) -> Result<()> {
    ensure!(
        loaded.catalog_sha256
            == sha256(&read_bounded_catalog_input(
                &loaded.repo_root,
                CATALOG_PATH
            )?)
            && loaded.feature_definitions_sha256
                == sha256(&read_bounded_catalog_input(
                    &loaded.repo_root,
                    FEATURE_DEFINITIONS_PATH
                )?)
            && sha256(metadata_bytes)
                == sha256(&read_checked(
                    &loaded.repo_root,
                    CORE_METADATA_PATH,
                    MAX_CORE_METADATA_BYTES
                )?)
            && *inventory == discover_core_source_files(core)?
            && sha256(provenance_bytes)
                == sha256(&read_checked(
                    minecraft_dir,
                    MANIFEST_FILE,
                    MAX_MANIFEST_BYTES as u64
                )?),
        "named native generation inputs changed during preflight"
    );
    Ok(())
}

struct SelectedProfile {
    dependencies_sha256: String,
    source_exclusions_sha256: String,
}

fn selected_profile(
    artifacts: &BTreeMap<String, ProjectedArtifact>,
    identity: &CatalogProjectionIdentity,
    profile: &str,
) -> Result<SelectedProfile> {
    let lock = artifacts
        .get(LOCKFILE)
        .ok_or_else(|| eyre::eyre!("native projection is missing `{LOCKFILE}`"))?;
    let document = parse_document(std::str::from_utf8(&lock.output_bytes)?)?;
    let lock = match document {
        ToolchainLockfileDocument::V4(lock) => lock,
        ToolchainLockfileDocument::V1(_) | ToolchainLockfileDocument::V2 { .. } => {
            eyre::bail!(
                "projection `{}` selects a released schema-1/2 lock: named native compatibility requires a genuine released-input adapter, which is not implemented; use the exact released Gradle inputs, do not migrate or silently substitute schema 4",
                identity.projection_key
            );
        }
        ToolchainLockfileDocument::V3(_) => {
            eyre::bail!(
                "projection `{}` selects schema 3 without a declared native profile; a reviewed explicit unfeatured adapter is required before native preflight",
                identity.projection_key
            );
        }
    };
    ensure!(
        lock.profiles.iter().any(|declared| declared.id == profile),
        "projection `{}` does not declare native dependency profile `{profile}`",
        identity.projection_key
    );
    let effective = lock
        .effective_lockfile(profile)
        .wrap_err("declared native profile is disabled or invalid")?;
    validate_platform_inputs(&effective, artifacts, identity)?;
    let exclusions = lock.effective_source_excludes(profile)?;
    for exclusion in &exclusions {
        ensure!(
            matches!(
                exclusion.source_set.as_str(),
                "main-java" | "gametest-java" | "datagen-java" | "test-java" | "generated-java"
            ),
            "native profile has an unsupported source-exclusion set"
        );
        validate_projection_key(
            exclusion
                .path
                .strip_suffix("/**")
                .unwrap_or(&exclusion.path),
        )
        .wrap_err("native source exclusion is not a safe relative path")?;
    }
    Ok(SelectedProfile {
        dependencies_sha256: sha256(facet_json::to_string(&effective)?.as_bytes()),
        source_exclusions_sha256: sha256(facet_json::to_string(&exclusions)?.as_bytes()),
    })
}

fn validate_platform_inputs(
    effective: &ArtifactLockfileV3,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
    identity: &CatalogProjectionIdentity,
) -> Result<()> {
    let properties = artifacts
        .get("gradle.properties")
        .ok_or_else(|| eyre::eyre!("native projection has no Gradle properties"))?;
    let properties = std::str::from_utf8(&properties.output_bytes)?;
    let neo_version = exact_property(properties, "neo_version")?;
    for (id, expected_kind) in [
        (
            &effective.platform.minecraft_dependency,
            ToolchainComponentKindV3::Minecraft,
        ),
        (
            &effective.platform.loader_dependency,
            ToolchainComponentKindV3::Loader,
        ),
    ] {
        let dependency = effective
            .dependencies
            .iter()
            .find(|dependency| dependency.id == *id)
            .ok_or_else(|| eyre::eyre!("native profile has no platform dependency `{id}`"))?;
        for component in &dependency.components {
            let ComponentAcquisitionV3::Toolchain(acquisition) = &component.declaration.acquisition
            else {
                eyre::bail!("native platform `{id}` has an unsupported non-toolchain declaration");
            };
            ensure!(
                acquisition.kind == expected_kind,
                "native platform kind mismatch for `{id}`"
            );
            let expected = if expected_kind == ToolchainComponentKindV3::Minecraft {
                identity.minecraft_version.clone()
            } else {
                let coordinate = component
                    .derived_checks
                    .resolved_coordinate
                    .as_deref()
                    .ok_or_else(|| eyre::eyre!("native loader has no resolved coordinate"))?;
                let parts = coordinate.split(':').collect::<Vec<_>>();
                ensure!(parts.len() >= 3, "native loader coordinate is incomplete");
                let expected = match (parts[0], parts[1]) {
                    ("net.minecraftforge" | "net.neoforged", "forge") => {
                        format!("{}-{neo_version}", identity.minecraft_version)
                    }
                    ("net.neoforged", "neoforge") => neo_version.to_owned(),
                    _ => eyre::bail!("native loader coordinate is not a supported platform"),
                };
                ensure!(
                    parts[2] == expected,
                    "native loader resolved coordinate disagrees with selected Gradle properties; select coordinated authored build inputs, not a replacement lock alone"
                );
                expected
            };
            ensure!(
                acquisition.requested_version == expected,
                "native profile platform `{id}` disagrees with selected Minecraft/Gradle properties; select coordinated authored build inputs, not a replacement lock alone"
            );
        }
    }
    Ok(())
}

fn exact_property<'a>(properties: &'a str, key: &str) -> Result<&'a str> {
    let values = properties
        .lines()
        .filter(|line| !line.trim_start().starts_with(['#', '!']))
        .filter_map(|line| line.split_once('='))
        .filter_map(|(candidate, value)| (candidate.trim() == key).then_some(value.trim()))
        .collect::<Vec<_>>();
    ensure!(
        values.len() == 1 && !values[0].is_empty(),
        "native projection requires exactly one nonempty `{key}` property"
    );
    Ok(values[0])
}

fn read_checked(root: &Path, relative: &str, limit: u64) -> Result<Vec<u8>> {
    let path = checked_file(root, relative)?;
    let file =
        fs::File::open(path).wrap_err_with(|| format!("cannot read native input `{relative}`"))?;
    ensure!(
        file.metadata()?.len() <= limit,
        "native input `{relative}` exceeds its bounded byte limit"
    );
    let mut bytes = Vec::new();
    file.take(limit + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= limit,
        "native input `{relative}` grew beyond its bounded byte limit"
    );
    Ok(bytes)
}

fn portable_path(path: &Path) -> Result<String> {
    path.components()
        .map(|component| match component {
            Component::Normal(value) => value
                .to_str()
                .map(str::to_owned)
                .ok_or_else(|| eyre::eyre!("native project path is not UTF-8")),
            _ => Err(eyre::eyre!(
                "native project path is not an exact relative path"
            )),
        })
        .collect::<Result<Vec<_>>>()
        .map(|parts| parts.join("/"))
}

fn inspect_cache_ancestors(project: &Path, relative: &str) -> Result<()> {
    let mut current = checked_directory(project)?;
    for part in relative.split('/') {
        for sibling in fs::read_dir(&current)? {
            let sibling = sibling?;
            if let Some(actual) = sibling.file_name().to_str()
                && actual.eq_ignore_ascii_case(part)
            {
                ensure!(
                    actual == part,
                    "native cache path has an existing case alias"
                );
            }
        }
        current.push(part);
        match fs::symlink_metadata(&current) {
            Ok(_) => {
                checked_directory(&current)?;
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
            Err(error) => return Err(error).wrap_err("cannot inspect native cache ancestor"),
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::core_inputs::BuildTargetMetadata;
    use crate::source_projection::core_inputs::InputPredicate;
    use crate::source_projection::core_inputs::InputVariant;
    use crate::toolchain_lockfile_schema::version::v4::ArtifactLockfileV4;
    use crate::toolchain_lockfile_schema::version::v4::FeatureComponentV4;
    use crate::toolchain_lockfile_schema::version::v4::FeatureV4;
    use crate::toolchain_lockfile_schema::version::v4::SourceExcludeV4;
    use std::process::Command;

    const RAW_V4: &str = include_str!(
        "../../../../minecraft/core-liquid-template/build/lockfiles/1.19.2/schema-4.json"
    );
    const RAW_V2: &str = include_str!(
        "../../../../minecraft/core-liquid-template/build/lockfiles/1.19.2/schema-2.json"
    );
    const KEY: &str = "released/nested";

    struct Fixture {
        temp: tempfile::TempDir,
        artifacts: BTreeMap<String, ProjectedArtifact>,
        identity: CatalogProjectionIdentity,
        root: PathBuf,
    }

    impl Fixture {
        fn new(lock: &str, key: &str, environment: &str) -> Self {
            let temp = tempfile::Builder::new()
                .prefix("sfm native target fixture ")
                .tempdir()
                .unwrap();
            assert!(
                Command::new("git")
                    .args(["init", "--quiet"])
                    .arg(temp.path())
                    .status()
                    .unwrap()
                    .success()
            );
            fs::write(
                temp.path().join(".gitignore"),
                "/platform/minecraft/projections/ignored/\n",
            )
            .unwrap();
            let core = temp.path().join(CORE_ROOT);
            fs::create_dir_all(core.join("src/main/java")).unwrap();
            fs::write(core.join("feature-definitions.json"), "{}").unwrap();
            fs::write(temp.path().join(CATALOG_PATH), format!(r#"{{"{key}":{{"minecraft_version":"1.19.2","environment":"{environment}","features":[]}}}}"#)).unwrap();
            fs::write(core.join("src/main/java/Shared.java"), b"class Shared {}\n").unwrap();
            let mut metadata = CoreProjectInputs {
                schema_version: 1,
                targets: BTreeMap::from([(
                    "1.19.2".to_owned(),
                    BuildTargetMetadata {
                        java_major: 17,
                        loader: "forge".to_owned(),
                    },
                )]),
                source_rules: BTreeMap::new(),
                project_files: BTreeMap::new(),
            };
            for (output, bytes) in [
                ("build.gradle", &b"// fixture\n"[..]),
                ("settings.gradle", &b"rootProject.name = 'sfm-1.19.2'\n"[..]),
                (
                    "gradle.properties",
                    &b"minecraft_version=1.19.2\nneo_version=43.4.0\nmod_version=4.34.0\n"[..],
                ),
                ("gradlew", &b"fixture\n"[..]),
                ("gradlew.bat", &b"fixture\n"[..]),
                (LOCKFILE, lock.as_bytes()),
                (
                    "gradle/wrapper/gradle-wrapper.properties",
                    &b"distributionUrl=https\\://example.invalid/gradle-7.5-bin.zip\n"[..],
                ),
                ("gradle/wrapper/gradle-wrapper.jar", &b"\x00\xffwrapper"[..]),
            ] {
                let input = format!("build/common/{output}");
                let path = core.join(&input);
                fs::create_dir_all(path.parent().unwrap()).unwrap();
                fs::write(path, bytes).unwrap();
                metadata.project_files.insert(
                    output.to_owned(),
                    vec![InputVariant {
                        input,
                        when: InputPredicate::default(),
                        template: false,
                    }],
                );
            }
            fs::write(
                temp.path().join(CORE_METADATA_PATH),
                facet_json::to_string_pretty(&metadata).unwrap(),
            )
            .unwrap();
            let catalog = CoreCatalog::load(temp.path(), temp.path()).unwrap();
            let context = catalog.context(key).unwrap();
            let inventory = discover_core_source_files(&core).unwrap();
            let selection = select_core_inputs(&metadata, &context, &inventory).unwrap();
            let artifacts = collect_core_artifacts(&core, &selection, &context).unwrap();
            let identity = CatalogProjectionIdentity::from_catalog(&catalog.catalog, key).unwrap();
            let root =
                catalog_projection_root(&catalog.repo_root, &catalog.catalog, key, &artifacts)
                    .unwrap();
            Self {
                temp,
                artifacts,
                identity,
                root,
            }
        }

        fn publish(&self) {
            sync_catalog_projection(&self.root, &self.identity, &self.artifacts, SyncMode::Apply)
                .unwrap();
        }

        fn check(&self) -> Result<NativeProjectTarget> {
            preflight_native_project(
                self.temp.path(),
                self.temp.path(),
                &self.identity.projection_key,
                NATIVE_DEPENDENCY_PROFILE,
            )
        }
    }

    fn v4() -> ArtifactLockfileV4 {
        let ToolchainLockfileDocument::V4(lock) = parse_document(RAW_V4).unwrap() else {
            panic!("v4 fixture")
        };
        lock
    }

    #[test]
    fn schema_two_refusal_precedes_named_check_or_cache_creation() {
        let fixture = Fixture::new(RAW_V2, KEY, "release");
        let error = fixture.check().unwrap_err().to_string();
        assert!(error.contains("genuine released-input adapter"), "{error}");
        assert!(!fixture.root.exists());
        assert!(
            !fixture
                .temp
                .path()
                .join("platform/minecraft/build")
                .exists()
        );
    }

    #[test]
    fn schema_three_does_not_fabricate_a_declared_profile() {
        let raw =
            facet_json::to_string(&v4().effective_lockfile(NATIVE_DEPENDENCY_PROFILE).unwrap())
                .unwrap();
        let fixture = Fixture::new(&raw, KEY, "release");
        assert!(
            fixture
                .check()
                .unwrap_err()
                .to_string()
                .contains("schema 3 without a declared native profile")
        );
        assert!(!fixture.root.exists());
    }

    #[test]
    fn explicit_profile_mismatch_and_missing_profile_fail_without_writes() {
        let fixture = Fixture::new(RAW_V4, KEY, "release");
        assert!(
            preflight_native_project(fixture.temp.path(), fixture.temp.path(), KEY, "gradle")
                .unwrap_err()
                .to_string()
                .contains("explicit declared profile")
        );
        let mut lock = v4();
        lock.profiles
            .retain(|profile| profile.id != NATIVE_DEPENDENCY_PROFILE);
        let raw = lock.to_canonical_json().unwrap();
        let missing = Fixture::new(&raw, KEY, "release");
        assert!(
            missing
                .check()
                .unwrap_err()
                .to_string()
                .contains("does not declare native dependency profile")
        );
        assert!(!fixture.root.exists());
        assert!(!missing.root.exists());
    }

    #[test]
    fn profile_cannot_disable_platform_components() {
        let mut lock = v4();
        lock.features.push(FeatureV4 {
            id: "platform-disabled".to_owned(),
            requires: Vec::new(),
            components: vec![FeatureComponentV4 {
                dependency_id: "forge".to_owned(),
                component_id: "userdev".to_owned(),
            }],
            source_sets: Vec::new(),
            source_excludes: Vec::new(),
        });
        let fixture = Fixture::new(&lock.to_canonical_json().unwrap(), KEY, "release");
        let error = format!("{:#}", fixture.check().unwrap_err());
        assert!(
            error.contains("disables all components of platform"),
            "{error}"
        );
        assert!(!fixture.root.exists());
    }

    #[test]
    fn replacement_lock_alone_cannot_change_the_selected_platform() {
        let fixture = Fixture::new(RAW_V4, KEY, "release");
        fs::write(
            fixture
                .temp
                .path()
                .join(CORE_ROOT)
                .join("build/common/gradle.properties"),
            b"minecraft_version=1.19.2\nneo_version=43.3.0\n",
        )
        .unwrap();
        assert!(
            fixture
                .check()
                .unwrap_err()
                .to_string()
                .contains("select coordinated authored build inputs")
        );
        assert!(!fixture.root.exists());
    }

    #[test]
    fn profile_source_exclusion_cannot_escape_the_relative_source_boundary() {
        let mut lock = v4();
        lock.features.push(FeatureV4 {
            id: "unsafe-exclusion".to_owned(),
            requires: Vec::new(),
            components: Vec::new(),
            source_sets: vec!["main-java".to_owned()],
            source_excludes: vec![SourceExcludeV4 {
                source_set: "main-java".to_owned(),
                path: "safe/../Outside.java".to_owned(),
            }],
        });
        let fixture = Fixture::new(&lock.to_canonical_json().unwrap(), KEY, "release");
        let error = format!("{:#}", fixture.check().unwrap_err());
        assert!(error.contains("not a safe relative path"), "{error}");
        assert!(!fixture.root.exists());
    }

    #[test]
    fn exact_current_release_and_ignored_dev_are_read_only_preflight_targets() {
        for (key, environment) in [(KEY, "release"), ("ignored/nested", "dev")] {
            let fixture = Fixture::new(RAW_V4, key, environment);
            assert!(fixture.check().is_err());
            assert!(!fixture.root.exists());
            fixture.publish();
            let manifest_before = fs::read(fixture.root.join(MANIFEST_FILE)).unwrap();
            let target = fixture.check().unwrap();
            assert_eq!(target.receipt.projection_key, key);
            assert_eq!(target.receipt.dependency_profile, NATIVE_DEPENDENCY_PROFILE);
            assert_eq!(target.receipt.files.len(), fixture.artifacts.len());
            assert_eq!(
                target.minecraft_dir,
                fs::canonicalize(&fixture.root).unwrap()
            );
            assert!(!target.cache_dir.exists());
            assert_eq!(
                fs::read(fixture.root.join(MANIFEST_FILE)).unwrap(),
                manifest_before
            );
            assert!(target.receipt.to_json().unwrap().contains(RECEIPT_SCHEMA));
            target.recheck().unwrap();
        }
    }

    #[test]
    fn unknown_unsafe_key_and_parent_traversal_are_not_target_aliases() {
        let fixture = Fixture::new(RAW_V4, KEY, "release");
        for key in ["unknown", "../escape", "released/nested/child"] {
            assert!(
                preflight_native_project(
                    fixture.temp.path(),
                    fixture.temp.path(),
                    key,
                    NATIVE_DEPENDENCY_PROFILE
                )
                .is_err()
            );
        }
        assert!(
            preflight_native_project(
                &fixture.temp.path().join(".."),
                fixture.temp.path(),
                KEY,
                NATIVE_DEPENDENCY_PROFILE
            )
            .is_err()
        );
        assert!(!fixture.root.exists());
    }

    #[test]
    fn recheck_rejects_changed_source_metadata_registry_catalog_and_outputs() {
        for path in [
            format!("{CORE_ROOT}/src/main/java/Shared.java"),
            CORE_METADATA_PATH.to_owned(),
            FEATURE_DEFINITIONS_PATH.to_owned(),
            CATALOG_PATH.to_owned(),
            format!("platform/minecraft/projections/{KEY}/{MANIFEST_FILE}"),
            format!("platform/minecraft/projections/{KEY}/src/main/java/Shared.java"),
        ] {
            let fixture = Fixture::new(RAW_V4, KEY, "release");
            fixture.publish();
            let target = fixture.check().unwrap();
            let changed = fixture.temp.path().join(path);
            let mut bytes = fs::read(&changed).unwrap();
            bytes.extend_from_slice(b" \n");
            fs::write(&changed, &bytes).unwrap();
            assert!(target.recheck().is_err(), "changed {}", changed.display());
            assert_eq!(fs::read(changed).unwrap(), bytes);
            assert!(!target.cache_dir.exists());
        }
    }

    #[test]
    fn cache_identity_includes_exact_context_profile_lock_and_provenance() {
        let fixture = Fixture::new(RAW_V4, KEY, "release");
        fixture.publish();
        let target = fixture.check().unwrap();
        let original = target.receipt.cache_identity().unwrap();
        for field in 0..7 {
            let mut changed = target.receipt.clone();
            match field {
                0 => changed.projection_key.push_str("-other"),
                1 => changed.environment = ProjectionEnvironment::Dev,
                2 => changed.context_identity.push('0'),
                3 => changed.dependency_profile.push_str("-other"),
                4 => changed.lockfile_sha256.push('0'),
                5 => changed.provenance_sha256.push('0'),
                _ => changed.effective_source_exclusions_sha256.push('0'),
            }
            assert_ne!(changed.cache_identity().unwrap(), original);
        }
        assert!(target.cache_dir.starts_with(&target.minecraft_dir));
        assert!(
            target
                .cache_dir
                .to_string_lossy()
                .contains("native-project")
        );
    }

    #[cfg(unix)]
    #[test]
    fn projection_and_cache_symlink_ancestors_are_refused() {
        use std::os::unix::fs::symlink;
        let fixture = Fixture::new(RAW_V4, KEY, "release");
        let external = tempfile::tempdir().unwrap();
        symlink(
            external.path(),
            fixture.temp.path().join("platform/minecraft/projections"),
        )
        .unwrap();
        assert!(fixture.check().is_err());
        assert!(!external.path().join("released").exists());
        let fixture = Fixture::new(RAW_V4, KEY, "release");
        fixture.publish();
        symlink(external.path(), fixture.root.join("build")).unwrap();
        assert!(fixture.check().is_err());
        assert!(fs::read_dir(external.path()).unwrap().next().is_none());
    }

    #[cfg(windows)]
    #[test]
    fn projection_and_cache_junction_ancestors_are_refused() {
        fn junction(parent: &Path, child: &str, external: &Path) -> PathBuf {
            let result = Command::new("powershell.exe")
                .args(["-NoLogo", "-NoProfile", "-NonInteractive", "-Command",
                    "$testParent = (Resolve-Path -LiteralPath $env:SFM_NATIVE_TEST_PARENT -ErrorAction Stop).ProviderPath; $testTarget = (Resolve-Path -LiteralPath $env:SFM_NATIVE_TEST_TARGET -ErrorAction Stop).ProviderPath; $testPath = Join-Path -Path $testParent -ChildPath $env:SFM_NATIVE_TEST_CHILD; New-Item -ItemType Junction -Path $testPath -Target $testTarget -ErrorAction Stop | Out-Null"])
                // PowerShell 5's Join-Path does not accept Rust's verbatim
                // canonical prefix (\\?\C:). Simplify only these existing
                // fixture paths; production root/reparse checks stay intact.
                .env("SFM_NATIVE_TEST_PARENT", dunce::canonicalize(parent).unwrap())
                .env("SFM_NATIVE_TEST_TARGET", dunce::canonicalize(external).unwrap())
                .env("SFM_NATIVE_TEST_CHILD", child)
                .output().unwrap();
            assert!(
                result.status.success(),
                "{}",
                String::from_utf8_lossy(&result.stderr)
            );
            parent.join(child)
        }
        let external = tempfile::Builder::new()
            .prefix("sfm native external ")
            .tempdir()
            .unwrap();
        let fixture = Fixture::new(RAW_V4, KEY, "release");
        let path = junction(
            &fixture.temp.path().join("platform/minecraft"),
            "projections",
            external.path(),
        );
        assert!(fixture.check().is_err());
        fs::remove_dir(path).unwrap();
        assert!(fs::read_dir(external.path()).unwrap().next().is_none());
        let fixture = Fixture::new(RAW_V4, KEY, "release");
        fixture.publish();
        let path = junction(&fixture.root, "build", external.path());
        assert!(fixture.check().is_err());
        fs::remove_dir(path).unwrap();
        assert!(fs::read_dir(external.path()).unwrap().next().is_none());
    }
}
