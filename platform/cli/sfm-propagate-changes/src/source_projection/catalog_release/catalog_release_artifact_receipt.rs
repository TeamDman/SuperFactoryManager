use super::CatalogReleasePlan;
use super::CatalogReleaseRequest;
use super::CatalogReleaseVersion;
use super::catalog_release_artifact_file::CatalogReleaseArtifactFile;
#[cfg(windows)]
use super::pending_catalog_release_receipt::PendingCatalogReleaseReceipt;
use crate::cancellation::CancellationToken;
use crate::jar_build::engine::expand_catalog_release_metadata;
use crate::jar_build::engine::verify_catalog_release_main_class;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::sfm_gradle_dot_properties_file_content::SfmGradleDotPropertiesFileContent;
use crate::source_projection::catalog_owned_project::CatalogOwnedProject;
#[cfg(test)]
use crate::source_projection::catalog_owned_project::collect_catalog_project;
use crate::source_projection::core_catalog::read_bounded_catalog_input;
use crate::source_projection::frozen_recipe_project::FrozenRecipeProjectReceipt;
use crate::source_projection::frozen_recipe_project::select_frozen_recipe_for_project;
use crate::source_projection::provenance::sha256;
use crate::source_projection::released_native_inputs::BUILD_CONFIGURATION_PATH;
use crate::source_projection::released_native_inputs::HISTORICAL_2612_CONSUMER_PATH;
use crate::source_projection::released_native_inputs::HISTORICAL_2612_CONSUMER_RECIPE;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use sha2::Digest as _;
use sha2::Sha256;
use std::collections::BTreeSet;
use std::ffi::OsStr;
use std::fs;
use std::io::Cursor;
use std::io::Error as IoError;
use std::io::ErrorKind;
use std::io::Read as _;
#[cfg(not(windows))]
use std::io::Write as _;
use std::path::Path;
#[cfg(not(windows))]
use tempfile::Builder;
use zip::ZipArchive;

pub(super) const MAX_JAR_BYTES: u64 = 128 * 1024 * 1024;
pub(super) const MAX_RECEIPT_BYTES: u64 = 1024 * 1024;
const SCHEMA: &str = "sfm:catalog_release_artifact@1";
const SCOPE: &str =
    "local_native_package_observation_not_authenticated_build_or_publication_authority";

/// Native packaging's exact local observation, admitted against current catalog
/// ownership when read. This unsigned sidecar is local provenance, not proof
/// against a writer able to replace the JAR and fabricate its receipt together.
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub(crate) struct CatalogReleaseArtifactReceipt {
    schema: String,
    scope: String,
    projection_key: String,
    project_dir: String,
    target_id: String,
    mod_version: String,
    ownership_identity: String,
    source_lock_sha256: String,
    recipe_id: String,
    recipe_configuration_path: String,
    recipe_review_sha256: String,
    /// Native producer observation; the current recipe/lock relationship is
    /// independently selected below, while this digest is not reconstructed.
    producer_observed_recipe_input_identity: String,
    package_input_identity: ContentHash,
    sdk_identity: String,
    producer_package: String,
    producer_version: String,
    producer_repository: String,
    producer_base_revision: String,
    producer_source_scope: String,
    producer_source_identity: String,
    jar_relative: String,
    jar_bytes: u64,
    jar_sha256: String,
}

impl CatalogReleaseArtifactReceipt {
    /// Record only an existing successful final native package while its caller
    /// still retains the original compiler, package-input and project authority.
    ///
    /// # Errors
    /// Rejects changed ownership/recipes, non-release metadata, an unsafe or
    /// incomplete JAR, a differing existing sidecar and cancellation. An exact
    /// existing receipt is accepted without replacement. Failure can retain a
    /// partial or complete receipt; independent verification decides whether
    /// the retained local observation qualifies for release preparation.
    pub(crate) fn publish_completed(
        project: &CatalogOwnedProject,
        jar: &Path,
        recipe: &FrozenRecipeProjectReceipt,
        package_input_identity: &str,
        sdk_identity: &str,
        cancellation: &CancellationToken,
    ) -> Result<()> {
        cancellation.bail_if_cancelled()?;
        project.recheck()?;
        ensure!(
            recipe.ownership == *project.receipt(),
            "package recipe and catalog ownership disagree"
        );
        ensure!(
            recipe.immutable_source_lock,
            "release artifact requires an immutable native recipe"
        );
        let properties = SfmGradleDotPropertiesFileContent::from_bytes(
            project.selected_input("gradle.properties")?.output_bytes(),
        )?;
        let version = CatalogReleaseVersion::parse(&properties.mod_version().to_string())?;
        let request = CatalogReleaseRequest::new(
            project.repo_root(),
            project.receipt().projection_key.clone(),
            version,
        )?;
        let metadata = CatalogReleasePlan::prepare(&request, cancellation)?;
        ensure!(
            metadata.ownership_identity() == project.ownership_identity()?,
            "package owner changed before receipt"
        );
        let relative = dunce::simplified(jar)
            .strip_prefix(dunce::simplified(project.project_root()))?
            .to_str()
            .ok_or_else(|| eyre::eyre!("release JAR path is not Unicode"))?
            .replace('\\', "/");
        let file =
            CatalogReleaseArtifactFile::read(project.project_root(), &relative, MAX_JAR_BYTES)?;
        let receipt = Self::from_completed(
            &metadata,
            project,
            &relative,
            file.bytes(),
            recipe,
            package_input_identity,
            sdk_identity,
        )?;
        receipt.validate(&metadata, project, &relative)?;
        Self::check_archive(&metadata, project, file.bytes(), cancellation)?;
        project.recheck()?;
        file.recheck()?;
        cancellation.bail_if_cancelled()?;
        receipt.publish(project.project_root(), cancellation)?;
        file.recheck()?;
        project.recheck()?;
        Ok(())
    }

    fn from_completed(
        metadata: &CatalogReleasePlan,
        project: &CatalogOwnedProject,
        relative: &str,
        bytes: &[u8],
        recipe: &FrozenRecipeProjectReceipt,
        package_input_identity: &str,
        sdk_identity: &str,
    ) -> Result<Self> {
        let recipe_configuration_path =
            if recipe.released_inputs.recipe_id == HISTORICAL_2612_CONSUMER_RECIPE {
                HISTORICAL_2612_CONSUMER_PATH
            } else {
                BUILD_CONFIGURATION_PATH
            };
        Ok(Self {
            schema: SCHEMA.to_owned(),
            scope: SCOPE.to_owned(),
            projection_key: metadata.projection_key().to_owned(),
            project_dir: metadata.project_dir().to_owned(),
            target_id: metadata.target_id().to_owned(),
            mod_version: metadata.mod_version().to_owned(),
            ownership_identity: metadata.ownership_identity().to_owned(),
            source_lock_sha256: sha256(
                project
                    .selected_input("sfm-toolchain.lock.json")?
                    .output_bytes(),
            ),
            recipe_id: recipe.released_inputs.recipe_id.clone(),
            recipe_configuration_path: recipe_configuration_path.to_owned(),
            recipe_review_sha256: recipe.released_inputs.recipe_review_sha256.clone(),
            producer_observed_recipe_input_identity: recipe.preparation_identity()?,
            package_input_identity: ContentHash::parse(package_input_identity)
                .map_err(|error| eyre::eyre!(error))?,
            sdk_identity: sdk_identity.to_owned(),
            producer_package: env!("CARGO_PKG_NAME").to_owned(),
            producer_version: env!("CARGO_PKG_VERSION").to_owned(),
            producer_repository: env!("CARGO_PKG_REPOSITORY").to_owned(),
            producer_base_revision: env!("GIT_REVISION").to_owned(),
            producer_source_scope:
                "packaging_admission_sources_and_dependency_lock_not_full_binary_signature"
                    .to_owned(),
            producer_source_identity: Self::producer_source_identity(),
            jar_relative: relative.to_owned(),
            jar_bytes: u64::try_from(bytes.len())?,
            jar_sha256: sha256(bytes),
        })
    }

    pub(super) fn validate(
        &self,
        metadata: &CatalogReleasePlan,
        project: &CatalogOwnedProject,
        relative: &str,
    ) -> Result<()> {
        Self::validate_relative(relative)?;
        let expected_configuration = if self.recipe_id == HISTORICAL_2612_CONSUMER_RECIPE {
            HISTORICAL_2612_CONSUMER_PATH
        } else {
            BUILD_CONFIGURATION_PATH
        };
        ensure!(
            self.schema == SCHEMA
                && self.scope == SCOPE
                && self.projection_key == metadata.projection_key()
                && self.project_dir == metadata.project_dir()
                && self.target_id == metadata.target_id()
                && self.mod_version == metadata.mod_version()
                && self.ownership_identity == metadata.ownership_identity()
                && self.ownership_identity == project.ownership_identity()?
                && self.jar_relative == relative,
            "native release receipt does not bind this current projection and JAR"
        );
        ensure!(
            self.source_lock_sha256
                == sha256(
                    project
                        .selected_input("sfm-toolchain.lock.json")?
                        .output_bytes()
                )
                && self.recipe_configuration_path == expected_configuration
                && self.recipe_review_sha256
                    == sha256(&read_bounded_catalog_input(
                        project.repo_root(),
                        &self.recipe_configuration_path
                    )?),
            "native release receipt source lock or recipe configuration changed"
        );
        ensure!(
            self.recipe_id == select_frozen_recipe_for_project(project)?
                && self.package_input_identity.algorithm == ContentHashAlgorithm::Blake3
                && Self::is_sha256(&self.producer_observed_recipe_input_identity)
                && Self::is_sha256(&self.sdk_identity)
                && self.producer_package == env!("CARGO_PKG_NAME")
                && self.producer_version == env!("CARGO_PKG_VERSION")
                && self.producer_repository == env!("CARGO_PKG_REPOSITORY")
                && self.producer_base_revision == env!("GIT_REVISION")
                && self.producer_source_scope
                    == "packaging_admission_sources_and_dependency_lock_not_full_binary_signature"
                && self.producer_source_identity == Self::producer_source_identity()
                && self.jar_bytes > 0
                && self.jar_bytes <= MAX_JAR_BYTES
                && Self::is_sha256(&self.jar_sha256),
            "native release receipt producer or packaging identity is invalid"
        );
        Ok(())
    }

    fn publish(&self, root: &Path, cancellation: &CancellationToken) -> Result<()> {
        Self::validate_relative(&self.jar_relative)?;
        let relative = Self::sidecar_relative(&self.jar_relative);
        let raw = facet_json::to_string_pretty(self)?;
        ensure!(
            u64::try_from(raw.len())? <= MAX_RECEIPT_BYTES,
            "release receipt exceeds byte limit"
        );
        if Self::existing_receipt_matches(root, &relative, raw.as_bytes())? {
            return Ok(());
        }
        cancellation.bail_if_cancelled()?;
        match Self::publish_new(root, &self.jar_relative, raw.as_bytes(), cancellation) {
            Ok(()) => Ok(()),
            Err(error) if error.downcast_ref::<IoError>().is_some_and(|cause| cause.kind() == ErrorKind::AlreadyExists) => {
                ensure!(
                    Self::existing_receipt_matches(root, &relative, raw.as_bytes())?,
                    "release receipt raced with another writer; use a fresh --output-name"
                );
                Ok(())
            }
            Err(error) => Err(error).wrap_err("cannot publish complete release receipt; preserve any new leaf and use a fresh --output-name"),
        }
    }

    #[cfg(windows)]
    fn publish_new(
        root: &Path,
        jar_relative: &str,
        bytes: &[u8],
        cancellation: &CancellationToken,
    ) -> Result<()> {
        // Parent LIST/read-only leases intentionally reject rename/link APIs.
        // CREATE_NEW with no sharing preserves those leases and prevents reads
        // until the writer closes. A failed operation may retain a partial leaf.
        PendingCatalogReleaseReceipt::new(root, jar_relative)?.finish(bytes, cancellation)
    }

    #[cfg(not(windows))]
    fn publish_new(
        root: &Path,
        jar_relative: &str,
        bytes: &[u8],
        cancellation: &CancellationToken,
    ) -> Result<()> {
        let destination = root.join(Self::sidecar_relative(jar_relative));
        let parent = destination
            .parent()
            .ok_or_else(|| eyre::eyre!("release receipt has no parent"))?;
        let mut temporary = Builder::new()
            .prefix("sfm-release-")
            .suffix(".json")
            .tempfile_in(parent)?;
        temporary.write_all(bytes)?;
        temporary.as_file_mut().sync_all()?;
        cancellation.bail_if_cancelled()?;
        temporary
            .persist_noclobber(destination)
            .map_err(|error| error.error)?;
        Ok(())
    }

    fn existing_receipt_matches(root: &Path, relative: &str, expected: &[u8]) -> Result<bool> {
        match fs::symlink_metadata(root.join(relative)) {
            Err(error) if error.kind() == ErrorKind::NotFound => return Ok(false),
            Err(error) => return Err(error).wrap_err("cannot inspect existing release receipt"),
            Ok(_) => {}
        }
        let current = CatalogReleaseArtifactFile::read(root, relative, MAX_RECEIPT_BYTES)?;
        ensure!(
            current.bytes() == expected,
            "existing release receipt differs; preserve the current JAR/sidecar and use a fresh --output-name"
        );
        current.recheck()?;
        Ok(true)
    }

    pub(super) fn sidecar_relative(jar_relative: &str) -> String {
        format!("{jar_relative}.sfm-release.json")
    }

    pub(super) fn validate_relative(relative: &str) -> Result<()> {
        let parts = relative.split('/').collect::<Vec<_>>();
        ensure!(
            parts.len() == 3
                && parts[0] == "build"
                && parts[1] == "libs"
                && parts[2].len() <= 128
                && Path::new(parts[2]).extension() == Some(OsStr::new("jar"))
                && parts[2]
                    .bytes()
                    .next()
                    .is_some_and(|byte| byte.is_ascii_alphanumeric())
                && parts[2]
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric() || b"._-".contains(&byte)),
            "release JAR must be a portable direct build/libs/*.jar leaf in the selected projection"
        );
        Ok(())
    }

    pub(super) fn is_sha256(value: &str) -> bool {
        value.strip_prefix("sha256:").is_some_and(|hex| {
            hex.len() == 64
                && hex
                    .bytes()
                    .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
        })
    }

    pub(super) fn jar_sha256(&self) -> &str {
        &self.jar_sha256
    }
    pub(super) fn jar_bytes(&self) -> u64 {
        self.jar_bytes
    }

    fn producer_source_identity() -> String {
        // These exact compiled source roles and dependency inputs identify the
        // producer implementation; this is deliberately not a binary signature.
        let mut digest = Sha256::new();
        for (name, bytes) in [
            (
                "engine",
                include_bytes!("../../jar_build/engine.rs").as_slice(),
            ),
            (
                "execute",
                include_bytes!("../../jar_build/engine_execute.rs").as_slice(),
            ),
            (
                "class_parser",
                include_bytes!("../../jar_build/engine_sources.rs").as_slice(),
            ),
            (
                "named_package",
                include_bytes!("../../jar_build/engine_named_jar.rs").as_slice(),
            ),
            (
                "named_compile",
                include_bytes!("../../jar_build/engine_named_compile.rs").as_slice(),
            ),
            (
                "neoform",
                include_bytes!("../../jar_build/engine_named_nfrt_compile.rs").as_slice(),
            ),
            (
                "release_packaging",
                include_bytes!("../../jar_build/engine_catalog_release_packaging.rs").as_slice(),
            ),
            (
                "release_receipt_hook",
                include_bytes!("../../jar_build/engine_catalog_release_receipt.rs").as_slice(),
            ),
            (
                "receipt",
                include_bytes!("catalog_release_artifact_receipt.rs").as_slice(),
            ),
            (
                "windows_receipt_publication",
                include_bytes!("pending_catalog_release_receipt.rs").as_slice(),
            ),
            (
                "retained_file",
                include_bytes!("catalog_release_artifact_file.rs").as_slice(),
            ),
            (
                "verification",
                include_bytes!("verified_catalog_release_artifact.rs").as_slice(),
            ),
            ("manifest", include_bytes!("../../../Cargo.toml").as_slice()),
            (
                "dependency_lock",
                include_bytes!("../../../Cargo.lock").as_slice(),
            ),
        ] {
            digest.update(name.as_bytes());
            digest.update([0]);
            digest.update(bytes);
            digest.update([0]);
        }
        format!("sha256:{:x}", digest.finalize())
    }

    pub(super) fn check_archive(
        metadata: &CatalogReleasePlan,
        project: &CatalogOwnedProject,
        bytes: &[u8],
        cancellation: &CancellationToken,
    ) -> Result<()> {
        let mut archive = ZipArchive::new(Cursor::new(bytes))?;
        ensure!(
            archive.len() <= 20_000,
            "release JAR exceeds its entry limit"
        );
        let mut seen = BTreeSet::new();
        let mut expanded = 0_u64;
        let mut has_mod_class = false;
        let mut has_loader_metadata = false;
        let loader_metadata = if matches!(metadata.target_id(), "1.21.0" | "1.21.1" | "26.1.2") {
            "META-INF/neoforge.mods.toml"
        } else {
            "META-INF/mods.toml"
        };
        let expected_metadata = expand_catalog_release_metadata(
            project
                .selected_input(&format!("src/main/resources/{loader_metadata}"))?
                .output_bytes(),
            project.selected_input("gradle.properties")?.output_bytes(),
        )?;
        for index in 0..archive.len() {
            cancellation.bail_if_cancelled()?;
            let mut entry = archive.by_index(index)?;
            let name = entry.name().to_owned();
            ensure!(
                !name.is_empty()
                    && !name.starts_with('/')
                    && !name.contains(['\\', ':', '\0'])
                    && name
                        .trim_end_matches('/')
                        .split('/')
                        .all(|part| !part.is_empty() && !matches!(part, "." | ".."))
                    && seen.insert(name.to_ascii_lowercase())
                    && entry
                        .unix_mode()
                        .is_none_or(|mode| mode & 0o170_000 != 0o120_000),
                "release JAR has duplicate, escaping or symbolic entries"
            );
            expanded = expanded
                .checked_add(entry.size())
                .ok_or_else(|| eyre::eyre!("release JAR size overflow"))?;
            ensure!(
                expanded <= 512 * 1024 * 1024 && entry.size() <= 64 * 1024 * 1024,
                "release JAR exceeds expanded byte limit"
            );
            let mut body = Vec::new();
            (&mut entry)
                .take(64 * 1024 * 1024 + 1)
                .read_to_end(&mut body)?;
            ensure!(
                u64::try_from(body.len())? == entry.size(),
                "release JAR entry length changed"
            );
            if name == "ca/teamdman/sfm/SFM.class" {
                verify_catalog_release_main_class(&body, metadata.java_major())?;
                has_mod_class = true;
            }
            if name == loader_metadata {
                ensure!(
                    body == expected_metadata,
                    "release JAR loader metadata differs from selected source/properties expansion"
                );
                has_loader_metadata = true;
            }
        }
        ensure!(
            has_mod_class && has_loader_metadata,
            "release JAR lacks SFM class or selected loader metadata"
        );
        Ok(())
    }

    #[cfg(test)]
    pub(crate) fn capture_fixture(
        request: &CatalogReleaseRequest,
        jar_relative: &str,
        cancellation: &CancellationToken,
    ) -> Result<()> {
        let project = collect_catalog_project(
            request.repo_root(),
            request.repo_root(),
            request.projection(),
        )?
        .check_current()?;
        let metadata = CatalogReleasePlan::prepare(request, cancellation)?;
        let file =
            CatalogReleaseArtifactFile::read(project.project_root(), jar_relative, MAX_JAR_BYTES)?;
        let configuration =
            read_bounded_catalog_input(project.repo_root(), BUILD_CONFIGURATION_PATH)?;
        let receipt = Self {
            schema: SCHEMA.to_owned(),
            scope: SCOPE.to_owned(),
            projection_key: metadata.projection_key().to_owned(),
            project_dir: metadata.project_dir().to_owned(),
            target_id: metadata.target_id().to_owned(),
            mod_version: metadata.mod_version().to_owned(),
            ownership_identity: project.ownership_identity()?,
            source_lock_sha256: sha256(
                project
                    .selected_input("sfm-toolchain.lock.json")?
                    .output_bytes(),
            ),
            recipe_id: select_frozen_recipe_for_project(&project)?,
            recipe_configuration_path: BUILD_CONFIGURATION_PATH.to_owned(),
            recipe_review_sha256: sha256(&configuration),
            producer_observed_recipe_input_identity: sha256(b"test native package receipt"),
            package_input_identity: ContentHash::parse(
                "blake3:0123456789012345678901234567890123456789",
            )
            .map_err(|error| eyre::eyre!(error))?,
            sdk_identity: sha256(b"test native SDK"),
            producer_package: env!("CARGO_PKG_NAME").to_owned(),
            producer_version: env!("CARGO_PKG_VERSION").to_owned(),
            producer_repository: env!("CARGO_PKG_REPOSITORY").to_owned(),
            producer_base_revision: env!("GIT_REVISION").to_owned(),
            producer_source_scope:
                "packaging_admission_sources_and_dependency_lock_not_full_binary_signature"
                    .to_owned(),
            producer_source_identity: Self::producer_source_identity(),
            jar_relative: jar_relative.to_owned(),
            jar_bytes: u64::try_from(file.bytes().len())?,
            jar_sha256: sha256(file.bytes()),
        };
        receipt.validate(&metadata, &project, jar_relative)?;
        Self::check_archive(&metadata, &project, file.bytes(), cancellation)?;
        receipt.publish(project.project_root(), cancellation)
    }
}
