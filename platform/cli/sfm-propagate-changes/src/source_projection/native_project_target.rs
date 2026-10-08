//! Read-only native preflight for an exact catalog-owned Minecraft project.
//!
//! This is a registered integration foundation, not a build/run entry point.
//! It never synchronizes outputs, resolves a JDK, acquires dependencies, creates
//! caches, or claims compilation/release parity. Existing named `Check` owns the
//! generated-output transaction. A later shared preparation hook must preserve
//! release check-only policy and guarded development preparation; do not copy
//! that policy here or disguise a projection as a Git branch.

use super::candidate_lock::checked_directory;
use super::catalog_owned_project::CatalogOwnedProject;
use super::catalog_owned_project::collect_catalog_project;
use super::projection_catalog::ProjectionEnvironment;
use super::projection_catalog::validate_projection_key;
use super::provenance::sha256;
use super::sync::CatalogProjectionIdentity;
use super::sync::ProjectedArtifact;
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
use std::fs;
use std::path::Path;
use std::path::PathBuf;

pub const NATIVE_DEPENDENCY_PROFILE: &str = "rust-toolchain";
const LOCKFILE: &str = "sfm-toolchain.lock.json";
const RECEIPT_SCHEMA: &str = "sfm:native_project_preflight@1";
const CACHE_PARENT: &str = "build/sfm-toolchain/native-project";

/// The captured `ForgeGradle` family; `NeoForm` needs its own development adapter.
pub(crate) fn development_forge_loader(target: &str, minecraft: &str) -> Result<&'static str> {
    ensure!(
        target == minecraft,
        "development Forge target/version mismatch"
    );
    match target {
        "1.19.2" | "1.19.4" | "1.20" => Ok("forge"),
        "1.20.1" => Ok("neoforge"),
        _ => eyre::bail!(
            "development native admission requires a captured Forge-family target: 1.19.2, 1.19.4, 1.20 or 1.20.1"
        ),
    }
}

/// Exact captured targets admitted by the two native development adapters.
pub(crate) fn development_native_loader(target: &str, minecraft: &str) -> Result<&'static str> {
    if matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1") {
        return development_forge_loader(target, minecraft);
    }
    let expected = match target {
        "1.20.2" | "1.20.3" | "1.20.4" | "1.21.1" | "26.1.2" => target,
        "1.21.0" => "1.21",
        _ => eyre::bail!("development target has no captured native adapter"),
    };
    ensure!(
        cfg!(windows) && minecraft == expected,
        "development NeoForm target/version requires its exact Windows adapter"
    );
    Ok("neoforge")
}

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
    /// Derive profile evidence from the retained checked owner, not a re-render.
    pub(crate) fn from_checked_project(
        project: &CatalogOwnedProject,
        declared_profile: &str,
    ) -> Result<Self> {
        ensure!(
            declared_profile == NATIVE_DEPENDENCY_PROFILE,
            "named native target requires the explicit rust-toolchain profile"
        );
        project.recheck()?;
        let profile = selected_profile(project.artifacts(), project.identity(), declared_profile)?;
        let target = target_from_checked(project, declared_profile, profile)?;
        project.recheck()?;
        Ok(target)
    }

    /// Reuse a retained owner, but freshly validate its exact input bytes.
    /// This avoids collecting and rendering the same project at each launch.
    pub(crate) fn recheck_with_project(&self, project: &CatalogOwnedProject) -> Result<()> {
        ensure!(
            self.receipt.dependency_profile == NATIVE_DEPENDENCY_PROFILE,
            "retained native target lost its rust-toolchain profile"
        );
        project.recheck()?;
        let profile = selected_profile(
            project.artifacts(),
            project.identity(),
            &self.receipt.dependency_profile,
        )?;
        let current = target_from_checked(project, &self.receipt.dependency_profile, profile)?;
        ensure!(current == *self, "retained native project target changed");
        project.recheck()?;
        Ok(())
    }

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
    let collected = collect_catalog_project(repo_root, invocation_dir, projection_key)?;
    // Preserve compatibility refusal before named ownership Check. A recipe is
    // a separate explicit adapter, never a fabricated schema-4 profile.
    let profile = selected_profile(
        collected.artifacts(),
        collected.identity(),
        declared_profile,
    )?;
    let checked = collected.check_current().wrap_err(
        "named native preflight needs a current owned project; prepare it through the existing named source workflow (release outputs are never auto-synced)",
    )?;
    let target = target_from_checked(&checked, declared_profile, profile)?;
    checked.recheck()?;
    Ok(target)
}

fn target_from_checked(
    checked: &CatalogOwnedProject,
    declared_profile: &str,
    profile: SelectedProfile,
) -> Result<NativeProjectTarget> {
    let owned = checked.receipt();
    // Keep the exact legacy @1 receipt field set and serialization order.
    // Added ownership inventory/template/byte evidence stays in its own receipt.
    let receipt = NativeProjectReceipt {
        schema: RECEIPT_SCHEMA.to_owned(),
        compilation: NativeValidationStatus::NotPerformed,
        release_compatibility: NativeValidationStatus::NotPerformed,
        projection_key: owned.projection_key.clone(),
        environment: owned.environment,
        target_id: owned.target_id.clone(),
        minecraft_version: owned.minecraft_version.clone(),
        java_major: owned.java_major,
        loader: owned.loader.clone(),
        context_identity: owned.context_identity.clone(),
        project_dir: owned.project_dir.clone(),
        dependency_profile: declared_profile.to_owned(),
        catalog_sha256: owned.catalog_sha256.clone(),
        feature_definitions_sha256: owned.feature_definitions_sha256.clone(),
        project_inputs_sha256: owned.project_inputs_sha256.clone(),
        lockfile_sha256: sha256(checked.selected_input(LOCKFILE)?.output_bytes()),
        effective_dependencies_sha256: profile.dependencies_sha256,
        effective_source_exclusions_sha256: profile.source_exclusions_sha256,
        files: owned
            .files
            .iter()
            .map(|(output, file)| {
                (
                    output.clone(),
                    NativeProjectFile {
                        authored_input: file.authored_input.clone(),
                        source_sha256: file.source_sha256.clone(),
                        output_sha256: file.output_sha256.clone(),
                    },
                )
            })
            .collect(),
    };
    let minecraft_dir = checked.project_root().to_path_buf();
    let cache_identity = receipt.cache_identity()?;
    let digest = cache_identity
        .strip_prefix("sha256:")
        .ok_or_else(|| eyre::eyre!("native cache identity has no SHA-256 prefix"))?;
    let cache_relative = format!("{CACHE_PARENT}/{digest}");
    inspect_cache_ancestors(&minecraft_dir, &cache_relative)?;
    Ok(NativeProjectTarget {
        repo_root: checked.repo_root().to_path_buf(),
        cache_dir: minecraft_dir.join(cache_relative),
        minecraft_dir,
        cache_identity,
        receipt,
    })
}

struct SelectedProfile {
    dependencies_sha256: String,
    source_exclusions_sha256: String,
}

pub(crate) fn validate_collected_native_profile(
    project: &super::catalog_owned_project::CollectedCatalogProject,
    profile: &str,
) -> Result<()> {
    ensure!(
        profile == NATIVE_DEPENDENCY_PROFILE,
        "unsupported native dependency profile"
    );
    selected_profile(project.artifacts(), project.identity(), profile)?;
    Ok(())
}

#[tracing::instrument(name = "native_target.selected_profile", skip_all)]
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

pub(crate) fn inspect_cache_ancestors(project: &Path, relative: &str) -> Result<()> {
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
    use crate::source_projection::core_catalog::CoreCatalog;
    use crate::source_projection::core_features::FEATURE_DEFINITIONS_PATH;
    use crate::source_projection::core_inputs::BuildTargetMetadata;
    use crate::source_projection::core_inputs::CORE_METADATA_PATH;
    use crate::source_projection::core_inputs::CORE_ROOT;
    use crate::source_projection::core_inputs::CoreProjectInputs;
    use crate::source_projection::core_inputs::InputPredicate;
    use crate::source_projection::core_inputs::InputVariant;
    use crate::source_projection::core_inputs::collect_core_artifacts;
    use crate::source_projection::core_inputs::discover_core_source_files;
    use crate::source_projection::core_inputs::select_core_inputs;
    use crate::source_projection::named_root::catalog_projection_root;
    use crate::source_projection::projection_catalog::CATALOG_PATH;
    use crate::source_projection::sync::SyncMode;
    use crate::source_projection::sync::sync_catalog_projection;
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
    fn development_forge_admission_rejects_newer_and_mismatched_targets() {
        for target in ["1.19.2", "1.19.4", "1.20", "1.20.1"] {
            let expected = if target == "1.20.1" {
                "neoforge"
            } else {
                "forge"
            };
            assert_eq!(development_forge_loader(target, target).unwrap(), expected);
            assert!(development_forge_loader(target, "different-version").is_err());
        }
        for (target, minecraft) in super::super::projection_catalog::SUPPORTED_TARGETS
            .iter()
            .skip(4)
        {
            assert!(development_forge_loader(target, minecraft).is_err());
        }
        assert!(development_forge_loader("unknown", "unknown").is_err());
    }

    #[test]
    fn checked_owner_target_matches_full_preflight_and_rejects_later_edits() -> Result<()> {
        let fixture = Fixture::new(RAW_V4, "ignored/nested", "dev");
        fixture.publish();
        let checked = collect_catalog_project(
            fixture.temp.path(),
            fixture.temp.path(),
            &fixture.identity.projection_key,
        )?
        .check_current()?;
        let retained =
            NativeProjectTarget::from_checked_project(&checked, NATIVE_DEPENDENCY_PROFILE)?;
        assert_eq!(retained, fixture.check()?);
        assert!(NativeProjectTarget::from_checked_project(&checked, "gradle").is_err());
        assert!(!fixture.root.join("build").exists());
        let changed = fixture.root.join(LOCKFILE);
        fs::write(&changed, b"{}")?;
        assert!(
            NativeProjectTarget::from_checked_project(&checked, NATIVE_DEPENDENCY_PROFILE).is_err()
        );
        assert_eq!(fs::read(changed)?, b"{}");
        Ok(())
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
            let manifest_before = fs::read(fixture.root.join("src/main/java/Shared.java")).unwrap();
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
                fs::read(fixture.root.join("src/main/java/Shared.java")).unwrap(),
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
                5 => changed.context_identity.push('0'),
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
    #[test]
    fn unowned_src_inputs_refuse_before_native_scans_or_cache_creation() {
        for (key, environment) in [(KEY, "release"), ("ignored/nested", "dev")] {
            for extra in [
                "src/main/java/Injected.java",
                "src/main/resources/injected.bin",
                "src/main/antlr/fixture/Injected.g4",
                "src/gametest/resources/injected.txt",
                "src/unregistered/input.custom",
            ] {
                let fixture = Fixture::new(RAW_V4, key, environment);
                fixture.publish();
                let target = fixture.check().unwrap();
                let manifest_before =
                    fs::read(fixture.root.join("src/main/java/Shared.java")).unwrap();
                let source = fixture.root.join(extra);
                fs::create_dir_all(source.parent().unwrap()).unwrap();
                fs::write(&source, b"unowned physical source input").unwrap();
                let error = format!("{:#}", fixture.check().unwrap_err());
                assert!(error.contains("extra unowned source inputs"), "{error}");
                assert!(target.recheck().is_err());
                assert_eq!(fs::read(&source).unwrap(), b"unowned physical source input");
                assert_eq!(
                    fs::read(fixture.root.join("src/main/java/Shared.java")).unwrap(),
                    manifest_before
                );
                assert!(!target.cache_dir.exists());
                assert!(!fixture.root.join("build").exists());
            }
        }
    }
}
