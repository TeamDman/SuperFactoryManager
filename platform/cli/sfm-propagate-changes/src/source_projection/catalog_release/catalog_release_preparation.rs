use super::CatalogGithubReleaseAmendPayload;
use super::CatalogGithubReleasePayload;
use super::CatalogReleaseAction;
use super::CatalogReleaseArtifactReadiness;
use super::CatalogReleaseCollectionPlan;
use super::CatalogReleasePlan;
use super::CatalogReleaseProviderPayloads;
use super::CatalogReleaseProviderRequest;
use super::CatalogReleaseRequest;
use super::CatalogReleaseTagPlan;
use super::VerifiedCatalogReleaseArtifact;
use super::catalog_release_compatibility::CatalogReleaseCompatibility;
use crate::cancellation::CancellationToken;
use crate::curseforge::CurseforgeAmendFilePayload;
use crate::curseforge::UploadMetadata;
use crate::git_read::head_revision;
use crate::modrinth::ModrinthAmendVersionPayload;
use crate::modrinth::ModrinthCreateVersionPayload;
use crate::sfm_mod_name::SfmModName;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::path::Path;
use std::process::Command;

/// One verified local candidate and offline provider handoffs. This is an
/// observation for review, never an executable publication authorization.
#[derive(Debug, Facet)]
pub struct CatalogReleasePreparation {
    schema: String,
    source_metadata: CatalogReleasePlan,
    artifact_receipt_sha256: String,
    artifact_readiness: CatalogReleaseArtifactReadiness,
    artifact_verification_scope: String,
    provider_catalog_sha256: String,
    github_repository: String,
    modrinth_project: String,
    curseforge_project: u64,
    provider_payloads: CatalogReleaseProviderPayloads,
    collection: CatalogReleaseCollectionPlan,
    tag: CatalogReleaseTagPlan,
    remote_state_verified: bool,
    publication_authorized: bool,
    source_writes_performed: bool,
    network_requests_performed: u64,
}

impl CatalogReleasePreparation {
    /// # Errors
    /// Rejects unproven/drifted artifacts, stale selected inputs, missing provider
    /// mappings, invalid amendment targets and unreadable Git revision state.
    pub fn prepare(
        request: &CatalogReleaseRequest,
        jar_relative: &str,
        providers: &CatalogReleaseProviderRequest,
        cancellation: &CancellationToken,
    ) -> Result<Self> {
        cancellation.bail_if_cancelled()?;
        let artifact = VerifiedCatalogReleaseArtifact::verify(request, jar_relative, cancellation)?;
        let metadata = artifact.metadata();
        let tag = Self::tag_plan(request.repo_root(), metadata)?;
        let provider_payloads = Self::payloads(metadata, providers, &tag)?;
        let collection = CatalogReleaseCollectionPlan {
            source: format!("{}/{}", metadata.project_dir(), artifact.relative_path()),
            destination_filename: format!(
                "{}-MC{}-{}.jar",
                SfmModName::VALUE,
                metadata.target_id(),
                metadata.mod_version()
            ),
            sha256: artifact.sha256().to_owned(),
            bytes: artifact.bytes(),
            performed: false,
        };
        let artifact_receipt_sha256 = artifact.receipt_sha256().to_owned();
        cancellation.bail_if_cancelled()?;
        artifact.recheck()?;
        cancellation.bail_if_cancelled()?;
        Ok(Self {
            schema: "sfm:catalog_release_preparation@1".to_owned(),
            source_metadata: artifact.into_metadata(),
            artifact_receipt_sha256,
            artifact_readiness: CatalogReleaseArtifactReadiness::VerifiedLocalNativeReceipt,
            artifact_verification_scope: "current_local_native_receipt_and_exact_jar_bytes_not_authenticated_build_or_gameplay".to_owned(),
            provider_catalog_sha256: providers.catalog_sha256().to_owned(),
            github_repository: "TeamDman/SuperFactoryManager".to_owned(),
            modrinth_project: "aecUorJQ".to_owned(),
            curseforge_project: 306_935,
            provider_payloads,
            collection,
            tag,
            remote_state_verified: false,
            publication_authorized: false,
            source_writes_performed: false,
            network_requests_performed: 0,
        })
    }

    fn payloads(
        metadata: &CatalogReleasePlan,
        request: &CatalogReleaseProviderRequest,
        tag: &CatalogReleaseTagPlan,
    ) -> Result<CatalogReleaseProviderPayloads> {
        let compatibility = CatalogReleaseCompatibility::for_metadata(metadata)?;
        let game_versions = request.resolve_names(&compatibility.curseforge_names)?;
        let name = format!(
            "Super Factory Manager MC{} v{}",
            metadata.target_id(),
            metadata.mod_version()
        );
        let changelog = metadata.changelog().content();
        let wrapped_changelog = format!("```\n{}\n```", changelog.trim());
        let github = CatalogGithubReleasePayload {
            tag_name: tag.name.clone(),
            target_commitish: tag.commit.clone(),
            name: format!("v{}", metadata.mod_version()),
            body: changelog.to_owned(),
            draft: false,
            prerelease: false,
        };
        Ok(match request.action() {
            CatalogReleaseAction::Create => CatalogReleaseProviderPayloads::Create {
                github,
                modrinth: Box::new(ModrinthCreateVersionPayload::for_release(
                    &name,
                    metadata.mod_version(),
                    &wrapped_changelog,
                    &[metadata.minecraft_version().to_owned()],
                    &compatibility.loader_slugs,
                    "aecUorJQ",
                )),
                curseforge: UploadMetadata {
                    changelog: wrapped_changelog.clone(),
                    changelog_type: "markdown".to_owned(),
                    display_name: name,
                    game_versions,
                    release_type: "release".to_owned(),
                },
            },
            CatalogReleaseAction::Amend(targets) => CatalogReleaseProviderPayloads::Amend {
                github_tag_name: github.tag_name,
                github: CatalogGithubReleaseAmendPayload {
                    name: github.name,
                    body: github.body,
                },
                modrinth_version_id: targets.modrinth_version_id().to_owned(),
                modrinth: ModrinthAmendVersionPayload {
                    changelog: wrapped_changelog.clone(),
                },
                curseforge: CurseforgeAmendFilePayload {
                    file_id: targets.curseforge_file_id(),
                    changelog: wrapped_changelog,
                    changelog_type: "markdown".to_owned(),
                    display_name: name,
                },
            },
        })
    }

    fn tag_plan(root: &Path, metadata: &CatalogReleasePlan) -> Result<CatalogReleaseTagPlan> {
        let commit = head_revision(root)?;
        let name = format!("{}-{}", metadata.mod_version(), metadata.target_id());
        let reference = format!("refs/tags/{name}");
        let status = Command::new("git")
            .args([
                "--no-optional-locks",
                "status",
                "--porcelain=v1",
                "--untracked-files=all",
            ])
            .current_dir(root)
            .output()
            .wrap_err("cannot observe candidate Git status")?;
        ensure!(
            status.status.success(),
            "cannot observe candidate Git status"
        );
        let existing = Command::new("git")
            .args(["rev-parse", "--verify", "--quiet", &reference])
            .current_dir(root)
            .output()
            .wrap_err("cannot observe candidate tag")?;
        let existing_object_id = if existing.status.success() {
            Some(String::from_utf8(existing.stdout)?.trim().to_owned())
        } else {
            ensure!(
                existing.status.code() == Some(1),
                "cannot observe candidate tag"
            );
            None
        };
        let working_tree_clean = status.stdout.is_empty();
        Ok(CatalogReleaseTagPlan {
            name,
            commit,
            working_tree_clean,
            existing_object_id,
            created: false,
        })
    }
}
