#![allow(clippy::doc_markdown)]

use super::curseforge_cli::fetch_game_versions;
use crate::branch_targets::select_required_minecraft_versions;
use crate::cli::jar::BranchSelector;
use crate::curseforge::CurseforgeApiSecret;
use crate::curseforge::CurseforgeHttpClient;
use crate::curseforge::CurseforgeVersionRow;
use crate::paths::AppHome;
use crate::worktree::parse_version;
use facet::Facet;
use figue as args;
use std::fmt::Debug;
use std::fmt::Formatter;
use std::fmt::Result as FmtResult;
use tracing::info;

/// Arguments for listing Minecraft game versions from CurseForge.
#[derive(Facet)]
pub struct CurseforgeMinecraftVersionListArgs {
    /// Branch selector expression.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// CurseForge API token (optional for this endpoint).
    #[facet(default, sensitive, args::named)]
    pub token: Option<String>,

    /// Explicit author token reference; no implicit environment or 1Password lookup.
    #[facet(default, args::named)]
    pub op_secret: Option<String>,
}

impl CurseforgeMinecraftVersionListArgs {
    /// # Errors
    ///
    /// Returns an error if Minecraft versions cannot be queried.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        list_minecraft_versions(self.branch, self.token, self.op_secret, app_home)
    }
}

fn list_minecraft_versions(
    branch: BranchSelector,
    token: Option<String>,
    op_secret: Option<String>,
    app_home: &AppHome,
) -> eyre::Result<()> {
    let branch_query = branch.into_query(app_home)?;
    let selected_versions = select_required_minecraft_versions(&branch_query, app_home)?;
    let token_value = CurseforgeApiSecret::resolve(token, op_secret)?;
    let client = CurseforgeHttpClient::new(&token_value)?;

    let mut rows: Vec<CurseforgeVersionRow> = fetch_game_versions(&client)?
        .into_iter()
        .filter_map(|version| {
            let parsed = parse_version(&version.name)?;
            selected_versions
                .iter()
                .any(|selected| selected.as_str() == version.name)
                .then(|| {
                    (
                        version.id,
                        version.name,
                        version.slug.unwrap_or_default(),
                        parsed,
                    )
                })
        })
        .collect();

    rows.sort_by(|left, right| left.3.cmp(&right.3).then(left.1.cmp(&right.1)));

    if rows.is_empty() {
        info!("No Minecraft game versions matched --branch '{branch_query}'.");
        return Ok(());
    }

    info!("CurseForge Minecraft versions matching --branch '{branch_query}':");
    info!("id\tname\tslug");
    for (id, name, slug, _) in rows {
        info!("{id}\t{name}\t{slug}");
    }

    Ok(())
}

impl Debug for CurseforgeMinecraftVersionListArgs {
    fn fmt(&self, f: &mut Formatter<'_>) -> FmtResult {
        f.debug_struct("CurseforgeMinecraftVersionListArgs")
            .finish_non_exhaustive()
    }
}
