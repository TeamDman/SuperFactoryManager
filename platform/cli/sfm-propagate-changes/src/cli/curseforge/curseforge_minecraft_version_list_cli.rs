#![allow(clippy::doc_markdown)]

use super::curseforge_cli::fetch_game_versions;
use crate::branch_targets::select_required_minecraft_versions;
use crate::cli::jar::BranchSelector;
use crate::curseforge::CurseforgeApiSecret;
use crate::curseforge::CurseforgeHttpClient;
use crate::curseforge::CurseforgeVersionRow;
use crate::worktree::parse_version;
use facet::Facet;
use figue as args;
use tracing::info;

/// Arguments for listing Minecraft game versions from CurseForge.
#[derive(Facet, Debug)]
pub struct CurseforgeMinecraftVersionListArgs {
    /// Branch selector expression.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// CurseForge API token (optional for this endpoint).
    #[facet(default, args::named)]
    pub token: Option<String>,

    /// 1Password secret reference used when token is omitted and env var is missing.
    #[facet(default, args::named)]
    pub op_secret: Option<String>,
}

impl CurseforgeMinecraftVersionListArgs {
    /// # Errors
    ///
    /// Returns an error if Minecraft versions cannot be queried.
    pub fn invoke(self) -> eyre::Result<()> {
        list_minecraft_versions(self.branch, self.token, self.op_secret)
    }
}

fn list_minecraft_versions(
    branch: BranchSelector,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    let branch_query = branch.into_query()?;
    let selected_versions = select_required_minecraft_versions(&branch_query)?;
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
