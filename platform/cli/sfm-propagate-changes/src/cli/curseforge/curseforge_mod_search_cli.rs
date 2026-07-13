use crate::curseforge::CurseforgeApiSecret;
use crate::curseforge::CurseforgeHttpClient;
use crate::curseforge::CurseforgeMod;
use crate::curseforge::CurseforgeModLoader;
use crate::curseforge::search_mods;
use crate::terminal_output::stdout_line;
use facet::Facet;
use figue as args;

/// Search `CurseForge` projects without changing an SFM lockfile.
#[derive(Facet, Debug)]
pub struct CurseforgeModSearchArgs {
    /// Name or keyword to search for.
    #[facet(args::positional)]
    pub query: String,
    /// Exact Minecraft version to match.
    #[facet(args::named)]
    pub minecraft: String,
    /// Mod loader to match.
    #[facet(args::named)]
    pub loader: CurseforgeModLoader,
    /// `CurseForge` Core API key; if omitted, configured secret sources are tried.
    #[facet(default, args::named)]
    pub api_key: Option<String>,
    /// Legacy API token fallback; prefer `--api-key` for Core API queries.
    #[facet(default, args::named)]
    pub token: Option<String>,
    /// 1Password secret reference used when no API key is configured.
    #[facet(default, args::named)]
    pub op_secret: Option<String>,
}

impl CurseforgeModSearchArgs {
    /// # Errors
    ///
    /// Returns an error when credentials cannot be resolved or the Core API query fails.
    pub fn invoke(self) -> eyre::Result<()> {
        let (key, _credential_source) =
            CurseforgeApiSecret::resolve_core(self.api_key, self.token, self.op_secret)?;
        let client = CurseforgeHttpClient::new_core_api(&key)?;
        let mods = search_mods(&client, &self.query, &self.minecraft, self.loader)?;
        print_mods(&mods)
    }
}

fn print_mods(mods: &[CurseforgeMod]) -> eyre::Result<()> {
    stdout_line("id\tslug\tname\tsummary\tdownloads\tupdated\tpopularity-rank")?;
    for mod_ in mods {
        stdout_line(format!(
            "{}\t{}\t{}\t{}\t{}\t{}\t{}",
            mod_.id,
            mod_.slug.as_deref().unwrap_or("-"),
            mod_.name,
            mod_.summary.as_deref().unwrap_or("-"),
            mod_.download_count,
            mod_.date_modified.as_deref().unwrap_or("-"),
            mod_.game_popularity_rank
                .map_or_else(|| "-".to_owned(), |rank| rank.to_string())
        ))?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn renders_stable_project_fields() {
        let mod_ = CurseforgeMod {
            id: 268_560.into(),
            name: "Mekanism".to_owned(),
            slug: Some("mekanism".to_owned()),
            summary: Some("Technology".to_owned()),
            download_count: 1_000,
            date_modified: Some("2026-07-13T00:00:00Z".to_owned()),
            date_released: None,
            game_popularity_rank: Some(5),
        };
        assert_eq!(mod_.id.to_string(), "268560");
        assert_eq!(mod_.slug.as_deref(), Some("mekanism"));
    }
}
