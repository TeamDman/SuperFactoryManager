use super::catalog_release_operation::CatalogReleaseOperation;
use super::catalog_release_plan_args::CatalogReleasePlanArgs;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::catalog_release::CatalogReleaseAction;
use crate::source_projection::catalog_release::CatalogReleaseAmendTargets;
use crate::source_projection::catalog_release::CatalogReleasePreparation;
use crate::source_projection::catalog_release::CatalogReleaseProviderRequest;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use figue as args;
use std::fs::File;
use std::io::Read as _;
use std::path::Path;
use std::path::PathBuf;

/// Prepare exact artifact/provider/copy/tag observations without network or writes.
#[derive(Debug, Facet)]
pub struct CatalogReleasePrepareArgs {
    #[facet(flatten)]
    pub selection: CatalogReleasePlanArgs,
    /// Projection-relative native-built JAR, for example build/libs/release.jar.
    #[facet(args::named)]
    pub jar: String,
    /// Explicit offline JSON array of `CurseForge` game-version rows; never fetched automatically.
    #[facet(args::named)]
    pub curseforge_game_versions: PathBuf,
    /// Create new provider objects or amend explicitly named existing objects.
    #[facet(default, args::named)]
    pub operation: CatalogReleaseOperation,
    /// Required only for amend; no latest-version discovery is performed.
    #[facet(default, args::named)]
    pub modrinth_version_id: Option<String>,
    /// Required only for amend; no latest-file discovery is performed.
    #[facet(default, args::named)]
    pub curseforge_file_id: Option<u64>,
}

impl CatalogReleasePrepareArgs {
    pub(super) fn invoke(
        self,
        invocation_dir: &Path,
        cancellation: &CancellationToken,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let request = self.selection.into_request(invocation_dir)?;
        let action = match (
            self.operation,
            self.modrinth_version_id,
            self.curseforge_file_id,
        ) {
            (CatalogReleaseOperation::Create, None, None) => CatalogReleaseAction::Create,
            (CatalogReleaseOperation::Amend, Some(version), Some(file)) => {
                CatalogReleaseAction::Amend(CatalogReleaseAmendTargets::new(version, file)?)
            }
            _ => eyre::bail!(
                "create rejects amendment IDs; amend requires both --modrinth-version-id and --curseforge-file-id"
            ),
        };
        let catalog = if self.curseforge_game_versions.is_absolute() {
            self.curseforge_game_versions
        } else {
            invocation_dir.join(self.curseforge_game_versions)
        };
        let mut file = File::open(catalog)?.take(8 * 1024 * 1024 + 1);
        let mut bytes = Vec::new();
        file.read_to_end(&mut bytes)?;
        ensure!(
            bytes.len() <= 8 * 1024 * 1024,
            "offline game-version catalog exceeds 8 MiB"
        );
        let providers = CatalogReleaseProviderRequest::from_bytes(action, &bytes)?;
        Ok(CliOutput::facet(CatalogReleasePreparation::prepare(
            &request,
            &self.jar,
            &providers,
            cancellation,
        )?))
    }
}
